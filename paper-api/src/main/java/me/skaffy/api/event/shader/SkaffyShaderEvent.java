package me.skaffy.api.event.shader;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class SkaffyShaderEvent extends PlayerEvent {
	private final String className;

	protected SkaffyShaderEvent(Player player, String className) {
		super(player);
		this.className = className;
	}

	public String getClassName() {
		return className;
	}
}
