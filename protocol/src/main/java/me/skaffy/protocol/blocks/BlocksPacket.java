package me.skaffy.protocol.blocks;

import java.util.List;

public sealed interface BlocksPacket {
	record DefineBlocks(List<BlockDefinition> blocks) implements BlocksPacket {
		public DefineBlocks {
			blocks = List.copyOf(blocks);
		}
	}

	record SetBlocks(long sectionPos, List<BlockPlacement> placements) implements BlocksPacket {
		public SetBlocks {
			placements = List.copyOf(placements);
		}
	}

	record StopMining(long blockPos) implements BlocksPacket {
	}

	record MiningStart(long blockPos, int block) implements BlocksPacket {
	}

	record MiningAbort(long blockPos) implements BlocksPacket {
	}

	record MiningFinish(long blockPos, int block) implements BlocksPacket {
	}

	record PickBlock(long blockPos, int block) implements BlocksPacket {
	}
}
