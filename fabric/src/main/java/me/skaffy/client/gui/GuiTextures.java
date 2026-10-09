package me.skaffy.client.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.serialization.JsonOps;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.gui.element.Affine;
import me.skaffy.client.gui.text.RichText.Picture;
import me.skaffy.client.gui.text.RichText.PictureKind;
import me.skaffy.protocol.Protocol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import org.jspecify.annotations.Nullable;

final class GuiTextures {
	private static final String NAMESPACE = "skaffy_gui_" + Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36).toLowerCase(Locale.ROOT);
	private static final Map<String, Texture> IMAGES = new HashMap<>();
	private static final Map<String, Supplier<PlayerSkinRenderCache.RenderInfo>> HEADS = new HashMap<>();
	private static final Map<List<Integer>, Texture> GRADIENTS = new HashMap<>();
	private static final Map<String, Object> ITEMS = new HashMap<>();
	private static final Map<String, Object> ENTITIES = new HashMap<>();
	private static int nextId;
	private static @Nullable Texture missing;

	private GuiTextures() {
	}

	static final class Texture {
		final Identifier id;
		final boolean owned;
		final int imageWidth;
		final int imageHeight;
		final int frameWidth;
		final int frameHeight;
		final int columns;
		final int[] frames;
		final int[] times;
		final int totalTime;
		boolean linear;

		Texture(Identifier id, boolean owned, int imageWidth, int imageHeight, int frameWidth, int frameHeight, int[] frames, int[] times) {
			this.id = id;
			this.owned = owned;
			this.imageWidth = imageWidth;
			this.imageHeight = imageHeight;
			this.frameWidth = Math.max(1, frameWidth);
			this.frameHeight = Math.max(1, frameHeight);
			this.columns = Math.max(1, imageWidth / this.frameWidth);
			this.frames = frames;
			this.times = times;
			this.totalTime = Math.max(1, Arrays.stream(times).sum());
		}

		int[] frameOrigin() {
			int frame = frames[0];

			if (frames.length > 1) {
				long tick = Util.getMillis() / 50 % totalTime;

				for (int i = 0; i < frames.length; i++) {
					if (tick < times[i]) {
						frame = frames[i];
						break;
					}

					tick -= times[i];
				}
			}

			return new int[] {frame % columns * frameWidth, frame / columns * frameHeight};
		}

		TextureSetup setup() {
			AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
			return TextureSetup.singleTexture(texture.getTextureView(), linear ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR) : texture.getSampler());
		}

		void release() {
			if (owned) {
				Minecraft.getInstance().getTextureManager().release(id);
			}
		}
	}

	static void clear() {
		IMAGES.values().forEach(Texture::release);
		IMAGES.clear();
		GRADIENTS.values().forEach(Texture::release);
		GRADIENTS.clear();
		HEADS.clear();
		ITEMS.clear();
		ENTITIES.clear();
	}

	static Texture missing() {
		if (missing == null) {
			missing = new Texture(MissingTextureAtlasSprite.getLocation(), false, 16, 16, 16, 16, new int[] {0}, new int[] {1});
		}

		return missing;
	}

	static Texture image(String source) {
		Texture cached = IMAGES.get(source);

		if (cached != null) {
			return cached;
		}

		Texture loaded = loadImage(source);
		IMAGES.put(source, loaded);
		return loaded;
	}

	private static Texture loadImage(String source) {
		byte[] png;
		byte[] metadata;

		if (source.contains(":")) {
			Identifier id = Identifier.tryParse(source.toLowerCase(Locale.ROOT));

			if (id == null) {
				return missing();
			}

			PackResources vanilla = Minecraft.getInstance().getVanillaPackResources().fullResources();
			png = read(vanilla, id.withPath("textures/" + id.getPath() + ".png"));
			metadata = read(vanilla, id.withPath("textures/" + id.getPath() + ".png.mcmeta"));
		} else {
			DiskAssetStore store = ClientGuis.assets();

			if (store == null || !Protocol.isValidAssetId(source)) {
				return missing();
			}

			try {
				png = store.size(source) >= 0 ? store.read(source) : null;
				metadata = store.size(source + ".mcmeta") >= 0 ? store.read(source + ".mcmeta") : null;
			} catch (IOException e) {
				png = null;
				metadata = null;
			}
		}

		if (png == null) {
			SkaffySAPIClient.LOGGER.warn("GUI image {} not found", source);
			return missing();
		}

		try {
			return upload(png, metadata);
		} catch (IOException | RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("GUI image {} isn't a readable PNG: {}", source, e.getMessage());
			return missing();
		}
	}

	private static Texture upload(byte[] png, byte @Nullable [] metadata) throws IOException {
		NativeImage image = NativeImage.read(png);
		Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, "image/" + nextId++);
		int width = image.getWidth();
		int height = image.getHeight();
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		AnimationMetadataSection animation = metadata == null ? null : animation(metadata);

		if (animation == null) {
			return new Texture(id, true, width, height, width, height, new int[] {0}, new int[] {1});
		}

		FrameSize size = animation.calculateFrameSize(width, height);
		int frameWidth = Math.max(1, Math.min(size.width(), width));
		int frameHeight = Math.max(1, Math.min(size.height(), height));
		int count = (width / frameWidth) * (height / frameHeight);
		List<Integer> frames = new ArrayList<>();
		List<Integer> times = new ArrayList<>();
		List<AnimationFrame> list = animation.frames().orElse(null);

		if (list == null) {
			for (int i = 0; i < count; i++) {
				frames.add(i);
				times.add(animation.defaultFrameTime());
			}
		} else {
			for (AnimationFrame frame : list) {
				if (frame.index() < count) {
					frames.add(frame.index());
					times.add(frame.timeOr(animation.defaultFrameTime()));
				}
			}
		}

		if (frames.isEmpty()) {
			frames.add(0);
			times.add(1);
		}

		return new Texture(id, true, width, height, frameWidth, frameHeight, frames.stream().mapToInt(Integer::intValue).toArray(), times.stream().mapToInt(Integer::intValue).toArray());
	}

	private static @Nullable AnimationMetadataSection animation(byte[] metadata) {
		try {
			JsonObject json = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
			return json.has("animation") ? AnimationMetadataSection.CODEC.parse(JsonOps.INSTANCE, json.get("animation")).result().orElse(null) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static byte @Nullable [] read(PackResources pack, Identifier path) {
		IoSupplier<InputStream> resource = pack.getResource(PackType.CLIENT_RESOURCES, path);

		if (resource == null) {
			return null;
		}

		try (InputStream stream = resource.get()) {
			return stream.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}

	static @Nullable TextureAtlasSprite guiSprite(String name) {
		Identifier id = Identifier.tryParse(name.toLowerCase(Locale.ROOT));

		if (id == null) {
			return null;
		}

		TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI).getSprite(id);
		return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : sprite;
	}

	static Texture gradient(int[] colors) {
		List<Integer> key = Arrays.stream(colors).boxed().toList();
		Texture cached = GRADIENTS.get(key);

		if (cached != null) {
			return cached;
		}

		NativeImage image = new NativeImage(256, 1, false);

		for (int x = 0; x < 256; x++) {
			double t = x / 255.0 * (colors.length - 1);
			int segment = Math.min((int) t, colors.length - 2);
			int argb = mix(colors[segment], colors[segment + 1], t - segment);
			image.setPixel(x, 0, argb);
		}

		Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, "gradient/" + nextId++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		Texture texture = new Texture(id, true, 256, 1, 256, 1, new int[] {0}, new int[] {1});
		texture.linear = true;
		GRADIENTS.put(key, texture);
		return texture;
	}

	private static int mix(int from, int to, double t) {
		int a = (int) Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
		int r = (int) Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		int g = (int) Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	static PlayerSkinRenderCache.RenderInfo skin(String player) {
		return HEADS.computeIfAbsent(player, GuiTextures::lookupSkin).get();
	}

	private static Supplier<PlayerSkinRenderCache.RenderInfo> lookupSkin(String player) {
		ResolvableProfile profile;
		UUID uuid = parseUuid(player);

		if (uuid != null) {
			profile = ResolvableProfile.createUnresolved(uuid);
		} else if (player.length() > 16) {
			Property textures = new Property("textures", player);
			profile = ResolvableProfile.createResolved(new GameProfile(new UUID(0, 0), "", new PropertyMap(ImmutableMultimap.of("textures", textures))));
		} else {
			profile = ResolvableProfile.createUnresolved(player);
		}

		return Minecraft.getInstance().playerSkinRenderCache().createLookup(profile);
	}

	private static @Nullable UUID parseUuid(String text) {
		String value = text.length() == 32 ? text.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5") : text;

		if (value.length() != 36) {
			return null;
		}

		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static double aspect(Picture picture) {
		if (picture.kind() == PictureKind.HEAD) {
			return 1;
		}

		Texture texture = image(picture.source());
		return texture.frameWidth / (double) texture.frameHeight;
	}

	static void drawPicture(GuiGraphicsExtractor graphics, Picture picture, Affine at, double width, double height, int tint) {
		if (picture.kind() == PictureKind.HEAD) {
			drawHead(graphics, picture.source(), picture.hat(), at, width, height, null, tint);
			return;
		}

		Texture texture = image(picture.source());
		int[] origin = texture.frameOrigin();
		Shapes.texturedRect(graphics, at, 0, 0, width, height, null, texture, origin[0] / (double) texture.imageWidth, origin[1] / (double) texture.imageHeight,
				(origin[0] + texture.frameWidth) / (double) texture.imageWidth, (origin[1] + texture.frameHeight) / (double) texture.imageHeight, tint);
	}

	static void drawHead(GuiGraphicsExtractor graphics, String player, boolean hat, Affine at, double width, double height, double @Nullable [] radii, int tint) {
		PlayerSkinRenderCache.RenderInfo info = skin(player);
		Identifier texturePath = info.playerSkin().body().texturePath();
		Texture skin = new Texture(texturePath, false, 64, 64, 64, 64, new int[] {0}, new int[] {1});
		Shapes.texturedRect(graphics, at, 0, 0, width, height, radii, skin, 8 / 64.0, 8 / 64.0, 16 / 64.0, 16 / 64.0, tint);

		if (hat) {
			Shapes.texturedRect(graphics, at, 0, 0, width, height, radii, skin, 40 / 64.0, 8 / 64.0, 48 / 64.0, 16 / 64.0, tint);
		}
	}

	static Object item(String spec) {
		return ITEMS.computeIfAbsent(spec, key -> {
			Minecraft minecraft = Minecraft.getInstance();

			if (minecraft.level == null) {
				return "not in a world";
			}

			try {
				ItemInput input = new ItemParser(minecraft.level.registryAccess()).parse(new StringReader(key));
				return new ItemStack(input.item(), 1, input.components());
			} catch (CommandSyntaxException e) {
				return e.getMessage();
			}
		});
	}

	static @Nullable LivingEntity entity(String type) {
		Object cached = ENTITIES.computeIfAbsent(type, key -> {
			Minecraft minecraft = Minecraft.getInstance();
			Identifier id = Identifier.tryParse(key.toLowerCase(Locale.ROOT));

			if (minecraft.level == null || id == null) {
				return "";
			}

			EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
			Entity entity = entityType == null ? null : entityType.create(minecraft.level, EntitySpawnReason.LOAD);

			if (!(entity instanceof LivingEntity living)) {
				return "";
			}

			living.setId(-1);
			return living;
		});
		return cached instanceof LivingEntity living ? living : null;
	}
}
