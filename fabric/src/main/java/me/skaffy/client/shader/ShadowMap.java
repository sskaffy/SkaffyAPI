package me.skaffy.client.shader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import me.skaffy.client.mixin.LevelRendererAccessor;
import me.skaffy.client.mixin.ViewAreaInvoker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

final class ShadowMap implements AutoCloseable {
	private static final float CASTER_RANGE = 192;
	private static final float STEP = 2;

	private @Nullable GpuTexture depth;
	private @Nullable GpuTextureView depthView;
	private @Nullable GpuTexture solid;
	private @Nullable GpuTextureView solidView;
	private @Nullable GpuTexture color;
	private @Nullable GpuTextureView colorView;
	static final int CASCADES = 3;

	private int size;
	private final Matrix4f matrix = new Matrix4f();
	private final Matrix4f[] cascades = {new Matrix4f(), new Matrix4f(), new Matrix4f()};
	private final Matrix4f[] intoCells = {new Matrix4f(), new Matrix4f(), new Matrix4f()};
	private final float[] radii = new float[CASCADES];
	private float distance = 128;
	private int resolution = 2048;
	private @Nullable DynamicGpuDataStorageMapped<Section> sections;
	private boolean drawn;

	private record Section(float x, float y, float z) implements DynamicGpuDataStorage.DynamicGpuData {
		@Override
		public void write(ByteBuffer buffer) {
			buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(0);
		}
	}

	Matrix4f matrix() {
		return matrix;
	}

	Matrix4f cellMatrix(int cascade) {
		return intoCells[cascade];
	}

	float[] scales() {
		return new float[] {radii[2] / radii[0], radii[2] / radii[1], 1};
	}

	float distance() {
		return distance;
	}

	boolean drawn() {
		return drawn;
	}

	@Nullable GpuTextureView depthView() {
		return drawn ? depthView : null;
	}

	@Nullable GpuTextureView solidView() {
		return drawn ? solidView : null;
	}

	@Nullable GpuTextureView colorView() {
		return drawn ? colorView : null;
	}

	void prepare(Vector3f light, Vec3 camera, float distance, int resolution) {
		this.distance = distance;
		this.resolution = resolution;
		drawn = false;
		radii[2] = Math.clamp(Integer.highestOneBit(Math.max((int) Math.ceil(distance) - 1, 1)) * 2, 32, 512);
		radii[1] = Math.max(radii[2] / 2, 16);
		radii[0] = Math.max(radii[2] / 8, 8);
		Vector3f toLight = new Vector3f(light).normalize();
		Vector3f up = Math.abs(toLight.y) > 0.99F ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
		float depthRange = radii[2] + CASTER_RANGE;
		Matrix4f view = new Matrix4f().setLookAt(toLight.x * depthRange, toLight.y * depthRange, toLight.z * depthRange, 0, 0, 0, up.x, up.y, up.z);
		float cx = (float) (Math.floor(camera.x / STEP) * STEP + STEP / 2 - camera.x);
		float cy = (float) (Math.floor(camera.y / STEP) * STEP + STEP / 2 - camera.y);
		float cz = (float) (Math.floor(camera.z / STEP) * STEP + STEP / 2 - camera.z);

		for (int i = 0; i < CASCADES; i++) {
			float r = radii[i];
			cascades[i].setOrtho(-r, r, -r, r, 0, depthRange * 2, true).mul(view).translate(-cx, -cy, -cz);
			intoCells[i].translation(i % 2 - 0.5F, i / 2 - 0.5F, 0).scale(0.5F, 0.5F, 1).mul(cascades[i]);
		}

		matrix.set(cascades[2]);
	}

	private void ensure(int wanted) {
		if (depth != null && size == wanted) {
			return;
		}


		closeTextures();
		GpuDevice device = RenderSystem.getDevice();
		size = wanted;
		depth = device.createTexture(() -> "Skaffy shadow map", 15, GpuFormat.D32_FLOAT, size, size, 1, 1);
		depthView = device.createTextureView(depth);
		solid = device.createTexture(() -> "Skaffy shadow map (solid)", 15, GpuFormat.D32_FLOAT, size, size, 1, 1);
		solidView = device.createTextureView(solid);
		color = device.createTexture(() -> "Skaffy shadow color", 15, WorldPrograms.SHADOW_COLOR_FORMAT, size, size, 1, 1);
		colorView = device.createTextureView(color);
	}

	void render(ShaderEffect owner, FeatureRenderDispatcher.PreparedFrame features) {
		Minecraft minecraft = Minecraft.getInstance();
		LevelRendererAccessor levelRenderer = (LevelRendererAccessor) minecraft.levelRenderer;
		ViewArea viewArea = levelRenderer.skaffy$viewArea();
		SectionRenderDispatcher dispatcher = levelRenderer.skaffy$sectionRenderDispatcher();

		if (viewArea == null || dispatcher == null || owner.shadowTerrain.size() < ChunkSectionLayer.values().length || owner.shadowSeeThrough == null) {
			return;
		}

		ensure(resolution * 2);

		if (sections == null) {
			sections = new DynamicGpuDataStorageMapped<>("Skaffy shadow sections", 16, GpuBuffer.USAGE_UNIFORM, 1024);
		}

		sections.endFrame();
		Vec3 camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos;
		List<Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>>> draws = new ArrayList<>();
		List<Section> offsets = new ArrayList<>();
		int largestIndexCount = collect(viewArea, dispatcher, camera, draws, offsets);
		GpuBufferSlice[] slices = sections.writeData(offsets.toArray(Section[]::new));
		RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

		if (largestIndexCount > 0) {
			autoIndices.requestIndexCount(largestIndexCount);
			RenderSystem.resizeAllAutoStorageIndexBuffers();
		}

		GpuTextureView atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

		try {
			for (int i = 0; i < CASCADES; i++) {
				ShaderRenderer.shadowCascade(i);
				RenderPassDescriptor solidPass = RenderPassDescriptor.builder(() -> "Skaffy shadow map")
						.withColorAttachment(colorView, Optional.of(new Vector4f(1, 1, 1, 0)))
						.withDepthAttachment(depthView, OptionalDouble.of(1.0))
						.withRenderArea(area(i))
						.build();

				try (RenderPass pass = encoder.createRenderPass(solidPass)) {
					RenderSystem.bindDefaultUniforms(pass);
					drawLayer(pass, owner, ChunkSectionLayer.SOLID, draws.get(i), slices, atlas, sampler, autoIndices, largestIndexCount);
					drawLayer(pass, owner, ChunkSectionLayer.CUTOUT, draws.get(i), slices, atlas, sampler, autoIndices, largestIndexCount);
					ShaderRenderer.drawingShadow(owner);

					try {
						features.executeSolid(pass);
					} finally {
						ShaderRenderer.drawingShadow(null);
					}
				}
			}

			encoder.copyTextureToTexture(depth, solid, 0, 0, 0, 0, 0, size, size);

			for (int i = 0; i < CASCADES; i++) {
				ShaderRenderer.shadowCascade(i);
				RenderPassDescriptor seeThroughPass = RenderPassDescriptor.builder(() -> "Skaffy shadow map (see-through)")
						.withColorAttachment(colorView)
						.withDepthAttachment(depthView)
						.withRenderArea(area(i))
						.build();

				try (RenderPass pass = encoder.createRenderPass(seeThroughPass)) {
					RenderSystem.bindDefaultUniforms(pass);
					drawLayer(pass, owner.shadowSeeThrough, ChunkSectionLayer.CUTOUT, draws.get(i), slices, atlas, sampler, autoIndices, largestIndexCount);
					drawLayer(pass, owner, ChunkSectionLayer.TRANSLUCENT, draws.get(i), slices, atlas, sampler, autoIndices, largestIndexCount);
				}
			}
		} finally {
			ShaderRenderer.shadowCascade(-1);
		}

		drawn = true;
	}

	private RenderPass.RenderArea area(int cascade) {
		return new RenderPass.RenderArea(cascade % 2 * resolution, cascade / 2 * resolution, resolution, resolution);
	}

	private int collect(ViewArea viewArea, SectionRenderDispatcher dispatcher, Vec3 camera,
			List<Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>>> draws, List<Section> offsets) {
		for (int i = 0; i < CASCADES; i++) {
			Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> layers = new EnumMap<>(ChunkSectionLayer.class);

			for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
				layers.put(layer, new ArrayList<>());
			}

			draws.add(layers);
		}

		int reach = (int) Math.ceil(radii[2] / 16) + 1;
		int centerX = SectionPos.blockToSectionCoord(camera.x);
		int centerZ = SectionPos.blockToSectionCoord(camera.z);
		int largest = 0;
		Vector4f clip = new Vector4f();
		boolean[] reaches = new boolean[CASCADES];
		dispatcher.lock();

		try {
			for (int x = centerX - reach; x <= centerX + reach; x++) {
				for (int z = centerZ - reach; z <= centerZ + reach; z++) {
					for (int y = viewArea.minSectionY(); y <= viewArea.maxSectionY(); y++) {
						SectionRenderDispatcher.RenderSection section = ((ViewAreaInvoker) viewArea).skaffy$getRenderSection(SectionPos.asLong(x, y, z));

						if (section == null) {
							continue;
						}

						SectionMesh mesh = section.getSectionMesh();
						BlockPos origin = section.getRenderOrigin();
						float rx = (float) (origin.getX() - camera.x);
						float ry = (float) (origin.getY() - camera.y);
						float rz = (float) (origin.getZ() - camera.z);
						boolean any = false;

						for (int i = 0; i < CASCADES; i++) {
							float margin = 14 / radii[i];
							cascades[i].transform(clip.set(rx + 8, ry + 8, rz + 8, 1));
							reaches[i] = Math.abs(clip.x) <= 1 + margin && Math.abs(clip.y) <= 1 + margin && clip.z >= -margin && clip.z <= 1 + margin;
							any |= reaches[i];
						}

						if (!any) {
							continue;
						}

						int index = -1;

						for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
							SectionMesh.SectionDraw draw = mesh.getSectionDraw(layer);
							SectionRenderDispatcher.RenderSectionBufferSlice slice = dispatcher.getRenderSectionSlice(mesh, layer);

							if (draw == null || slice == null || draw.hasCustomIndexBuffer() && slice.indexBuffer() == null) {
								continue;
							}

							if (index < 0) {
								index = offsets.size();
								offsets.add(new Section(rx, ry, rz));
							}

							int at = index;
							int baseVertex = (int) (slice.vertexBufferOffset() / layer.vertexFormat().getVertexSize());
							GpuBuffer indexBuffer = draw.hasCustomIndexBuffer() ? slice.indexBuffer() : null;
							IndexType indexType = draw.hasCustomIndexBuffer() ? draw.indexType() : null;
							int firstIndex = draw.hasCustomIndexBuffer() ? (int) (slice.indexBufferOffset() / draw.indexType().bytes) : 0;

							if (!draw.hasCustomIndexBuffer()) {
								largest = Math.max(largest, draw.indexCount());
							}

							RenderPass.Draw<GpuBufferSlice[]> sectionDraw = new RenderPass.Draw<>(0, slice.vertexBuffer(), indexBuffer, indexType, firstIndex,
									draw.indexCount(), baseVertex, (ubos, uploader) -> uploader.setUniform("SkSection", ubos[at]));

							for (int i = 0; i < CASCADES; i++) {
								if (reaches[i]) {
									draws.get(i).get(layer).add(sectionDraw);
								}
							}
						}
					}
				}
			}
		} finally {
			dispatcher.unlock();
		}

		return largest;
	}

	private void drawLayer(RenderPass pass, ShaderEffect owner, ChunkSectionLayer layer, Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> draws,
			GpuBufferSlice[] slices, GpuTextureView atlas, GpuSampler sampler, RenderSystem.AutoStorageIndexBuffer autoIndices, int largestIndexCount) {
		drawLayer(pass, owner.shadowTerrain.get(layer), layer, draws, slices, atlas, sampler, autoIndices, largestIndexCount);
	}

	private void drawLayer(RenderPass pass, @Nullable CompiledRenderPipeline pipeline, ChunkSectionLayer layer,
			Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> draws, GpuBufferSlice[] slices, GpuTextureView atlas, GpuSampler sampler,
			RenderSystem.AutoStorageIndexBuffer autoIndices, int largestIndexCount) {
		List<RenderPass.Draw<GpuBufferSlice[]>> layerDraws = draws.get(layer);

		if (layerDraws.isEmpty() || pipeline == null) {
			return;
		}

		pass.setPipeline(pipeline);
		pass.setUniform("Sampler0", atlas, sampler);
		pass.drawMultipleIndexed(layerDraws, largestIndexCount == 0 ? null : autoIndices.getBuffer(), largestIndexCount == 0 ? null : autoIndices.type(), List.of("SkSection"), slices);
	}

	private void closeTextures() {
		for (AutoCloseable closeable : new AutoCloseable[] {depthView, depth, solidView, solid, colorView, color}) {
			try {
				if (closeable != null) {
					closeable.close();
				}
			} catch (Exception ignored) {
			}
		}

		depth = null;
		depthView = null;
		solid = null;
		solidView = null;
		color = null;
		colorView = null;
		size = 0;
	}

	@Override
	public void close() {
		closeTextures();

		if (sections != null) {
			sections.close();
			sections = null;
		}
	}
}
