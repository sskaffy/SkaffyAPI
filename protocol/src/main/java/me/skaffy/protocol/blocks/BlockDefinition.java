package me.skaffy.protocol.blocks;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record BlockDefinition(
		String name,
		String model,
		String collision,
		String hitbox,
		Transparency transparency,
		int light,
		float hardness,
		Tool tool,
		boolean requiresTool,
		boolean removeWhenReplaced,
		boolean rotatable) {
	public enum Transparency {
		SOLID,
		CUTOUT,
		TRANSLUCENT
	}

	public enum Tool {
		NONE,
		PICKAXE,
		AXE,
		SHOVEL,
		HOE,
		SWORD
	}

	public BlockDefinition {
		if (name.isEmpty() || name.length() > 64) {
			throw new ProtocolException("Block name must be 1 to 64 characters");
		}

		Protocol.requireAssetId(model);

		if (collision != null) {
			Protocol.requireAssetId(collision);
		}

		if (hitbox != null) {
			Protocol.requireAssetId(hitbox);
		}

		if (light < 0 || light > 15) {
			throw new ProtocolException("Light must be 0 to 15, got " + light);
		}

		if (Float.isNaN(hardness)) {
			throw new ProtocolException("Hardness is NaN");
		}
	}

	public boolean unbreakable() {
		return hardness < 0;
	}

	public static BlockDefinition read(PacketReader reader) {
		String name = reader.readString(64);
		String model = reader.readString(Protocol.MAX_ASSET_ID_LENGTH);
		String collision = reader.readBoolean() ? reader.readString(Protocol.MAX_ASSET_ID_LENGTH) : null;
		String hitbox = reader.readBoolean() ? reader.readString(Protocol.MAX_ASSET_ID_LENGTH) : null;
		Transparency transparency = reader.readEnum(Transparency.values(), "transparency");
		int light = reader.readUnsignedByte();
		float hardness = reader.readFloat();
		Tool tool = reader.readEnum(Tool.values(), "tool");
		boolean requiresTool = reader.readBoolean();
		boolean removeWhenReplaced = reader.readBoolean();
		boolean rotatable = reader.readBoolean();
		return new BlockDefinition(name, model, collision, hitbox, transparency, light, hardness, tool, requiresTool, removeWhenReplaced, rotatable);
	}

	public void write(PacketWriter writer) {
		writer.writeString(name, 64);
		writer.writeString(model, Protocol.MAX_ASSET_ID_LENGTH);
		writeOptional(writer, collision);
		writeOptional(writer, hitbox);
		writer.writeByte(transparency.ordinal());
		writer.writeByte(light);
		writer.writeFloat(hardness);
		writer.writeByte(tool.ordinal());
		writer.writeBoolean(requiresTool);
		writer.writeBoolean(removeWhenReplaced);
		writer.writeBoolean(rotatable);
	}

	private static void writeOptional(PacketWriter writer, String value) {
		writer.writeBoolean(value != null);

		if (value != null) {
			writer.writeString(value, Protocol.MAX_ASSET_ID_LENGTH);
		}
	}
}
