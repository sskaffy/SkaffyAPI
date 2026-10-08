package me.skaffy.api.keybind;

import java.util.Collection;
import java.util.Optional;

import org.bukkit.entity.Player;

public interface CustomKeybinds {
	int MAX_KEYBINDS = 256;

	void setCategory(String name);

	Optional<String> getCategory();

	void register(CustomKeybind keybind);

	boolean unregister(String id);

	Optional<CustomKeybind> getKeybind(String id);

	Collection<CustomKeybind> getKeybinds();

	boolean setKey(Player player, CustomKeybind keybind, String key);

	Optional<String> getKey(Player player, CustomKeybind keybind);

	boolean setActive(Player player, CustomKeybind keybind, boolean active);

	boolean isActive(Player player, CustomKeybind keybind);

	boolean setWinsOverVanilla(Player player, CustomKeybind keybind, boolean wins);

	boolean winsOverVanilla(Player player, CustomKeybind keybind);
}
