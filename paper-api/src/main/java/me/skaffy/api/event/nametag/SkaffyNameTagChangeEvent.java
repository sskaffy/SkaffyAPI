package me.skaffy.api.event.nametag;

import me.skaffy.api.nametag.NameTag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkaffyNameTagChangeEvent extends Event {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final Entity entity;
	private final Player viewer;
	private final NameTag previous;
	private final NameTag tag;
	private final boolean saved;

	public SkaffyNameTagChangeEvent(Entity entity, Player viewer, NameTag previous, NameTag tag, boolean saved) {
		super(!Bukkit.isPrimaryThread());
		this.entity = entity;
		this.viewer = viewer;
		this.previous = previous;
		this.tag = tag;
		this.saved = saved;
	}

	public Entity getEntity() {
		return entity;
	}

	public Player getViewer() {
		return viewer;
	}

	public NameTag getPrevious() {
		return previous;
	}

	public NameTag getTag() {
		return tag;
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
