package me.skaffy.api.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface CustomEntityModels {
	int MAX_MODELS = 4096;

	int DEFAULT_MAX_TEXTURE_SIZE = 2048;

	void register(CustomEntityModel model);

	default CustomEntityModel.Builder importModel(String name, Path source) throws IOException {
		return importModel(name, source, DEFAULT_MAX_TEXTURE_SIZE);
	}

	CustomEntityModel.Builder importModel(String name, Path source, int maxTextureSize) throws IOException;

	boolean unregister(String name);

	Optional<CustomEntityModel> getModel(String name);

	Collection<CustomEntityModel> getModels();

	default void set(Entity entity, CustomEntityModel model) {
		set(entity, model, false);
	}

	void set(Entity entity, CustomEntityModel model, boolean save);

	boolean remove(Entity entity);

	Optional<CustomEntityModel> get(Entity entity);

	default void set(Player viewer, Entity entity, CustomEntityModel model) {
		set(viewer, entity, model, false);
	}

	void set(Player viewer, Entity entity, CustomEntityModel model, boolean save);

	boolean remove(Player viewer, Entity entity);

	Optional<CustomEntityModel> get(Player viewer, Entity entity);

	default void playAnimation(Entity entity, String animation) {
		playAnimation(entity, animation, AnimationMode.DEFAULT, 1);
	}

	default void playAnimation(Entity entity, String animation, AnimationMode mode, float speed) {
		playAnimation(entity, animation, mode, speed, 0);
	}

	void playAnimation(Entity entity, String animation, AnimationMode mode, float speed, float fadeIn);

	void playAnimation(Player viewer, Entity entity, String animation, AnimationMode mode, float speed);

	default void stopAnimation(Entity entity, String animation) {
		stopAnimation(entity, animation, 0);
	}

	void stopAnimation(Entity entity, String animation, float fadeOut);

	void setVariable(Entity entity, String name, float value);

	void stopAnimations(Entity entity);
}
