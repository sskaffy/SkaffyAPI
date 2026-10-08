package me.skaffy.api.particle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface CustomParticles {
	void register(CustomParticleType type);

	boolean unregister(String name);

	Optional<CustomParticleType> getType(String name);

	Collection<CustomParticleType> getTypes();

	void spawn(ParticleSpawn spawn);

	default void spawn(CustomParticleType type, Location location, int count) {
		spawn(ParticleSpawn.builder(type, location).count(count).build());
	}

	void spawn(Collection<? extends Player> players, ParticleSpawn spawn);

	default void spawn(Player player, ParticleSpawn spawn) {
		spawn(List.of(player), spawn);
	}
}
