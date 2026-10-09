package me.skaffy.paper.animation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import me.skaffy.api.animation.AnimationEntity;
import me.skaffy.api.animation.PlayOptions;
import me.skaffy.api.animation.SkaffyAnimations;
import me.skaffy.api.event.SkaffyClientReadyEvent;
import me.skaffy.api.event.animation.SkaffyAnimationClickEvent;
import me.skaffy.api.event.animation.SkaffyAnimationEndEvent;
import me.skaffy.api.event.animation.SkaffyAnimationMarkerEvent;
import me.skaffy.api.model.CustomEntityModel;
import me.skaffy.paper.model.AnimationInfo;
import me.skaffy.paper.model.AnimationTimers;
import me.skaffy.paper.model.ModelSession;
import me.skaffy.paper.model.PaperCustomEntityModels;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.animations.AnimationsCodec;
import me.skaffy.protocol.animations.AnimationsPacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;

public final class PaperAnimations implements SkaffyAnimations, Listener, PluginMessageListener {
	private static final String EVERYONE = "w:";
	private static final String OWN = "p:";
	private static final int TRACK_EVERY_TICKS = 10;

	private final Plugin plugin;
	private final Function<UUID, ModelSession> modelSessions;
	private final Predicate<UUID> ready;
	private final PaperCustomEntityModels models;
	private final AnimationTimers timers;
	private final Map<String, PaperAnimationEntity> everyone = new ConcurrentHashMap<>();
	private final Map<UUID, Map<String, PaperAnimationEntity>> own = new ConcurrentHashMap<>();
	private final Map<String, Boolean> warned = new ConcurrentHashMap<>();
	private int ticks;

	public PaperAnimations(Plugin plugin, Function<UUID, ModelSession> modelSessions, Predicate<UUID> ready, PaperCustomEntityModels models) {
		this.plugin = plugin;
		this.modelSessions = modelSessions;
		this.ready = ready;
		this.models = models;
		this.timers = models.timers();
	}

	public void tick() {
		timers.tick();

		if (++ticks % TRACK_EVERY_TICKS == 0) {
			track();
		}
	}


	@Override
	public AnimationEntity spawn(String id, CustomEntityModel model, Location location) {
		checkId(id);
		requireRegistered(model);
		PaperAnimationEntity entity = new PaperAnimationEntity(this, id, EVERYONE + id, model, null, location);
		PaperAnimationEntity previous = everyone.put(id, entity);

		if (previous != null) {
			drop(previous);
		}

		return entity;
	}

	@Override
	public AnimationEntity spawn(Player viewer, String id, CustomEntityModel model, Location location) {
		checkId(id);
		requireRegistered(model);
		PaperAnimationEntity entity = new PaperAnimationEntity(this, id, OWN + id, model, viewer.getUniqueId(), location);
		PaperAnimationEntity previous = own.computeIfAbsent(viewer.getUniqueId(), player -> new ConcurrentHashMap<>()).put(id, entity);

		if (previous != null) {
			drop(previous);
		}

		sync(entity, viewer);
		return entity;
	}

	@Override
	public Optional<AnimationEntity> get(String id) {
		return Optional.ofNullable(everyone.get(id));
	}

	@Override
	public Optional<AnimationEntity> get(Player viewer, String id) {
		Map<String, PaperAnimationEntity> entities = own.get(viewer.getUniqueId());
		return Optional.ofNullable(entities == null ? null : entities.get(id));
	}

	@Override
	public Collection<AnimationEntity> getAll() {
		return List.copyOf(everyone.values());
	}

	@Override
	public Collection<AnimationEntity> getAll(Player viewer) {
		Map<String, PaperAnimationEntity> entities = own.get(viewer.getUniqueId());
		return entities == null ? List.of() : List.copyOf(entities.values());
	}

	@Override
	public void removeAll(String prefix) {
		for (PaperAnimationEntity entity : List.copyOf(everyone.values())) {
			if (entity.id.startsWith(prefix)) {
				remove(entity);
			}
		}
	}

	@Override
	public void removeAll(Player viewer, String prefix) {
		Map<String, PaperAnimationEntity> entities = own.get(viewer.getUniqueId());

		if (entities != null) {
			for (PaperAnimationEntity entity : List.copyOf(entities.values())) {
				if (entity.id.startsWith(prefix)) {
					remove(entity);
				}
			}
		}
	}

	@Override
	public boolean isSupported(Player player) {
		return ready.test(player.getUniqueId());
	}


	void remove(PaperAnimationEntity entity) {
		if (entity.viewer == null) {
			everyone.remove(entity.id, entity);
		} else {
			Map<String, PaperAnimationEntity> entities = own.get(entity.viewer);

			if (entities != null) {
				entities.remove(entity.id, entity);
			}
		}

		drop(entity);
	}

	private void drop(PaperAnimationEntity entity) {
		entity.removed = true;
		timers.stopAll(entity);
		sendRemove(entity);
	}

	void sendRemove(PaperAnimationEntity entity) {
		byte[] data = AnimationsCodec.encode(new AnimationsPacket.Remove(entity.wireId));

		for (UUID id : List.copyOf(entity.seenBy)) {
			Player player = Bukkit.getPlayer(id);

			if (player != null) {
				player.sendPluginMessage(plugin, AnimationsCodec.CHANNEL, data);
			}
		}

		entity.seenBy.clear();
	}

	void send(PaperAnimationEntity entity, AnimationsPacket packet) {
		byte[] data;

		try {
			data = AnimationsCodec.encode(packet);
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		for (UUID id : entity.seenBy) {
			Player player = Bukkit.getPlayer(id);

			if (player != null) {
				player.sendPluginMessage(plugin, AnimationsCodec.CHANNEL, data);
			}
		}
	}

	private void sync(PaperAnimationEntity entity, Player player) {
		if (!ready.test(player.getUniqueId())) {
			return;
		}

		ModelSession session = modelSessions.apply(player.getUniqueId());
		int number = session == null ? 0 : session.modelNumber(entity.model);

		if (number == 0) {
			if (warned.putIfAbsent(player.getUniqueId() + entity.model.getName(), Boolean.TRUE) == null) {
				plugin.getLogger().warning(player.getName() + " joined before model " + entity.model.getName() + " was registered and can't see animation entity " + entity.id);
			}

			return;
		}

		ServerMotion.Pose pose = entity.motion.at(System.nanoTime());
		player.sendPluginMessage(plugin, AnimationsCodec.CHANNEL, AnimationsCodec.encode(new AnimationsPacket.Spawn(entity.wireId, number, entity.placement(pose), entity.settings)));

		for (AnimationsPacket packet : entity.state()) {
			player.sendPluginMessage(plugin, AnimationsCodec.CHANNEL, AnimationsCodec.encode(packet));
		}

		entity.seenBy.add(player.getUniqueId());
	}

	private void track() {
		if (everyone.isEmpty()) {
			return;
		}

		List<Player> players = new ArrayList<>();

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (ready.test(player.getUniqueId())) {
				players.add(player);
			}
		}

		for (PaperAnimationEntity entity : everyone.values()) {
			Location location;

			try {
				location = entity.getLocation();
			} catch (RuntimeException e) {
				continue;
			}

			World world = location.getWorld();
			double range = entity.trackingRange > 0 ? entity.trackingRange : (world == null ? Bukkit.getViewDistance() : world.getViewDistance()) * 16.0;

			for (Player player : players) {
				Location at = player.getLocation();
				boolean inRange = world != null && at.getWorld() == world && at.distanceSquared(location) <= range * range;
				boolean seeing = entity.seenBy.contains(player.getUniqueId());

				if (inRange && !seeing) {
					sync(entity, player);
				} else if (!inRange && seeing) {
					entity.seenBy.remove(player.getUniqueId());
					player.sendPluginMessage(plugin, AnimationsCodec.CHANNEL, AnimationsCodec.encode(new AnimationsPacket.Remove(entity.wireId)));
				}
			}

			entity.seenBy.removeIf(id -> Bukkit.getPlayer(id) == null);
		}
	}


	void startTimer(PaperAnimationEntity entity, String animation, PlayOptions options) {
		AnimationInfo.Clip clip = models.clip(entity.model, animation);

		if (clip == null) {
			return;
		}

		timers.start(entity, animation, clip, options.mode(), options.speed(), options.startAt(), new AnimationTimers.Listener() {
			@Override
			public void marker(AnimationInfo.Marker marker) {
				if (!entity.removed) {
					new SkaffyAnimationMarkerEvent(entity, null, animation, marker.text(), marker.time()).callEvent();
				}
			}

			@Override
			public void end() {
				if (entity.removed) {
					return;
				}

				entity.played.remove(animation);
				new SkaffyAnimationEndEvent(entity, animation, options.removeWhenDone()).callEvent();

				if (options.removeWhenDone()) {
					remove(entity);
				}
			}
		});
	}

	void stopTimer(PaperAnimationEntity entity, String animation) {
		timers.stop(entity, animation);
	}

	void stopTimers(PaperAnimationEntity entity) {
		timers.stopAll(entity);
	}


	@EventHandler
	public void onReady(SkaffyClientReadyEvent event) {
		Player player = Bukkit.getPlayer(event.getClient().getPlayerId());
		Map<String, PaperAnimationEntity> entities = player == null ? null : own.get(player.getUniqueId());

		if (entities != null) {
			player.getScheduler().runDelayed(plugin, task -> entities.values().forEach(entity -> sync(entity, player)), null, 1);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		Map<String, PaperAnimationEntity> entities = own.remove(id);

		if (entities != null) {
			entities.values().forEach(entity -> {
				entity.removed = true;
				timers.stopAll(entity);
			});
		}

		everyone.values().forEach(entity -> entity.seenBy.remove(id));
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!channel.equals(AnimationsCodec.CHANNEL)) {
			return;
		}

		AnimationsPacket packet;

		try {
			packet = AnimationsCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			return;
		}

		if (!(packet instanceof AnimationsPacket.Click click)) {
			return;
		}

		PaperAnimationEntity entity = null;

		if (click.id().startsWith(EVERYONE)) {
			entity = everyone.get(click.id().substring(EVERYONE.length()));
		} else if (click.id().startsWith(OWN)) {
			Map<String, PaperAnimationEntity> entities = own.get(player.getUniqueId());
			entity = entities == null ? null : entities.get(click.id().substring(OWN.length()));
		}

		if (entity == null || entity.removed) {
			return;
		}

		PaperAnimationEntity target = entity;
		SkaffyAnimationClickEvent.Click type = click.button() == AnimationsPacket.Button.ATTACK ? SkaffyAnimationClickEvent.Click.ATTACK : SkaffyAnimationClickEvent.Click.USE;
		EquipmentSlot hand = click.offHand() ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
		player.getScheduler().run(plugin, task -> new SkaffyAnimationClickEvent(player, target, type, hand, new Vector(click.x(), click.y(), click.z()), click.sneaking()).callEvent(),
				null);
	}


	public static String wireId(AnimationEntity entity, Player player) {
		if (!(entity instanceof PaperAnimationEntity paper)) {
			throw new IllegalArgumentException("Not an animation entity of this server");
		}

		return paper.wireId;
	}

	static int animationNumber(CustomEntityModel model, String animation) {
		List<CustomEntityModel.ModelAnimation> animations = model.getAnimations();

		for (int i = 0; i < animations.size(); i++) {
			if (animations.get(i).name().equals(animation)) {
				return i + 1;
			}
		}

		return 0;
	}

	private static void checkId(String id) {
		if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) {
			throw new IllegalArgumentException("Animation entity ids are 1 to " + MAX_ID_LENGTH + " characters: " + id);
		}
	}

	private void requireRegistered(CustomEntityModel model) {
		if (models.getModel(model.getName()).isEmpty()) {
			throw new IllegalArgumentException("Model " + model.getName() + " isn't registered");
		}
	}
}
