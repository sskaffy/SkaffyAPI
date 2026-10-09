package me.skaffy.api.event.shader;

import me.skaffy.api.shader.SkaffyShaders;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class SkaffyVistaChangeEvent extends PlayerEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final boolean enabled;
	private final int shaderDistance;
	private final boolean byPlayer;

	public SkaffyVistaChangeEvent(Player player, boolean enabled, int shaderDistance, boolean byPlayer) {
		super(player);
		this.enabled = enabled;
		this.shaderDistance = shaderDistance;
		this.byPlayer = byPlayer;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public int getShaderDistance() {
		return shaderDistance;
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
