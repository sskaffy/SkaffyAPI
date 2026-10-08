package me.skaffy.client.mixin;

import me.skaffy.client.block.CustomBlock;
import me.skaffy.protocol.blocks.BlockDefinition;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
	@Inject(method = "forceOpaque", at = @At("HEAD"), cancellable = true)
	private static void skaffy$solidCustomBlocks(boolean cutoutLeaves, BlockState blockState, CallbackInfoReturnable<Boolean> cir) {
		if (blockState.getBlock() instanceof CustomBlock block && block.spec().definition().transparency() == BlockDefinition.Transparency.SOLID) {
			cir.setReturnValue(true);
		}
	}
}
