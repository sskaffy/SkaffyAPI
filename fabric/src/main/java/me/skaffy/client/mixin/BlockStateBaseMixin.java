package me.skaffy.client.mixin;

import me.skaffy.client.blockshape.ClientBlockShapes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
	private void skaffy$collision(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null) {
			cir.setReturnValue(entry.collision());
		}
	}

	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
	private void skaffy$collisionInContext(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null) {
			cir.setReturnValue(entry.collision());
		}
	}

	@Inject(method = "getVisualShape", at = @At("HEAD"), cancellable = true)
	private void skaffy$visual(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null) {
			cir.setReturnValue(entry.collision());
		}
	}

	@Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
	private void skaffy$outline(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null) {
			cir.setReturnValue(entry.outline());
		}
	}

	@Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
	private void skaffy$outlineInContext(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null) {
			cir.setReturnValue(entry.outline());
		}
	}

	@Inject(method = "isSuffocating", at = @At("HEAD"), cancellable = true)
	private void skaffy$suffocating(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null && !entry.full()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isViewBlocking", at = @At("HEAD"), cancellable = true)
	private void skaffy$viewBlocking(BlockGetter level, BlockPos pos, AABB nearPlane, CallbackInfoReturnable<Boolean> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null && !entry.full()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isCollisionShapeFullBlock", at = @At("HEAD"), cancellable = true)
	private void skaffy$fullBlock(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null) {
			cir.setReturnValue(entry.full());
		}
	}

	@Inject(method = "getInteractionShape", at = @At("HEAD"), cancellable = true)
	private void skaffy$interaction(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
		ClientBlockShapes.Entry entry = ClientBlockShapes.at(level, pos);

		if (entry != null) {
			cir.setReturnValue(entry.outline());
		}
	}
}
