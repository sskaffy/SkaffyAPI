package me.skaffy.api.event.block;

import me.skaffy.api.block.PlacedCustomBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class SkaffyBlockEvent extends PlayerEvent {
	private final PlacedCustomBlock block;

	protected SkaffyBlockEvent(Player player, PlacedCustomBlock block) {
		super(player);
		this.block = block;
	}

	public PlacedCustomBlock getBlock() {
		return block;
	}
}
