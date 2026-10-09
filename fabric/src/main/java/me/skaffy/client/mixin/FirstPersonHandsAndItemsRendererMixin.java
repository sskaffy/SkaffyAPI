package me.skaffy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import me.skaffy.client.look.PlayerLookHooks;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
	@Inject(method = "renderPlayerHand", at = @At("HEAD"), cancellable = true)
	private void skaffy$hiddenArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, HumanoidArm arm, PlayerRenderState playerState, CallbackInfo ci) {
		if (playerState.avatarRenderState != null && PlayerLookHooks.hidesArm(playerState.avatarRenderState, arm == HumanoidArm.RIGHT)) {
			ci.cancel();
		}
	}
}
