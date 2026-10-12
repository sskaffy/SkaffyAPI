package me.skaffy.client.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

import me.skaffy.client.shader.lang.ShaderModule;

import org.jspecify.annotations.Nullable;

final class FileValues {
	private static final float BLEND_MILLIS = 2000;

	private final @Nullable IdMaterials<Biome> biomes;
	private final @Nullable IdMaterials<Item> items;
	private final ShaderModule module;
	private int biome = -1;
	private int previousBiome;
	private long changed;

	FileValues(ShaderModule module) {
		this.module = module;
		this.biomes = IdMaterials.of(module.ids().get("Biomes"), Registries.BIOME);
		this.items = IdMaterials.of(module.ids().get("Items"), Registries.ITEM);
	}

	void update(UniformValues values) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;

		if (level == null) {
			return;
		}

		long now = Util.getMillis();

		if (biomes != null) {
			BlockPos eye = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.blockPos;
			int current = biomes.of(level.getBiome(eye));

			if (biome == -1) {
				biome = current;
				previousBiome = current;
				changed = now - (long) BLEND_MILLIS;
			} else if (current != biome) {
				previousBiome = biome;
				biome = current;
				changed = now;
			}

			values.setFileValue("$biome", biome);
			values.setFileValue("$previousBiome", previousBiome);
			values.setFileValue("$biomeBlend", Math.clamp((now - changed) / BLEND_MILLIS, 0, 1));
		} else {
			values.setFileValue("$biomeBlend", 1);
		}

		LocalPlayer player = minecraft.player;

		if (items != null && player != null) {
			values.setFileValue("$heldItem", items.of(BuiltInRegistries.ITEM.wrapAsHolder(player.getMainHandItem().getItem())));
			values.setFileValue("$heldItemOffhand", items.of(BuiltInRegistries.ITEM.wrapAsHolder(player.getOffhandItem().getItem())));
		}

		int dimension = IdMaterials.dimension(module.ids().get("Dimensions"), level.dimension().identifier());
		values.setFileValue("$dimension", dimension == 0 ? -1 : dimension);
	}
}
