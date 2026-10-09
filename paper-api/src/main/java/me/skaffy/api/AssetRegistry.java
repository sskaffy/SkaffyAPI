package me.skaffy.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import org.bukkit.plugin.Plugin;

public interface AssetRegistry {
	void register(String id, byte[] data);

	void register(String id, Path file) throws IOException;

	void register(Plugin plugin, String id, String resourcePath) throws IOException;

	boolean unregister(String id);

	boolean contains(String id);

	Set<String> getIds();
}
