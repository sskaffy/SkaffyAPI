package me.skaffy.protocol.particles;

import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record ParticleMotion(
		float gravity,
		float friction,
		boolean collides,
		boolean dieOnGround,
		float velocityRandomness,
		float wanderChance,
		float wanderSpeed,
		float sway,
		float swayPeriod,
		boolean converge,
		Easing convergeEasing) {
	public ParticleMotion {
		ParticleDefinition.checkFinite("Gravity", gravity);
		ParticleDefinition.checkAtLeastZero("Friction", friction);
		ParticleDefinition.checkAtLeastZero("Velocity randomness", velocityRandomness);
		ParticleDefinition.checkZeroToOne("Wander chance", wanderChance);
		ParticleDefinition.checkAtLeastZero("Wander speed", wanderSpeed);
		ParticleDefinition.checkAtLeastZero("Sway", sway);

		if (!(swayPeriod > 0) || !Float.isFinite(swayPeriod)) {
			throw new ProtocolException("Sway period must be above 0, got " + swayPeriod);
		}
	}

	public static ParticleMotion read(PacketReader reader) {
		float gravity = reader.readFloat();
		float friction = reader.readFloat();
		boolean collides = reader.readBoolean();
		boolean dieOnGround = reader.readBoolean();
		float velocityRandomness = reader.readFloat();
		float wanderChance = reader.readFloat();
		float wanderSpeed = reader.readFloat();
		float sway = reader.readFloat();
		float swayPeriod = reader.readFloat();
		boolean converge = reader.readBoolean();
		Easing convergeEasing = reader.readEnum(Easing.values(), "easing");
		return new ParticleMotion(gravity, friction, collides, dieOnGround, velocityRandomness, wanderChance, wanderSpeed, sway, swayPeriod, converge, convergeEasing);
	}

	public void write(PacketWriter writer) {
		writer.writeFloat(gravity);
		writer.writeFloat(friction);
		writer.writeBoolean(collides);
		writer.writeBoolean(dieOnGround);
		writer.writeFloat(velocityRandomness);
		writer.writeFloat(wanderChance);
		writer.writeFloat(wanderSpeed);
		writer.writeFloat(sway);
		writer.writeFloat(swayPeriod);
		writer.writeBoolean(converge);
		writer.writeByte(convergeEasing.ordinal());
	}
}
