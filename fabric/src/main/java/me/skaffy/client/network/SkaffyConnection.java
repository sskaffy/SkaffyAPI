package me.skaffy.client.network;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import me.skaffy.client.ClientContent;
import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.asset.ServerCacheKey;
import me.skaffy.client.block.ClientBlocks;
import me.skaffy.client.blockshape.ClientBlockShapes;
import me.skaffy.client.brightness.ClientBrightness;
import me.skaffy.client.fov.ClientFov;
import me.skaffy.client.gui.ClientGuis;
import me.skaffy.client.keybind.ClientKeybinds;
import me.skaffy.client.look.ClientPlayerLooks;
import me.skaffy.client.mixin.ConnectScreenInvoker;
import me.skaffy.client.model.ClientEntityModels;
import me.skaffy.client.nametag.ClientNameTags;
import me.skaffy.client.particle.ClientParticles;
import me.skaffy.client.perspective.ClientPerspective;
import me.skaffy.client.shader.ClientShaders;
import me.skaffy.client.shape.ClientShapes;
import me.skaffy.client.animation.ClientAnimations;
import me.skaffy.protocol.animations.AnimationsCodec;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.blockshapes.BlockShapesCodec;
import me.skaffy.protocol.brightness.BrightnessCodec;
import me.skaffy.protocol.core.FeatureRange;
import me.skaffy.protocol.core.FeatureVersion;
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
import me.skaffy.protocol.session.ClientSession;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class SkaffyConnection {
	private static final List<FeatureRange> FEATURES = List.of(
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
			new FeatureRange(AnimationsCodec.FEATURE, AnimationsCodec.VERSION, AnimationsCodec.VERSION));
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Skaffy's API");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile ClientSession session;
	private static volatile boolean answered;
	private static volatile boolean helloInPlay;
	private static volatile String shownStatus;
	private static final Set<String> LOGGED_DROPS = ConcurrentHashMap.newKeySet();

	private SkaffyConnection() {
	}

	public static void startConfiguration(Minecraft client) {
		helloInPlay = false;
		start(client, ClientConfigurationNetworking.getSender());
	}

	public static void startPlay(Minecraft client, PacketSender sender) {
		if (helloInPlay) {
			helloInPlay = false;
			start(client, sender);
		}
	}

	private static void start(Minecraft client, PacketSender sender) {
		end("A new session started");
		answered = false;

		try {
			sender.sendPacket(new RegistrationPayload(RegistrationPayload.REGISTER, Stream.concat(Stream.of(SkaffyPayload.CORE), SkaffyPayload.FEATURES.stream()).map(CustomPacketPayload.Type::id).toList()));
		} catch (RuntimeException | LinkageError e) {
			SkaffySAPIClient.LOGGER.warn("Could not announce the Skaffy's API channels, some servers won't answer", e);
		}

		Path cache = client.gameDirectory.toPath().resolve("skaffy-api").resolve(ServerCacheKey.of(client.getCurrentServer()));
		DiskAssetStore store = new DiskAssetStore(cache);
		Listener listener = new Listener(store);
		ClientSession started = new ClientSession(FEATURES, store, (channel, data) -> sender.sendPacket(new SkaffyPayload(SkaffyPayload.CORE, data)), listener);
		listener.session = started;
		session = started;
		started.start(SkaffySAPIClient.version());
	}

	public static void receive(byte[] data) {
		ClientSession current = session;

		if (current != null) {
			answered = true;
			EXECUTOR.execute(() -> current.handle(data));
		}
	}

	public static void receiveFeature(String channel, byte[] data) {
		ClientSession current = session;

		if (current == null) {
			return;
		}

		EXECUTOR.execute(() -> {
			if (current != session) {
				return;
			}

			boolean registering = current.state() == ClientSession.State.REGISTERING;

			if (channel.equals(BlocksCodec.CHANNEL) && current.featureVersion(BlocksCodec.FEATURE) != 0) {
				ClientBlocks.receive(data, registering);
			} else if (channel.equals(ParticlesCodec.CHANNEL) && current.featureVersion(ParticlesCodec.FEATURE) != 0) {
				ClientParticles.receive(data, registering);
			} else if (channel.equals(KeybindsCodec.CHANNEL) && current.featureVersion(KeybindsCodec.FEATURE) != 0) {
				ClientKeybinds.receive(data, registering);
			} else if (channel.equals(EntityModelsCodec.CHANNEL) && current.featureVersion(EntityModelsCodec.FEATURE) != 0) {
				ClientEntityModels.receive(data, registering);
			} else if (channel.equals(FovCodec.CHANNEL) && current.featureVersion(FovCodec.FEATURE) != 0) {
				ClientFov.receive(data);
			} else if (channel.equals(NameTagsCodec.CHANNEL) && current.featureVersion(NameTagsCodec.FEATURE) != 0) {
				ClientNameTags.receive(data, registering);
			} else if (channel.equals(GuiCodec.CHANNEL) && current.featureVersion(GuiCodec.FEATURE) != 0) {
				ClientGuis.receive(data, registering);
			} else if (channel.equals(PerspectiveCodec.CHANNEL) && current.featureVersion(PerspectiveCodec.FEATURE) != 0) {
				ClientPerspective.receive(data);
			} else if (channel.equals(BrightnessCodec.CHANNEL) && current.featureVersion(BrightnessCodec.FEATURE) != 0) {
				ClientBrightness.receive(data);
			} else if (channel.equals(PlayerLooksCodec.CHANNEL) && current.featureVersion(PlayerLooksCodec.FEATURE) != 0) {
				ClientPlayerLooks.receive(data);
			} else if (channel.equals(ShadersCodec.CHANNEL) && current.featureVersion(ShadersCodec.FEATURE) != 0) {
				ClientShaders.receive(data, registering);
			} else if (channel.equals(BlockShapesCodec.CHANNEL) && current.featureVersion(BlockShapesCodec.FEATURE) != 0) {
				ClientBlockShapes.receive(data);
			} else if (channel.equals(ShapesCodec.CHANNEL) && current.featureVersion(ShapesCodec.FEATURE) != 0) {
				ClientShapes.receive(data);
			} else if (channel.equals(AnimationsCodec.CHANNEL) && current.featureVersion(AnimationsCodec.FEATURE) != 0) {
				ClientAnimations.receive(data);
			} else if (LOGGED_DROPS.add(channel)) {
				SkaffySAPIClient.LOGGER.warn("Dropping packets on {}: that feature isn't enabled", channel);
			}
		});
	}

	public static void sendFeature(String channel, byte[] data) {
		CustomPacketPayload.Type<SkaffyPayload> type = channel.equals(BlocksCodec.CHANNEL) ? SkaffyPayload.BLOCKS
				: channel.equals(KeybindsCodec.CHANNEL) ? SkaffyPayload.KEYBINDS
				: channel.equals(FovCodec.CHANNEL) ? SkaffyPayload.FOV
				: channel.equals(GuiCodec.CHANNEL) ? SkaffyPayload.GUI
				: channel.equals(PerspectiveCodec.CHANNEL) ? SkaffyPayload.PERSPECTIVE
				: channel.equals(BrightnessCodec.CHANNEL) ? SkaffyPayload.BRIGHTNESS
				: channel.equals(ShadersCodec.CHANNEL) ? SkaffyPayload.SHADERS
				: channel.equals(AnimationsCodec.CHANNEL) ? SkaffyPayload.ANIMATIONS
				: null;

		if (type == null || session == null || session.state() != ClientSession.State.READY) {
			return;
		}

		try {
			ClientPlayNetworking.send(new SkaffyPayload(type, data));
		} catch (IllegalStateException e) {
		}
	}

	public static boolean supports(String feature) {
		ClientSession current = session;
		return current != null && current.state() == ClientSession.State.READY && current.featureVersion(feature) != 0;
	}

	public static void completeConfiguration() {
		ClientSession current = session;

		if (current == null) {
			return;
		}

		if (!answered) {
			helloInPlay = true;
			EXECUTOR.execute(() -> current.abandon("Server didn't answer during configuration"));
			return;
		}

		EXECUTOR.execute(() -> {
			if (current.state() != ClientSession.State.READY && current.abandon("Configuration ended before everything was loaded")) {
				SkaffySAPIClient.LOGGER.warn("Server ended configuration before Skaffy's API content was loaded");
				ClientContent.unload();
			}
		});
	}

	public static void disconnect() {
		helloInPlay = false;
		end("Disconnected");
	}

	private static void end(String reason) {
		ClientSession previous = session;
		session = null;
		LOGGED_DROPS.clear();

		if (previous != null) {
			EXECUTOR.execute(() -> previous.abandon(reason));
			ClientContent.unload();
		}
	}

	private static void showStatus(Component status, String key) {
		if (key.equals(shownStatus)) {
			return;
		}

		shownStatus = key;
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.gui.screen() instanceof ConnectScreen screen) {
				((ConnectScreenInvoker) screen).skaffy$updateStatus(status);
			}
		});
	}

	private static void resetStatus() {
		if (shownStatus != null) {
			showStatus(Component.translatable("connect.joining"), "");
			shownStatus = null;
		}
	}

	private static String mebibytes(long bytes) {
		return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
	}

	private static final class Listener implements ClientSession.Listener {
		private final DiskAssetStore store;
		private ClientSession session;

		Listener(DiskAssetStore store) {
			this.store = store;
		}

		@Override
		public void onWelcome(List<FeatureVersion> features) {
			SkaffySAPIClient.LOGGER.info("Server supports Skaffy's API, features: {}", features);
		}

		@Override
		public void onProgress(long receivedBytes, long expectedBytes) {
			String received = mebibytes(receivedBytes);
			String expected = mebibytes(expectedBytes);
			showStatus(Component.translatable("skaffys-api.connect.downloading", received, expected), received + "/" + expected);
		}

		@Override
		public void onRegistrationEnd() {
			showStatus(Component.translatable("skaffys-api.connect.loading"), "loading");
			ScheduledFuture<?> keepAlive = EXECUTOR.scheduleAtFixedRate(session::keepAlive, 10, 10, TimeUnit.SECONDS);

			ClientContent.load(store).whenComplete((result, error) -> {
				keepAlive.cancel(false);
				resetStatus();

				if (error != null) {
					SkaffySAPIClient.LOGGER.error("Could not load Skaffy's API content", error);
					session.fail("Could not load custom content: " + error);
					ClientContent.unload();
				} else {
					session.ready();
				}
			});
		}

		@Override
		public void onFailed(String reason) {
			resetStatus();
			SkaffySAPIClient.LOGGER.warn("Skaffy's API session failed: {}", reason);
		}
	}
}
