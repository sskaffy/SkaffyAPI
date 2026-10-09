package me.skaffy.api.animation;

import java.util.Collection;
import java.util.Optional;

import me.skaffy.api.model.CustomEntityModel;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface SkaffyAnimations {
	int MAX_ID_LENGTH = 100;
	int MAX_PER_PLAYER = 8192;

	AnimationEntity spawn(String id, CustomEntityModel model, Location location);

	AnimationEntity spawn(Player viewer, String id, CustomEntityModel model, Location location);

	Optional<AnimationEntity> get(String id);

	Optional<AnimationEntity> get(Player viewer, String id);

	Collection<AnimationEntity> getAll();

	Collection<AnimationEntity> getAll(Player viewer);

	void removeAll(String prefix);

	void removeAll(Player viewer, String prefix);

	boolean isSupported(Player player);
}
