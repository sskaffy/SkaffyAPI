package me.skaffy.api.look;

import java.util.Optional;

import org.bukkit.entity.Player;

public interface PlayerLooks {
	default void set(Player player, PlayerLook look) {
		set(player, look, false);
	}

	void set(Player player, PlayerLook look, boolean save);

	boolean remove(Player player);

	Optional<PlayerLook> get(Player player);

	default void set(Player viewer, Player player, PlayerLook look) {
		set(viewer, player, look, false);
	}

	void set(Player viewer, Player player, PlayerLook look, boolean save);

	boolean remove(Player viewer, Player player);

	Optional<PlayerLook> get(Player viewer, Player player);
}
