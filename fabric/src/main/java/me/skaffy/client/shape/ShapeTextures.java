package me.skaffy.client.shape;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.platform.NativeImage;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.protocol.Protocol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import org.jspecify.annotations.Nullable;

final class ShapeTextures {
	private static final String NAMESPACE = "skaffy_shape_" + Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36).toLowerCase(Locale.ROOT);
	private static final Map<String, Identifier> LOADED = new HashMap<>();
	private static @Nullable DiskAssetStore assets;
	private static int nextId;

	private ShapeTextures() {
	}

	static void assets(@Nullable DiskAssetStore store) {
		assets = store;
	}

	static Identifier get(String name) {
		Identifier loaded = LOADED.get(name);

		if (loaded == null) {
			loaded = load(name);
			LOADED.put(name, loaded);
		}

		return loaded;
	}

	private static Identifier load(String name) {
		byte[] png = null;

		try {
			if (name.contains(":")) {
				Identifier id = Identifier.tryParse(name.toLowerCase(Locale.ROOT));

				if (id != null) {
					PackResources vanilla = Minecraft.getInstance().getVanillaPackResources().fullResources();
					IoSupplier<InputStream> resource = vanilla.getResource(PackType.CLIENT_RESOURCES, id.withPath("textures/" + id.getPath() + ".png"));

					if (resource != null) {
						try (InputStream stream = resource.get()) {
							png = stream.readAllBytes();
						}
					}
				}
			} else if (assets != null && Protocol.isValidAssetId(name) && assets.size(name) >= 0) {
				png = assets.read(name);
			}
		} catch (IOException e) {
			png = null;
		}

		if (png == null) {
			SkaffySAPIClient.LOGGER.warn("Shape texture {} not found", name);
			return MissingTextureAtlasSprite.getLocation();
		}

		try {
			NativeImage image = NativeImage.read(png);

			if (image.getHeight() > image.getWidth() && image.getHeight() % image.getWidth() == 0) {
				NativeImage frame = new NativeImage(image.getWidth(), image.getWidth(), false);

				for (int x = 0; x < image.getWidth(); x++) {
					for (int y = 0; y < image.getWidth(); y++) {
						frame.setPixel(x, y, image.getPixel(x, y));
					}
				}

				image.close();
				image = frame;
			}

			Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, "texture/" + nextId++);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			return id;
		} catch (IOException | RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("Shape texture {} isn't a readable PNG: {}", name, e.getMessage());
			return MissingTextureAtlasSprite.getLocation();
		}
	}

	static void clear() {
		for (Identifier id : LOADED.values()) {
			if (id.getNamespace().equals(NAMESPACE)) {
				Minecraft.getInstance().getTextureManager().release(id);
			}
		}

		LOADED.clear();
		ShapeRenderTypes.clear();
	}
}
