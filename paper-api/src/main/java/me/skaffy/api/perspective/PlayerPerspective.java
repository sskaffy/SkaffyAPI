package me.skaffy.api.perspective;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import me.skaffy.api.Easing;
import org.bukkit.entity.Player;

public interface PlayerPerspective {
	int MAX_DURATION = 600_000;

	default void set(Player player, Perspective perspective) {
		set(player, perspective, 0, Easing.LINEAR);
	}

	void set(Player player, Perspective perspective, int durationMillis, Easing easing);

	default void setCamera(Player player, CameraView camera) {
		setCamera(player, camera, 0, Easing.LINEAR);
	}

	void setCamera(Player player, CameraView camera, int durationMillis, Easing easing);

	void setAllowed(Player player, Set<Perspective> allowed);

	default void allowAll(Player player) {
		setAllowed(player, EnumSet.of(Perspective.FIRST_PERSON, Perspective.THIRD_PERSON_BACK, Perspective.THIRD_PERSON_FRONT));
	}

	CompletableFuture<Perspective> get(Player player);
}
