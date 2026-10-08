package me.skaffy.paper.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Stream;

import me.skaffy.api.Easing;
import me.skaffy.api.event.shader.SkaffyShaderErrorEvent;
import me.skaffy.api.event.shader.SkaffyShaderStateEvent;
import me.skaffy.api.event.shader.SkaffyVistaChangeEvent;
import me.skaffy.api.shader.SkaffyShaders;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.shaders.ShadersCodec;
import me.skaffy.protocol.shaders.ShadersPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PaperShaders implements SkaffyShaders, PluginMessageListener, Listener {
	private final Plugin plugin;
	private final Function<UUID, ShaderSession> sessions;
	private final Map<String, byte[]> files = new LinkedHashMap<>();
	private final Map<UUID, Map<String, Integer>> enabled = new ConcurrentHashMap<>();
	private final Map<UUID, Set<String>> running = new ConcurrentHashMap<>();
	private final Map<UUID, ShadersPacket.VistaState> vista = new ConcurrentHashMap<>();

	public PaperShaders(Plugin plugin, Function<UUID, ShaderSession> sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
	}


	@Override
	public void addFile(String className, String source) {
		if (!ShadersCodec.isValidClassName(className)) {
			throw new IllegalArgumentException("Invalid class name " + className + " (it needs a package, like effects.Dream)");
		}

		checkNotReserved(className);

		byte[] data = source.getBytes(StandardCharsets.UTF_8);

		if (data.length > ShadersCodec.MAX_FILE_SIZE) {
			throw new IllegalArgumentException(className + " is " + data.length + " bytes, a shader file can be at most " + ShadersCodec.MAX_FILE_SIZE);
		}

		synchronized (files) {
			files.put(className, data);
		}

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (sessions.apply(player.getUniqueId()) != null) {
				sendFile(chunk -> player.sendPluginMessage(plugin, ShadersCodec.CHANNEL, chunk), className, data);
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
			plugin.getLogger().log(Level.WARNING, "Couldn't read shader files from " + owner.getName() + "'s jar", e);
		}

		Path data = owner.getDataFolder().toPath().resolve(folder);

		if (Files.isDirectory(data)) {
			try (Stream<Path> walk = Files.walk(data)) {
				for (Path path : walk.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".sfy")).toList()) {
					String relative = data.relativize(path).toString().replace('\\', '/');
					found.put(classNameOf(relative), Files.readString(path, StandardCharsets.UTF_8));
				}
			} catch (IOException e) {
				plugin.getLogger().log(Level.WARNING, "Couldn't read shader files from " + data, e);
			}
		}

		List<String> added = new ArrayList<>();

		found.forEach((className, source) -> {
			try {
				addFile(className, source);
				added.add(className);
			} catch (IllegalArgumentException e) {
				plugin.getLogger().warning("Skipping shader file: " + e.getMessage());
			}
		});

		return added;
	}

	private static String classNameOf(String relativePath) {
		return relativePath.substring(0, relativePath.length() - ".sfy".length()).replace('/', '.');
	}

	@Override
	public void removeFile(String className) {
		boolean removed;

		synchronized (files) {
			removed = files.remove(className) != null;
		}

		if (removed) {
			byte[] packet = ShadersCodec.encode(new ShadersPacket.RemoveFile(className));

			for (Player player : Bukkit.getOnlinePlayers()) {
				if (sessions.apply(player.getUniqueId()) != null) {
					player.sendPluginMessage(plugin, ShadersCodec.CHANNEL, packet);
				}

				Map<String, Integer> playerEnabled = enabled.get(player.getUniqueId());

				if (playerEnabled != null) {
					playerEnabled.remove(className);
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

	public void register(ShaderSession session) {
		Map<String, byte[]> snapshot;

		synchronized (files) {
			snapshot = new LinkedHashMap<>(files);
		}

		snapshot.forEach((className, data) -> sendFile(session::sendShaderDefinition, className, data));
	}

	private static void checkNotReserved(String className) {
		if (ShadersCodec.isReserved(className)) {
			throw new IllegalArgumentException(className + ": classes in skaffy. packages belong to the mod" + (className.equals(VISTA) ? " (Vista: use setVista)" : ""));
		}
	}

	private static void sendFile(Consumer<byte[]> out, String className, byte[] data) {
		int offset = 0;

		do {
			int length = Math.min(ShadersCodec.MAX_CHUNK_SIZE, data.length - offset);
			byte[] chunk = Arrays.copyOfRange(data, offset, offset + length);
			out.accept(ShadersCodec.encode(new ShadersPacket.FileChunk(className, data.length, offset, chunk)));
			offset += length;
		} while (offset < data.length);
	}


	private boolean send(Player player, ShadersPacket packet) {
		if (sessions.apply(player.getUniqueId()) == null) {
			return false;
		}

		byte[] data;

		try {
			data = ShadersCodec.encode(packet);
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (data.length > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new IllegalArgumentException("The values are too big to send (" + data.length + " bytes)");
		}

		player.sendPluginMessage(plugin, ShadersCodec.CHANNEL, data);
		return true;
	}

	@Override
	public boolean enable(Player player, String className, int order, Map<String, ?> uniforms) {
		checkNotReserved(className);
		boolean sent;

		try {
			sent = send(player, new ShadersPacket.Enable(className, order, plainValues(uniforms)));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (sent) {
			enabled.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>()).put(className, order);
		}

		return sent;
	}

	@Override
	public void disable(Player player, String className) {
		checkNotReserved(className);

		try {
			send(player, new ShadersPacket.Disable(className));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		Map<String, Integer> playerEnabled = enabled.get(player.getUniqueId());

		if (playerEnabled != null) {
			playerEnabled.remove(className);
		}
	}

	@Override
	public void disableAll(Player player) {
		send(player, new ShadersPacket.DisableAll());
		enabled.remove(player.getUniqueId());
	}

	@Override
	public void animate(Player player, String className, Map<String, ?> values, int durationMillis, Easing easing) {
		if (durationMillis < 0 || durationMillis > ShadersPacket.MAX_DURATION) {
			throw new IllegalArgumentException("Duration must be 0 to " + ShadersPacket.MAX_DURATION + " ms, got " + durationMillis);
		}

		try {
			send(player, new ShadersPacket.SetUniforms(className, plainValues(values), durationMillis, me.skaffy.protocol.Easing.valueOf(easing.name())));
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public Map<String, Integer> getEnabled(Player player) {
		return Map.copyOf(enabled.getOrDefault(player.getUniqueId(), Map.of()));
	}

	@Override
	public boolean isRunning(Player player, String className) {
		return running.getOrDefault(player.getUniqueId(), Set.of()).contains(className);
	}

	@Override
	public boolean isSupported(Player player) {
		return sessions.apply(player.getUniqueId()) != null;
	}

	@Override
	public boolean setVista(Player player, Boolean enabled, boolean locked) {
		return send(player, new ShadersPacket.SetVista(enabled, locked));
	}

	@Override
	public Boolean isVistaOn(Player player) {
		ShadersPacket.VistaState state = vista.get(player.getUniqueId());
		return state == null ? null : state.enabled();
	}

	@Override
	public boolean setShaderDistance(Player player, int chunks, boolean locked) {
		if (chunks != 0 && (chunks < MIN_SHADER_DISTANCE || chunks > MAX_SHADER_DISTANCE)) {
			throw new IllegalArgumentException("Shader Distance must be 0 (the player's own) or " + MIN_SHADER_DISTANCE + " to " + MAX_SHADER_DISTANCE + " chunks, got " + chunks);
		}

		return send(player, new ShadersPacket.SetShaderDistance(chunks, locked));
	}

	@Override
	public int getShaderDistance(Player player) {
		ShadersPacket.VistaState state = vista.get(player.getUniqueId());
		return state == null ? 0 : state.chunks();
	}

	private static Map<String, Object> plainValues(Map<String, ?> values) {
		Map<String, Object> plain = new LinkedHashMap<>();
		values.forEach((name, value) -> plain.put(name, plain(name, value)));
		return plain;
	}

	private static Object plain(String name, Object value) {
		return switch (value) {
			case Boolean ignored -> value;
			case Integer ignored -> value;
			case Long ignored -> value;
			case Double ignored -> value;
			case Float number -> number.doubleValue();
			case Short number -> number.intValue();
			case Byte number -> number.intValue();
			case String text -> {
				if (!text.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
					throw new IllegalArgumentException("Uniform " + name + ": text values are colors like #FF8800 or #80FF8800");
				}

				yield text;
			}
			case org.bukkit.Color color -> String.format("#%08X", color.asARGB());
			case org.bukkit.util.Vector vector -> List.of(vector.getX(), vector.getY(), vector.getZ());
			case float[] array -> {
				List<Object> list = new ArrayList<>();

				for (float element : array) {
					list.add((double) element);
				}

				yield list;
			}
			case double[] array -> Arrays.stream(array).boxed().map(element -> (Object) element).toList();
			case int[] array -> Arrays.stream(array).boxed().map(element -> (Object) element).toList();
			case Collection<?> collection -> {
				List<Object> list = new ArrayList<>();

				for (Object element : collection) {
					if (!(element instanceof Number) && !(element instanceof Boolean)) {
						throw new IllegalArgumentException("Uniform " + name + ": lists hold numbers");
					}

					list.add(element instanceof Number number && !(number instanceof Integer) && !(number instanceof Long) ? number.doubleValue() : element);
				}

				yield list;
			}
			case null -> throw new IllegalArgumentException("Uniform " + name + " can't be null");
			default -> throw new IllegalArgumentException("Uniform " + name + ": can't send a " + value.getClass().getSimpleName()
					+ " (numbers, booleans, lists of numbers, Color, Vector or \"#RRGGBB\")");
		};
	}


	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!ShadersCodec.CHANNEL.equals(channel) || sessions.apply(player.getUniqueId()) == null) {
			return;
		}

		ShadersPacket packet;

		try {
			packet = ShadersCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			return;
		}

		switch (packet) {
			case ShadersPacket.Error error -> {
				SkaffyShaderErrorEvent event = new SkaffyShaderErrorEvent(player, error.className(), SkaffyShaderErrorEvent.Kind.valueOf(error.kind().name()), error.message());

				if (event.callEvent()) {
					plugin.getLogger().warning("[Shader " + error.className() + "] " + error.kind().name().toLowerCase() + " error on " + player.getName() + "'s client:\n" + error.message());
				}
			}
			case ShadersPacket.State state -> {
				Set<String> playerRunning = running.computeIfAbsent(player.getUniqueId(), key -> ConcurrentHashMap.newKeySet());

				if (state.running()) {
					playerRunning.add(state.className());
				} else {
					playerRunning.remove(state.className());
				}

				new SkaffyShaderStateEvent(player, state.className(), state.running()).callEvent();
			}
			case ShadersPacket.VistaState state -> {
				vista.put(player.getUniqueId(), state);
				new SkaffyVistaChangeEvent(player, state.enabled(), state.chunks(), state.byPlayer()).callEvent();
			}
			default -> {
			}
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		enabled.remove(event.getPlayer().getUniqueId());
		running.remove(event.getPlayer().getUniqueId());
		vista.remove(event.getPlayer().getUniqueId());
	}
}
