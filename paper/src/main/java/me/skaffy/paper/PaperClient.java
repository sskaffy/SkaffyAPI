package me.skaffy.paper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.connection.PlayerCommonConnection;
import io.papermc.paper.connection.PlayerConnection;
import me.skaffy.api.SkaffyClient;
import me.skaffy.api.block.CustomBlockType;
import me.skaffy.api.event.SkaffyClientFailedEvent;
import me.skaffy.api.event.SkaffyClientReadyEvent;
import me.skaffy.api.keybind.CustomKeybind;
import me.skaffy.api.model.CustomEntityModel;
import me.skaffy.api.particle.CustomParticleType;
import me.skaffy.paper.block.BlockSession;
import me.skaffy.paper.gui.GuiSession;
import me.skaffy.paper.shader.ShaderSession;
import me.skaffy.paper.keybind.KeybindSession;
import me.skaffy.paper.look.LookSession;
import me.skaffy.paper.model.ModelSession;
import me.skaffy.paper.nametag.NameTagSession;
import me.skaffy.paper.particle.ParticleSession;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.core.FeatureRange;
import me.skaffy.protocol.core.FeatureVersion;
import me.skaffy.protocol.core.ServerboundCorePacket.Hello;
import me.skaffy.protocol.entitymodels.EntityModelsCodec;
import me.skaffy.protocol.gui.GuiCodec;
import me.skaffy.protocol.shaders.ShadersCodec;
import me.skaffy.protocol.keybinds.KeybindsCodec;
import me.skaffy.protocol.nametags.NameTagsCodec;
import me.skaffy.protocol.particles.ParticlesCodec;
import me.skaffy.protocol.session.ServerSession;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageRecipient;

final class PaperClient implements SkaffyClient, ServerSession.Listener, BlockSession, ParticleSession, KeybindSession, ModelSession, NameTagSession, GuiSession, LookSession, ShaderSession {
	private final SkaffyPlugin plugin;
	private final UUID playerId;
	private final String playerName;
	private final Hello hello;
	private final PlayerConnection origin;
	private final CompletableFuture<Void> done = new CompletableFuture<>();

	private volatile PlayerConnection connection;
	private volatile PluginMessageRecipient target;
	private volatile ServerSession session;
	private volatile List<CustomBlockType> blockTypes = List.of();
	private volatile Map<String, Integer> blockNumbers = Map.of();
	private volatile Map<String, Integer> particleNumbers = Map.of();
	private volatile List<CustomKeybind> keybinds = List.of();
	private volatile Map<String, Integer> keybindNumbers = Map.of();
	private final Map<String, String> keybindKeys = new ConcurrentHashMap<>();
	private final Map<String, boolean[]> keybindStates = new ConcurrentHashMap<>();
	private volatile List<CustomEntityModel> models = List.of();
	private volatile Map<String, Integer> modelNumbers = Map.of();
	private volatile Map<String, Integer> spriteNumbers = Map.of();

	PaperClient(SkaffyPlugin plugin, UUID playerId, String playerName, PlayerCommonConnection connection, Hello hello) {
		this.plugin = plugin;
		this.playerId = playerId;
		this.playerName = playerName;
		this.hello = hello;
		this.origin = connection;
		this.connection = connection;
		this.target = connection;
	}

	void start(List<FeatureRange> serverFeatures) {
		session = ServerSession.start(hello, serverFeatures, (channel, data) -> target.sendPluginMessage(plugin, channel, data), this);
	}

	void attach(Player player) {
		this.connection = player.getConnection();
		this.target = player;
	}

	boolean isFrom(PlayerConnection connection) {
		return origin == connection;
	}

	ServerSession session() {
		return session;
	}

	CompletableFuture<Void> done() {
		return done;
	}

	boolean hasFeatureEnabled(String featureId) {
		ServerSession session = this.session;
		return session != null && session.features().stream().anyMatch(feature -> feature.id().equals(featureId));
	}

	@Override
	public void setBlockTypes(List<CustomBlockType> types) {
		Map<String, Integer> numbers = new HashMap<>();

		for (int i = 0; i < types.size(); i++) {
			numbers.put(types.get(i).getName(), i + 1);
		}

		this.blockTypes = List.copyOf(types);
		this.blockNumbers = numbers;
	}

	@Override
	public int blockNumber(CustomBlockType type) {
		return blockNumbers.getOrDefault(type.getName(), 0);
	}

	@Override
	public CustomBlockType blockType(int number) {
		List<CustomBlockType> types = blockTypes;
		return number >= 1 && number <= types.size() ? types.get(number - 1) : null;
	}

	@Override
	public void sendDefinition(byte[] data) {
		session.sendDefinition(BlocksCodec.CHANNEL, data);
	}

	@Override
	public void setParticleTypes(List<CustomParticleType> types) {
		Map<String, Integer> numbers = new HashMap<>();

		for (int i = 0; i < types.size(); i++) {
			numbers.put(types.get(i).getName(), i + 1);
		}

		this.particleNumbers = numbers;
	}

	@Override
	public int particleNumber(CustomParticleType type) {
		return particleNumbers.getOrDefault(type.getName(), 0);
	}

	@Override
	public void sendParticleDefinition(byte[] data) {
		session.sendDefinition(ParticlesCodec.CHANNEL, data);
	}

	@Override
	public void setKeybinds(List<CustomKeybind> keybinds) {
		Map<String, Integer> numbers = new HashMap<>();

		for (int i = 0; i < keybinds.size(); i++) {
			numbers.put(keybinds.get(i).getId(), i + 1);
		}

		this.keybinds = List.copyOf(keybinds);
		this.keybindNumbers = numbers;
	}

	@Override
	public int keybindNumber(CustomKeybind keybind) {
		return keybindNumbers.getOrDefault(keybind.getId(), 0);
	}

	@Override
	public CustomKeybind keybind(int number) {
		List<CustomKeybind> list = keybinds;
		return number >= 1 && number <= list.size() ? list.get(number - 1) : null;
	}

	@Override
	public String currentKey(CustomKeybind keybind) {
		return keybindKeys.get(keybind.getId());
	}

	@Override
	public void setCurrentKey(CustomKeybind keybind, String key) {
		keybindKeys.put(keybind.getId(), key);
	}

	@Override
	public boolean[] keybindState(CustomKeybind keybind) {
		boolean[] state = keybindStates.get(keybind.getId());
		return state != null ? state.clone() : new boolean[] {keybind.isActiveAtStart(), keybind.winsOverVanillaAtStart()};
	}

	@Override
	public void setKeybindState(CustomKeybind keybind, boolean active, boolean wins) {
		keybindStates.put(keybind.getId(), new boolean[] {active, wins});
	}

	@Override
	public void sendKeybindDefinition(byte[] data) {
		session.sendDefinition(KeybindsCodec.CHANNEL, data);
	}

	@Override
	public void setModels(List<CustomEntityModel> models) {
		Map<String, Integer> numbers = new HashMap<>();

		for (int i = 0; i < models.size(); i++) {
			numbers.put(models.get(i).getName(), i + 1);
		}

		this.models = List.copyOf(models);
		this.modelNumbers = numbers;
	}

	@Override
	public int modelNumber(CustomEntityModel model) {
		return modelNumbers.getOrDefault(model.getName(), 0);
	}

	@Override
	public CustomEntityModel model(int number) {
		List<CustomEntityModel> list = models;
		return number >= 1 && number <= list.size() ? list.get(number - 1) : null;
	}

	@Override
	public int lookModelNumber(String model) {
		return modelNumbers.getOrDefault(model, 0);
	}

	@Override
	public void sendModelDefinition(byte[] data) {
		session.sendDefinition(EntityModelsCodec.CHANNEL, data);
	}

	@Override
	public void setSprites(List<String> assets) {
		Map<String, Integer> numbers = new HashMap<>();

		for (int i = 0; i < assets.size(); i++) {
			numbers.put(assets.get(i), i + 1);
		}

		this.spriteNumbers = numbers;
	}

	@Override
	public int spriteNumber(String asset) {
		return spriteNumbers.getOrDefault(asset, 0);
	}

	@Override
	public void sendNameTagDefinition(byte[] data) {
		session.sendDefinition(NameTagsCodec.CHANNEL, data);
	}

	@Override
	public void sendGuiDefinition(byte[] data) {
		session.sendDefinition(GuiCodec.CHANNEL, data);
	}

	@Override
	public void sendShaderDefinition(byte[] data) {
		session.sendDefinition(ShadersCodec.CHANNEL, data);
	}

	@Override
	public void onReady(ServerSession session) {
		done.complete(null);
		plugin.async(() -> new SkaffyClientReadyEvent(this, connection).callEvent());
	}

	@Override
	public void onFailed(ServerSession session, String reason) {
		done.complete(null);
		plugin.getLogger().info("Skaffy's API session of " + playerName + " failed: " + reason);
		plugin.async(() -> new SkaffyClientFailedEvent(this, connection, reason).callEvent());
	}

	@Override
	public UUID getPlayerId() {
		return playerId;
	}

	@Override
	public String getPlayerName() {
		return playerName;
	}

	@Override
	public String getModVersion() {
		return hello.modVersion();
	}

	@Override
	public Map<String, Integer> getFeatures() {
		ServerSession session = this.session;

		if (session == null) {
			return Map.of();
		}

		Map<String, Integer> features = new LinkedHashMap<>();

		for (FeatureVersion feature : session.features()) {
			features.put(feature.id(), feature.version());
		}

		return features;
	}

	@Override
	public State getState() {
		ServerSession session = this.session;

		if (session == null) {
			return State.REGISTERING;
		}

		return switch (session.state()) {
			case REGISTERING -> State.REGISTERING;
			case LOADING -> State.LOADING;
			case READY -> State.READY;
			case FAILED -> State.FAILED;
		};
	}

	@Override
	public Optional<String> getFailureReason() {
		ServerSession session = this.session;
		return session == null ? Optional.empty() : Optional.ofNullable(session.failureReason());
	}
}
