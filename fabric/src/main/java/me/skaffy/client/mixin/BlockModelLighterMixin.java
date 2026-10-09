package me.skaffy.client.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;

import me.skaffy.client.shader.MaterialEncoding;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelLighter.class)
public abstract class BlockModelLighterMixin {
	@Inject(method = "prepareQuadAmbientOcclusion", at = @At("TAIL"))
	private void skaffy$separateAo(BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, QuadInstance instance, CallbackInfo ci) {
		MaterialEncoding.separateAo(level, quad, instance, true);
	}

	@Inject(method = "prepareQuadFlat", at = @At("TAIL"))
	private void skaffy$flatAo(BlockAndTintGetter level, BlockState state, BlockPos pos, int lightCoords, BakedQuad quad, QuadInstance instance, CallbackInfo ci) {
		MaterialEncoding.separateAo(level, quad, instance, false);
	}
}
