package me.skaffy.paper.gui;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Stream;

import me.skaffy.api.event.gui.SkaffyGuiCloseEvent;
import me.skaffy.api.event.gui.SkaffyGuiErrorEvent;
import me.skaffy.api.event.gui.SkaffyGuiMessageEvent;
import me.skaffy.api.event.gui.SkaffyGuiOpenEvent;
import me.skaffy.api.gui.GuiMessage;
import me.skaffy.api.gui.SkaffyGuis;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.gui.GuiCodec;
import me.skaffy.protocol.gui.GuiPacket;
import me.skaffy.protocol.gui.GuiPacket.CallResult;
import me.skaffy.protocol.gui.GuiPacket.Closed;
import me.skaffy.protocol.gui.GuiPacket.FontDefinition;
import me.skaffy.protocol.gui.GuiPacket.Message;
import me.skaffy.protocol.gui.GuiPacket.Opened;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PaperGuis implements SkaffyGuis, PluginMessageListener, Listener {
	private static final long CALL_TIMEOUT_SECONDS = 10;

	private final Plugin plugin;
	private final Function<UUID, GuiSession> sessions;
	private final Map<String, byte[]> files = new LinkedHashMap<>();
	private final List<FontDefinition> fonts = new CopyOnWriteArrayList<>();
	private final Map<UUID, OpenGui> open = new ConcurrentHashMap<>();
	private final Map<UUID, Map<String, String>> huds = new ConcurrentHashMap<>();
	private final Map<String, List<GuiMessageHandler>> handlers = new ConcurrentHashMap<>();
	private final AtomicInteger nextCall = new AtomicInteger(1);
	private final Map<Integer, PendingCall> calls = new ConcurrentHashMap<>();

	private record PendingCall(UUID player, CompletableFuture<Object> future) {
	}

	public PaperGuis(Plugin plugin, Function<UUID, GuiSession> sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
	}


	@Override
	public void addFile(String className, String source) {
		if (!GuiCodec.isValidClassName(className)) {
			throw new IllegalArgumentException("Invalid class name " + className + " (it needs a package, like shop.Shop)");
		}

		byte[] data = source.getBytes(StandardCharsets.UTF_8);

		if (data.length > GuiCodec.MAX_FILE_SIZE) {
			throw new IllegalArgumentException(className + " is " + data.length + " bytes, a file can be at most " + GuiCodec.MAX_FILE_SIZE);
		}

		synchronized (files) {
			files.put(className, data);
		}

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (sessions.apply(player.getUniqueId()) != null) {
				sendFile(chunk -> player.sendPluginMessage(plugin, GuiCodec.CHANNEL, chunk), className, data);
			}
		}
	}

	@Override
	public List<String> addFiles(Plugin owner, String folder) {
		Map<String, String> found = new LinkedHashMap<>();
		String prefix = folder.isEmpty() || folder.endsWith("/") ? folder : folder + "/";

		try {
			Path jar = Path.of(owner.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

			if (Files.isRegularFile(jar)) {
				try (JarFile file = new JarFile(jar.toFile())) {
					for (JarEntry entry : file.stream().toList()) {
						String name = entry.getName();

						if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(".sfy")) {
							try (InputStream stream = file.getInputStream(entry)) {
								found.put(classNameOf(name.substring(prefix.length())), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
							}
						}
					}
				}
			}
		} catch (IOException | java.net.URISyntaxException | SecurityException e) {
			plugin.getLogger().log(Level.WARNING, "Couldn't read GUI files from " + owner.getName() + "'s jar", e);
		}

		Path data = owner.getDataFolder().toPath().resolve(folder);

		if (Files.isDirectory(data)) {
			try (Stream<Path> walk = Files.walk(data)) {
				for (Path path : walk.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".sfy")).toList()) {
					String relative = data.relativize(path).toString().replace('\\', '/');
					found.put(classNameOf(relative), Files.readString(path, StandardCharsets.UTF_8));
				}
			} catch (IOException e) {
				plugin.getLogger().log(Level.WARNING, "Couldn't read GUI files from " + data, e);
			}
		}

		List<String> added = new ArrayList<>();

		found.forEach((className, source) -> {
			try {
				addFile(className, source);
				added.add(className);
			} catch (IllegalArgumentException e) {
				plugin.getLogger().warning("Skipping GUI file: " + e.getMessage());
			}
		});

		return added;
	}

	private static String classNameOf(String relativePath) {
		String withoutExtension = relativePath.substring(0, relativePath.length() - ".sfy".length());
		return withoutExtension.replace('/', '.');
	}

	@Override
	public void removeFile(String className) {
		boolean removed;

		synchronized (files) {
			removed = files.remove(className) != null;
		}

		if (removed) {
			byte[] packet = GuiCodec.encode(new GuiPacket.RemoveFile(className));

			for (Player player : Bukkit.getOnlinePlayers()) {
				if (sessions.apply(player.getUniqueId()) != null) {
					player.sendPluginMessage(plugin, GuiCodec.CHANNEL, packet);
				}
			}
		}
	}

	@Override
	public Set<String> getFiles() {
		synchronized (files) {
			return Set.copyOf(files.keySet());
		}
	}

	@Override
	public void addFont(String name, String assetId, float size, float oversample) {
		if (!Protocol.isValidAssetId(assetId)) {
			throw new IllegalArgumentException("Invalid asset id " + assetId);
		}

		FontDefinition font;

		try {
			font = new FontDefinition(name, assetId, size, oversample, 0, 0);
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage());
		}

		if (fonts.size() >= GuiCodec.MAX_FONTS) {
			throw new IllegalStateException("At most " + GuiCodec.MAX_FONTS + " GUI fonts");
		}

		fonts.removeIf(existing -> existing.name().equals(name));
		fonts.add(font);
	}

	public List<FontDefinition> getFonts() {
		return List.copyOf(fonts);
	}

	public void register(GuiSession session) {
		if (!fonts.isEmpty()) {
			session.sendGuiDefinition(GuiCodec.encode(new GuiPacket.DefineFonts(List.copyOf(fonts))));
		}

		Map<String, byte[]> snapshot;

		synchronized (files) {
			snapshot = new LinkedHashMap<>(files);
		}

		snapshot.forEach((className, data) -> sendFile(session::sendGuiDefinition, className, data));
	}

	private static void sendFile(java.util.function.Consumer<byte[]> out, String className, byte[] data) {
		int offset = 0;

		do {
			int length = Math.min(GuiCodec.MAX_CHUNK_SIZE, data.length - offset);
			byte[] chunk = java.util.Arrays.copyOfRange(data, offset, offset + length);
			out.accept(GuiCodec.encode(new GuiPacket.FileChunk(className, data.length, offset, chunk)));
			offset += length;
		} while (offset < data.length);
	}


	private boolean send(Player player, GuiPacket packet) {
		if (sessions.apply(player.getUniqueId()) == null) {
			return false;
		}

		byte[] data;

		try {
			data = GuiCodec.encode(packet);
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (data.length > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new IllegalArgumentException("The values are too big to send (" + data.length + " bytes)");
		}

		player.sendPluginMessage(plugin, GuiCodec.CHANNEL, data);
		return true;
	}

	@Override
	public boolean open(Player player, String className, String method, Object... args) {
		try {
			return send(player, new GuiPacket.Open(className, method, plainList(args)));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public void close(Player player) {
		send(player, new GuiPacket.Close());
	}

	@Override
	public CompletableFuture<Object> call(Player player, String className, String method, Object... args) {
		if (sessions.apply(player.getUniqueId()) == null) {
			return CompletableFuture.failedFuture(new IllegalStateException(player.getName() + " doesn't have Skaffy's API GUIs"));
		}

		int callId = nextCall.getAndUpdate(id -> id == Integer.MAX_VALUE ? 1 : id + 1);
		CompletableFuture<Object> future = new CompletableFuture<>();
		calls.put(callId, new PendingCall(player.getUniqueId(), future));
		future.orTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((value, error) -> calls.remove(callId));

		try {
			send(player, new GuiPacket.Call(callId, className, method, plainList(args)));
		} catch (RuntimeException e) {
			future.completeExceptionally(e);
		}

		return future;
	}

	@Override
	public void run(Player player, String className, String method, Object... args) {
		try {
			send(player, new GuiPacket.Call(0, className, method, plainList(args)));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public void replaceMethod(Player player, String className, String methodSource) {
		if (methodSource.length() > GuiCodec.MAX_SOURCE_LENGTH) {
			throw new IllegalArgumentException("A method can be at most " + GuiCodec.MAX_SOURCE_LENGTH + " characters");
		}

		try {
			send(player, new GuiPacket.ReplaceMethod(className, methodSource));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public void openLink(Player player, String url) {
		if (url.length() > GuiCodec.MAX_URL_LENGTH) {
			throw new IllegalArgumentException("A link can be at most " + GuiCodec.MAX_URL_LENGTH + " characters");
		}

		send(player, new GuiPacket.OpenLink(url));
	}

	@Override
	public Optional<OpenGui> getOpen(Player player) {
		return Optional.ofNullable(open.get(player.getUniqueId()));
	}

	@Override
	public boolean showHud(Player player, int order, String className, String method, Object... args) {
		try {
			return send(player, new GuiPacket.ShowHud(className, method, plainList(args), order));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public void hideHud(Player player, String className) {
		try {
			send(player, new GuiPacket.HideHud(className));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public void hideAllHuds(Player player) {
		send(player, new GuiPacket.HideHud(""));
	}

	@Override
	public Map<String, String> getHuds(Player player) {
		return Map.copyOf(huds.getOrDefault(player.getUniqueId(), Map.of()));
	}

	@Override
	public boolean setHudPart(Player player, me.skaffy.api.gui.HudPart part, boolean visible, float x, float y, float scale) {
		try {
			return send(player, new GuiPacket.SetHudPart(GuiPacket.HudPart.valueOf(part.name()), visible, x, y, scale));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public void resetHudParts(Player player) {
		for (GuiPacket.HudPart part : GuiPacket.HudPart.values()) {
			send(player, new GuiPacket.SetHudPart(part, true, 0, 0, 1));
		}
	}

	@Override
	public boolean isSupported(Player player) {
		return sessions.apply(player.getUniqueId()) != null;
	}

	@Override
	public void onMessage(String className, String name, GuiMessageHandler handler) {
		handlers.computeIfAbsent(className + "#" + name, key -> new CopyOnWriteArrayList<>()).add(handler);
	}

	private static List<Object> plainList(Object[] args) {
		List<Object> values = new ArrayList<>(args.length);

		for (Object arg : args) {
			values.add(plain(arg));
		}

		return values;
	}

	static Object plain(Object value) {
		return switch (value) {
			case null -> null;
			case Boolean ignored -> value;
			case Integer ignored -> value;
			case Long ignored -> value;
			case Double ignored -> value;
			case String ignored -> value;
			case Short number -> number.intValue();
			case Byte number -> number.intValue();
			case Float number -> number.doubleValue();
			case Character character -> String.valueOf(character);
			case Enum<?> constant -> constant.name();
			case UUID uuid -> uuid.toString();
			case org.bukkit.Color color -> color.asARGB();
			case Object[] array -> {
				List<Object> list = new ArrayList<>(array.length);

				for (Object element : array) {
					list.add(plain(element));
				}

				yield list;
			}
			case Collection<?> collection -> {
				List<Object> list = new ArrayList<>(collection.size());

				for (Object element : collection) {
					list.add(plain(element));
				}

				yield list;
			}
			case Map<?, ?> map -> {
				Map<Object, Object> result = new LinkedHashMap<>();
				map.forEach((key, element) -> result.put(plain(key), plain(element)));
				yield result;
			}
			case Record record -> {
				Map<Object, Object> result = new LinkedHashMap<>();

				for (RecordComponent component : record.getClass().getRecordComponents()) {
					try {
						component.getAccessor().setAccessible(true);
						result.put(component.getName(), plain(component.getAccessor().invoke(record)));
					} catch (ReflectiveOperationException e) {
						throw new IllegalArgumentException("Can't read " + record.getClass().getSimpleName() + "." + component.getName(), e);
					}
				}

				yield result;
			}
			default -> throw new IllegalArgumentException("Can't send a " + value.getClass().getSimpleName()
					+ " to a GUI (only null, booleans, numbers, strings, enums, colors, records, lists and maps)");
		};
	}


	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!GuiCodec.CHANNEL.equals(channel) || sessions.apply(player.getUniqueId()) == null) {
			return;
		}

		GuiPacket packet;

		try {
			packet = GuiCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			return;
		}

		switch (packet) {
			case Opened opened -> {
				open.put(player.getUniqueId(), new OpenGui(opened.className(), opened.method()));
				new SkaffyGuiOpenEvent(player, opened.className(), opened.method()).callEvent();
			}
			case Closed closed -> {
				OpenGui current = open.get(player.getUniqueId());

				if (current != null && current.className().equals(closed.className())) {
					open.remove(player.getUniqueId());
				}

				new SkaffyGuiCloseEvent(player, closed.className(), SkaffyGuiCloseEvent.Reason.valueOf(closed.reason().name())).callEvent();
			}
			case Message sent -> {
				GuiMessage guiMessage = new GuiMessage(sent.className(), sent.name(), sent.values());

				for (GuiMessageHandler handler : handlers.getOrDefault(sent.className() + "#" + sent.name(), List.of())) {
					try {
						handler.handle(player, guiMessage);
					} catch (RuntimeException e) {
						plugin.getLogger().log(Level.SEVERE, "GUI message handler for " + sent.className() + " " + sent.name() + " failed", e);
					}
				}

				new SkaffyGuiMessageEvent(player, guiMessage).callEvent();
			}
			case CallResult result -> {
				PendingCall call = calls.remove(result.callId());

				if (call != null && call.player.equals(player.getUniqueId())) {
					if (result.ok()) {
						call.future.complete(result.value());
					} else {
						call.future.completeExceptionally(new IllegalStateException(result.error()));
					}
				}
			}
			case GuiPacket.Error error -> {
				SkaffyGuiErrorEvent event = new SkaffyGuiErrorEvent(player, error.className(), SkaffyGuiErrorEvent.Kind.valueOf(error.kind().name()), error.message());

				if (event.callEvent()) {
					plugin.getLogger().warning("[GUI " + error.className() + "] " + error.kind().name().toLowerCase() + " error on " + player.getName() + "'s client:\n" + error.message());
				}
			}
			case GuiPacket.HudShown shown -> huds.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>()).put(shown.className(), shown.method());
			case GuiPacket.HudHidden hidden -> {
				Map<String, String> layers = huds.get(player.getUniqueId());

				if (layers != null) {
					layers.remove(hidden.className());
				}
			}
			case GuiPacket.Log log -> {
				String line = "[GUI " + log.className() + "] (" + player.getName() + ") " + log.message();

				switch (log.level()) {
					case WARN -> plugin.getLogger().warning(line);
					case ERROR -> plugin.getLogger().severe(line);
					default -> plugin.getLogger().info(line);
				}
			}
			default -> {
			}
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID player = event.getPlayer().getUniqueId();
		open.remove(player);
		huds.remove(player);
		calls.values().removeIf(call -> {
			if (call.player.equals(player)) {
				call.future.completeExceptionally(new IllegalStateException("The player left"));
				return true;
			}

			return false;
		});
	}
}
