package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import me.skaffy.client.shader.PipelineTwins;
import me.skaffy.client.shader.ShaderRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
	@ModifyReturnValue(method = "getCompiledPipelineNullable", at = @At("RETURN"))
	private static CompiledRenderPipeline skaffy$shaderPipeline(CompiledRenderPipeline original, @Local(argsOnly = true) RenderPipeline pipeline) {
		if (original != null) {
			PipelineTwins.rememberVanilla(pipeline, original);
		}

		CompiledRenderPipeline ours = ShaderRenderer.swap(pipeline);
		return ours != null ? ours : original;
	}
}
