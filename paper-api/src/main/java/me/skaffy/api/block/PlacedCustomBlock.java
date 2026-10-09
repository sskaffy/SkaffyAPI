package me.skaffy.api.block;

import org.bukkit.Location;

public record PlacedCustomBlock(Location location, CustomBlockType type, BlockRotation rotation) {
	public PlacedCustomBlock {
		location = location.toBlockLocation();
	}
}
