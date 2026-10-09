package me.skaffy.client.mixin;

import me.skaffy.client.model.gpu.GpuModelFeature;

import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {
	@Shadow
	@Final
	private FeatureRendererMap featureRenderers;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void skaffy$addModelFeature(CallbackInfo ci) {
		featureRenderers.put(GpuModelFeature.TYPE, new GpuModelFeature());
	}
}
