package me.skaffy.api.event.keybind;

import me.skaffy.api.keybind.CustomKeybind;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class SkaffyKeyChangeEvent extends SkaffyKeybindEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final String previousKey;

	public SkaffyKeyChangeEvent(Player player, CustomKeybind keybind, String key, String previousKey) {
		super(player, keybind, key);
		this.previousKey = previousKey;
	}

	public String getPreviousKey() {
		return previousKey;
	}

	public boolean isDefault() {
		return getKey().equals(getKeybind().getDefaultKey());
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
