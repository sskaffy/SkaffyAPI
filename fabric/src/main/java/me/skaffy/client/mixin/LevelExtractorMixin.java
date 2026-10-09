package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.skaffy.client.look.PlayerLookHooks;
import me.skaffy.client.shader.ColoredLight;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@WrapOperation(
			method = "extractVisibleEntities",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
	private EntityRenderState skaffy$entityLook(LevelExtractor extractor, Entity entity, float partialTicks, Operation<EntityRenderState> original) {
		return PlayerLookHooks.disguise(entity, original.call(extractor, entity, partialTicks), partialTicks);
	}

	@Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
	private void skaffy$animationVisible(Entity entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ, float partialTicks, long chunkFadeDuration,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof me.skaffy.client.animation.AnimationEntity) {
			cir.setReturnValue(net.minecraft.client.Minecraft.getInstance().levelRenderer.entityRenderDispatcher().shouldRender(entity, frustum, camX, camY, camZ, partialTicks));
		}
	}

	@Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
	private void skaffy$lightSection(int x, int y, int z, boolean playerChanged, CallbackInfo ci) {
		ColoredLight.sectionChanged(x, y, z);
	}

	@Inject(method = "allChanged", at = @At("HEAD"))
	private void skaffy$lightEverything(CallbackInfo ci) {
		ColoredLight.everythingChanged();
	}
}
