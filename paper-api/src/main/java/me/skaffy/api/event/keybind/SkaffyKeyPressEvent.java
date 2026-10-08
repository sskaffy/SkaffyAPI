package me.skaffy.api.event.keybind;

import me.skaffy.api.keybind.CustomKeybind;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class SkaffyKeyPressEvent extends SkaffyKeybindEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	public SkaffyKeyPressEvent(Player player, CustomKeybind keybind, String key) {
		super(player, keybind, key);
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
