package me.skaffy.api.event.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class SkaffyGuiOpenEvent extends SkaffyGuiEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final String screen;

	public SkaffyGuiOpenEvent(Player player, String className, String screen) {
		super(player, className);
		this.screen = screen;
	}

	public String getScreen() {
		return screen;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
