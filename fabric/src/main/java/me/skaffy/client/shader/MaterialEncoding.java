package me.skaffy.client.shader;

import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

public final class MaterialEncoding {
	private static volatile boolean active;
	private static volatile Reference2IntMap<BlockState> materials = new Reference2IntOpenHashMap<>();
	private static final ThreadLocal<int[]> CURRENT = ThreadLocal.withInitial(() -> new int[] {-1, 0, 0});

	private MaterialEncoding() {
	}

	public static boolean active() {
		return active;
	}

	static void set(@Nullable Map<Integer, List<String>> selectors) {
		Reference2IntMap<BlockState> resolved = new Reference2IntOpenHashMap<>();

		if (selectors != null) {
			for (boolean tags : new boolean[] {true, false}) {
				for (Map.Entry<Integer, List<String>> entry : selectors.entrySet()) {
					for (String selector : entry.getValue()) {
						if (BlockSelectors.isTag(selector) == tags) {
							BlockSelectors.resolve(selector, "block material " + entry.getKey()).forEach(state -> resolved.put(state, (int) entry.getKey()));
						}
					}
				}
			}
		}

		boolean nowActive = selectors != null;

		if (nowActive == active && resolved.equals(materials)) {
			return;
		}

		materials = resolved;
		active = nowActive;
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level != null) {
			minecraft.levelExtractor.allChanged();
		}
	}

	public static void begin(BlockState state, float blockY) {
		if (active) {
			int[] current = CURRENT.get();
			current[0] = materials.getOrDefault(state, 0);
			current[1] = Math.round(blockY * 1000);
			current[2] = 0;
		}
	}

	public static void beginFluid(FluidState fluid, int blockY) {
		if (active) {
			int[] current = CURRENT.get();
			current[0] = materials.getOrDefault(fluid.createLegacyBlock(), 0);
			current[1] = (blockY & 15) * 1000;
			current[2] = 1;
		}
	}

	public static int encodeColor(int color) {
		int[] current = CURRENT.get();
		return current[0] >= 0 && current[2] == 1 ? color | 0xFF000000 : color;
	}

	public static void end() {
		CURRENT.get()[0] = -1;
	}

	public static void separateAo(BlockAndTintGetter level, BakedQuad quad, QuadInstance instance, boolean smooth) {
		if (!active || CURRENT.get()[0] < 0) {
			return;
		}

		Direction override = quad.materialInfo().shadeDirectionOverride();
		Direction direction = override != null ? override : quad.direction();
		float shade = Math.max(level.cardinalLighting().byFace(direction), 0.01f);

		for (int vertex = 0; vertex < 4; vertex++) {
			float ao = smooth ? Math.clamp(ARGB.red(instance.getColor(vertex)) / 255f / shade, 0f, 1f) : 1f;
			int packed = direction.ordinal() * 32 + Math.round(ao * 31);
			instance.setColor(vertex, ARGB.color(packed, 255, 255, 255));
		}
	}

	public static int encode(int light, float y, float nx, float ny, float nz) {
		int[] current = CURRENT.get();
		int material = current[0];

		if (material < 0) {
			return light;
		}

		int face = face(nx, ny, nz);
		int top = y - current[1] / 1000f > 0.5f ? 1 : 0;
		int u = light & 0xFF | (material & 0xFF) << 8;
		int v = light >>> 16 & 0xFF | (material >> 8 & 0x0F) << 8 | top << 12 | face << 13;
		return u | v << 16;
	}

	private static int face(float x, float y, float z) {
		float ax = Math.abs(x);
		float ay = Math.abs(y);
		float az = Math.abs(z);

		if (ax < 0.01f && ay < 0.01f && az < 0.01f) {
			return 0;
		}

		if (ay >= ax && ay >= az) {
			return y < 0 ? 1 : 2;
		}

		if (az >= ax) {
			return z < 0 ? 3 : 4;
		}

		return x < 0 ? 5 : 6;
	}
}
