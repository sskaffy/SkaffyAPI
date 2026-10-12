package me.skaffy.client.shader;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import org.jspecify.annotations.Nullable;

public final class EntityMaterials {
	private static volatile boolean active;
	private static volatile @Nullable IdMaterials<EntityType<?>> entities;
	private static volatile @Nullable IdMaterials<Item> items;
	private static final ThreadLocal<int[]> CURRENT = ThreadLocal.withInitial(() -> new int[1]);

	private EntityMaterials() {
	}

	static void set(boolean on, @Nullable Map<Integer, List<String>> entitySelectors, @Nullable Map<Integer, List<String>> itemSelectors) {
		active = on && (entitySelectors != null || itemSelectors != null);
		entities = on ? IdMaterials.of(entitySelectors, Registries.ENTITY_TYPE) : null;
		items = on ? IdMaterials.of(itemSelectors, Registries.ITEM) : null;
	}

	private static boolean tagging() {
		return active && (RenderSystem.isRenderingLevel || ShaderRenderer.isDrawingHand());
	}

	public static int beginEntity(@Nullable EntityType<?> type) {
		int[] current = CURRENT.get();
		int previous = current[0];
		IdMaterials<EntityType<?>> lookup = entities;
		current[0] = type != null && lookup != null && tagging() ? lookup.of(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type)) : 0;
		return previous;
	}

	public static void endEntity(int previous) {
		CURRENT.get()[0] = previous;
	}

	public static int tagEntity(int overlay) {
		int entity = CURRENT.get()[0];
		return entity == 0 || !tagging() ? overlay : overlay | (entity & 0x7FF) << 4;
	}

	public static int tagItem(int overlay, @Nullable Item item) {
		IdMaterials<Item> lookup = items;

		if (item == null || lookup == null || !tagging()) {
			return overlay;
		}

		int number = lookup.of(BuiltInRegistries.ITEM.wrapAsHolder(item));
		return number == 0 ? overlay : overlay | (number & 0x7FF) << 20;
	}

	public interface ItemHolder {
		@Nullable Item skaffy$item();

		void skaffy$setItem(@Nullable Item item);
	}
}
