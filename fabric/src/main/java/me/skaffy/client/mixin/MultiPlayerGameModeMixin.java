package me.skaffy.client.mixin;

import me.skaffy.client.block.BlockMining;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private boolean isDestroying;

	@Shadow
	private BlockPos destroyBlockPos;

	@Shadow
	protected abstract boolean sameDestroyTarget(BlockPos pos);

	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void skaffy$startMining(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (minecraft.player == null || minecraft.level == null || minecraft.player.getAbilities().instabuild) {
			return;
		}

		boolean sameTarget = isDestroying && sameDestroyTarget(pos);

		if (isDestroying && !sameTarget) {
			BlockMining.aborted(destroyBlockPos, minecraft.level.getBlockState(destroyBlockPos));
		}

		if (!sameTarget) {
			BlockState state = minecraft.level.getBlockState(pos);
			BlockMining.started(pos, state);

			if (state.getDestroyProgress(minecraft.player, minecraft.level, pos) >= 1) {
				BlockMining.finished(pos, state);
			}
		}
	}

	@Inject(method = "stopDestroyBlock", at = @At("HEAD"))
	private void skaffy$abortMining(CallbackInfo ci) {
		if (isDestroying && minecraft.level != null) {
			BlockMining.aborted(destroyBlockPos, minecraft.level.getBlockState(destroyBlockPos));
		}
	}

	@Inject(
			method = "continueDestroyBlock",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V",
					ordinal = 1))
	private void skaffy$finishMining(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (minecraft.level != null) {
			BlockMining.finished(pos, minecraft.level.getBlockState(pos));
		}
	}

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void skaffy$attackAnimation(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
		net.minecraft.world.phys.Vec3 hit = minecraft.hitResult != null ? minecraft.hitResult.getLocation() : entity.position();

		if (me.skaffy.client.animation.ClientAnimations.click(entity, true, net.minecraft.world.InteractionHand.MAIN_HAND, hit, player.isShiftKeyDown())) {
			player.resetAttackStrengthTicker();
			ci.cancel();
		}
	}

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void skaffy$useAnimation(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity entity, net.minecraft.world.phys.EntityHitResult hitResult,
			net.minecraft.world.InteractionHand hand, CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
		if (me.skaffy.client.animation.ClientAnimations.click(entity, false, hand, hitResult.getLocation(), player.isShiftKeyDown())) {
			cir.setReturnValue(net.minecraft.world.InteractionResult.SUCCESS);
		}
	}

	@Inject(method = "handlePickItemFromBlock", at = @At("HEAD"), cancellable = true)
	private void skaffy$pickBlock(BlockPos pos, boolean includeData, CallbackInfo ci) {
		if (minecraft.level != null && BlockMining.picked(pos, minecraft.level.getBlockState(pos))) {
			ci.cancel();
		}
	}
}
