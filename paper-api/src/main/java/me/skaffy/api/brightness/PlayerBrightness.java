package me.skaffy.api.brightness;

import java.util.concurrent.CompletableFuture;

import me.skaffy.api.Easing;
import org.bukkit.entity.Player;

public interface PlayerBrightness {
	void set(Player player, BrightnessChange change);

	default void set(Player player, float brightness) {
		set(player, BrightnessChange.of(brightness));
	}

	void animate(Player player, BrightnessAnimation animation);

	default void reset(Player player) {
		reset(player, 0, Easing.LINEAR);
	}

	void reset(Player player, int durationMillis, Easing easing);

	void setSetting(Player player, float brightness);

	CompletableFuture<BrightnessInfo> get(Player player);
}
