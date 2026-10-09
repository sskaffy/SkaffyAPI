package me.skaffy.client.mixin;

import me.skaffy.client.brightness.ClientBrightness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
	@Shadow
	private boolean needsUpdate;

	@Inject(method = "extract", at = @At("HEAD"))
	private void skaffy$updateWhileAnimating(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		if (ClientBrightness.needsUpdate()) {
			needsUpdate = true;
		}
	}

	@Inject(method = "extract", at = @At("TAIL"))
	private void skaffy$serverBrightness(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();

		if (renderState.needsUpdate && minecraft.level != null && minecraft.player != null) {
			ClientBrightness.apply(renderState, minecraft.player, partialTicks);
		}
	}
}
