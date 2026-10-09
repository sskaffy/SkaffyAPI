package me.skaffy.api.event.perspective;

import me.skaffy.api.perspective.Perspective;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkaffyPerspectiveChangeEvent extends Event {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final Player player;
	private final Perspective from;
	private final Perspective to;

	public SkaffyPerspectiveChangeEvent(Player player, Perspective from, Perspective to) {
		super(!Bukkit.isPrimaryThread());
		this.player = player;
		this.from = from;
		this.to = to;
	}

	public Player getPlayer() {
		return player;
	}

	public Perspective getFrom() {
		return from;
	}

	public Perspective getTo() {
		return to;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
