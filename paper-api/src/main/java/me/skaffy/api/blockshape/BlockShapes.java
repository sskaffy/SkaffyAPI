package me.skaffy.api.blockshape;

import java.util.List;
import java.util.Optional;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

public interface BlockShapes {
	int MAX_BOXES = 16;

	default void set(Block block, List<BoundingBox> collision) {
		set(block, collision, null);
	}

	void set(Block block, List<BoundingBox> collision, List<BoundingBox> outline);

	void remove(Block block);

	Optional<List<BoundingBox>> getCollision(Block block);

	Optional<List<BoundingBox>> getOutline(Block block);

	boolean has(Block block);

	void clear(World world);
}
