package me.skaffy.api.gui;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface SkaffyGuis {
	void addFile(String className, String source);

	List<String> addFiles(Plugin plugin, String folder);

	void removeFile(String className);

	Set<String> getFiles();

	void addFont(String name, String assetId, float size, float oversample);

	boolean open(Player player, String className, String method, Object... args);

	void close(Player player);

	CompletableFuture<Object> call(Player player, String className, String method, Object... args);

	void run(Player player, String className, String method, Object... args);

	void replaceMethod(Player player, String className, String methodSource);

	void openLink(Player player, String url);

	Optional<OpenGui> getOpen(Player player);

	default boolean showHud(Player player, String className, String method, Object... args) {
		return showHud(player, 0, className, method, args);
	}

	boolean showHud(Player player, int order, String className, String method, Object... args);

	void hideHud(Player player, String className);

	void hideAllHuds(Player player);

	java.util.Map<String, String> getHuds(Player player);

	default boolean setHudPart(Player player, HudPart part, boolean visible) {
		return setHudPart(player, part, visible, 0, 0, 1);
	}

	boolean setHudPart(Player player, HudPart part, boolean visible, float x, float y, float scale);

	void resetHudParts(Player player);

	boolean isSupported(Player player);

	void onMessage(String className, String name, GuiMessageHandler handler);

	@FunctionalInterface
	interface GuiMessageHandler {
		void handle(Player player, GuiMessage message);
	}

	record OpenGui(String className, String screen) {
	}
}
