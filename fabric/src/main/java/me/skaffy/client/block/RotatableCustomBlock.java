package me.skaffy.client.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class RotatableCustomBlock extends CustomBlock {
	public static final IntegerProperty X = IntegerProperty.create("x", 0, 3);
	public static final IntegerProperty Y = IntegerProperty.create("y", 0, 3);

	public RotatableCustomBlock(Properties properties, CustomBlockSpec spec) {
		super(properties, spec);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(X, Y);
	}

	@Override
	public int rotation(BlockState state) {
		return state.getValue(Y) | state.getValue(X) << 2;
	}

	@Override
	public BlockState stateFor(int rotation) {
		return defaultBlockState().setValue(Y, rotation & 3).setValue(X, rotation >> 2 & 3);
	}
}
