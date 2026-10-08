package me.skaffy.client.look;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.NativeImage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.model.ClientEntityModels;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.playerlooks.PlayerLook;
import me.skaffy.protocol.playerlooks.PlayerLooksCodec;
import me.skaffy.protocol.playerlooks.PlayerLooksPacket;
import me.skaffy.protocol.playerlooks.PlayerLooksPacket.RemoveLook;
import me.skaffy.protocol.playerlooks.PlayerLooksPacket.SetLook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

public final class ClientPlayerLooks {
	private static final String NAMESPACE = "skaffy_look_" + Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36).toLowerCase(Locale.ROOT);
	private static final Int2ObjectMap<ClientLook> LOOKS = new Int2ObjectOpenHashMap<>();
	private static final Map<String, ClientAsset.@Nullable Texture> TEXTURES = new HashMap<>();
	private static final Map<PlayerLook.Profile, Supplier<PlayerSkinRenderCache.RenderInfo>> PROFILES = new HashMap<>();
	private static volatile @Nullable DiskAssetStore assets;
	private static int nextTexture;

	private ClientPlayerLooks() {
	}

	static final class ClientLook {
		final PlayerLook look;
		private @Nullable LookDummy dummy;
		private @Nullable Level dummyLevel;

		ClientLook(PlayerLook look) {
			this.look = look;
		}

		@Nullable LookDummy dummy() {
			Level level = Minecraft.getInstance().level;

			if (!(look.form() instanceof PlayerLook.EntityForm form) || level == null) {
				return null;
			}

			if (dummyLevel != level) {
				dummyLevel = level;
				dummy = LookDummy.create(form, level);
			}

			return dummy;
		}
	}

	public static void receive(byte[] data) {
		PlayerLooksPacket packet;

		try {
			packet = PlayerLooksCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed player looks packet: {}", e.getMessage());
			return;
		}

		Minecraft.getInstance().execute(() -> {
			switch (packet) {
				case SetLook set -> set(set.entity(), set.look());
				case RemoveLook remove -> remove(remove.entity());
			}
		});
	}

	public static void load(DiskAssetStore store) {
		assets = store;
	}

	public static void unload() {
		assets = null;
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			int[] ids = LOOKS.keySet().toIntArray();
			LOOKS.clear();

			for (int id : ids) {
				ClientEntityModels.setPlayerModel(id, 0);
				refresh(id);
			}

			TEXTURES.values().forEach(texture -> {
				if (texture != null) {
					minecraft.getTextureManager().release(texture.texturePath());
				}
			});
			TEXTURES.clear();
			PROFILES.clear();
		});
	}

	public static void forget(int entityId) {
		if (LOOKS.remove(entityId) != null) {
			ClientEntityModels.setPlayerModel(entityId, 0);
		}
	}

	public static void added(Entity entity) {
		if (entity instanceof AbstractClientPlayer && LOOKS.containsKey(entity.getId())) {
			entity.refreshDimensions();
		}
	}

	private static void set(int entityId, PlayerLook look) {
		LOOKS.put(entityId, new ClientLook(look));
		ClientEntityModels.setPlayerModel(entityId, look.form() instanceof PlayerLook.CustomModel model ? model.model() : 0);
		refresh(entityId);
	}

	private static void remove(int entityId) {
		if (LOOKS.remove(entityId) != null) {
			ClientEntityModels.setPlayerModel(entityId, 0);
			refresh(entityId);
		}
	}

	private static void refresh(int entityId) {
		Level level = Minecraft.getInstance().level;
		Entity entity = level == null ? null : level.getEntity(entityId);

		if (entity instanceof AbstractClientPlayer) {
			entity.refreshDimensions();
		}
	}

	static @Nullable ClientLook shown(Entity entity) {
		if (LOOKS.isEmpty() || !(entity instanceof AbstractClientPlayer)) {
			return null;
		}

		ClientLook look = LOOKS.get(entity.getId());
		return look != null && (entity != Minecraft.getInstance().player || look.look.showToSelf()) ? look : null;
	}

	public static boolean showsCustomModel(Entity entity) {
		ClientLook look = shown(entity);
		return look != null && look.look.form() instanceof PlayerLook.CustomModel;
	}

	public static EntityDimensions dimensions(AbstractClientPlayer player, Pose pose, EntityDimensions original) {
		if (LOOKS.isEmpty() || pose == Pose.SLEEPING) {
			return original;
		}

		ClientLook look = LOOKS.get(player.getId());

		if (look == null || player == Minecraft.getInstance().player && !look.look.ownHitbox()) {
			return original;
		}

		PlayerLook.Hitbox hitbox = look.look.hitbox();

		if (hitbox != null) {
			EntityDimensions custom = EntityDimensions.scalable(hitbox.width(), hitbox.height());
			return hitbox.eyeHeight() != null ? custom.withEyeHeight(hitbox.eyeHeight()) : custom;
		}

		float scale = look.look.scale();

		return switch (look.look.form()) {
			case PlayerLook.PlayerModel ignored -> scaled(original, scale);
			case PlayerLook.CustomModel model -> scaled(original, scale * ClientEntityModels.modelScale(model.model()));
			case PlayerLook.EntityForm ignored -> {
				LookDummy dummy = look.dummy();
				yield dummy == null ? scaled(original, scale) : scaled(dummy.entity.getDimensions(pose), scale);
			}
		};
	}

	private static EntityDimensions scaled(EntityDimensions dimensions, float scale) {
		if (scale == 1) {
			return dimensions;
		}

		return new EntityDimensions(dimensions.width() * scale, dimensions.height() * scale, dimensions.eyeHeight() * scale,
				dimensions.attachments().scale(scale, scale, scale), false);
	}

	static ClientAsset.@Nullable Texture texture(String asset) {
		if (TEXTURES.containsKey(asset)) {
			return TEXTURES.get(asset);
		}

		ClientAsset.Texture texture = loadTexture(asset);
		TEXTURES.put(asset, texture);
		return texture;
	}

	private static ClientAsset.@Nullable Texture loadTexture(String asset) {
		DiskAssetStore store = assets;

		if (store == null) {
			return null;
		}

		try {
			if (store.size(asset) < 0) {
				SkaffySAPIClient.LOGGER.warn("Player look texture {} wasn't sent by the server", asset);
				return null;
			}

			NativeImage image = NativeImage.read(store.read(asset));
			Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, "texture/" + nextTexture++);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			return new ClientAsset.ResourceTexture(id, id);
		} catch (IOException | RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("Player look texture {} isn't a readable PNG: {}", asset, e.getMessage());
			return null;
		}
	}

	static PlayerSkinRenderCache.RenderInfo profile(PlayerLook.Profile profile) {
		return PROFILES.computeIfAbsent(profile, ClientPlayerLooks::lookup).get();
	}

	private static Supplier<PlayerSkinRenderCache.RenderInfo> lookup(PlayerLook.Profile profile) {
		ResolvableProfile resolvable;

		if (profile.value() != null) {
			Property textures = profile.signature() != null ? new Property("textures", profile.value(), profile.signature()) : new Property("textures", profile.value());
			PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", textures));
			resolvable = ResolvableProfile.createResolved(new GameProfile(profile.id() != null ? profile.id() : new UUID(0, 0), profile.name() != null ? profile.name() : "", properties));
		} else if (profile.id() != null) {
			resolvable = ResolvableProfile.createUnresolved(profile.id());
		} else {
			resolvable = ResolvableProfile.createUnresolved(profile.name());
		}

		return Minecraft.getInstance().playerSkinRenderCache().createLookup(resolvable);
	}
}
