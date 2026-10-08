package me.skaffy.api.event.animation;

import me.skaffy.api.animation.AnimationEntity;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkaffyAnimationEndEvent extends Event {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final AnimationEntity entity;
	private final String animation;
	private final boolean removing;

	public SkaffyAnimationEndEvent(AnimationEntity entity, String animation, boolean removing) {
		super(!Bukkit.isPrimaryThread());
		this.entity = entity;
		this.animation = animation;
		this.removing = removing;
	}

	public AnimationEntity getAnimationEntity() {
		return entity;
	}

	public String getAnimation() {
		return animation;
	}

	public boolean isRemoving() {
		return removing;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
