package me.skaffy.api.event.block;

import me.skaffy.api.block.PlacedCustomBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class SkaffyBlockPickEvent extends SkaffyBlockEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	public SkaffyBlockPickEvent(Player player, PlacedCustomBlock block) {
		super(player, block);
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
