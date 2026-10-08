package me.skaffy.paper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.skaffy.api.AssetRegistry;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.asset.ServerAsset;
import org.bukkit.plugin.Plugin;

final class PaperAssetRegistry implements AssetRegistry {
	private final Map<String, ServerAsset> assets = new ConcurrentHashMap<>();

	@Override
	public void register(String id, byte[] data) {
		String problem = validate(id, data);

		if (problem != null) {
			throw new IllegalArgumentException(problem);
		}

		assets.put(id, ServerAsset.of(data.clone()));
	}

	@Override
	public void register(String id, Path file) throws IOException {
		register(id, Files.readAllBytes(file));
	}

	@Override
	public void register(Plugin plugin, String id, String resourcePath) throws IOException {
		try (InputStream stream = plugin.getResource(resourcePath)) {
			if (stream == null) {
				throw new IOException(plugin.getName() + " has no resource " + resourcePath);
			}

			register(id, stream.readAllBytes());
		}
	}

	@Override
	public boolean unregister(String id) {
		return assets.remove(id) != null;
	}

	@Override
	public boolean contains(String id) {
		return assets.containsKey(id);
	}

	@Override
	public Set<String> getIds() {
		return Set.copyOf(assets.keySet());
	}

	byte[] read(String id) {
		ServerAsset asset = assets.get(id);
		return asset == null ? null : asset.data();
	}

	Map<String, ServerAsset> snapshot() {
		return new LinkedHashMap<>(assets);
	}

	static String validate(String id, byte[] data) {
		if (!Protocol.isValidAssetId(id)) {
			return "Invalid asset id '" + id + "': use 1 to 64 characters of a-z 0-9 _ - . like myplugin_ruby.png";
		}

		if (data.length > Protocol.MAX_ASSET_SIZE) {
			return "Asset " + id + " is " + data.length + " bytes, max is " + Protocol.MAX_ASSET_SIZE;
		}

		return null;
	}
}
