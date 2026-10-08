package me.skaffy.client.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.shader.lang.ShaderModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class ColoredLight implements AutoCloseable {
	static final int ITERATIONS = 3;
	private static final int SCANS_PER_FRAME = 32;
	private static final ConcurrentLinkedQueue<Long> CHANGED = new ConcurrentLinkedQueue<>();
	private static volatile boolean allChanged;
	private static volatile boolean listening;

	private static final String VERTEX = ShaderModule.PASS_VERTEX;
	private static final String FRAGMENT = """
			#version 330
			#extension GL_ARB_separate_shader_objects : require

			layout(std140) uniform SkLightSpread {
				ivec4 SkOrigin;
				ivec4 SkLayout;
				vec4 SkFilters[32];
			};

			uniform sampler2D SkLightData;
			uniform sampler2D SkPrevA;
			uniform sampler2D SkPrevB;
			uniform sampler2D SkPrevTint;

			layout(location = 0) in vec2 sk_passUv;
			layout(location = 0) out vec4 outA;
			layout(location = 1) out vec4 outB;
			layout(location = 2) out vec4 outTint;

			ivec2 skTexel(ivec3 local) {
				int tile = SkLayout.y;
				int columns = SkLayout.z;
				return ivec2((local.y % columns) * tile + 1 + local.x, (local.y / columns) * tile + 1 + local.z);
			}

			void main() {
				ivec2 texel = ivec2(gl_FragCoord.xy);
				ivec3 size = ivec3(SkOrigin.w, SkLayout.x, SkOrigin.w);
				int tile = SkLayout.y;
				ivec2 tileIndex = texel / tile;
				ivec2 inTile = texel - tileIndex * tile;
				int slice = tileIndex.y * SkLayout.z + tileIndex.x;
				if (slice >= size.y || inTile.x >= size.x + 2 || inTile.y >= size.z + 2) {
					outA = vec4(0.0);
					outB = vec4(0.0);
					outTint = vec4(1.0);
					return;
				}
				ivec3 local = ivec3((inTile.x - 1 + size.x) % size.x, slice, (inTile.y - 1 + size.z) % size.z);
				ivec3 relative = (local - SkOrigin.xyz + size) % size;
				vec4 data = texelFetch(SkLightData, skTexel(local), 0);
				int type = int(data.r * 255.0 + 0.5);
				float level = floor(data.g * 255.0 + 0.5) / 15.0;
				int opacity = int(data.b * 255.0 + 0.5);
				int filterIndex = int(data.a * 255.0 + 0.5);
				vec4 a = vec4(0.0);
				vec4 b = vec4(0.0);
				vec3 tint = vec3(1.0);
				float best = 0.0;
				ivec3 directions[6] = ivec3[](ivec3(1, 0, 0), ivec3(-1, 0, 0), ivec3(0, 1, 0), ivec3(0, -1, 0), ivec3(0, 0, 1), ivec3(0, 0, -1));
				for (int i = 0; i < 6; i++) {
					ivec3 next = relative + directions[i];
					if (any(lessThan(next, ivec3(0))) || any(greaterThanEqual(next, size))) {
						continue;
					}
					ivec2 at = skTexel((next + SkOrigin.xyz) % size);
					vec4 na = texelFetch(SkPrevA, at, 0);
					vec4 nb = texelFetch(SkPrevB, at, 0);
					a = max(a, na);
					b = max(b, nb);
					float sum = dot(na, vec4(1.0)) + dot(nb, vec4(1.0));
					if (sum > best) {
						best = sum;
						vec4 lit = texelFetch(SkPrevTint, at, 0);
						tint = lit.a > 0.5 ? lit.rgb / lit.a : vec3(1.0);
					}
				}
				float loss = float(max(opacity, 1)) / 15.0;
				a = max(a - loss, vec4(0.0));
				b = max(b - loss, vec4(0.0));
				if (opacity >= 15) {
					a = vec4(0.0);
					b = vec4(0.0);
				}
				if (type > 0 && level > 0.0) {
					if (type <= 4) {
						a[type - 1] = max(a[type - 1], level);
					} else {
						b[type - 5] = max(b[type - 5], level);
					}
					tint = vec3(1.0);
				}
				if (filterIndex > 0) {
					tint *= SkFilters[filterIndex - 1].rgb;
				}
				float open = opacity < 15 || (type > 0 && level > 0.0) ? 1.0 : 0.0;
				outA = a;
				outB = b;
				outTint = vec4(tint * open, open);
			}
			""";

	private @Nullable ShaderEffect owner;
	private int range;
	private int height;
	private int tile;
	private int columns;
	private int rows;
	private int width;
	private int atlasHeight;
	private final Reference2IntMap<BlockState> types = new Reference2IntOpenHashMap<>();
	private final Reference2IntMap<BlockState> levels = new Reference2IntOpenHashMap<>();
	private final Reference2IntMap<BlockState> filters = new Reference2IntOpenHashMap<>();
	private final Reference2IntMap<BlockState> packed = new Reference2IntOpenHashMap<>();
	private int originX = Integer.MIN_VALUE;
	private int originY;
	private int originZ;
	private byte @Nullable [] mirror;
	private boolean @Nullable [] dirtyTiles;
	private final LongOpenHashSet scanned = new LongOpenHashSet();
	private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
	private @Nullable ByteBuffer staging;

	private @Nullable GpuTexture data;
	private @Nullable GpuTextureView dataView;
	private final GpuTexture[][] light = new GpuTexture[2][3];
	private final GpuTextureView[][] lightViews = new GpuTextureView[2][3];
	private int current;
	private @Nullable MappableRingBuffer spreadBlock;
	private @Nullable CompiledRenderPipeline pipeline;
	private @Nullable CompletableFuture<CompiledRenderPipeline.Pending> compiling;
	private @Nullable EffectShaderSource source;

	ColoredLight() {
		packed.defaultReturnValue(-1);
	}

	public static void sectionChanged(int x, int y, int z) {
		if (listening) {
			CHANGED.add(SectionPos.asLong(x, y, z));
		}
	}

	public static void everythingChanged() {
		allChanged = true;
	}

	boolean active() {
		return owner != null && data != null;
	}

	@Nullable ShaderEffect owner() {
		return owner;
	}

	int range() {
		return range;
	}

	int columns() {
		return columns;
	}

	int atlasWidth() {
		return width;
	}

	int atlasHeight() {
		return atlasHeight;
	}

	Vec3 origin() {
		return new Vec3(originX, originY, originZ);
	}

	@Nullable GpuTextureView view(int which) {
		return active() ? lightViews[current][which] : null;
	}

	float @Nullable [] colorOf(BlockState state) {
		if (owner == null || !types.containsKey(state)) {
			return null;
		}

		return owner.color(owner.module.lights().get(types.getInt(state) - 1));
	}

	int levelOf(BlockState state) {
		return owner != null && types.containsKey(state) ? levels.getInt(state) : -1;
	}

	void configure(@Nullable ShaderEffect newOwner, int newRange) {
		if (newOwner == owner && newRange == range) {
			return;
		}

		boolean resized = newRange != range;
		owner = newOwner;
		range = newRange;
		types.clear();
		levels.clear();
		filters.clear();
		packed.clear();
		scanned.clear();
		queue.clear();
		originX = Integer.MIN_VALUE;

		if (owner == null) {
			closeGpu();
			mirror = null;
			listening = false;
			CHANGED.clear();
			return;
		}

		listening = true;

		List<ShaderModule.Light> lights = owner.module.lights();

		for (int i = 0; i < lights.size(); i++) {
			int type = i + 1;

			for (ShaderModule.LightGroup group : lights.get(i).groups()) {
				for (String selector : group.blocks()) {
					for (BlockState state : BlockSelectors.resolve(selector, "light")) {
						if (!types.containsKey(state)) {
							types.put(state, type);
							levels.put(state, levelFor(state, group));
						}
					}
				}
			}
		}

		List<ShaderModule.Light> filterList = owner.module.filters();

		for (int i = 0; i < filterList.size(); i++) {
			int filter = i + 1;

			for (ShaderModule.LightGroup group : filterList.get(i).groups()) {
				for (String selector : group.blocks()) {
					for (BlockState state : BlockSelectors.resolve(selector, "light filter")) {
						filters.putIfAbsent(state, filter);
					}
				}
			}
		}

		height = range / 2;
		tile = range + 2;
		columns = (int) Math.ceil(Math.sqrt(height));
		rows = (height + columns - 1) / columns;
		width = columns * tile;
		atlasHeight = rows * tile;

		if (resized || mirror == null) {
			closeGpu();
			mirror = new byte[width * atlasHeight * 4];
			dirtyTiles = new boolean[columns * rows];
			staging = ByteBuffer.allocateDirect(tile * tile * 4).order(ByteOrder.nativeOrder());
		} else {
			java.util.Arrays.fill(mirror, (byte) 0);
		}

		java.util.Arrays.fill(dirtyTiles, true);
		ensureGpu();
	}

	private static int levelFor(BlockState state, ShaderModule.LightGroup group) {
		if (group.levelFrom() != null) {
			return BlockSelectors.scaledLevel(state, group.levelFrom(), group.level());
		}

		return group.level() > 0 ? group.level() : state.getLightEmission();
	}

	private void ensureGpu() {
		GpuDevice device = RenderSystem.getDevice();

		if (data == null) {
			data = device.createTexture(() -> "Skaffy light blocks", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, width, atlasHeight, 1, 1);
			dataView = device.createTextureView(data);

			for (int set = 0; set < 2; set++) {
				for (int i = 0; i < 3; i++) {
					light[set][i] = device.createTexture(() -> "Skaffy light", 15, GpuFormat.RGBA8_UNORM, width, atlasHeight, 1, 1);
					lightViews[set][i] = device.createTextureView(light[set][i]);
					device.createCommandEncoder().clearColorTexture(light[set][i], new Vector4f(0, 0, 0, 0));
				}
			}
		}

		if (spreadBlock == null) {
			spreadBlock = new MappableRingBuffer(() -> "Skaffy light spread", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, 32 + 16 * 32);
		}

		if (pipeline == null && compiling == null) {
			source = new EffectShaderSource(Map.of(), false);
			Identifier id = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "light_spread");
			source.shader(id, VERTEX, FRAGMENT);
			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation(id)
					.withVertexShader(id)
					.withFragmentShader(id)
					.withBindGroupLayout(BindGroupLayout.builder()
							.withUniform("SkLightSpread", UniformType.UNIFORM_BUFFER)
							.withUniform("SkLightData", UniformType.COMBINED_IMAGE_SAMPLER)
							.withUniform("SkPrevA", UniformType.COMBINED_IMAGE_SAMPLER)
							.withUniform("SkPrevB", UniformType.COMBINED_IMAGE_SAMPLER)
							.withUniform("SkPrevTint", UniformType.COMBINED_IMAGE_SAMPLER)
							.build())
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.withCull(false);

			for (int i = 0; i < 3; i++) {
				builder.withColorTargetState(i, new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL));
			}

			compiling = device.compilePipeline(builder.build(), source, Util.backgroundExecutor());
		}
	}

	void update(ClientLevel level, Vec3 camera) {
		if (owner == null || mirror == null) {
			return;
		}

		if (pipeline == null && compiling != null && compiling.isDone()) {
			try {
				pipeline = compiling.join().finishCompile();
			} catch (RuntimeException e) {
				SkaffySAPIClient.LOGGER.error("Shaders: colored light didn't build", e);
			}

			compiling = null;

			if (pipeline == null) {
				SkaffySAPIClient.LOGGER.error("Shaders: colored light is off (its pass didn't build)");
			}
		}

		if (allChanged) {
			allChanged = false;
			scanned.clear();
			queue.clear();
			originX = Integer.MIN_VALUE;
		}

		move(camera);
		Long changed;

		while ((changed = CHANGED.poll()) != null) {
			if (inWindow(changed)) {
				queue.addAndMoveToFirst(changed);
			}
		}

		for (int i = 0; i < SCANS_PER_FRAME && !queue.isEmpty(); i++) {
			long section = queue.removeFirstLong();

			if (!scan(level, section)) {
				queue.addAndMoveToLast(section);
			}
		}

		upload();

		if (pipeline != null) {
			spread();
		}
	}

	private boolean inWindow(long section) {
		int x = SectionPos.x(section) * 16 - originX;
		int y = SectionPos.y(section) * 16 - originY;
		int z = SectionPos.z(section) * 16 - originZ;
		return originX != Integer.MIN_VALUE && x >= 0 && x < range && y >= 0 && y < height && z >= 0 && z < range;
	}

	private void move(Vec3 camera) {
		int sections = range / 16;
		int layers = height / 16;
		int newX = (SectionPos.blockToSectionCoord(camera.x) - sections / 2) * 16;
		int newY = (SectionPos.blockToSectionCoord(camera.y) - layers / 2) * 16;
		int newZ = (SectionPos.blockToSectionCoord(camera.z) - sections / 2) * 16;

		if (newX == originX && newY == originY && newZ == originZ) {
			return;
		}

		originX = newX;
		originY = newY;
		originZ = newZ;
		scanned.removeIf(section -> !inWindow(section));
		List<long[]> fresh = new ArrayList<>();
		int cx = SectionPos.blockToSectionCoord(camera.x);
		int cy = SectionPos.blockToSectionCoord(camera.y);
		int cz = SectionPos.blockToSectionCoord(camera.z);

		for (int x = 0; x < sections; x++) {
			for (int y = 0; y < layers; y++) {
				for (int z = 0; z < sections; z++) {
					int sx = originX / 16 + x;
					int sy = originY / 16 + y;
					int sz = originZ / 16 + z;
					long section = SectionPos.asLong(sx, sy, sz);

					if (!scanned.contains(section) && !queue.contains(section)) {
						clearSection(sx, sy, sz);
						fresh.add(new long[] {section, (long) (sx - cx) * (sx - cx) + (long) (sy - cy) * (sy - cy) * 4 + (long) (sz - cz) * (sz - cz)});
					}
				}
			}
		}

		queue.removeIf(section -> !inWindow(section));
		fresh.sort((a, b) -> Long.compare(a[1], b[1]));
		fresh.forEach(entry -> queue.add(entry[0]));
	}

	private void clearSection(int sx, int sy, int sz) {
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					write(sx * 16 + x, sy * 16 + y, sz * 16 + z, 0);
				}
			}
		}
	}

	private boolean scan(ClientLevel level, long section) {
		int sx = SectionPos.x(section);
		int sy = SectionPos.y(section);
		int sz = SectionPos.z(section);

		if (!inWindow(section)) {
			return true;
		}

		LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);

		if (chunk == null) {
			return false;
		}

		int index = chunk.getSectionIndexFromSectionY(sy);
		LevelChunkSection blocks = index >= 0 && index < chunk.getSectionsCount() ? chunk.getSection(index) : null;

		if (blocks == null || blocks.hasOnlyAir()) {
			clearSection(sx, sy, sz);
		} else {
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						write(sx * 16 + x, sy * 16 + y, sz * 16 + z, pack(blocks.getBlockState(x, y, z)));
					}
				}
			}
		}

		scanned.add(section);
		return true;
	}

	private int pack(BlockState state) {
		int value = packed.getInt(state);

		if (value >= 0) {
			return value;
		}

		int type = types.getInt(state);
		int level = type == 0 ? 0 : levels.getInt(state);
		int opacity = Math.clamp(state.getLightDampening(), 0, 15);
		int filter = filters.getInt(state);
		value = (type & 0xFF) | (level & 0xFF) << 8 | opacity << 16 | (filter & 0xFF) << 24;
		packed.put(state, value);
		return value;
	}

	private void write(int wx, int wy, int wz, int value) {
		int lx = Math.floorMod(wx, range);
		int ly = Math.floorMod(wy, height);
		int lz = Math.floorMod(wz, range);
		int tileX = ly % columns;
		int tileY = ly / columns;
		int baseX = tileX * tile;
		int baseY = tileY * tile;
		set(baseX + 1 + lx, baseY + 1 + lz, value);

		boolean edgeX = lx == 0 || lx == range - 1;
		boolean edgeZ = lz == 0 || lz == range - 1;
		int padX = lx == 0 ? range + 1 : 0;
		int padZ = lz == 0 ? range + 1 : 0;

		if (edgeX) {
			set(baseX + padX, baseY + 1 + lz, value);
		}

		if (edgeZ) {
			set(baseX + 1 + lx, baseY + padZ, value);
		}

		if (edgeX && edgeZ) {
			set(baseX + padX, baseY + padZ, value);
		}

		dirtyTiles[tileY * columns + tileX] = true;
	}

	private void set(int x, int y, int value) {
		int at = (y * width + x) * 4;
		mirror[at] = (byte) value;
		mirror[at + 1] = (byte) (value >> 8);
		mirror[at + 2] = (byte) (value >> 16);
		mirror[at + 3] = (byte) (value >> 24);
	}

	private void upload() {
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

		for (int tileY = 0; tileY < rows; tileY++) {
			for (int tileX = 0; tileX < columns; tileX++) {
				if (!dirtyTiles[tileY * columns + tileX]) {
					continue;
				}

				dirtyTiles[tileY * columns + tileX] = false;
				staging.clear();

				for (int y = 0; y < tile; y++) {
					staging.put(mirror, ((tileY * tile + y) * width + tileX * tile) * 4, tile * 4);
				}

				staging.flip();
				encoder.writeToTexture(data, staging, 0, 0, tileX * tile, tileY * tile, tile, tile);
			}
		}
	}

	private void spread() {
		spreadBlock.rotate();

		try (GpuBufferSlice.MappedView mapped = spreadBlock.currentBuffer().map(false, true)) {
			ByteBuffer buffer = mapped.data().order(ByteOrder.nativeOrder());
			buffer.putInt(0, Math.floorMod(originX, range)).putInt(4, Math.floorMod(originY, height)).putInt(8, Math.floorMod(originZ, range)).putInt(12, range);
			buffer.putInt(16, height).putInt(20, tile).putInt(24, columns).putInt(28, rows);
			List<ShaderModule.Light> filterList = owner.module.filters();

			for (int i = 0; i < 32; i++) {
				float[] color = i < filterList.size() ? owner.color(filterList.get(i)) : new float[] {1, 1, 1};
				buffer.putFloat(32 + i * 16, color[0]).putFloat(36 + i * 16, color[1]).putFloat(40 + i * 16, color[2]).putFloat(44 + i * 16, 1);
			}
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

		for (int step = 0; step < ITERATIONS; step++) {
			int next = 1 - current;
			RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> "Skaffy colored light")
					.withColorAttachment(lightViews[next][0])
					.withColorAttachment(lightViews[next][1])
					.withColorAttachment(lightViews[next][2])
					.build();

			try (RenderPass pass = encoder.createRenderPass(descriptor)) {
				pass.setPipeline(pipeline);
				pass.setUniform("SkLightSpread", spreadBlock.currentBuffer().slice());
				pass.setUniform("SkLightData", dataView, nearest);
				pass.setUniform("SkPrevA", lightViews[current][0], nearest);
				pass.setUniform("SkPrevB", lightViews[current][1], nearest);
				pass.setUniform("SkPrevTint", lightViews[current][2], nearest);
				pass.draw(3, 1, 0, 0);
			}

			current = next;
		}
	}

	private void closeGpu() {
		if (dataView != null) {
			dataView.close();
			data.close();
		}

		dataView = null;
		data = null;

		for (int set = 0; set < 2; set++) {
			for (int i = 0; i < 3; i++) {
				if (lightViews[set][i] != null) {
					lightViews[set][i].close();
					light[set][i].close();
				}

				lightViews[set][i] = null;
				light[set][i] = null;
			}
		}
	}

	@Override
	public void close() {
		configure(null, 0);

		if (spreadBlock != null) {
			spreadBlock.close();
			spreadBlock = null;
		}

		if (pipeline != null) {
			pipeline.close();
			pipeline = null;
		}

		if (compiling != null) {
			compiling.thenAccept(result -> Minecraft.getInstance().execute(() -> {
				CompiledRenderPipeline leftover = result.finishCompile();

				if (leftover != null) {
					leftover.close();
				}
			}));
			compiling = null;
		}

		if (source != null) {
			source.close();
			source = null;
		}
	}
}
