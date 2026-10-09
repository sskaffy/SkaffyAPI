package me.skaffy.api.event.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class SkaffyGuiCloseEvent extends SkaffyGuiEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	public enum Reason {
		ESCAPE,
		SERVER,
		CODE,
		REPLACED,
		ERROR
	}

	private final Reason reason;

	public SkaffyGuiCloseEvent(Player player, String className, Reason reason) {
		super(player, className);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
