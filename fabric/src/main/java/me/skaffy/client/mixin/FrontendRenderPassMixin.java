package me.skaffy.client.mixin;

import java.util.List;
import java.util.Optional;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPass;

import me.skaffy.client.shader.ShaderRenderer;

import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendRenderPass.class)
public abstract class FrontendRenderPassMixin {
	@Shadow
	@Final
	private List<RenderPassDescriptor.@Nullable Attachment<Optional<Vector4fc>>> colorAttachments;

	@ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true)
	private CompiledRenderPipeline skaffy$fitPipeline(CompiledRenderPipeline pipeline) {
		return ShaderRenderer.adapt(colorAttachments, pipeline);
	}

	@Inject(method = "setPipeline", at = @At("TAIL"))
	private void skaffy$bindShaderUniforms(CompiledRenderPipeline pipeline, CallbackInfo ci) {
		ShaderRenderer.bind((RenderPass) (Object) this, pipeline);
	}

	@Inject(method = {"drawIndexed", "multiDrawIndexed", "drawIndexedIndirect", "drawMultipleIndexed", "draw", "multiDraw", "drawIndirect"}, at = @At("HEAD"), cancellable = true)
	private void skaffy$skipShadowDraws(CallbackInfo ci) {
		if (ShaderRenderer.skipsDraws()) {
			ci.cancel();
		}
	}
}
