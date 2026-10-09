package me.skaffy.client.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.protocol.Protocol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

public final class PackBuilder {
	private final DiskAssetStore assets;
	private final PackResources vanilla;
	private final String namespace;
	private final Map<Identifier, byte[]> files = new HashMap<>();

	public PackBuilder(DiskAssetStore assets, PackResources vanilla) {
		this.assets = assets;
		this.vanilla = vanilla;
		this.namespace = "skaffy_" + Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36).toLowerCase(Locale.ROOT);
	}

	public String namespace() {
		return namespace;
	}

	public Map<Identifier, byte[]> files() {
		return files;
	}

	public void add(Identifier path, byte[] data) {
		files.put(path, data);
	}

	public void add(Identifier path, String text) {
		add(path, text.getBytes(StandardCharsets.UTF_8));
	}

	public Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	public boolean hasAsset(String reference) {
		return Protocol.isValidAssetId(reference) && assets.size(reference) >= 0;
	}

	public byte[] readAsset(String id) {
		if (!Protocol.isValidAssetId(id)) {
			return null;
		}

		try {
			return assets.read(id);
		} catch (IOException e) {
			SkaffySAPIClient.LOGGER.warn("Could not read server file {}", id, e);
			return null;
		}
	}

	public byte[] readVanilla(String folder, String reference, String extension) {
		Identifier id = vanillaId(reference);
		IoSupplier<InputStream> resource = vanilla.getResource(PackType.CLIENT_RESOURCES, id.withPath(folder + "/" + id.getPath() + extension));

		if (resource == null) {
			return null;
		}

		try (InputStream stream = resource.get()) {
			return stream.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}

	public static Identifier vanillaId(String reference) {
		Identifier id = Identifier.tryParse(reference);
		return id != null ? id : Identifier.withDefaultNamespace("missing");
	}
}
