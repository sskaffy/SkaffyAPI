package me.skaffy.paper;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.SkaffyClient;
import me.skaffy.paper.animation.PaperAnimations;
import me.skaffy.paper.block.BarrierMovement;
import me.skaffy.paper.block.BlockPacketListener;
import me.skaffy.paper.block.BlockSession;
import me.skaffy.paper.block.PaperCustomBlocks;
import me.skaffy.paper.blockshape.PaperBlockShapes;
import me.skaffy.paper.brightness.PaperBrightness;
import me.skaffy.paper.fov.PaperFov;
import me.skaffy.paper.gui.GuiSession;
import me.skaffy.paper.gui.PaperGuis;
import me.skaffy.paper.keybind.KeybindPacketListener;
import me.skaffy.paper.keybind.KeybindSession;
import me.skaffy.paper.keybind.PaperCustomKeybinds;
import me.skaffy.paper.look.LookSession;
import me.skaffy.paper.look.PaperPlayerLooks;
import me.skaffy.paper.model.ModelSession;
import me.skaffy.paper.model.PaperCustomEntityModels;
import me.skaffy.paper.nametag.NameTagSession;
import me.skaffy.paper.nametag.PaperNameTags;
import me.skaffy.paper.particle.PaperCustomParticles;
import me.skaffy.paper.particle.ParticleSession;
import me.skaffy.paper.perspective.PaperPerspective;
import me.skaffy.paper.shader.PaperShaders;
import me.skaffy.paper.shader.ShaderSession;
import me.skaffy.paper.shape.PaperShapes;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.animations.AnimationsCodec;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.blockshapes.BlockShapesCodec;
import me.skaffy.protocol.brightness.BrightnessCodec;
import me.skaffy.protocol.entitymodels.EntityModelsCodec;
import me.skaffy.protocol.fov.FovCodec;
import me.skaffy.protocol.gui.GuiCodec;
import me.skaffy.protocol.keybinds.KeybindsCodec;
import me.skaffy.protocol.nametags.NameTagsCodec;
import me.skaffy.protocol.particles.ParticlesCodec;
import me.skaffy.protocol.perspective.PerspectiveCodec;
import me.skaffy.protocol.playerlooks.PlayerLooksCodec;
import me.skaffy.protocol.shaders.ShadersCodec;
import me.skaffy.protocol.shapes.ShapesCodec;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkaffyPlugin extends JavaPlugin {
	private final PaperAssetRegistry assets = new PaperAssetRegistry();
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private ClientManager clients;
	private PaperCustomBlocks blocks;
	private PaperCustomParticles particles;
	private PaperCustomKeybinds keybinds;
	private PaperCustomEntityModels entityModels;
	private PaperFov fov;
	private PaperNameTags nameTags;
	private PaperGuis guis;
	private PaperPerspective perspective;
	private PaperBrightness brightness;
	private PaperPlayerLooks playerLooks;
	private PaperShaders shaders;
	private PaperBlockShapes blockShapes;
	private PaperShapes shapes;
	private PaperAnimations animations;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		clients = new ClientManager(this);
		blocks = new PaperCustomBlocks(this, this::blockSession);
		particles = new PaperCustomParticles(this, this::particleSession);
		keybinds = new PaperCustomKeybinds(this, this::keybindSession);
		entityModels = new PaperCustomEntityModels(this, this::modelSession);
		fov = new PaperFov(this, playerId -> hasReadyFeature(playerId, FovCodec.FEATURE));
		nameTags = new PaperNameTags(this, this::nameTagSession);
		guis = new PaperGuis(this, this::guiSession);
		perspective = new PaperPerspective(this, playerId -> hasReadyFeature(playerId, PerspectiveCodec.FEATURE));
		brightness = new PaperBrightness(this, playerId -> hasReadyFeature(playerId, BrightnessCodec.FEATURE));
		playerLooks = new PaperPlayerLooks(this, this::lookSession, entityModels);
		entityModels.setLookModels(playerLooks::lookModel);
		shaders = new PaperShaders(this, this::shaderSession);
		blockShapes = new PaperBlockShapes(this, playerId -> hasReadyFeature(playerId, BlockShapesCodec.FEATURE));
		shapes = new PaperShapes(this, playerId -> hasReadyFeature(playerId, ShapesCodec.FEATURE));
		entityModels.setAssets(assets::read, new me.skaffy.paper.model.ModelImporter(getDataFolder().toPath().resolve("imported"), assets, getLogger()));
		animations = new PaperAnimations(this, this::modelSession, playerId -> hasReadyFeature(playerId, AnimationsCodec.FEATURE) && modelSession(playerId) != null, entityModels);

		BarrierMovement.Mode barrierMovement;

		try {
			barrierMovement = BarrierMovement.Mode.parse(getConfig().getString("barrier-movement", "everyone"));
		} catch (IllegalArgumentException e) {
			getLogger().warning("Unknown barrier-movement '" + getConfig().getString("barrier-movement") + "', using everyone");
			barrierMovement = BarrierMovement.Mode.EVERYONE;
		}

		getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CORE_CHANNEL, clients);
		getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CORE_CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, BlocksCodec.CHANNEL, new BlockPacketListener(this, blocks, this::blockSession));
		getServer().getMessenger().registerOutgoingPluginChannel(this, BlocksCodec.CHANNEL);
		getServer().getMessenger().registerOutgoingPluginChannel(this, ParticlesCodec.CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, KeybindsCodec.CHANNEL, new KeybindPacketListener(this::keybindSession));
		getServer().getMessenger().registerOutgoingPluginChannel(this, KeybindsCodec.CHANNEL);
		getServer().getMessenger().registerOutgoingPluginChannel(this, EntityModelsCodec.CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, FovCodec.CHANNEL, fov);
		getServer().getMessenger().registerOutgoingPluginChannel(this, FovCodec.CHANNEL);
		getServer().getMessenger().registerOutgoingPluginChannel(this, NameTagsCodec.CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, GuiCodec.CHANNEL, guis);
		getServer().getMessenger().registerOutgoingPluginChannel(this, GuiCodec.CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, PerspectiveCodec.CHANNEL, perspective);
		getServer().getMessenger().registerOutgoingPluginChannel(this, PerspectiveCodec.CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, BrightnessCodec.CHANNEL, brightness);
		getServer().getMessenger().registerOutgoingPluginChannel(this, BrightnessCodec.CHANNEL);
		getServer().getMessenger().registerOutgoingPluginChannel(this, PlayerLooksCodec.CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, ShadersCodec.CHANNEL, shaders);
		getServer().getMessenger().registerOutgoingPluginChannel(this, ShadersCodec.CHANNEL);
		getServer().getMessenger().registerOutgoingPluginChannel(this, BlockShapesCodec.CHANNEL);
		getServer().getMessenger().registerOutgoingPluginChannel(this, ShapesCodec.CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, AnimationsCodec.CHANNEL, animations);
		getServer().getMessenger().registerOutgoingPluginChannel(this, AnimationsCodec.CHANNEL);
		getServer().getPluginManager().registerEvents(clients, this);
		getServer().getPluginManager().registerEvents(playerLooks, this);
		getServer().getPluginManager().registerEvents(entityModels, this);
		getServer().getPluginManager().registerEvents(nameTags, this);
		getServer().getPluginManager().registerEvents(blocks, this);
		getServer().getPluginManager().registerEvents(guis, this);
		getServer().getPluginManager().registerEvents(shaders, this);
		getServer().getPluginManager().registerEvents(blockShapes, this);
		getServer().getPluginManager().registerEvents(shapes, this);
		getServer().getPluginManager().registerEvents(animations, this);
		getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> animations.tick(), 1, 1);
		getServer().getPluginManager().registerEvents(new BarrierMovement(barrierMovement, blocks, blockShapes, this::blockSession), this);
		getServer().getServicesManager().register(SkaffyAPI.class, new PaperSkaffyAPI(clients, assets, blocks, particles, keybinds, entityModels, fov, nameTags, guis, perspective, brightness, playerLooks, shaders, blockShapes, shapes,
				animations), this, ServicePriority.Normal);
		getCommand("skaffy-api").setExecutor(new SkaffyCommand(clients));
		getServer().getAsyncScheduler().runAtFixedRate(this, task -> clients.checkTimeouts(), 1, 1, TimeUnit.SECONDS);
	}

	@Override
	public void onDisable() {
		getServer().getAsyncScheduler().cancelTasks(this);
		getServer().getMessenger().unregisterIncomingPluginChannel(this);
		getServer().getMessenger().unregisterOutgoingPluginChannel(this);
		getServer().getServicesManager().unregisterAll(this);
		executor.shutdownNow();
	}

	PaperAssetRegistry assets() {
		return assets;
	}

	PaperCustomBlocks blocks() {
		return blocks;
	}

	PaperCustomParticles particles() {
		return particles;
	}

	PaperCustomKeybinds keybinds() {
		return keybinds;
	}

	PaperCustomEntityModels entityModels() {
		return entityModels;
	}

	PaperNameTags nameTags() {
		return nameTags;
	}

	PaperGuis guis() {
		return guis;
	}

	PaperShaders shaders() {
		return shaders;
	}

	private BlockSession blockSession(UUID playerId) {
		return clients.get(playerId)
				.filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(BlocksCodec.FEATURE))
				.orElse(null);
	}

	private ParticleSession particleSession(UUID playerId) {
		return clients.get(playerId)
				.filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(ParticlesCodec.FEATURE))
				.orElse(null);
	}

	private KeybindSession keybindSession(UUID playerId) {
		return clients.get(playerId)
				.filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(KeybindsCodec.FEATURE))
				.orElse(null);
	}

	private boolean hasReadyFeature(UUID playerId, String feature) {
		return clients.get(playerId).filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(feature)).isPresent();
	}

	private ModelSession modelSession(UUID playerId) {
		return clients.get(playerId)
				.filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(EntityModelsCodec.FEATURE))
				.orElse(null);
	}

	private NameTagSession nameTagSession(UUID playerId) {
		return clients.get(playerId)
				.filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(NameTagsCodec.FEATURE))
				.orElse(null);
	}

	private GuiSession guiSession(UUID playerId) {
		return clients.get(playerId)
				.filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(GuiCodec.FEATURE))
				.orElse(null);
	}

	private ShaderSession shaderSession(UUID playerId) {
		return clients.get(playerId)
				.filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(ShadersCodec.FEATURE))
				.orElse(null);
	}

	private LookSession lookSession(UUID playerId) {
		return clients.get(playerId)
				.filter(client -> client.getState() == SkaffyClient.State.READY && client.hasFeatureEnabled(PlayerLooksCodec.FEATURE))
				.orElse(null);
	}

	void async(Runnable task) {
		try {
			executor.execute(() -> {
				try {
					task.run();
				} catch (RuntimeException e) {
					getLogger().log(Level.SEVERE, "Error in Skaffy's API task", e);
				}
			});
		} catch (RejectedExecutionException ignored) {
		}
	}
}
