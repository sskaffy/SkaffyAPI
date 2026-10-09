package me.skaffy.protocol.blocks;

import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record BlockPlacement(int localPos, int block, int rotation) {
	public BlockPlacement {
		if (localPos < 0 || localPos > 4095) {
			throw new ProtocolException("Invalid position " + localPos + " in section");
		}

		if (block < 0) {
			throw new ProtocolException("Invalid block " + block);
		}

		if (rotation < 0 || rotation > 15) {
			throw new ProtocolException("Invalid rotation " + rotation);
		}
	}

	public static int localPos(int x, int y, int z) {
		return (x & 15) << 8 | (z & 15) << 4 | y & 15;
	}

	public static int rotation(int xTurns, int yTurns) {
		return Math.floorMod(yTurns, 4) | Math.floorMod(xTurns, 4) << 2;
	}

	public int localX() {
		return localPos >> 8 & 15;
	}

	public int localY() {
		return localPos & 15;
	}

	public int localZ() {
		return localPos >> 4 & 15;
	}

	public int yTurns() {
		return rotation & 3;
	}

	public int xTurns() {
		return rotation >> 2 & 3;
	}

	public static BlockPlacement read(PacketReader reader) {
		int localPos = reader.readShortUnsigned();
		int block = reader.readVarInt();
		int rotation = reader.readUnsignedByte();
		return new BlockPlacement(localPos, block, rotation);
	}

	public void write(PacketWriter writer) {
		writer.writeShort(localPos);
		writer.writeVarInt(block);
		writer.writeByte(rotation);
	}
}
