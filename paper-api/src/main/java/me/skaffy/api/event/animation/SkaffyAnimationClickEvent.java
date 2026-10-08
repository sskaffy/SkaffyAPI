package me.skaffy.api.event.animation;

import me.skaffy.api.animation.AnimationEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

public class SkaffyAnimationClickEvent extends Event {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	public enum Click {
		ATTACK,
		USE
	}

	private final Player player;
	private final AnimationEntity entity;
	private final Click click;
	private final EquipmentSlot hand;
	private final Vector position;
	private final boolean sneaking;

	public SkaffyAnimationClickEvent(Player player, AnimationEntity entity, Click click, EquipmentSlot hand, Vector position, boolean sneaking) {
		super(!Bukkit.isPrimaryThread());
		this.player = player;
		this.entity = entity;
		this.click = click;
		this.hand = hand;
		this.position = position;
		this.sneaking = sneaking;
	}

	public Player getPlayer() {
		return player;
	}

	public AnimationEntity getAnimationEntity() {
		return entity;
	}

	public Click getClick() {
		return click;
	}

	public EquipmentSlot getHand() {
		return hand;
	}

	public Vector getPosition() {
		return position.clone();
	}

	public boolean isSneaking() {
		return sneaking;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
