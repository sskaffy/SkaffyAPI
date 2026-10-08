package me.skaffy.protocol.fov;

import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record FovValue(boolean multiplier, float value) {
	public FovValue {
		if (!(value > 0) || !Float.isFinite(value)) {
			throw new ProtocolException("FOV must be above 0, got " + value);
		}
	}

	public static FovValue degrees(float degrees) {
		return new FovValue(false, degrees);
	}

	public static FovValue multiplier(float multiplier) {
		return new FovValue(true, multiplier);
	}

	public float resolve(float playerFov) {
		return multiplier ? value * playerFov : value;
	}

	public static FovValue read(PacketReader reader) {
		return new FovValue(reader.readBoolean(), reader.readFloat());
	}

	public void write(PacketWriter writer) {
		writer.writeBoolean(multiplier);
		writer.writeFloat(value);
	}
}
