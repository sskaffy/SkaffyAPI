package me.skaffy.client.mixin;

import me.skaffy.client.shader.EntityMaterials;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.Item;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements EntityMaterials.ItemHolder {
	@Unique
	private @Nullable Item skaffy$item;

	@Override
	public @Nullable Item skaffy$item() {
		return skaffy$item;
	}

	@Override
	public void skaffy$setItem(@Nullable Item item) {
		skaffy$item = item;
	}

	@ModifyVariable(method = "submit", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int skaffy$tagItem(int overlayCoords) {
		return EntityMaterials.tagItem(overlayCoords, skaffy$item);
	}
}
