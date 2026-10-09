package me.skaffy.api.event.shader;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class SkaffyShaderStateEvent extends SkaffyShaderEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final boolean running;

	public SkaffyShaderStateEvent(Player player, String className, boolean running) {
		super(player, className);
		this.running = running;
	}

	public boolean isRunning() {
		return running;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
