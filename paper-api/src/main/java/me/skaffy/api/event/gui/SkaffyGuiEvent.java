package me.skaffy.api.event.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class SkaffyGuiEvent extends PlayerEvent {
	private final String className;

	protected SkaffyGuiEvent(Player player, String className) {
		super(player);
		this.className = className;
	}

	public String getClassName() {
		return className;
	}
}
