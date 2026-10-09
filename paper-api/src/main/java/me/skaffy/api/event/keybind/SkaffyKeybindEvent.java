package me.skaffy.api.event.keybind;

import me.skaffy.api.keybind.CustomKeybind;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class SkaffyKeybindEvent extends PlayerEvent {
	private final CustomKeybind keybind;
	private final String key;

	protected SkaffyKeybindEvent(Player player, CustomKeybind keybind, String key) {
		super(player);
		this.keybind = keybind;
		this.key = key;
	}

	public CustomKeybind getKeybind() {
		return keybind;
	}

	public String getKey() {
		return key;
	}
}
