package me.skaffy.protocol.blocks;

import java.util.List;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.blocks.BlocksPacket.DefineBlocks;
import me.skaffy.protocol.blocks.BlocksPacket.MiningAbort;
import me.skaffy.protocol.blocks.BlocksPacket.MiningFinish;
import me.skaffy.protocol.blocks.BlocksPacket.MiningStart;
import me.skaffy.protocol.blocks.BlocksPacket.PickBlock;
import me.skaffy.protocol.blocks.BlocksPacket.SetBlocks;
import me.skaffy.protocol.blocks.BlocksPacket.StopMining;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class BlocksCodec {
	public static final String FEATURE = "blocks";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);
	public static final int MAX_DEFINITIONS = 65_536;

	public static final int DEFINE_BLOCKS = 0;
	public static final int SET_BLOCKS = 1;
	public static final int STOP_MINING = 2;

	public static final int MINING_START = 0;
	public static final int MINING_ABORT = 1;
	public static final int MINING_FINISH = 2;
	public static final int PICK_BLOCK = 3;

	private BlocksCodec() {
	}

	public static byte[] encode(BlocksPacket packet) {
		PacketWriter writer = new PacketWriter(256);
		boolean clientbound = true;

		switch (packet) {
			case DefineBlocks define -> writer.writeVarInt(DEFINE_BLOCKS)
					.writeList(define.blocks(), (w, block) -> block.write(w));
			case SetBlocks set -> writer.writeVarInt(SET_BLOCKS)
					.writeLong(set.sectionPos())
					.writeList(set.placements(), (w, placement) -> placement.write(w));
			case StopMining stop -> writer.writeVarInt(STOP_MINING)
					.writeLong(stop.blockPos());
			case MiningStart start -> {
				clientbound = false;
				writer.writeVarInt(MINING_START).writeLong(start.blockPos()).writeVarInt(start.block());
			}
			case MiningAbort abort -> {
				clientbound = false;
				writer.writeVarInt(MINING_ABORT).writeLong(abort.blockPos());
			}
			case MiningFinish finish -> {
				clientbound = false;
				writer.writeVarInt(MINING_FINISH).writeLong(finish.blockPos()).writeVarInt(finish.block());
			}
			case PickBlock pick -> {
				clientbound = false;
				writer.writeVarInt(PICK_BLOCK).writeLong(pick.blockPos()).writeVarInt(pick.block());
			}
		}

		int max = clientbound ? Protocol.MAX_CLIENTBOUND_PAYLOAD : Protocol.MAX_SERVERBOUND_PAYLOAD;

		if (writer.size() > max) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + max);
		}

		return writer.toByteArray();
	}

	public static BlocksPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		BlocksPacket packet = switch (id) {
			case DEFINE_BLOCKS -> new DefineBlocks(reader.readList(MAX_DEFINITIONS, BlockDefinition::read));
			case SET_BLOCKS -> {
				long sectionPos = reader.readLong();
				List<BlockPlacement> placements = reader.readList(4096, BlockPlacement::read);
				yield new SetBlocks(sectionPos, placements);
			}
			case STOP_MINING -> new StopMining(reader.readLong());
			default -> throw new ProtocolException("Unknown clientbound blocks packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static BlocksPacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		BlocksPacket packet = switch (id) {
			case MINING_START -> new MiningStart(reader.readLong(), reader.readVarInt());
			case MINING_ABORT -> new MiningAbort(reader.readLong());
			case MINING_FINISH -> new MiningFinish(reader.readLong(), reader.readVarInt());
			case PICK_BLOCK -> new PickBlock(reader.readLong(), reader.readVarInt());
			default -> throw new ProtocolException("Unknown serverbound blocks packet " + id);
		};

		reader.expectEnd();
		return packet;
	}
}
