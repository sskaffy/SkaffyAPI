package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import me.skaffy.client.look.PlayerLookHooks;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("HEAD"), cancellable = true)
	private static void skaffy$entityLook(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> cir) {
		EntityRenderState state = PlayerLookHooks.previewState(entity);

		if (state != null) {
			cir.setReturnValue(state);
		}
	}

	@ModifyReturnValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
	private static EntityRenderState skaffy$previewSize(EntityRenderState state) {
		PlayerLookHooks.previewSize(state);
		return state;
	}
}
