package me.skaffy.api.event.shader;

import me.skaffy.api.shader.SkaffyShaders;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class SkaffyClientShaderChangeEvent extends PlayerEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final boolean shaderOn;
	private final boolean byPlayer;

	public SkaffyClientShaderChangeEvent(Player player, boolean shaderOn, boolean byPlayer) {
		super(player);
		this.shaderOn = shaderOn;
		this.byPlayer = byPlayer;
	}

	public boolean isShaderOn() {
		return shaderOn;
	}

	public boolean isByPlayer() {
		return byPlayer;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
