package me.skaffy.client.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CustomBlock extends Block {
	private final CustomBlockSpec spec;

	public CustomBlock(Properties properties, CustomBlockSpec spec) {
		super(properties);
		this.spec = spec;
	}

	public CustomBlockSpec spec() {
		return spec;
	}

	public int rotation(BlockState state) {
		return 0;
	}

	public BlockState stateFor(int rotation) {
		return defaultBlockState();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return spec.hitbox(rotation(state));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return spec.collision(rotation(state));
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return spec.occlusion(rotation(state));
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return spec.destroyProgress(player);
	}
}
