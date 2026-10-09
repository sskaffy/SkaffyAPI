package me.skaffy.api.event.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class SkaffyGuiErrorEvent extends SkaffyGuiEvent implements Cancellable {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	public enum Kind {
		COMPILE,
		RUNTIME,
		REQUEST
	}

	private final Kind kind;
	private final String message;
	private boolean cancelled;

	public SkaffyGuiErrorEvent(Player player, String className, Kind kind, String message) {
		super(player, className);
		this.kind = kind;
		this.message = message;
	}

	public Kind getKind() {
		return kind;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
