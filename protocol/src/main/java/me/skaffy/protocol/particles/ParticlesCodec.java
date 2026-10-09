package me.skaffy.protocol.particles;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.particles.ParticlesPacket.DefineParticles;
import me.skaffy.protocol.particles.ParticlesPacket.Rotation;
import me.skaffy.protocol.particles.ParticlesPacket.SpawnParticles;

public final class ParticlesCodec {
	public static final String FEATURE = "particles";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);
	public static final int MAX_DEFINITIONS = 65_536;
	public static final int MAX_COUNT = 16_384;

	public static final int DEFINE_PARTICLES = 0;
	public static final int SPAWN_PARTICLES = 1;

	private ParticlesCodec() {
	}

	public static byte[] encode(ParticlesPacket packet) {
		PacketWriter writer = new PacketWriter(128);

		switch (packet) {
			case DefineParticles define -> writer.writeVarInt(DEFINE_PARTICLES)
					.writeList(define.particles(), (w, particle) -> particle.write(w));
			case SpawnParticles spawn -> {
				writer.writeVarInt(SPAWN_PARTICLES)
						.writeVarInt(spawn.particle())
						.writeDouble(spawn.x())
						.writeDouble(spawn.y())
						.writeDouble(spawn.z())
						.writeVarInt(spawn.count())
						.writeFloat(spawn.spreadX())
						.writeFloat(spawn.spreadY())
						.writeFloat(spawn.spreadZ())
						.writeByte(spawn.spread().ordinal())
						.writeFloat(spawn.velocityX())
						.writeFloat(spawn.velocityY())
						.writeFloat(spawn.velocityZ())
						.writeBoolean(spawn.force());
				writer.writeBoolean(spawn.color() != null);

				if (spawn.color() != null) {
					writer.writeInt(spawn.color());
				}

				writer.writeBoolean(spawn.size() != null);

				if (spawn.size() != null) {
					writer.writeFloat(spawn.size());
				}

				writer.writeBoolean(spawn.lifetime() != null);

				if (spawn.lifetime() != null) {
					writer.writeVarInt(spawn.lifetime());
				}

				writer.writeBoolean(spawn.rotation() != null);

				if (spawn.rotation() != null) {
					writer.writeFloat(spawn.rotation().yaw()).writeFloat(spawn.rotation().pitch());
				}
			}
		}

		if (writer.size() > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + Protocol.MAX_CLIENTBOUND_PAYLOAD);
		}

		return writer.toByteArray();
	}

	public static ParticlesPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		ParticlesPacket packet = switch (id) {
			case DEFINE_PARTICLES -> new DefineParticles(reader.readList(MAX_DEFINITIONS, ParticleDefinition::read));
			case SPAWN_PARTICLES -> {
				int particle = reader.readVarInt();
				double x = reader.readDouble();
				double y = reader.readDouble();
				double z = reader.readDouble();
				int count = reader.readVarInt();
				float spreadX = reader.readFloat();
				float spreadY = reader.readFloat();
				float spreadZ = reader.readFloat();
				SpawnParticles.Spread spread = reader.readEnum(SpawnParticles.Spread.values(), "spread");
				float velocityX = reader.readFloat();
				float velocityY = reader.readFloat();
				float velocityZ = reader.readFloat();
				boolean force = reader.readBoolean();
				Integer color = reader.readBoolean() ? reader.readInt() : null;
				Float size = reader.readBoolean() ? reader.readFloat() : null;
				Integer lifetime = reader.readBoolean() ? reader.readVarInt() : null;
				Rotation rotation = reader.readBoolean() ? new Rotation(reader.readFloat(), reader.readFloat()) : null;
				yield new SpawnParticles(particle, x, y, z, count, spreadX, spreadY, spreadZ, spread, velocityX, velocityY, velocityZ, force, color, size, lifetime, rotation);
			}
			default -> throw new ProtocolException("Unknown clientbound particles packet " + id);
		};

		reader.expectEnd();
		return packet;
	}
}
