package me.skaffy.client.model.gpu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;

import me.skaffy.client.model.ModelData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.BatchableSubmit;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.client.renderer.oit.OitStage;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class GpuModelFeature implements FeatureRenderer<GpuModelFeature.Submit> {
	public static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("Skaffy GPU model");
	private static final int UBO_SIZE = new com.mojang.blaze3d.buffers.Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().putIVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();

	private final DynamicGpuDataStorageMapped<Block> blocks = new DynamicGpuDataStorageMapped<>("Skaffy model UBO", UBO_SIZE, GpuBuffer.USAGE_UNIFORM, 4);
	private final List<Group> groups = new ArrayList<>();

	public record Draw(GpuModel model, @Nullable GpuModelInstance instance, int dynamic, int first, int count, int material, Matrix4f pose, int light, int overlay, int tint, int frame,
			boolean outline) {
	}

	public record Submit(Draw draw, float distanceToCameraSq) implements BatchableSubmit, TranslucentSubmit {
		@Override
		public Object batchKey() {
			return draw.model;
		}

		@Override
		public FeatureRendererType<Submit> featureType() {
			return TYPE;
		}
	}

	private record Group(GpuBufferSlice[] blocks, GpuBufferSlice transforms) {
	}

	@Override
	public void beginPrepare(FeatureFrameContext context) {
		blocks.endFrame();
	}

	@Override
	public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean strictlyOrdered) {
		Block[] data = new Block[submits.size()];

		for (int i = 0; i < data.length; i++) {
			Draw draw = submits.get(i).draw;

			if (draw.instance != null && draw.dynamic >= 0) {
				draw.instance.uploadPending();
			}

			data[i] = new Block(draw);
		}

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
		groups.add(new Group(data.length == 0 ? new GpuBufferSlice[0] : blocks.writeData(data), transforms));
	}

	@Override
	public void executeGroup(FeatureFrameContext context, @Nullable OitStage stage, RenderPass renderPass, int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
		Group group = groups.get(groupIndex);
		Minecraft minecraft = Minecraft.getInstance();

		for (int i = 0; i < submits.size(); i++) {
			Draw draw = submits.get(i).draw;
			GpuModel model = draw.model;
			GpuBuffer vertexBuffer = draw.dynamic >= 0 ? draw.instance == null ? null : draw.instance.buffer(draw.dynamic) : model.vertexBuffer();
			GpuBuffer indexBuffer = model.indexBuffer();

			if (vertexBuffer == null || indexBuffer == null || vertexBuffer.isClosed() || indexBuffer.isClosed()) {
				continue;
			}

			ModelData.Material material = model.data().materials().get(draw.material);
			ModelTextures.MaterialTextures textures = model.material(draw.material);

			if (textures == null) {
				continue;
			}

			boolean blended = material.alphaMode() == ModelData.AlphaMode.BLEND || (draw.tint >>> 24) < 255;
			RenderPipeline pipeline;

			if (draw.outline) {
				pipeline = ModelPipelines.OUTLINE;
			} else if (stage != null) {
				pipeline = ModelPipelines.oit(!material.doubleSided()).getPipeline(stage);
			} else {
				pipeline = ModelPipelines.pipeline(blended, !material.doubleSided());
			}

			renderPass.pushDebugGroup(() -> "Skaffy model " + model.model().definition().name());
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", group.transforms);
			renderPass.setUniform("SkaffyModel", group.blocks[i]);
			renderPass.setUniform("Sampler0", textures.base().view(), textures.base().sampler());
			renderPass.setUniform("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			ModelTextures.Texture map = textures.material() != null ? textures.material() : ModelTextures.white();
			renderPass.setUniform("Sampler3", map.view(), map.sampler());
			renderPass.setUniform("Sampler5", textures.emissive().view(), textures.emissive().sampler());
			renderPass.setVertexBuffer(0, vertexBuffer.slice());
			renderPass.setIndexBuffer(indexBuffer, IndexType.INT);
			renderPass.drawIndexed(draw.count, 1, draw.first, 0, 0);
			renderPass.popDebugGroup();
		}
	}

	@Override
	public void finishExecute(FeatureFrameContext context) {
		groups.clear();
	}

	@Override
	public void close() {
		blocks.close();
	}

	private record Block(Draw draw) implements DynamicGpuDataStorage.DynamicGpuData {
		@Override
		public void write(ByteBuffer buffer) {
			Draw draw = this.draw;
			ModelData.Material material = draw.model.data().materials().get(draw.material);
			Matrix4fc pose = draw.pose;
			Matrix3f normal = pose.normal(new Matrix3f());
			Matrix4f normal4 = new Matrix4f().set(normal);
			int tint = draw.tint;
			float alphaCutoff = material.alphaMode() == ModelData.AlphaMode.OPAQUE && (tint >>> 24) == 255 ? -1
					: material.alphaMode() == ModelData.AlphaMode.MASK ? material.alphaCutoff() : 0.004f;
			ModelData.TextureAnimation animation = material.animation();
			float uvScaleV = animation == null ? 1 : 1f / animation.frames();
			float uvOffsetV = animation == null ? 0 : draw.frame / (float) animation.frames();
			ModelTextures.MaterialTextures textures = draw.model.material(draw.material);
			boolean bakedGlow = textures != null && textures.bakedEmissive();
			float[] emissive = bakedGlow ? new float[] {1, 1, 1} : material.emissiveColor();
			boolean fullBright = material.fullBright();
			boolean specularGlossiness = material.specularKind() == ModelData.SpecularKind.SPECULAR_GLOSSINESS;
			boolean hasMap = textures != null && textures.material() != null;
			int light = draw.light;
			int overlayU = draw.overlay & 0xFFFF;
			boolean hurt = (draw.overlay >>> 16 & 0xFFFF) < 8;
			float overlayAlpha = hurt ? 178 / 255f : (int) ((1 - overlayU / 15f * 0.75f) * 255) / 255f;
			Std140Builder.intoBuffer(buffer)
					.putMat4f(pose)
					.putMat4f(normal4)
					.putVec4((tint >> 16 & 0xFF) / 255f * ((material.baseColor() >> 16 & 0xFF) / 255f), (tint >> 8 & 0xFF) / 255f * ((material.baseColor() >> 8 & 0xFF) / 255f),
							(tint & 0xFF) / 255f * ((material.baseColor() & 0xFF) / 255f), (tint >>> 24) / 255f * ((material.baseColor() >>> 24) / 255f))
					.putVec4(1, uvScaleV, 0, uvOffsetV)
					.putIVec4(fullBright ? 240 : light & 0xFFFF, fullBright ? 240 : light >>> 16 & 0xFFFF, 0, 0)
					.putVec4(1, hurt ? 0 : 1, hurt ? 0 : 1, overlayAlpha)
					.putVec4(alphaCutoff, fullBright ? 1 : 0, material.unlit() ? 1 : 0, material.normalScale())
					.putVec4(emissive[0], emissive[1], emissive[2], material.emissiveTexture() != null || bakedGlow ? 1 : 0)
					.putVec4(specularGlossiness ? 1 : 0, specularGlossiness ? 1 : material.metallic(), material.roughness(), hasMap ? 1 : 0)
					.putVec4(material.specularColor()[0], material.specularColor()[1], material.specularColor()[2], 0);
		}
	}
}
