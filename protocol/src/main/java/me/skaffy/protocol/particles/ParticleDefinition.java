package me.skaffy.protocol.particles;

import java.util.List;

import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record ParticleDefinition(
		String name,
		List<String> textures,
		FrameMode frameMode,
		RenderMode render,
		Facing facing,
		float yaw,
		float pitch,
		int light,
		float minSize,
		float maxSize,
		int minLifetime,
		int maxLifetime,
		float colorVariation,
		float minSpin,
		float maxSpin,
		boolean randomAngle,
		Curve size,
		Curve alpha,
		ColorCurve color,
		Curve spin,
		ParticleMotion motion) {
	public static final int MAX_FRAMES = 256;
	public static final int MAX_TEXTURE_LENGTH = 128;
	public static final int MAX_LIFETIME = 6000;

	public enum FrameMode {
		AGE,
		RANDOM
	}

	public enum RenderMode {
		OPAQUE,
		TRANSLUCENT
	}

	public enum Facing {
		CAMERA,
		VERTICAL,
		HORIZONTAL,
		FIXED
	}

	public ParticleDefinition {
		if (name.isEmpty() || name.length() > 64) {
			throw new ProtocolException("Particle name must be 1 to 64 characters");
		}

		textures = List.copyOf(textures);

		if (textures.isEmpty() || textures.size() > MAX_FRAMES) {
			throw new ProtocolException("A particle needs 1 to " + MAX_FRAMES + " textures, got " + textures.size());
		}

		for (String texture : textures) {
			if (texture.isEmpty() || texture.length() > MAX_TEXTURE_LENGTH) {
				throw new ProtocolException("Particle texture must be 1 to " + MAX_TEXTURE_LENGTH + " characters: " + texture);
			}
		}

		checkFinite("Yaw", yaw);
		checkFinite("Pitch", pitch);

		if (light < 0 || light > 15) {
			throw new ProtocolException("Light must be 0 to 15, got " + light);
		}

		checkAtLeastZero("Size", minSize);
		checkAtLeastZero("Size", maxSize);

		if (minSize > maxSize) {
			throw new ProtocolException("Smallest size " + minSize + " is above the largest " + maxSize);
		}

		if (minLifetime < 1 || minLifetime > maxLifetime || maxLifetime > MAX_LIFETIME) {
			throw new ProtocolException("Lifetime must be 1 to " + MAX_LIFETIME + " ticks with min <= max, got " + minLifetime + " to " + maxLifetime);
		}

		checkZeroToOne("Color variation", colorVariation);
		checkFinite("Spin", minSpin);
		checkFinite("Spin", maxSpin);

		if (minSpin > maxSpin) {
			throw new ProtocolException("Smallest spin " + minSpin + " is above the largest " + maxSpin);
		}
	}

	public static ParticleDefinition read(PacketReader reader) {
		String name = reader.readString(64);
		List<String> textures = reader.readList(MAX_FRAMES, r -> r.readString(MAX_TEXTURE_LENGTH));
		FrameMode frameMode = reader.readEnum(FrameMode.values(), "frame mode");
		RenderMode render = reader.readEnum(RenderMode.values(), "render mode");
		Facing facing = reader.readEnum(Facing.values(), "facing");
		float yaw = facing == Facing.FIXED ? reader.readFloat() : 0;
		float pitch = facing == Facing.FIXED ? reader.readFloat() : 0;
		int light = reader.readUnsignedByte();
		float minSize = reader.readFloat();
		float maxSize = reader.readFloat();
		int minLifetime = reader.readVarInt();
		int maxLifetime = reader.readVarInt();
		float colorVariation = reader.readFloat();
		float minSpin = reader.readFloat();
		float maxSpin = reader.readFloat();
		boolean randomAngle = reader.readBoolean();
		Curve size = Curve.read(reader);
		Curve alpha = Curve.read(reader);
		ColorCurve color = ColorCurve.read(reader);
		Curve spin = Curve.read(reader);
		ParticleMotion motion = ParticleMotion.read(reader);
		return new ParticleDefinition(name, textures, frameMode, render, facing, yaw, pitch, light, minSize, maxSize, minLifetime, maxLifetime,
				colorVariation, minSpin, maxSpin, randomAngle, size, alpha, color, spin, motion);
	}

	public void write(PacketWriter writer) {
		writer.writeString(name, 64);
		writer.writeList(textures, (w, texture) -> w.writeString(texture, MAX_TEXTURE_LENGTH));
		writer.writeByte(frameMode.ordinal());
		writer.writeByte(render.ordinal());
		writer.writeByte(facing.ordinal());

		if (facing == Facing.FIXED) {
			writer.writeFloat(yaw);
			writer.writeFloat(pitch);
		}

		writer.writeByte(light);
		writer.writeFloat(minSize);
		writer.writeFloat(maxSize);
		writer.writeVarInt(minLifetime);
		writer.writeVarInt(maxLifetime);
		writer.writeFloat(colorVariation);
		writer.writeFloat(minSpin);
		writer.writeFloat(maxSpin);
		writer.writeBoolean(randomAngle);
		size.write(writer);
		alpha.write(writer);
		color.write(writer);
		spin.write(writer);
		motion.write(writer);
	}

	static void checkFinite(String what, float value) {
		if (!Float.isFinite(value)) {
			throw new ProtocolException(what + " must be a finite number");
		}
	}

	static void checkAtLeastZero(String what, float value) {
		if (!(value >= 0) || !Float.isFinite(value)) {
			throw new ProtocolException(what + " must be 0 or more, got " + value);
		}
	}

	static void checkZeroToOne(String what, float value) {
		if (!(value >= 0 && value <= 1)) {
			throw new ProtocolException(what + " must be from 0 to 1, got " + value);
		}
	}
}
