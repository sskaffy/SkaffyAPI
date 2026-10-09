package me.skaffy.paper.particle;

import java.util.List;

import me.skaffy.api.particle.CustomParticleType;

public interface ParticleSession {
	void setParticleTypes(List<CustomParticleType> types);

	int particleNumber(CustomParticleType type);

	void sendParticleDefinition(byte[] data);
}
