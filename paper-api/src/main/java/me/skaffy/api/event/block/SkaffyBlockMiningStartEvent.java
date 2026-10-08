package me.skaffy.api.event.block;

import me.skaffy.api.block.PlacedCustomBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class SkaffyBlockMiningStartEvent extends SkaffyBlockEvent implements Cancellable {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private boolean cancelled;

	public SkaffyBlockMiningStartEvent(Player player, PlacedCustomBlock block) {
		super(player, block);
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
