package me.skaffy.client.block;

import me.skaffy.client.mixin.MultiPlayerGameModeAccessor;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.blocks.BlocksPacket;
import me.skaffy.protocol.blocks.BlocksPacket.MiningAbort;
import me.skaffy.protocol.blocks.BlocksPacket.MiningFinish;
import me.skaffy.protocol.blocks.BlocksPacket.MiningStart;
import me.skaffy.protocol.blocks.BlocksPacket.PickBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockMining {
	private BlockMining() {
	}

	public static void started(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof CustomBlock block) {
			send(new MiningStart(ClientBlocks.packed(pos), block.spec().number()));
		}
	}

	public static void aborted(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof CustomBlock) {
			send(new MiningAbort(ClientBlocks.packed(pos)));
		}
	}

	public static void finished(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof CustomBlock block) {
			send(new MiningFinish(ClientBlocks.packed(pos), block.spec().number()));
		}
	}

	public static boolean picked(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof CustomBlock block) {
			send(new PickBlock(ClientBlocks.packed(pos), block.spec().number()));
			return true;
		}

		return false;
	}

	static void stop(BlockPos pos) {
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;

		if (gameMode != null && gameMode.isDestroying() && ((MultiPlayerGameModeAccessor) gameMode).skaffy$destroyBlockPos().equals(pos)) {
			gameMode.stopDestroyBlock();
		}
	}

	private static void send(BlocksPacket packet) {
		SkaffyConnection.sendFeature(BlocksCodec.CHANNEL, BlocksCodec.encode(packet));
	}
}
