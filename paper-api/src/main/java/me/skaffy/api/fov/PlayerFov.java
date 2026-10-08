package me.skaffy.api.fov;

import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

public interface PlayerFov {
	void set(Player player, FovChange change);

	default void set(Player player, float degrees) {
		set(player, FovChange.degrees(degrees));
	}

	void animate(Player player, FovAnimation animation);

	default void reset(Player player) {
		reset(player, 0, FovEasing.LINEAR);
	}

	void reset(Player player, int durationMillis, FovEasing easing);

	void setFovSetting(Player player, int fov);

	void setEffectScale(Player player, float effectScale);

	CompletableFuture<FovInfo> get(Player player);
}
