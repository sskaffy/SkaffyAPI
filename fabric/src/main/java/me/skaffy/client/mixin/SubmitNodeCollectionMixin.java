package me.skaffy.client.mixin;

import me.skaffy.client.shader.EntityMaterials;

import net.minecraft.client.renderer.SubmitNodeCollection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
	@ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int skaffy$tagModel(int overlayCoords) {
		return EntityMaterials.tagEntity(overlayCoords);
	}

	@ModifyVariable(method = "submitItem", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int skaffy$tagItem(int overlayCoords) {
		return EntityMaterials.tagEntity(overlayCoords);
	}
}
