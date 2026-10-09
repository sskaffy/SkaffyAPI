package me.skaffy.paper.nametag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import me.skaffy.api.event.nametag.SkaffyNameTagChangeEvent;
import me.skaffy.api.nametag.NameTag;
import me.skaffy.api.nametag.NameTagLine;
import me.skaffy.api.nametag.NameTags;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.nametags.NameTagsCodec;
import me.skaffy.protocol.nametags.NameTagsPacket.DefineSprites;
import me.skaffy.protocol.nametags.NameTagsPacket.RemoveNameTag;
import me.skaffy.protocol.nametags.NameTagsPacket.SetLine;
import me.skaffy.protocol.nametags.NameTagsPacket.SetNameTag;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class PaperNameTags implements NameTags, Listener {
	private final Plugin plugin;
	private final Function<UUID, NameTagSession> sessions;
	private final NamespacedKey savedKey;
	private final NamespacedKey savedViewersKey;
	private final List<String> sprites = new ArrayList<>();
	private final Map<UUID, NameTag> everyone = new ConcurrentHashMap<>();
	private final Map<UUID, Map<UUID, NameTag>> perViewer = new ConcurrentHashMap<>();
	private final Map<UUID, Boolean> restored = new ConcurrentHashMap<>();
	private final Set<String> warnedSprites = ConcurrentHashMap.newKeySet();

	public PaperNameTags(Plugin plugin, Function<UUID, NameTagSession> sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
		this.savedKey = new NamespacedKey(plugin, "name_tag");
		this.savedViewersKey = new NamespacedKey(plugin, "name_tag_viewers");
	}

	@Override
	public void registerSprite(String asset) {
		if (!Protocol.isValidAssetId(asset)) {
			throw new IllegalArgumentException("Invalid asset id " + asset);
		}

		synchronized (sprites) {
			if (sprites.contains(asset)) {
				return;
			}

			if (sprites.size() >= MAX_SPRITES) {
				throw new IllegalStateException("A server has at most " + MAX_SPRITES + " name tag sprites");
			}

			sprites.add(asset);
		}
	}

	@Override
	public boolean unregisterSprite(String asset) {
		synchronized (sprites) {
			return sprites.remove(asset);
		}
	}

	@Override
	public Collection<String> getSprites() {
		synchronized (sprites) {
			return List.copyOf(sprites);
		}
	}

	@Override
	public void set(Entity entity, NameTag tag, boolean save) {
		validate(tag);
		restore(entity);
		NameTag previous = everyone.put(entity.getUniqueId(), tag);
		PersistentDataContainer data = entity.getPersistentDataContainer();

		if (save) {
			data.set(savedKey, PersistentDataType.BYTE_ARRAY, NameTagConversion.save(tag));
		} else {
			data.remove(savedKey);
		}

		new SkaffyNameTagChangeEvent(entity, null, previous, tag, save).callEvent();
		sendToViewers(entity);
	}

	@Override
	public boolean remove(Entity entity) {
		restore(entity);
		NameTag previous = everyone.remove(entity.getUniqueId());
		entity.getPersistentDataContainer().remove(savedKey);

		if (previous == null) {
			return false;
		}

		new SkaffyNameTagChangeEvent(entity, null, previous, null, false).callEvent();
		sendToViewers(entity);
		return true;
	}

	@Override
	public Optional<NameTag> get(Entity entity) {
		restore(entity);
		return Optional.ofNullable(everyone.get(entity.getUniqueId()));
	}

	@Override
	public void set(Player viewer, Entity entity, NameTag tag, boolean save) {
		validate(tag);
		restore(entity);
		NameTag previous = perViewer.computeIfAbsent(viewer.getUniqueId(), id -> new ConcurrentHashMap<>()).put(entity.getUniqueId(), tag);
		PersistentDataContainer saved = savedViewers(entity);
		NamespacedKey viewerKey = viewerKey(viewer.getUniqueId());

		if (save) {
			saved.set(viewerKey, PersistentDataType.BYTE_ARRAY, NameTagConversion.save(tag));
		} else {
			saved.remove(viewerKey);
		}

		saveViewers(entity, saved);
		new SkaffyNameTagChangeEvent(entity, viewer, previous, tag, save).callEvent();
		send(viewer, entity);
	}

	@Override
	public boolean remove(Player viewer, Entity entity) {
		restore(entity);
		Map<UUID, NameTag> own = perViewer.get(viewer.getUniqueId());
		NameTag previous = own == null ? null : own.remove(entity.getUniqueId());
		PersistentDataContainer saved = savedViewers(entity);
		NamespacedKey viewerKey = viewerKey(viewer.getUniqueId());

		if (saved.has(viewerKey)) {
			saved.remove(viewerKey);
			saveViewers(entity, saved);
		}

		if (previous == null) {
			return false;
		}

		new SkaffyNameTagChangeEvent(entity, viewer, previous, null, false).callEvent();
		send(viewer, entity);
		return true;
	}

	@Override
	public Optional<NameTag> get(Player viewer, Entity entity) {
		restore(entity);
		return Optional.ofNullable(visibleTo(viewer.getUniqueId(), entity.getUniqueId()));
	}

	@Override
	public void setLine(Entity entity, int line, NameTagLine content) {
		restore(entity);
		NameTag previous = everyone.get(entity.getUniqueId());

		if (previous == null) {
			throw new IllegalStateException("The entity has no name tag");
		}

		NameTag tag = previous.withLine(line, content);
		validate(tag);
		everyone.put(entity.getUniqueId(), tag);
		PersistentDataContainer data = entity.getPersistentDataContainer();
		boolean saved = data.has(savedKey);

		if (saved) {
			data.set(savedKey, PersistentDataType.BYTE_ARRAY, NameTagConversion.save(tag));
		}

		new SkaffyNameTagChangeEvent(entity, null, previous, tag, saved).callEvent();

		for (Player viewer : viewers(entity)) {
			if (visibleTo(viewer.getUniqueId(), entity.getUniqueId()) == tag) {
				sendLine(viewer, entity, line, content);
			}
		}
	}

	@Override
	public void setLine(Player viewer, Entity entity, int line, NameTagLine content) {
		restore(entity);
		Map<UUID, NameTag> own = perViewer.get(viewer.getUniqueId());
		NameTag previous = own == null ? null : own.get(entity.getUniqueId());

		if (previous == null) {
			throw new IllegalStateException("The player has no name tag of their own for the entity");
		}

		NameTag tag = previous.withLine(line, content);
		validate(tag);
		own.put(entity.getUniqueId(), tag);
		PersistentDataContainer saved = savedViewers(entity);
		NamespacedKey viewerKey = viewerKey(viewer.getUniqueId());
		boolean isSaved = saved.has(viewerKey);

		if (isSaved) {
			saved.set(viewerKey, PersistentDataType.BYTE_ARRAY, NameTagConversion.save(tag));
			saveViewers(entity, saved);
		}

		new SkaffyNameTagChangeEvent(entity, viewer, previous, tag, isSaved).callEvent();
		sendLine(viewer, entity, line, content);
	}

	public void register(NameTagSession session) {
		List<String> snapshot = List.copyOf(getSprites());
		session.setSprites(snapshot);

		if (!snapshot.isEmpty()) {
			session.sendNameTagDefinition(NameTagsCodec.encode(new DefineSprites(snapshot)));
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

		if (visibleTo(viewer.getUniqueId(), entity.getUniqueId()) != null) {
			send(viewer, entity);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		sendOwnLater(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent event) {
		sendOwnLater(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldChange(PlayerChangedWorldEvent event) {
		sendOwnLater(event.getPlayer());
	}

	@EventHandler
	public void onRemove(EntityRemoveEvent event) {
		if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD || event.getCause() == EntityRemoveEvent.Cause.PLAYER_QUIT) {
			return;
		}

		UUID id = event.getEntity().getUniqueId();
		everyone.remove(id);
		restored.remove(id);
		perViewer.values().forEach(own -> own.remove(id));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		perViewer.remove(event.getPlayer().getUniqueId());
	}

	private void sendOwnLater(Player player) {
		player.getScheduler().runDelayed(plugin, task -> {
			restore(player);

			if (visibleTo(player.getUniqueId(), player.getUniqueId()) != null) {
				send(player, player);
			}
		}, null, 1);
	}

	private void restore(Entity entity) {
		if (restored.putIfAbsent(entity.getUniqueId(), Boolean.TRUE) != null) {
			return;
		}

		PersistentDataContainer data = entity.getPersistentDataContainer();
		byte[] saved = data.get(savedKey, PersistentDataType.BYTE_ARRAY);
		NameTag tag = saved == null ? null : NameTagConversion.load(saved);

		if (tag != null) {
			everyone.putIfAbsent(entity.getUniqueId(), tag);
		}

		PersistentDataContainer viewers = data.get(savedViewersKey, PersistentDataType.TAG_CONTAINER);

		if (viewers == null) {
			return;
		}

		for (NamespacedKey key : viewers.getKeys()) {
			byte[] bytes = viewers.get(key, PersistentDataType.BYTE_ARRAY);
			NameTag own = bytes == null ? null : NameTagConversion.load(bytes);

			try {
				if (own != null) {
					perViewer.computeIfAbsent(UUID.fromString(key.getKey()), id -> new ConcurrentHashMap<>()).putIfAbsent(entity.getUniqueId(), own);
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	private PersistentDataContainer savedViewers(Entity entity) {
		PersistentDataContainer data = entity.getPersistentDataContainer();
		PersistentDataContainer saved = data.get(savedViewersKey, PersistentDataType.TAG_CONTAINER);
		return saved != null ? saved : data.getAdapterContext().newPersistentDataContainer();
	}

	private void saveViewers(Entity entity, PersistentDataContainer saved) {
		if (saved.isEmpty()) {
			entity.getPersistentDataContainer().remove(savedViewersKey);
		} else {
			entity.getPersistentDataContainer().set(savedViewersKey, PersistentDataType.TAG_CONTAINER, saved);
		}
	}

	private NamespacedKey viewerKey(UUID viewer) {
		return new NamespacedKey(plugin, viewer.toString());
	}

	private NameTag visibleTo(UUID viewer, UUID entity) {
		Map<UUID, NameTag> own = perViewer.get(viewer);
		NameTag tag = own == null ? null : own.get(entity);
		return tag != null ? tag : everyone.get(entity);
	}

	private static Collection<Player> viewers(Entity entity) {
		Set<Player> viewers = new HashSet<>(entity.getTrackedBy());

		if (entity instanceof Player player) {
			viewers.add(player);
		}

		return viewers;
	}

	private void sendToViewers(Entity entity) {
		for (Player viewer : viewers(entity)) {
			send(viewer, entity);
		}
	}

	private void send(Player viewer, Entity entity) {
		NameTagSession session = sessions.apply(viewer.getUniqueId());

		if (session == null) {
			return;
		}

		NameTag tag = visibleTo(viewer.getUniqueId(), entity.getUniqueId());
		byte[] data = tag == null
				? NameTagsCodec.encode(new RemoveNameTag(entity.getEntityId()))
				: NameTagsCodec.encode(new SetNameTag(entity.getEntityId(), NameTagConversion.toProtocol(tag, asset -> spriteNumber(session, asset))));
		viewer.sendPluginMessage(plugin, NameTagsCodec.CHANNEL, data);
	}

	private void sendLine(Player viewer, Entity entity, int line, NameTagLine content) {
		NameTagSession session = sessions.apply(viewer.getUniqueId());

		if (session != null) {
			SetLine packet = new SetLine(entity.getEntityId(), line, NameTagConversion.toProtocol(content, asset -> spriteNumber(session, asset)));
			viewer.sendPluginMessage(plugin, NameTagsCodec.CHANNEL, NameTagsCodec.encode(packet));
		}
	}

	private int spriteNumber(NameTagSession session, String asset) {
		int number = session.spriteNumber(asset);

		if (number == 0 && warnedSprites.add(asset)) {
			plugin.getLogger().warning("Name tag sprite " + asset + " isn't registered (or was registered after some players joined); they see the missing texture");
		}

		return number;
	}

	private static void validate(NameTag tag) {
		try {
			NameTagsCodec.encode(new SetNameTag(0, NameTagConversion.toProtocol(tag, asset -> {
				Protocol.requireAssetId(asset);
				return 1;
			})));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException("Name tag can't be sent: " + e.getMessage(), e);
		}
	}
}
