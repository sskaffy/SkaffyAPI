package me.skaffy.api.shape;

import java.util.Optional;
import java.util.Set;

import org.bukkit.entity.Player;

public interface SkaffyShapes {
	int MAX_ID_LENGTH = 100;

	void place(String id, Shape shape, ShapePlacement placement);

	boolean move(String id, ShapePlacement placement);

	boolean remove(String id);

	Optional<Shape> getPlaced(String id);

	Optional<ShapePlacement> getPlacement(String id);

	Set<String> getPlacedIds();

	boolean show(Player player, String id, Shape shape, ShapePlacement placement);

	boolean move(Player player, String id, ShapePlacement placement);

	void hide(Player player, String id);

	void hideAll(Player player, String prefix);

	boolean isSupported(Player player);
}
