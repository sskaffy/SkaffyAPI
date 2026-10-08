package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;

import me.skaffy.client.shader.MaterialEncoding;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {
	@Shadow
	@Final
	private boolean blockFormat;

	@ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int skaffy$fluidColor(int color) {
		return blockFormat && MaterialEncoding.active() ? MaterialEncoding.encodeColor(color) : color;
	}

	@ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int skaffy$materialLight(int light, @Local(argsOnly = true, ordinal = 1) float y, @Local(argsOnly = true, ordinal = 5) float nx,
			@Local(argsOnly = true, ordinal = 6) float ny, @Local(argsOnly = true, ordinal = 7) float nz) {
		return blockFormat && MaterialEncoding.active() ? MaterialEncoding.encode(light, y, nx, ny, nz) : light;
	}
}
