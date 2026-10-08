package me.skaffy.paper.look;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import me.skaffy.api.event.look.SkaffyPlayerLookChangeEvent;
import me.skaffy.api.look.PlayerLook;
import me.skaffy.api.look.PlayerLooks;
import me.skaffy.paper.model.PaperCustomEntityModels;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.playerlooks.PlayerLooksCodec;
import me.skaffy.protocol.playerlooks.PlayerLooksPacket.RemoveLook;
import me.skaffy.protocol.playerlooks.PlayerLooksPacket.SetLook;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class PaperPlayerLooks implements PlayerLooks, Listener {
	private final Plugin plugin;
	private final Function<UUID, LookSession> sessions;
	private final PaperCustomEntityModels models;
	private final NamespacedKey savedKey;
	private final NamespacedKey savedViewersKey;
	private final Map<UUID, PlayerLook> everyone = new ConcurrentHashMap<>();
	private final Map<UUID, Map<UUID, PlayerLook>> perViewer = new ConcurrentHashMap<>();
	private final Map<UUID, Boolean> restored = new ConcurrentHashMap<>();
	private final Set<String> warnedModels = ConcurrentHashMap.newKeySet();

	public PaperPlayerLooks(Plugin plugin, Function<UUID, LookSession> sessions, PaperCustomEntityModels models) {
		this.plugin = plugin;
		this.sessions = sessions;
		this.models = models;
		this.savedKey = new NamespacedKey(plugin, "player_look");
		this.savedViewersKey = new NamespacedKey(plugin, "player_look_viewers");
	}

	@Override
	public void set(Player player, PlayerLook look, boolean save) {
		validate(look);
		restore(player);
		PlayerLook previous = everyone.put(player.getUniqueId(), look);
		PersistentDataContainer data = player.getPersistentDataContainer();

		if (save) {
			data.set(savedKey, PersistentDataType.BYTE_ARRAY, LookConversion.save(look));
		} else {
			data.remove(savedKey);
		}

		modelChanged(player, previous, look);
		new SkaffyPlayerLookChangeEvent(player, null, previous, look, save).callEvent();
		sendToViewers(player);
	}

	@Override
	public boolean remove(Player player) {
		restore(player);
		PlayerLook previous = everyone.remove(player.getUniqueId());
		player.getPersistentDataContainer().remove(savedKey);

		if (previous == null) {
			return false;
		}

		modelChanged(player, previous, null);
		new SkaffyPlayerLookChangeEvent(player, null, previous, null, false).callEvent();
		sendToViewers(player);
		return true;
	}

	@Override
	public Optional<PlayerLook> get(Player player) {
		restore(player);
		return Optional.ofNullable(everyone.get(player.getUniqueId()));
	}

	@Override
	public void set(Player viewer, Player player, PlayerLook look, boolean save) {
		validate(look);
		restore(player);
		PlayerLook previous = perViewer.computeIfAbsent(viewer.getUniqueId(), id -> new ConcurrentHashMap<>()).put(player.getUniqueId(), look);
		PersistentDataContainer saved = savedViewers(player);
		NamespacedKey viewerKey = viewerKey(viewer.getUniqueId());

		if (save) {
			saved.set(viewerKey, PersistentDataType.BYTE_ARRAY, LookConversion.save(look));
		} else {
			saved.remove(viewerKey);
		}

		saveViewers(player, saved);
		new SkaffyPlayerLookChangeEvent(player, viewer, previous, look, save).callEvent();
		send(viewer, player);
	}

	@Override
	public boolean remove(Player viewer, Player player) {
		restore(player);
		Map<UUID, PlayerLook> own = perViewer.get(viewer.getUniqueId());
		PlayerLook previous = own == null ? null : own.remove(player.getUniqueId());
		PersistentDataContainer saved = savedViewers(player);
		NamespacedKey viewerKey = viewerKey(viewer.getUniqueId());

		if (saved.has(viewerKey)) {
			saved.remove(viewerKey);
			saveViewers(player, saved);
		}

		if (previous == null) {
			return false;
		}

		new SkaffyPlayerLookChangeEvent(player, viewer, previous, null, false).callEvent();
		send(viewer, player);
		return true;
	}

	@Override
	public Optional<PlayerLook> get(Player viewer, Player player) {
		restore(player);
		return Optional.ofNullable(visibleTo(viewer.getUniqueId(), player.getUniqueId()));
	}

	public String lookModel(UUID viewer, UUID player) {
		PlayerLook look = viewer == null ? everyone.get(player) : visibleTo(viewer, player);
		return look != null && look.getKind() == PlayerLook.Kind.MODEL ? look.getModel() : null;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTrack(PlayerTrackEntityEvent event) {
		if (!(event.getEntity() instanceof Player player) || sessions.apply(event.getPlayer().getUniqueId()) == null) {
			return;
		}

		restore(player);

		if (visibleTo(event.getPlayer().getUniqueId(), player.getUniqueId()) != null) {
			send(event.getPlayer(), player);
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

	private void modelChanged(Player player, PlayerLook previous, PlayerLook look) {
		String before = previous != null && previous.getKind() == PlayerLook.Kind.MODEL ? previous.getModel() : null;
		String after = look != null && look.getKind() == PlayerLook.Kind.MODEL ? look.getModel() : null;

		if (!Objects.equals(before, after)) {
			models.forgetAnimations(player);
		}
	}

	private void restore(Player player) {
		if (restored.putIfAbsent(player.getUniqueId(), Boolean.TRUE) != null) {
			return;
		}

		PersistentDataContainer data = player.getPersistentDataContainer();
		byte[] saved = data.get(savedKey, PersistentDataType.BYTE_ARRAY);
		PlayerLook look = saved == null ? null : LookConversion.load(saved);

		if (look != null) {
			everyone.putIfAbsent(player.getUniqueId(), look);
		}

		PersistentDataContainer viewers = data.get(savedViewersKey, PersistentDataType.TAG_CONTAINER);

		if (viewers == null) {
			return;
		}

		for (NamespacedKey key : viewers.getKeys()) {
			byte[] bytes = viewers.get(key, PersistentDataType.BYTE_ARRAY);
			PlayerLook own = bytes == null ? null : LookConversion.load(bytes);

			try {
				if (own != null) {
					perViewer.computeIfAbsent(UUID.fromString(key.getKey()), id -> new ConcurrentHashMap<>()).putIfAbsent(player.getUniqueId(), own);
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	private PersistentDataContainer savedViewers(Player player) {
		PersistentDataContainer data = player.getPersistentDataContainer();
		PersistentDataContainer saved = data.get(savedViewersKey, PersistentDataType.TAG_CONTAINER);
		return saved != null ? saved : data.getAdapterContext().newPersistentDataContainer();
	}

	private void saveViewers(Player player, PersistentDataContainer saved) {
		if (saved.isEmpty()) {
			player.getPersistentDataContainer().remove(savedViewersKey);
		} else {
			player.getPersistentDataContainer().set(savedViewersKey, PersistentDataType.TAG_CONTAINER, saved);
		}
	}

	private NamespacedKey viewerKey(UUID viewer) {
		return new NamespacedKey(plugin, viewer.toString());
	}

	private PlayerLook visibleTo(UUID viewer, UUID player) {
		Map<UUID, PlayerLook> own = perViewer.get(viewer);
		PlayerLook look = own == null ? null : own.get(player);
		return look != null ? look : everyone.get(player);
	}

	private static Set<Player> viewers(Player player) {
		Set<Player> viewers = new HashSet<>(player.getTrackedBy());
		viewers.add(player);
		return viewers;
	}

	private void sendToViewers(Player player) {
		for (Player viewer : viewers(player)) {
			send(viewer, player);
		}
	}

	private void send(Player viewer, Entity player) {
		LookSession session = sessions.apply(viewer.getUniqueId());

		if (session == null) {
			return;
		}

		PlayerLook look = visibleTo(viewer.getUniqueId(), player.getUniqueId());
		byte[] data;

		if (look == null) {
			data = PlayerLooksCodec.encode(new RemoveLook(player.getEntityId()));
		} else {
			int model = 0;

			if (look.getKind() == PlayerLook.Kind.MODEL) {
				model = session.lookModelNumber(look.getModel());

				if (model == 0 && warnedModels.add(look.getModel())) {
					plugin.getLogger().warning("Player look model " + look.getModel() + " isn't known to some players (registered after they joined, or they don't support entity models); they see the vanilla player");
				}
			}

			data = model == 0 && look.getKind() == PlayerLook.Kind.MODEL
					? PlayerLooksCodec.encode(new RemoveLook(player.getEntityId()))
					: PlayerLooksCodec.encode(new SetLook(player.getEntityId(), LookConversion.toProtocol(look, model)));
		}

		viewer.sendPluginMessage(plugin, PlayerLooksCodec.CHANNEL, data);
	}

	private void validate(PlayerLook look) {
		if (look.getKind() == PlayerLook.Kind.MODEL && models.getModel(look.getModel()).isEmpty()) {
			throw new IllegalArgumentException("Model " + look.getModel() + " isn't registered");
		}

		try {
			PlayerLooksCodec.encode(new SetLook(0, LookConversion.toProtocol(look, 1)));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException("Look can't be sent: " + e.getMessage(), e);
		}
	}
}
