package me.skaffy.paper.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import me.skaffy.api.event.animation.SkaffyAnimationMarkerEvent;
import me.skaffy.api.event.model.SkaffyEntityModelChangeEvent;
import me.skaffy.api.model.AnimationMode;
import me.skaffy.api.model.CustomEntityModel;
import me.skaffy.api.model.CustomEntityModels;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.entitymodels.EntityModelsCodec;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.DefineModels;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.PlayAnimation;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.SetEntityModel;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.SetVariables;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.Variable;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.StopAnimation;
import me.skaffy.protocol.entitymodels.ModelDefinition;
import me.skaffy.protocol.io.PacketWriter;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class PaperCustomEntityModels implements CustomEntityModels, Listener {
	private static final int DEFINITIONS_BUDGET = 512 * 1024;
	private static final int REMEMBERED_ANIMATIONS = 16;

	private final Plugin plugin;
	private final Function<UUID, ModelSession> sessions;
	private final NamespacedKey savedKey;
	private final NamespacedKey savedViewersKey;
	private final Map<String, CustomEntityModel> models = new ConcurrentHashMap<>();
	private final Map<UUID, String> everyone = new ConcurrentHashMap<>();
	private final Map<UUID, Map<UUID, String>> perViewer = new ConcurrentHashMap<>();
	private final Map<UUID, List<Played>> played = new ConcurrentHashMap<>();
	private final Map<UUID, Boolean> restored = new ConcurrentHashMap<>();
	private final Map<UUID, Map<String, Float>> variables = new ConcurrentHashMap<>();
	private final Map<String, Map<String, AnimationInfo.Clip>> clipFiles = new ConcurrentHashMap<>();
	private final AnimationTimers timers = new AnimationTimers();
	private volatile Function<String, byte[]> assetReader = id -> null;
	private volatile ModelImporter importer;
	private volatile BiFunction<UUID, UUID, String> lookModels = (viewer, player) -> null;

	public PaperCustomEntityModels(Plugin plugin, Function<UUID, ModelSession> sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
		this.savedKey = new NamespacedKey(plugin, "entity_model");
		this.savedViewersKey = new NamespacedKey(plugin, "entity_model_viewers");
	}

	public void setAssets(Function<String, byte[]> reader, ModelImporter importer) {
		this.assetReader = reader;
		this.importer = importer;
	}

	public AnimationTimers timers() {
		return timers;
	}

	@Override
	public void register(CustomEntityModel model) {
		try {
			definition(model);
		} catch (ProtocolException e) {
			throw new IllegalArgumentException("Model " + model.getName() + ": " + e.getMessage(), e);
		}

		byte[] geometry = assetReader.apply(model.getGeometry());

		if (geometry != null && model.getTexture() == null && ModelImporter.isBedrockGeometry(geometry)) {
			throw new IllegalArgumentException("Model " + model.getName() + " is Bedrock geometry, which needs a texture (CustomEntityModel.builder(name, geometry, texture))");
		}

		if (!models.containsKey(model.getName()) && models.size() >= MAX_MODELS) {
			throw new IllegalStateException("A server has at most " + MAX_MODELS + " entity models");
		}

		models.put(model.getName(), model);
	}

	@Override
	public boolean unregister(String name) {
		return models.remove(name) != null;
	}

	@Override
	public Optional<CustomEntityModel> getModel(String name) {
		return Optional.ofNullable(models.get(name));
	}

	@Override
	public Collection<CustomEntityModel> getModels() {
		return List.copyOf(models.values());
	}

	@Override
	public void set(Entity entity, CustomEntityModel model, boolean save) {
		requireRegistered(model);
		restore(entity);
		CustomEntityModel previous = modelNamed(everyone.put(entity.getUniqueId(), model.getName()));

		if (previous == null || !previous.getName().equals(model.getName())) {
			played.remove(entity.getUniqueId());
		}

		PersistentDataContainer data = entity.getPersistentDataContainer();

		if (save) {
			data.set(savedKey, PersistentDataType.STRING, model.getName());
		} else {
			data.remove(savedKey);
		}

		new SkaffyEntityModelChangeEvent(entity, null, previous, model, save).callEvent();
		sendToViewers(entity);
	}

	@Override
	public boolean remove(Entity entity) {
		restore(entity);
		CustomEntityModel previous = modelNamed(everyone.remove(entity.getUniqueId()));
		played.remove(entity.getUniqueId());
		entity.getPersistentDataContainer().remove(savedKey);

		if (previous == null) {
			return false;
		}

		new SkaffyEntityModelChangeEvent(entity, null, previous, null, false).callEvent();
		sendToViewers(entity);
		return true;
	}

	@Override
	public Optional<CustomEntityModel> get(Entity entity) {
		restore(entity);
		return Optional.ofNullable(modelNamed(everyone.get(entity.getUniqueId())));
	}

	@Override
	public void set(Player viewer, Entity entity, CustomEntityModel model, boolean save) {
		requireRegistered(model);
		restore(entity);
		CustomEntityModel previous = modelNamed(perViewer.computeIfAbsent(viewer.getUniqueId(), id -> new ConcurrentHashMap<>()).put(entity.getUniqueId(), model.getName()));
		Map<UUID, String> saved = savedViewers(entity);

		if (save) {
			saved.put(viewer.getUniqueId(), model.getName());
		} else {
			saved.remove(viewer.getUniqueId());
		}

		saveViewers(entity, saved);
		new SkaffyEntityModelChangeEvent(entity, viewer, previous, model, save).callEvent();
		send(viewer, entity);
	}

	@Override
	public boolean remove(Player viewer, Entity entity) {
		restore(entity);
		Map<UUID, String> own = perViewer.get(viewer.getUniqueId());
		CustomEntityModel previous = own == null ? null : modelNamed(own.remove(entity.getUniqueId()));
		Map<UUID, String> saved = savedViewers(entity);

		if (saved.remove(viewer.getUniqueId()) != null) {
			saveViewers(entity, saved);
		}

		if (previous == null) {
			return false;
		}

		new SkaffyEntityModelChangeEvent(entity, viewer, previous, null, false).callEvent();
		send(viewer, entity);
		return true;
	}

	@Override
	public Optional<CustomEntityModel> get(Player viewer, Entity entity) {
		restore(entity);
		return Optional.ofNullable(visibleTo(viewer.getUniqueId(), entity.getUniqueId()));
	}

	@Override
	public CustomEntityModel.Builder importModel(String name, java.nio.file.Path source, int maxTextureSize) throws java.io.IOException {
		if (importer == null) {
			throw new IllegalStateException("SkaffysAPI isn't enabled yet");
		}

		return importer.importModel(name, source, maxTextureSize);
	}

	@Override
	public void playAnimation(Entity entity, String animation, AnimationMode mode, float speed, float fadeIn) {
		checkSpeed(speed);
		checkFade(fadeIn);
		List<Played> remembered = played.computeIfAbsent(entity.getUniqueId(), id -> new ArrayList<>());

		synchronized (remembered) {
			remembered.removeIf(entry -> entry.animation.equals(animation));
			remembered.add(new Played(animation, mode, speed, System.nanoTime(), fadeIn));

			if (remembered.size() > REMEMBERED_ANIMATIONS) {
				remembered.removeFirst();
			}
		}

		CustomEntityModel model = everyoneModel(entity.getUniqueId());
		AnimationInfo.Clip clip = model == null ? null : clip(model, animation);

		if (clip != null) {
			UUID id = entity.getUniqueId();
			timers.start(id, animation, clip, mode, speed, 0, new AnimationTimers.Listener() {
				@Override
				public void marker(AnimationInfo.Marker marker) {
					Entity current = org.bukkit.Bukkit.getEntity(id);

					if (current != null) {
						new SkaffyAnimationMarkerEvent(null, current, animation, marker.text(), marker.time()).callEvent();
					}
				}

				@Override
				public void end() {
				}
			});
		}

		for (Player viewer : viewers(entity)) {
			sendAnimation(viewer, entity, animation, mode, speed, 0, fadeIn);
		}
	}

	@Override
	public void playAnimation(Player viewer, Entity entity, String animation, AnimationMode mode, float speed) {
		checkSpeed(speed);
		sendAnimation(viewer, entity, animation, mode, speed, 0, 0);
	}

	@Override
	public void setVariable(Entity entity, String name, float value) {
		Variable variable = new Variable(name, value);
		variables.computeIfAbsent(entity.getUniqueId(), id -> new ConcurrentHashMap<>()).put(name, value);

		for (Player viewer : viewers(entity)) {
			if (sessions.apply(viewer.getUniqueId()) != null) {
				sendPacket(viewer, EntityModelsCodec.encode(new SetVariables(entity.getEntityId(), List.of(variable))));
			}
		}
	}

	public AnimationInfo.Clip clip(CustomEntityModel model, String animation) {
		CustomEntityModel.ModelAnimation reference = model.getAnimation(animation).orElse(null);

		if (reference == null) {
			return null;
		}

		Map<String, AnimationInfo.Clip> clips = clipFiles.computeIfAbsent(reference.asset(), asset -> {
			byte[] data = assetReader.apply(asset);
			return data == null ? Map.of() : AnimationInfo.read(data);
		});
		return clips.get(reference.name());
	}

	public void forgetClips() {
		clipFiles.clear();
	}

	@Override
	public void stopAnimation(Entity entity, String animation, float fadeOut) {
		checkFade(fadeOut);
		timers.stop(entity.getUniqueId(), animation);
		List<Played> remembered = played.get(entity.getUniqueId());

		if (remembered != null) {
			synchronized (remembered) {
				remembered.removeIf(entry -> entry.animation.equals(animation));
			}
		}

		for (Player viewer : viewers(entity)) {
			ModelSession session = sessions.apply(viewer.getUniqueId());
			int number = animationNumber(visibleTo(viewer.getUniqueId(), entity.getUniqueId()), animation);

			if (session != null && number > 0) {
				sendPacket(viewer, EntityModelsCodec.encode(new StopAnimation(entity.getEntityId(), number, fadeOut)));
			}
		}
	}

	@Override
	public void stopAnimations(Entity entity) {
		played.remove(entity.getUniqueId());
		timers.stopAll(entity.getUniqueId());

		for (Player viewer : viewers(entity)) {
			if (sessions.apply(viewer.getUniqueId()) != null) {
				sendPacket(viewer, EntityModelsCodec.encode(new StopAnimation(entity.getEntityId(), 0, 0)));
			}
		}
	}

	public void setLookModels(BiFunction<UUID, UUID, String> lookModels) {
		this.lookModels = lookModels;
	}

	public void forgetAnimations(Entity entity) {
		played.remove(entity.getUniqueId());
	}

	public void register(ModelSession session) {
		List<CustomEntityModel> snapshot = List.copyOf(models.values());
		session.setModels(snapshot);
		List<ModelDefinition> part = new ArrayList<>();
		int partSize = 0;

		for (CustomEntityModel model : snapshot) {
			ModelDefinition definition = definition(model);
			PacketWriter measure = new PacketWriter();
			definition.write(measure);

			if (!part.isEmpty() && partSize + measure.size() > DEFINITIONS_BUDGET) {
				session.sendModelDefinition(EntityModelsCodec.encode(new DefineModels(part)));
				part = new ArrayList<>();
				partSize = 0;
			}

			part.add(definition);
			partSize += measure.size();
		}

		if (!part.isEmpty()) {
			session.sendModelDefinition(EntityModelsCodec.encode(new DefineModels(part)));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTrack(PlayerTrackEntityEvent event) {
		Player viewer = event.getPlayer();
		Entity entity = event.getEntity();

		if (sessions.apply(viewer.getUniqueId()) == null) {
			return;
		}

		restore(entity);
		CustomEntityModel model = visibleTo(viewer.getUniqueId(), entity.getUniqueId());

		if (model == null) {
			return;
		}

		if (!(entity instanceof Player)) {
			send(viewer, entity);
		}

		Map<String, Float> own = variables.get(entity.getUniqueId());

		if (own != null && !own.isEmpty()) {
			List<Variable> list = new ArrayList<>();
			own.forEach((name, value) -> list.add(new Variable(name, value)));
			sendPacket(viewer, EntityModelsCodec.encode(new SetVariables(entity.getEntityId(), list)));
		}

		List<Played> remembered = played.get(entity.getUniqueId());

		if (remembered == null || model != everyoneModel(entity.getUniqueId())) {
			return;
		}

		synchronized (remembered) {
			for (Played entry : remembered) {
				float seconds = (System.nanoTime() - entry.startNanos) / 1_000_000_000f;
				sendAnimation(viewer, entity, entry.animation, entry.mode, entry.speed, seconds * entry.speed, Math.max(0, entry.fadeIn - seconds));
			}
		}
	}

	@EventHandler
	public void onRemove(EntityRemoveEvent event) {
		if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD || event.getCause() == EntityRemoveEvent.Cause.PLAYER_QUIT) {
			return;
		}

		UUID id = event.getEntity().getUniqueId();
		everyone.remove(id);
		played.remove(id);
		variables.remove(id);
		timers.stopAll(id);
		restored.remove(id);
		perViewer.values().forEach(own -> own.remove(id));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		perViewer.remove(event.getPlayer().getUniqueId());
	}

	private void restore(Entity entity) {
		if (restored.putIfAbsent(entity.getUniqueId(), Boolean.TRUE) != null) {
			return;
		}

		String saved = entity.getPersistentDataContainer().get(savedKey, PersistentDataType.STRING);

		if (saved != null && models.containsKey(saved)) {
			everyone.putIfAbsent(entity.getUniqueId(), saved);
		}

		savedViewers(entity).forEach((viewer, name) -> {
			if (models.containsKey(name)) {
				perViewer.computeIfAbsent(viewer, id -> new ConcurrentHashMap<>()).putIfAbsent(entity.getUniqueId(), name);
			}
		});
	}

	private Map<UUID, String> savedViewers(Entity entity) {
		Map<UUID, String> viewers = new HashMap<>();
		String saved = entity.getPersistentDataContainer().get(savedViewersKey, PersistentDataType.STRING);

		if (saved == null || saved.isEmpty()) {
			return viewers;
		}

		for (String entry : saved.split(";")) {
			int separator = entry.indexOf('=');

			if (separator > 0) {
				try {
					viewers.put(UUID.fromString(entry.substring(0, separator)), entry.substring(separator + 1));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}

		return viewers;
	}

	private void saveViewers(Entity entity, Map<UUID, String> viewers) {
		if (viewers.isEmpty()) {
			entity.getPersistentDataContainer().remove(savedViewersKey);
			return;
		}

		StringBuilder encoded = new StringBuilder();
		viewers.forEach((viewer, name) -> encoded.append(encoded.isEmpty() ? "" : ";").append(viewer).append('=').append(name));
		entity.getPersistentDataContainer().set(savedViewersKey, PersistentDataType.STRING, encoded.toString());
	}

	private CustomEntityModel visibleTo(UUID viewer, UUID entity) {
		CustomEntityModel look = modelNamed(lookModels.apply(viewer, entity));

		if (look != null) {
			return look;
		}

		Map<UUID, String> own = perViewer.get(viewer);
		CustomEntityModel model = own == null ? null : modelNamed(own.get(entity));
		return model != null ? model : modelNamed(everyone.get(entity));
	}

	private CustomEntityModel everyoneModel(UUID entity) {
		CustomEntityModel look = modelNamed(lookModels.apply(null, entity));
		return look != null ? look : modelNamed(everyone.get(entity));
	}

	private static Collection<Player> viewers(Entity entity) {
		if (!(entity instanceof Player player)) {
			return entity.getTrackedBy();
		}

		Set<Player> viewers = new HashSet<>(entity.getTrackedBy());
		viewers.add(player);
		return viewers;
	}

	private CustomEntityModel modelNamed(String name) {
		return name == null ? null : models.get(name);
	}

	private void sendToViewers(Entity entity) {
		for (Player viewer : entity.getTrackedBy()) {
			send(viewer, entity);
		}
	}

	private void send(Player viewer, Entity entity) {
		ModelSession session = sessions.apply(viewer.getUniqueId());

		if (session == null) {
			return;
		}

		CustomEntityModel model = visibleTo(viewer.getUniqueId(), entity.getUniqueId());
		int number = model == null ? 0 : session.modelNumber(model);
		sendPacket(viewer, EntityModelsCodec.encode(new SetEntityModel(entity.getEntityId(), number)));
	}

	private void sendAnimation(Player viewer, Entity entity, String animation, AnimationMode mode, float speed, float start, float fadeIn) {
		if (sessions.apply(viewer.getUniqueId()) == null) {
			return;
		}

		int number = animationNumber(visibleTo(viewer.getUniqueId(), entity.getUniqueId()), animation);

		if (number > 0) {
			PlayAnimation.Mode wireMode = PlayAnimation.Mode.valueOf(mode.name());
			sendPacket(viewer, EntityModelsCodec.encode(new PlayAnimation(entity.getEntityId(), number, wireMode, speed, start, fadeIn)));
		}
	}

	private void sendPacket(Player viewer, byte[] data) {
		viewer.sendPluginMessage(plugin, EntityModelsCodec.CHANNEL, data);
	}

	private void requireRegistered(CustomEntityModel model) {
		if (!models.containsKey(model.getName())) {
			throw new IllegalArgumentException("Model " + model.getName() + " isn't registered");
		}
	}

	static int animationNumber(CustomEntityModel model, String animation) {
		if (model == null) {
			return 0;
		}

		List<CustomEntityModel.ModelAnimation> animations = model.getAnimations();

		for (int i = 0; i < animations.size(); i++) {
			if (animations.get(i).name().equals(animation)) {
				return i + 1;
			}
		}

		return 0;
	}

	private static void checkFade(float seconds) {
		if (!(seconds >= 0) || seconds > 600) {
			throw new IllegalArgumentException("Fades are 0 to 600 seconds, got " + seconds);
		}
	}

	private static void checkSpeed(float speed) {
		if (!(speed > 0) || !Float.isFinite(speed)) {
			throw new IllegalArgumentException("Animation speed must be above 0, got " + speed);
		}
	}

	private static ModelDefinition definition(CustomEntityModel model) {
		List<ModelDefinition.AnimationRef> animations = model.getAnimations().stream()
				.map(animation -> new ModelDefinition.AnimationRef(animation.asset(), animation.name(), animation.replacesVanilla()))
				.toList();
		return new ModelDefinition(
				model.getName(),
				model.getGeometry(),
				model.getBabyGeometry(),
				model.getTexture(),
				model.getEmissiveTexture(),
				model.isTranslucent(),
				model.getScale(),
				animations,
				animationNumber(model, model.getIdle()),
				animationNumber(model, model.getWalk()),
				animationNumber(model, model.getAttack()),
				animationNumber(model, model.getHurt()),
				animationNumber(model, model.getDeath()));
	}

	private record Played(String animation, AnimationMode mode, float speed, long startNanos, float fadeIn) {
	}
}
