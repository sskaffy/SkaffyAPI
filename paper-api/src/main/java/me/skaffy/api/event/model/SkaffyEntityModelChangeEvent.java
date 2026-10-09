package me.skaffy.api.event.model;

import me.skaffy.api.model.CustomEntityModel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkaffyEntityModelChangeEvent extends Event {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final Entity entity;
	private final Player viewer;
	private final CustomEntityModel previous;
	private final CustomEntityModel model;
	private final boolean saved;

	public SkaffyEntityModelChangeEvent(Entity entity, Player viewer, CustomEntityModel previous, CustomEntityModel model, boolean saved) {
		super(!Bukkit.isPrimaryThread());
		this.entity = entity;
		this.viewer = viewer;
		this.previous = previous;
		this.model = model;
		this.saved = saved;
	}

	public Entity getEntity() {
		return entity;
	}

	public Player getViewer() {
		return viewer;
	}

	public CustomEntityModel getPrevious() {
		return previous;
	}

	public CustomEntityModel getModel() {
		return model;
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
