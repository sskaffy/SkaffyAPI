package me.skaffy.api.nametag;

import java.util.Collection;
import java.util.Optional;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface NameTags {
	int MAX_SPRITES = 4096;

	void registerSprite(String asset);

	boolean unregisterSprite(String asset);

	Collection<String> getSprites();

	default void set(Entity entity, NameTag tag) {
		set(entity, tag, false);
	}

	void set(Entity entity, NameTag tag, boolean save);

	boolean remove(Entity entity);

	Optional<NameTag> get(Entity entity);

	default void set(Player viewer, Entity entity, NameTag tag) {
		set(viewer, entity, tag, false);
	}

	void set(Player viewer, Entity entity, NameTag tag, boolean save);

	boolean remove(Player viewer, Entity entity);

	Optional<NameTag> get(Player viewer, Entity entity);

	void setLine(Entity entity, int line, NameTagLine content);

	void setLine(Player viewer, Entity entity, int line, NameTagLine content);
}
