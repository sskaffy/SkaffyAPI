package me.skaffy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.commands.RenderPass;

import me.skaffy.client.shader.ShaderRenderer;

import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Inject(method = "renderSunMoonAndStars", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", shift = At.Shift.AFTER))
	private void skaffy$tiltSunPath(RenderPass renderPass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
			float rainBrightness, float starBrightness, CallbackInfo ci) {
		float tilt = ShaderRenderer.sunPathRotation();

		if (tilt != 0) {
			poseStack.rotateDegrees(Axis.ZP, tilt);
		}
	}
}
