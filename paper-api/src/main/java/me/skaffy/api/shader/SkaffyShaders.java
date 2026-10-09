package me.skaffy.api.shader;

import java.util.List;
import java.util.Map;
import java.util.Set;

import me.skaffy.api.Easing;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface SkaffyShaders {
	String VISTA = "skaffy.vista.Vista";

	int MIN_SHADER_DISTANCE = 2;
	int MAX_SHADER_DISTANCE = 32;

	void addFile(String className, String source);

	List<String> addFiles(Plugin plugin, String folder);

	void removeFile(String className);

	Set<String> getFiles();

	default boolean enable(Player player, String className) {
		return enable(player, className, 0, Map.of());
	}

	default boolean enable(Player player, String className, int order) {
		return enable(player, className, order, Map.of());
	}

	boolean enable(Player player, String className, int order, Map<String, ?> uniforms);

	void disable(Player player, String className);

	void disableAll(Player player);

	default void set(Player player, String className, String uniform, Object value) {
		set(player, className, Map.of(uniform, value));
	}

	default void set(Player player, String className, Map<String, ?> values) {
		animate(player, className, values, 0, Easing.LINEAR);
	}

	default void animate(Player player, String className, String uniform, Object value, int durationMillis, Easing easing) {
		animate(player, className, Map.of(uniform, value), durationMillis, easing);
	}

	void animate(Player player, String className, Map<String, ?> values, int durationMillis, Easing easing);

	Map<String, Integer> getEnabled(Player player);

	boolean isRunning(Player player, String className);

	boolean isSupported(Player player);

	default boolean setVista(Player player, boolean enabled) {
		return setVista(player, enabled, false);
	}

	boolean setVista(Player player, Boolean enabled, boolean locked);

	default boolean resetVista(Player player) {
		return setVista(player, null, false);
	}

	Boolean isVistaOn(Player player);

	default boolean setShaderDistance(Player player, int chunks) {
		return setShaderDistance(player, chunks, false);
	}

	boolean setShaderDistance(Player player, int chunks, boolean locked);

	default boolean resetShaderDistance(Player player) {
		return setShaderDistance(player, 0, false);
	}

	int getShaderDistance(Player player);
}
