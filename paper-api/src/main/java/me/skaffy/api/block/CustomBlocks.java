package me.skaffy.api.block;

import java.util.Collection;
import java.util.Optional;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface CustomBlocks {
	void register(CustomBlockType type);

	boolean unregister(String name);

	Optional<CustomBlockType> getType(String name);

	Collection<CustomBlockType> getTypes();

	default void set(Location location, CustomBlockType type) {
		set(location, type, BlockRotation.NONE, true);
	}

	default void set(Location location, CustomBlockType type, BlockRotation rotation) {
		set(location, type, rotation, true);
	}

	void set(Location location, CustomBlockType type, BlockRotation rotation, boolean placeBarrier);

	boolean remove(Location location);

	Optional<PlacedCustomBlock> get(Location location);

	Collection<PlacedCustomBlock> getAll(Chunk chunk);

	void set(Player player, Location location, CustomBlockType type, BlockRotation rotation);

	boolean remove(Player player, Location location);

	Optional<PlacedCustomBlock> get(Player player, Location location);
}
