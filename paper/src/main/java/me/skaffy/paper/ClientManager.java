package me.skaffy.paper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerCommonConnection;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import me.skaffy.api.block.CustomBlockType;
import me.skaffy.api.event.SkaffyClientRegisterEvent;
import me.skaffy.api.model.CustomEntityModel;
import me.skaffy.api.particle.CustomParticleType;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.asset.ServerAsset;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.blockshapes.BlockShapesCodec;
import me.skaffy.protocol.brightness.BrightnessCodec;
import me.skaffy.protocol.core.CoreCodec;
import me.skaffy.protocol.core.FeatureRange;
import me.skaffy.protocol.core.ServerboundCorePacket;
import me.skaffy.protocol.core.ServerboundCorePacket.Hello;
import me.skaffy.protocol.entitymodels.EntityModelsCodec;
import me.skaffy.protocol.fov.FovCodec;
import me.skaffy.protocol.gui.GuiCodec;
import me.skaffy.protocol.shaders.ShadersCodec;
import me.skaffy.protocol.shapes.ShapesCodec;
import me.skaffy.protocol.keybinds.KeybindsCodec;
import me.skaffy.protocol.nametags.NameTagsCodec;
import me.skaffy.protocol.particles.ParticlesCodec;
import me.skaffy.protocol.perspective.PerspectiveCodec;
import me.skaffy.protocol.playerlooks.PlayerLooksCodec;
import me.skaffy.protocol.session.ServerSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

final class ClientManager implements PluginMessageListener, Listener {
	private static final long TIMEOUT_MILLIS = 45_000;
	private static final long HELLO_GRACE_MILLIS = 5_000;

	private final SkaffyPlugin plugin;
	private final Map<UUID, PaperClient> clients = new ConcurrentHashMap<>();
	private final Set<String> warnedAssets = ConcurrentHashMap.newKeySet();

	ClientManager(SkaffyPlugin plugin) {
		this.plugin = plugin;
	}

	Optional<PaperClient> get(UUID playerId) {
		return Optional.ofNullable(clients.get(playerId));
	}

	Collection<PaperClient> all() {
		return List.copyOf(clients.values());
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
	}

	@Override
	public void onPluginMessageReceived(String channel, PlayerConnection connection, byte[] message) {
		if (!Protocol.CORE_CHANNEL.equals(channel)) {
			return;
		}

		UUID playerId;
		String playerName;

		switch (connection) {
			case PlayerConfigurationConnection configuration -> {
				playerId = configuration.getProfile().getId();
				playerName = configuration.getProfile().getName();
			}
			case PlayerGameConnection game -> {
				playerId = game.getPlayer().getUniqueId();
				playerName = game.getPlayer().getName();
			}
			default -> {
				return;
			}
		}

		if (playerId == null) {
			return;
		}

		ServerboundCorePacket packet;

		try {
			packet = CoreCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			get(playerId).ifPresent(client -> client.session().fail("Malformed packet from client: " + e.getMessage()));
			return;
		}

		if (packet instanceof Hello hello) {
			start(playerId, playerName, (PlayerCommonConnection) connection, hello);
			return;
		}

		get(playerId).ifPresent(client -> client.session().handle(packet));
	}

	private void start(UUID playerId, String playerName, PlayerCommonConnection connection, Hello hello) {
		PaperClient client = new PaperClient(plugin, playerId, playerName, connection, hello);
		PaperClient previous = clients.put(playerId, client);

		if (previous != null) {
			previous.session().fail("Client started a new session");
		}

		if (connection instanceof PlayerGameConnection game) {
			client.attach(game.getPlayer());
		}

		client.start(List.of(
				new FeatureRange(BlocksCodec.FEATURE, BlocksCodec.VERSION, BlocksCodec.VERSION),
				new FeatureRange(ParticlesCodec.FEATURE, ParticlesCodec.VERSION, ParticlesCodec.VERSION),
				new FeatureRange(KeybindsCodec.FEATURE, KeybindsCodec.VERSION, KeybindsCodec.VERSION),
				new FeatureRange(EntityModelsCodec.FEATURE, EntityModelsCodec.VERSION, EntityModelsCodec.VERSION),
				new FeatureRange(FovCodec.FEATURE, FovCodec.VERSION, FovCodec.VERSION),
				new FeatureRange(NameTagsCodec.FEATURE, NameTagsCodec.VERSION, NameTagsCodec.VERSION),
				new FeatureRange(GuiCodec.FEATURE, GuiCodec.VERSION, GuiCodec.VERSION),
				new FeatureRange(PerspectiveCodec.FEATURE, PerspectiveCodec.VERSION, PerspectiveCodec.VERSION),
				new FeatureRange(BrightnessCodec.FEATURE, BrightnessCodec.VERSION, BrightnessCodec.VERSION),
				new FeatureRange(PlayerLooksCodec.FEATURE, PlayerLooksCodec.VERSION, PlayerLooksCodec.VERSION),
				new FeatureRange(ShadersCodec.FEATURE, ShadersCodec.VERSION, ShadersCodec.VERSION),
				new FeatureRange(BlockShapesCodec.FEATURE, BlockShapesCodec.VERSION, BlockShapesCodec.VERSION),
				new FeatureRange(ShapesCodec.FEATURE, ShapesCodec.VERSION, ShapesCodec.VERSION),
				new FeatureRange(me.skaffy.protocol.animations.AnimationsCodec.FEATURE, me.skaffy.protocol.animations.AnimationsCodec.VERSION,
						me.skaffy.protocol.animations.AnimationsCodec.VERSION)));

		if (client.session().state() != ServerSession.State.REGISTERING) {
			return;
		}

		plugin.async(() -> register(client, connection));
	}

	private void register(PaperClient client, PlayerConnection connection) {
		ServerSession session = client.session();
		SkaffyClientRegisterEvent event = new SkaffyClientRegisterEvent(client, connection, () -> cachedAssetIds(session));
		event.callEvent();

		Map<String, ServerAsset> assets = plugin.assets().snapshot();

		event.getAssets().forEach((id, data) -> {
			String problem = PaperAssetRegistry.validate(id, data);

			if (problem != null) {
				plugin.getLogger().warning("Ignoring asset added in SkaffyClientRegisterEvent: " + problem);
			} else {
				assets.put(id, ServerAsset.of(data));
			}
		});

		try {
			if (client.hasFeatureEnabled(BlocksCodec.FEATURE)) {
				plugin.blocks().register(client);
				warnAboutMissingAssets(assets);
			}

			if (client.hasFeatureEnabled(ParticlesCodec.FEATURE)) {
				plugin.particles().register(client);
				warnAboutMissingTextures(assets);
			}

			if (client.hasFeatureEnabled(KeybindsCodec.FEATURE)) {
				plugin.keybinds().register(client, event.getKeys());
			}

			if (client.hasFeatureEnabled(EntityModelsCodec.FEATURE)) {
				plugin.entityModels().register(client);
				warnAboutMissingModelAssets(assets);
			}

			if (client.hasFeatureEnabled(NameTagsCodec.FEATURE)) {
				plugin.nameTags().register(client);
				warnAboutMissingSprites(assets);
			}

			if (client.hasFeatureEnabled(GuiCodec.FEATURE)) {
				plugin.guis().register(client);
				warnAboutMissingFonts(assets);
			}

			if (client.hasFeatureEnabled(ShadersCodec.FEATURE)) {
				plugin.shaders().register(client);
			}

			if (!event.getDeletedAssets().isEmpty()) {
				session.delete(event.getDeletedAssets());
			}

			session.syncAssets(assets).thenRun(session::endRegistration);
		} catch (IllegalStateException e) {
		}
	}

	private void warnAboutMissingAssets(Map<String, ServerAsset> assets) {
		for (CustomBlockType type : plugin.blocks().getTypes()) {
			for (String asset : new String[] {type.getModel(), type.getCollision(), type.getHitbox()}) {
				if (asset != null && !assets.containsKey(asset) && warnedAssets.add(asset)) {
					plugin.getLogger().warning("Block " + type.getName() + " uses asset " + asset + ", which isn't registered");
				}
			}
		}
	}

	private void warnAboutMissingTextures(Map<String, ServerAsset> assets) {
		for (CustomParticleType type : plugin.particles().getTypes()) {
			for (String texture : type.getTextures()) {
				if (texture.contains(".") && Protocol.isValidAssetId(texture) && !assets.containsKey(texture) && warnedAssets.add(texture)) {
					plugin.getLogger().warning("Particle " + type.getName() + " uses asset " + texture + ", which isn't registered");
				}
			}
		}
	}

	private void warnAboutMissingModelAssets(Map<String, ServerAsset> assets) {
		for (CustomEntityModel model : plugin.entityModels().getModels()) {
			List<String> used = new ArrayList<>(Arrays.asList(model.getGeometry(), model.getBabyGeometry(), model.getTexture(), model.getEmissiveTexture()));
			model.getAnimations().forEach(animation -> used.add(animation.asset()));

			for (String asset : used) {
				if (asset != null && !assets.containsKey(asset) && warnedAssets.add(asset)) {
					plugin.getLogger().warning("Entity model " + model.getName() + " uses asset " + asset + ", which isn't registered");
				}
			}
		}
	}

	private void warnAboutMissingSprites(Map<String, ServerAsset> assets) {
		for (String sprite : plugin.nameTags().getSprites()) {
			if (!assets.containsKey(sprite) && warnedAssets.add(sprite)) {
				plugin.getLogger().warning("Name tag sprite " + sprite + " isn't a registered asset");
			}
		}
	}

	private void warnAboutMissingFonts(Map<String, ServerAsset> assets) {
		plugin.guis().getFonts().forEach(font -> {
			if (!assets.containsKey(font.asset()) && warnedAssets.add(font.asset())) {
				plugin.getLogger().warning("GUI font " + font.name() + " uses asset " + font.asset() + ", which isn't registered");
			}
		});
	}

	private Set<String> cachedAssetIds(ServerSession session) {
		try {
			return session.query(List.of(), false).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).keySet();
		} catch (IllegalStateException | ExecutionException | TimeoutException e) {
			return Set.of();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Set.of();
		}
	}

	void checkTimeouts() {
		for (PaperClient client : clients.values()) {
			ServerSession session = client.session();

			if (session.isWaitingForClient() && session.idleMillis() > TIMEOUT_MILLIS) {
				session.fail("No response for " + TIMEOUT_MILLIS / 1000 + " seconds");
			}
		}
	}

	@EventHandler
	public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
		PlayerConfigurationConnection connection = event.getConnection();
		PaperClient client = awaitHello(connection);

		if (client == null) {
			return;
		}

		while (!client.done().isDone()) {
			try {
				client.done().get(1, TimeUnit.SECONDS);
			} catch (TimeoutException | ExecutionException ignored) {
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			if (!connection.isConnected()) {
				client.session().fail("Disconnected");
			}
		}
	}

	private PaperClient awaitHello(PlayerConfigurationConnection connection) {
		UUID playerId = connection.getProfile().getId();
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(HELLO_GRACE_MILLIS);

		while (true) {
			PaperClient client = playerId == null ? null : clients.get(playerId);

			if (client != null && client.isFrom(connection)) {
				return client;
			}

			if (!connection.getListeningPluginChannels().contains(Protocol.CORE_CHANNEL) || !connection.isConnected() || System.nanoTime() > deadline) {
				return null;
			}

			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		get(event.getPlayer().getUniqueId()).ifPresent(client -> client.attach(event.getPlayer()));
	}

	@EventHandler
	public void onConnectionClose(PlayerConnectionCloseEvent event) {
		PaperClient client = clients.remove(event.getPlayerUniqueId());

		if (client != null) {
			client.session().fail("Disconnected");
		}
	}
}
