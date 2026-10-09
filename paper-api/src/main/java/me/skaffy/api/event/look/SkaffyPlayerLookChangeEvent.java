package me.skaffy.api.event.look;

import me.skaffy.api.look.PlayerLook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkaffyPlayerLookChangeEvent extends Event {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final Player player;
	private final Player viewer;
	private final PlayerLook previous;
	private final PlayerLook look;
	private final boolean saved;

	public SkaffyPlayerLookChangeEvent(Player player, Player viewer, PlayerLook previous, PlayerLook look, boolean saved) {
		super(!Bukkit.isPrimaryThread());
		this.player = player;
		this.viewer = viewer;
		this.previous = previous;
		this.look = look;
		this.saved = saved;
	}

	public Player getPlayer() {
		return player;
	}

	public Player getViewer() {
		return viewer;
	}

	public PlayerLook getPrevious() {
		return previous;
	}

	public PlayerLook getLook() {
		return look;
	}

	public boolean isSaved() {
		return saved;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
