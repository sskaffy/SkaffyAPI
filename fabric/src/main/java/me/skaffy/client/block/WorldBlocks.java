package me.skaffy.client.block;

import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import me.skaffy.protocol.blocks.BlockPlacement;
import me.skaffy.protocol.blocks.BlocksPacket.SetBlocks;
import me.skaffy.protocol.blocks.Positions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldBlocks {
	private static final int UPDATE_FLAGS = 19;

	private static List<CustomBlock> blocks = List.of();
	private static ClientLevel level;
	private static final Long2ObjectMap<Placed> PLACED = new Long2ObjectOpenHashMap<>();
	private static final Long2ObjectMap<LongSet> BY_CHUNK = new Long2ObjectOpenHashMap<>();

	private WorldBlocks() {
	}

	static void setBlocks(List<CustomBlock> registered) {
		clear();
		blocks = registered;
	}

	static void clear() {
		PLACED.clear();
		BY_CHUNK.clear();
		blocks = List.of();
		level = null;
	}

	static void apply(SetBlocks packet) {
		ClientLevel current = currentLevel();

		if (current == null) {
			return;
		}

		int baseX = Positions.sectionX(packet.sectionPos()) << 4;
		int baseY = Positions.sectionY(packet.sectionPos()) << 4;
		int baseZ = Positions.sectionZ(packet.sectionPos()) << 4;

		if (!current.getChunkSource().hasChunk(baseX >> 4, baseZ >> 4)) {
			return;
		}

		for (BlockPlacement placement : packet.placements()) {
			BlockPos pos = new BlockPos(baseX + placement.localX(), baseY + placement.localY(), baseZ + placement.localZ());
			long key = pos.asLong();
			Placed existing = PLACED.get(key);

			if (placement.block() == 0 || placement.block() > blocks.size()) {
				if (existing != null) {
					remove(key);
					current.setBlock(pos, existing.carrier, UPDATE_FLAGS);
				}

				continue;
			}

			CustomBlock block = blocks.get(placement.block() - 1);
			BlockState state = block.stateFor(placement.rotation());
			BlockState carrier = existing != null ? existing.carrier : current.getBlockState(pos);
			PLACED.put(key, new Placed(state, carrier));
			BY_CHUNK.computeIfAbsent(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4), chunk -> new LongOpenHashSet()).add(key);
			current.setBlock(pos, state, UPDATE_FLAGS);
		}
	}

	public static BlockState serverState(ClientLevel from, BlockPos pos, BlockState state) {
		if (from != level || PLACED.isEmpty()) {
			return state;
		}

		long key = pos.asLong();
		Placed placed = PLACED.get(key);

		if (placed == null || state == placed.custom) {
			return state;
		}

		if (state == placed.carrier) {
			return placed.custom;
		}

		CustomBlock block = (CustomBlock) placed.custom.getBlock();

		if (block.spec().definition().removeWhenReplaced()) {
			remove(key);
			return state;
		}

		placed.carrier = state;
		return placed.custom;
	}

	public static void chunkReplaced(ClientLevel from, int chunkX, int chunkZ) {
		if (from == level) {
			LongSet keys = BY_CHUNK.remove(ChunkPos.pack(chunkX, chunkZ));

			if (keys != null) {
				keys.forEach(PLACED::remove);
			}
		}
	}

	public static void lightReplaced(ClientLevel from, int chunkX, int chunkZ) {
		LongSet keys = from == level ? BY_CHUNK.get(ChunkPos.pack(chunkX, chunkZ)) : null;

		if (keys == null) {
			return;
		}

		keys.forEach(key -> {
			Placed placed = PLACED.get(key);

			if (placed != null && placed.custom.getLightEmission() > 0) {
				from.getChunkSource().getLightEngine().checkBlock(BlockPos.of(key));
			}
		});
	}

	private static void remove(long key) {
		PLACED.remove(key);
		LongSet chunk = BY_CHUNK.get(ChunkPos.pack(BlockPos.getX(key) >> 4, BlockPos.getZ(key) >> 4));

		if (chunk != null) {
			chunk.remove(key);
		}
	}

	private static ClientLevel currentLevel() {
		ClientLevel current = Minecraft.getInstance().level;

		if (current != level) {
			PLACED.clear();
			BY_CHUNK.clear();
			level = current;
		}

		return current;
	}

	private static final class Placed {
		final BlockState custom;
		BlockState carrier;

		Placed(BlockState custom, BlockState carrier) {
			this.custom = custom;
			this.carrier = carrier;
		}
	}
}
