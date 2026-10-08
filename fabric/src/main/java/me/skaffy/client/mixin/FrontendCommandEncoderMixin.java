package me.skaffy.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;

import me.skaffy.client.shader.ShaderRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FrontendCommandEncoder.class)
public abstract class FrontendCommandEncoderMixin {
	@ModifyVariable(method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/api/commands/RenderPass;", at = @At("HEAD"), argsOnly = true)
	private RenderPassDescriptor skaffy$outputs(RenderPassDescriptor descriptor) {
		return ShaderRenderer.withOutputs(descriptor);
	}
}
