package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import me.skaffy.client.look.PlayerLookHooks;
import me.skaffy.client.model.ClientEntityModels;
import me.skaffy.client.model.CustomRendererHooks;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
	private void skaffy$customRenderer(Entity entity, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
		if (entity instanceof me.skaffy.client.animation.AnimationEntity) {
			cir.setReturnValue(me.skaffy.client.animation.ClientAnimations.renderer());
			return;
		}

		EntityRenderer<?, ?> renderer = ClientEntityModels.rendererFor(entity);

		if (renderer != null) {
			cir.setReturnValue(renderer);
		}
	}

	@Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
	private void skaffy$customRendererForState(EntityRenderState state, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
		if (state instanceof me.skaffy.client.animation.AnimationEntityRenderer.State) {
			cir.setReturnValue(me.skaffy.client.animation.ClientAnimations.renderer());
			return;
		}

		EntityRenderer<?, ?> renderer = CustomRendererHooks.rendererOf(state);

		if (renderer != null) {
			cir.setReturnValue(renderer);
		}
	}

	@ModifyArg(method = "onResourceManagerReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderers;createEntityRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"))
	private EntityRendererProvider.Context skaffy$captureContext(EntityRendererProvider.Context context) {
		CustomRendererHooks.setContext(context);
		me.skaffy.client.animation.ClientAnimations.setContext(context);
		return context;
	}

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void skaffy$entityLookCulling(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		Entity dummy = PlayerLookHooks.dummyFor(entity);

		if (dummy != null) {
			cir.setReturnValue(((EntityRenderDispatcher) (Object) this).shouldRender(dummy, culler, camX, camY, camZ, partialTicks));
		}
	}

	@WrapOperation(
			method = "submit",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
	private void skaffy$nonLivingLookScale(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
		float scale = PlayerLookHooks.nonLivingScale(state);

		if (scale == 1) {
			original.call(renderer, state, poseStack, collector, camera);
			return;
		}

		poseStack.pushPose();
		poseStack.scale(scale, scale, scale);
		original.call(renderer, state, poseStack, collector, camera);
		poseStack.popPose();
	}
}
