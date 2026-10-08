package me.skaffy.api.event.animation;

import me.skaffy.api.animation.AnimationEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkaffyAnimationMarkerEvent extends Event {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final AnimationEntity animationEntity;
	private final Entity entity;
	private final String animation;
	private final String marker;
	private final float time;

	public SkaffyAnimationMarkerEvent(AnimationEntity animationEntity, Entity entity, String animation, String marker, float time) {
		super(!Bukkit.isPrimaryThread());
		this.animationEntity = animationEntity;
		this.entity = entity;
		this.animation = animation;
		this.marker = marker;
		this.time = time;
	}

	public AnimationEntity getAnimationEntity() {
		return animationEntity;
	}

	public Entity getEntity() {
		return entity;
	}

	public String getAnimation() {
		return animation;
	}

	public String getMarker() {
		return marker;
	}

	public float getTime() {
		return time;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
