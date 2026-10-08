package me.skaffy.protocol.particles;

import java.util.List;

import me.skaffy.protocol.ProtocolException;

public sealed interface ParticlesPacket {
	record DefineParticles(List<ParticleDefinition> particles) implements ParticlesPacket {
		public DefineParticles {
			particles = List.copyOf(particles);
		}
	}

	record SpawnParticles(
			int particle,
			double x,
			double y,
			double z,
			int count,
			float spreadX,
			float spreadY,
			float spreadZ,
			Spread spread,
			float velocityX,
			float velocityY,
			float velocityZ,
			boolean force,
			Integer color,
			Float size,
			Integer lifetime,
			Rotation rotation) implements ParticlesPacket {
		public enum Spread {
			GAUSSIAN,
			BOX
		}

		public SpawnParticles {
			if (particle < 1) {
				throw new ProtocolException("Invalid particle " + particle);
			}

			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				throw new ProtocolException("Particle position must be finite");
			}

			if (count < 1 || count > ParticlesCodec.MAX_COUNT) {
				throw new ProtocolException("Particle count must be 1 to " + ParticlesCodec.MAX_COUNT + ", got " + count);
			}

			ParticleDefinition.checkAtLeastZero("Spread", spreadX);
			ParticleDefinition.checkAtLeastZero("Spread", spreadY);
			ParticleDefinition.checkAtLeastZero("Spread", spreadZ);
			ParticleDefinition.checkFinite("Velocity", velocityX);
			ParticleDefinition.checkFinite("Velocity", velocityY);
			ParticleDefinition.checkFinite("Velocity", velocityZ);

			if (color != null) {
				color &= 0xFFFFFF;
			}

			if (size != null) {
				ParticleDefinition.checkAtLeastZero("Size", size);
			}

			if (lifetime != null && (lifetime < 1 || lifetime > ParticleDefinition.MAX_LIFETIME)) {
				throw new ProtocolException("Lifetime must be 1 to " + ParticleDefinition.MAX_LIFETIME + " ticks, got " + lifetime);
			}
		}
	}

	record Rotation(float yaw, float pitch) {
		public Rotation {
			ParticleDefinition.checkFinite("Yaw", yaw);
			ParticleDefinition.checkFinite("Pitch", pitch);
		}
	}
}
