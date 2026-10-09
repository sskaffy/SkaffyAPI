package me.skaffy.client.nametag;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.pack.PackBuilder;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.nametags.NameTag;
import me.skaffy.protocol.nametags.NameTagsCodec;
import me.skaffy.protocol.nametags.NameTagsPacket;
import me.skaffy.protocol.nametags.NameTagsPacket.DefineSprites;
import me.skaffy.protocol.nametags.NameTagsPacket.RemoveNameTag;
import me.skaffy.protocol.nametags.NameTagsPacket.SetLine;
import me.skaffy.protocol.nametags.NameTagsPacket.SetNameTag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

public final class ClientNameTags {
	private static final double VANILLA_DISTANCE = 64;
	private static final double SNEAKING_DISTANCE = 32;
	private static final List<String> PENDING = new ArrayList<>();
	private static volatile List<SpriteFile> prepared = List.of();
	private static volatile String namespace = "skaffys-api";
	private static List<NameTagTexture> sprites = List.of();
	private static final Map<String, NameTagTexture> VANILLA = new HashMap<>();
	private static final Map<HeadKey, Supplier<PlayerSkinRenderCache.RenderInfo>> HEADS = new HashMap<>();
	private static final Int2ObjectMap<NameTag> TAGS = new Int2ObjectOpenHashMap<>();
	private static @Nullable NameTagTexture missing;

	private record SpriteFile(String asset, byte @Nullable [] png, byte @Nullable [] metadata) {
	}

	private record HeadKey(@Nullable UUID id, @Nullable String name, @Nullable String skin, @Nullable String signature) {
	}

	private ClientNameTags() {
	}

	public static void receive(byte[] data, boolean registering) {
		NameTagsPacket packet;

		try {
			packet = NameTagsCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed name tags packet: {}", e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		switch (packet) {
			case DefineSprites define -> {
				if (registering) {
					synchronized (PENDING) {
						if (PENDING.size() + define.assets().size() <= NameTagsCodec.MAX_SPRITES) {
							PENDING.addAll(define.assets());
						} else {
							SkaffySAPIClient.LOGGER.warn("Server defined more than {} name tag sprites, ignoring the rest", NameTagsCodec.MAX_SPRITES);
						}
					}
				}
			}
			case SetNameTag set -> minecraft.execute(() -> TAGS.put(set.entity(), set.tag()));
			case SetLine set -> minecraft.execute(() -> {
				NameTag tag = TAGS.get(set.entity());

				if (tag != null && set.line() < tag.lines().size()) {
					TAGS.put(set.entity(), tag.withLine(set.line(), set.content()));
				}
			});
			case RemoveNameTag remove -> minecraft.execute(() -> TAGS.remove(remove.entity()));
		}
	}

	public static void load(PackBuilder pack) {
		List<String> assets;

		synchronized (PENDING) {
			assets = List.copyOf(PENDING);
			PENDING.clear();
		}

		List<SpriteFile> files = new ArrayList<>(assets.size());

		for (String asset : assets) {
			byte[] png = pack.readAsset(asset);

			if (png == null) {
				SkaffySAPIClient.LOGGER.warn("Name tag sprite {} not found", asset);
			}

			files.add(new SpriteFile(asset, png, pack.readAsset(asset + ".mcmeta")));
		}

		namespace = pack.namespace();
		prepared = List.copyOf(files);
	}

	public static void activate() {
		List<SpriteFile> files = prepared;
		prepared = List.of();
		List<NameTagTexture> loaded = new ArrayList<>(files.size());

		for (int i = 0; i < files.size(); i++) {
			SpriteFile file = files.get(i);
			NameTagTexture texture = null;

			if (file.png() != null) {
				try {
					texture = NameTagTexture.load(Identifier.fromNamespaceAndPath(namespace, "name_tag/sprite/" + (i + 1)), file.png(), file.metadata());
				} catch (IOException | RuntimeException e) {
					SkaffySAPIClient.LOGGER.warn("Name tag sprite {} isn't a readable PNG: {}", file.asset(), e.getMessage());
				}
			}

			loaded.add(texture != null ? texture : missing());
		}

		sprites = List.copyOf(loaded);
	}

	public static void unload() {
		synchronized (PENDING) {
			PENDING.clear();
		}

		prepared = List.of();
		Minecraft.getInstance().execute(() -> {
			sprites.forEach(NameTagTexture::release);
			sprites = List.of();
			VANILLA.values().forEach(NameTagTexture::release);
			VANILLA.clear();
			HEADS.clear();
			TAGS.clear();
			NameTagRenderTypes.clear();
		});
	}

	public static void forget(int entityId) {
		TAGS.remove(entityId);
	}

	public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
		if (TAGS.isEmpty()) {
			return;
		}

		NameTag tag = TAGS.get(entity.getId());

		if (tag == null) {
			return;
		}

		state.nameTag = null;
		state.scoreText = null;
		NameTagRenderer.Look look = look(tag, entity);

		if (look == null) {
			return;
		}

		Vec3 attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));

		if (attachment == null) {
			attachment = new Vec3(0, entity.getBbHeight(), 0);
		}

		Minecraft minecraft = Minecraft.getInstance();
		boolean behindTranslucent = false;

		if (look != NameTagRenderer.Look.ALWAYS && !minecraft.gameRenderer.useImprovedTransparency() && minecraft.level != null) {
			Vec3 anchor = new Vec3(state.x + attachment.x + tag.offsetX(), state.y + attachment.y + 0.5 + tag.offsetY(), state.z + attachment.z + tag.offsetZ());
			behindTranslucent = translucentBetween(minecraft.level, minecraft.gameRenderer.mainCamera().position(), anchor);
		}

		((NameTagRenderState) state).skaffy$setNameTag(new NameTagRenderState.Data(tag, attachment, look, state.lightCoords, behindTranslucent));
	}

	private static boolean translucentBetween(ClientLevel level, Vec3 from, Vec3 to) {
		BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
		return BlockGetter.traverseBlocks(from, to, level, (world, pos) -> {
			BlockState block = world.getBlockState(pos);

			if (block.isAir()) {
				return null;
			}

			return block.getFluidState().is(FluidTags.WATER) || models.get(block).hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT) ? Boolean.TRUE : null;
		}, world -> Boolean.FALSE);
	}

	private static NameTagRenderer.@Nullable Look look(NameTag tag, Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.gui.hud.isHidden() || entity == minecraft.getCameraEntity() && !tag.showToSelf()) {
			return null;
		}

		if (!tag.showWhenInvisible() && minecraft.player != null && entity.isInvisibleTo(minecraft.player)) {
			return null;
		}

		double distance = tag.maxDistance() > 0 ? tag.maxDistance() : vanillaDistance(entity);
		NameTagRenderer.Look look = switch (tag.render()) {
			case DEFAULT -> NameTagRenderer.Look.DEFAULT;
			case BLOCK -> NameTagRenderer.Look.BLOCK;
			case ALWAYS -> NameTagRenderer.Look.ALWAYS;
		};

		if (entity.isDiscrete()) {
			switch (tag.sneak()) {
				case DEFAULT -> {
					look = NameTagRenderer.Look.SNEAKING;
					distance = Math.min(distance, SNEAKING_DISTANCE);
				}
				case HIDE -> {
					return null;
				}
				case SOFT_HIDE -> look = NameTagRenderer.Look.BLOCK;
				case SHOW -> {
				}
			}
		}

		return minecraft.getEntityRenderDispatcher().distanceToSqr(entity) < distance * distance ? look : null;
	}

	private static double vanillaDistance(Entity entity) {
		if (entity instanceof LivingEntity living) {
			AttributeInstance attribute = living.getAttribute(Attributes.NAME_TAG_DISTANCE);

			if (attribute != null) {
				return attribute.getValue();
			}
		}

		return VANILLA_DISTANCE;
	}

	static SpriteImage image(NameTag.Content content) {
		return switch (content) {
			case NameTag.Sprite sprite -> sprite.number() >= 1 && sprite.number() <= sprites.size() ? sprites.get(sprite.number() - 1) : missing();
			case NameTag.VanillaSprite sprite -> VANILLA.computeIfAbsent(sprite.texture(), ClientNameTags::loadVanilla);
			case NameTag.Head head -> new HeadImage(HEADS.computeIfAbsent(new HeadKey(head.id(), head.name(), head.skin(), head.signature()), ClientNameTags::lookupSkin), head.hat());
			case NameTag.Text text -> throw new IllegalArgumentException("Text isn't a picture");
		};
	}

	private static NameTagTexture loadVanilla(String reference) {
		Identifier id = Identifier.tryParse(reference);

		if (id == null) {
			return missing();
		}

		PackResources vanilla = Minecraft.getInstance().getVanillaPackResources().fullResources();
		byte[] png = read(vanilla, id.withPath("textures/" + id.getPath() + ".png"));

		if (png == null) {
			SkaffySAPIClient.LOGGER.warn("Name tag sprite {} isn't a vanilla texture", reference);
			return missing();
		}

		try {
			String path = "name_tag/vanilla/" + id.getNamespace() + "/" + id.getPath().toLowerCase(Locale.ROOT);
			return NameTagTexture.load(Identifier.fromNamespaceAndPath(namespace, path), png, read(vanilla, id.withPath("textures/" + id.getPath() + ".png.mcmeta")));
		} catch (IOException | RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("Vanilla texture {} can't be read: {}", reference, e.getMessage());
			return missing();
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

	private static Supplier<PlayerSkinRenderCache.RenderInfo> lookupSkin(HeadKey head) {
		ResolvableProfile profile;

		if (head.skin() != null) {
			Property textures = head.signature() != null ? new Property("textures", head.skin(), head.signature()) : new Property("textures", head.skin());
			PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", textures));
			UUID id = head.id() != null ? head.id() : new UUID(0, 0);
			profile = ResolvableProfile.createResolved(new GameProfile(id, head.name() != null ? head.name() : "", properties));
		} else if (head.id() != null) {
			profile = ResolvableProfile.createUnresolved(head.id());
		} else {
			profile = ResolvableProfile.createUnresolved(head.name());
		}

		return Minecraft.getInstance().playerSkinRenderCache().createLookup(profile);
	}

	private static NameTagTexture missing() {
		if (missing == null) {
			missing = NameTagTexture.missing();
		}

		return missing;
	}
}
