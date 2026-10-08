package me.skaffy.client.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.look.ClientPlayerLooks;
import me.skaffy.client.model.gpu.GpuModels;
import me.skaffy.client.pack.PackBuilder;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.entitymodels.EntityModelsCodec;
import me.skaffy.protocol.entitymodels.EntityModelsPacket;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.DefineModels;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.PlayAnimation;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.SetEntityModel;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.SetVariables;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.StopAnimation;
import me.skaffy.protocol.entitymodels.ModelDefinition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class ClientEntityModels {
	private static final List<ModelDefinition> PENDING = new ArrayList<>();
	private static volatile List<ClientModel> prepared = List.of();
	private static List<ClientModel> models = List.of();
	private static volatile List<Identifier> textures = List.of();
	private static final Int2ObjectMap<AnimationPlayer> ENTITIES = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<AnimationPlayer> PLAYERS = new Int2ObjectOpenHashMap<>();
	private static long lastRelease;

	private ClientEntityModels() {
	}

	public static void receive(byte[] data, boolean registering) {
		EntityModelsPacket packet;

		try {
			packet = EntityModelsCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed entity models packet: {}", e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		switch (packet) {
			case DefineModels define -> {
				if (registering) {
					synchronized (PENDING) {
						PENDING.addAll(define.models());
					}
				}
			}
			case SetEntityModel set -> minecraft.execute(() -> setModel(set.entity(), set.model()));
			case PlayAnimation play -> minecraft.execute(() -> {
				AnimationPlayer player = playerOf(play.entity());

				if (player != null) {
					player.play(play.animation(), play.mode(), play.speed(), play.start(), play.fadeIn(), false, AnimationPlayer.now());
				}
			});
			case StopAnimation stop -> minecraft.execute(() -> {
				AnimationPlayer player = playerOf(stop.entity());

				if (player != null) {
					player.stop(stop.animation(), stop.fadeOut(), AnimationPlayer.now());
				}
			});
			case SetVariables set -> minecraft.execute(() -> {
				AnimationPlayer player = playerOf(set.entity());

				if (player != null) {
					set.variables().forEach(variable -> player.setVariable(variable.name(), variable.value()));
				}
			});
		}
	}

	public static void load(PackBuilder pack, DiskAssetStore assets) {
		List<ModelDefinition> definitions;

		synchronized (PENDING) {
			definitions = List.copyOf(PENDING);
			PENDING.clear();
		}

		ModelLoader loader = new ModelLoader(assets);
		Map<String, BedrockGeometry> geometries = new HashMap<>();
		Map<String, Identifier> exportedTextures = new HashMap<>();
		List<ClientModel> loaded = new ArrayList<>(definitions.size());

		for (int i = 0; i < definitions.size(); i++) {
			ModelDefinition definition = definitions.get(i);
			ClientModel.Format format = loader.format(definition.geometry());

			if (format != ClientModel.Format.BEDROCK) {
				loaded.add(new ClientModel(i + 1, definition, format, null, null, null, null, null, loader));
				continue;
			}

			BedrockGeometry geometry = geometries.computeIfAbsent(definition.geometry(), asset -> geometry(loader, asset));
			BedrockGeometry babyGeometry = definition.babyGeometry() == null ? null : geometries.computeIfAbsent(definition.babyGeometry(), asset -> geometry(loader, asset));
			Identifier texture = definition.texture() == null ? null : exportedTextures.computeIfAbsent(definition.texture(), asset -> texture(pack, asset));
			Identifier emissive = definition.emissiveTexture() == null ? null : exportedTextures.computeIfAbsent(definition.emissiveTexture(), asset -> texture(pack, asset));
			AnimationClip[] clips = new AnimationClip[definition.animations().size()];
			ClientModel model = new ClientModel(i + 1, definition, format, geometry, babyGeometry, texture, emissive, clips, loader);

			for (int a = 0; a < clips.length; a++) {
				clips[a] = loader.clip(model, null, definition.animations().get(a));
			}

			loaded.add(model);
		}

		prepared = List.copyOf(loaded);
		textures = List.copyOf(exportedTextures.values());
	}

	public static void activate() {
		models = prepared;
		prepared = List.of();
	}

	public static void unload() {
		synchronized (PENDING) {
			PENDING.clear();
		}

		prepared = List.of();
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			models.forEach(ClientModel::release);
			models = List.of();
			ENTITIES.clear();
			PLAYERS.clear();
			CustomRenderers.clear();
			GenericModelRenderer.clear();
			GpuModels.clear();
			ModelEffects.clear();
			textures.forEach(minecraft.getTextureManager()::release);
			textures = List.of();
		});
	}

	public static @Nullable ClientModel model(int number) {
		return number >= 1 && number <= models.size() ? models.get(number - 1) : null;
	}

	public static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		double now = AnimationPlayer.now();

		if (level != null) {
			for (Int2ObjectMap<AnimationPlayer> map : List.of(ENTITIES, PLAYERS)) {
				for (Int2ObjectMap.Entry<AnimationPlayer> entry : map.int2ObjectEntrySet()) {
					Entity entity = level.getEntity(entry.getIntKey());

					if (entity != null) {
						AnimationPlayer player = entry.getValue();
						player.tick(entity, now, effect -> playEffect(entity, player.model(), effect));
					}
				}
			}
		}

		long millis = Util.getMillis();

		if (millis - lastRelease > 1000) {
			lastRelease = millis;

			for (ClientModel model : models) {
				model.releaseIfUnused(millis);
			}
		}
	}

	private static void playEffect(Entity entity, ClientModel model, AnimationClip.Effect effect) {
		Vector3f offset = new Vector3f(0, entity.getBbHeight() / 2, 0);
		ModelData data = effect.locator().isEmpty() ? null : model.data();

		if (data != null) {
			Vector3f point = locatorPoint(data, effect.locator());

			if (point != null) {
				float yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
				float scale = model.definition().scale();
				offset = new Matrix4f().rotateY((float) Math.toRadians(-yaw)).scale(scale).translate(0, 1.501f, 0).scale(1, -1, -1).transformPosition(point);
			}
		}

		ModelEffects.play(effect, entity.getX() + offset.x, entity.getY() + offset.y, entity.getZ() + offset.z);
	}

	static @Nullable Vector3f locatorPoint(ModelData data, String name) {
		ModelData.Locator locator = data.locator(name);
		int bone = locator != null ? locator.bone() : data.boneIndex(name);

		if (bone < 0) {
			return null;
		}

		Matrix4f matrix = new Matrix4f();
		List<Integer> chain = new ArrayList<>();

		for (int b = bone; b >= 0; b = data.bones().get(b).parent()) {
			chain.addFirst(b);
		}

		for (int b : chain) {
			ModelData.Bone part = data.bones().get(b);
			matrix.translate(part.x() / 16, part.y() / 16, part.z() / 16).rotateZYX(part.zRot(), part.yRot(), part.xRot()).scale(part.xScale(), part.yScale(), part.zScale());
		}

		return locator == null ? matrix.transformPosition(new Vector3f()) : matrix.transformPosition(locator.x() / 16, locator.y() / 16, locator.z() / 16, new Vector3f());
	}

	public static void forget(int entityId) {
		ENTITIES.remove(entityId);
		PLAYERS.remove(entityId);
		GenericModelRenderer.forget(entityId);
	}

	public static @Nullable EntityRenderer<?, ?> rendererFor(Entity entity) {
		if (entity instanceof Avatar) {
			if (PLAYERS.isEmpty() || !ClientPlayerLooks.showsCustomModel(entity)) {
				return null;
			}

			AnimationPlayer player = PLAYERS.get(entity.getId());
			return player == null ? null : CustomRenderers.get(player.model(), EntityTypes.PLAYER);
		}

		if (ENTITIES.isEmpty()) {
			return null;
		}

		AnimationPlayer player = ENTITIES.get(entity.getId());
		return player == null ? null : CustomRenderers.get(player.model(), entity.getType());
	}

	public static void setPlayerModel(int entityId, int number) {
		ClientModel model = model(number);

		if (model == null) {
			PLAYERS.remove(entityId);
			return;
		}

		AnimationPlayer existing = PLAYERS.get(entityId);

		if (existing == null || existing.model() != model) {
			PLAYERS.put(entityId, new AnimationPlayer(model));
		}
	}

	public static float modelScale(int number) {
		ClientModel model = model(number);
		return model == null ? 1 : model.definition().scale();
	}

	private static @Nullable AnimationPlayer playerOf(int entityId) {
		AnimationPlayer player = PLAYERS.get(entityId);
		return player != null ? player : ENTITIES.get(entityId);
	}

	public static @Nullable AnimationPlayer animations(Entity entity) {
		return entity instanceof Avatar ? PLAYERS.get(entity.getId()) : ENTITIES.get(entity.getId());
	}

	public static void attach(EntityRenderer<?, ?> renderer, Entity entity, EntityRenderState state, float partialTicks) {
		if (ENTITIES.isEmpty() && PLAYERS.isEmpty() || !CustomRenderers.isCustom(renderer)) {
			return;
		}

		AnimationPlayer player = animations(entity);

		if (player == null) {
			return;
		}

		((SkaffyRenderState) state).skaffy$setModelData(renderData(player, renderer, entity, partialTicks));
	}

	static ModelRenderData renderData(AnimationPlayer player, EntityRenderer<?, ?> renderer, Entity entity, float partialTicks) {
		double dx = entity.getX() - entity.xo;
		double dz = entity.getZ() - entity.zo;
		float headY = 0;
		float distance = 0;

		if (entity instanceof LivingEntity living) {
			headY = Mth.wrapDegrees(Mth.rotLerp(partialTicks, living.yHeadRotO, living.yHeadRot) - Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot));
			distance = living.walkAnimation.position(partialTicks);
		}

		return new ModelRenderData(
				player.model(),
				renderer,
				player.active(AnimationPlayer.now()),
				(entity.tickCount + partialTicks) / 20,
				(float) Math.sqrt(dx * dx + dz * dz) * 20,
				entity.getViewXRot(partialTicks),
				headY,
				distance,
				Map.copyOf(player.variables()));
	}

	private static void setModel(int entityId, int number) {
		ClientModel model = model(number);

		if (model == null) {
			ENTITIES.remove(entityId);
			return;
		}

		AnimationPlayer existing = ENTITIES.get(entityId);

		if (existing == null || existing.model() != model) {
			ENTITIES.put(entityId, new AnimationPlayer(model));
		}
	}

	private static BedrockGeometry geometry(ModelLoader loader, String asset) {
		try {
			byte[] data = loader.read(asset);

			if (data == null) {
				throw new IllegalArgumentException("the server didn't send it");
			}

			return BedrockGeometry.parse(JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject());
		} catch (RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("Server model {} can't be read: {}", asset, e.getMessage());
			return new BedrockGeometry(64, 64, List.of());
		}
	}

	private static Identifier texture(PackBuilder pack, String asset) {
		Identifier id = pack.id("textures/entity/a/" + asset);
		byte[] image = pack.readAsset(asset);

		if (image == null) {
			SkaffySAPIClient.LOGGER.warn("Server entity texture {} not found", asset);
		} else {
			pack.add(id, image);
		}

		return id;
	}
}
