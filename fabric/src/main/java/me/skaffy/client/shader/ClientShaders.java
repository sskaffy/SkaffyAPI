package me.skaffy.client.shader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.gui.sfy.CompileException;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.client.shader.lang.ShaderCompiler;
import me.skaffy.client.shader.lang.ShaderModule;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.shaders.ShadersCodec;
import me.skaffy.protocol.shaders.ShadersPacket;
import me.skaffy.protocol.shaders.ShadersPacket.Disable;
import me.skaffy.protocol.shaders.ShadersPacket.DisableAll;
import me.skaffy.protocol.shaders.ShadersPacket.Enable;
import me.skaffy.protocol.shaders.ShadersPacket.ErrorKind;
import me.skaffy.protocol.shaders.ShadersPacket.FileChunk;
import me.skaffy.protocol.shaders.ShadersPacket.RemoveFile;
import me.skaffy.protocol.shaders.ShadersPacket.SetShaderDistance;
import me.skaffy.protocol.shaders.ShadersPacket.SetUniforms;
import me.skaffy.protocol.shaders.ShadersPacket.SetVista;

import net.minecraft.client.Minecraft;

public final class ClientShaders {
	private static final Object LOCK = new Object();
	private static final Map<String, Partial> PARTIAL = new HashMap<>();
	private static final Map<String, Integer> SIZES = new HashMap<>();
	private static final Map<String, ShaderModule> MODULES = new ConcurrentHashMap<>();
	private static final Map<String, String> SOURCES = new ConcurrentHashMap<>();
	private static final Map<String, java.util.Set<String>> DEPENDENCIES = new ConcurrentHashMap<>();
	private static final Map<String, String> WAITING = new ConcurrentHashMap<>();
	private static final List<ShadersPacket> QUEUED = new ArrayList<>();
	private static volatile boolean ready;

	private record Partial(byte[] data, int received) {
	}

	private ClientShaders() {
	}

	public static void receive(byte[] data, boolean registering) {
		ShadersPacket packet;

		try {
			packet = ShadersCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed shaders packet: {}", e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		String reserved = reserved(packet);

		if (reserved != null) {
			if (!(packet instanceof FileChunk chunk) || chunk.offset() == 0) {
				report(reserved, ErrorKind.REQUEST, "Classes in skaffy. packages belong to the mod, a server can't send, remove, enable or disable "
						+ reserved + (reserved.equals(ShadersCodec.VISTA) ? " (Vista is turned on and off with SetVista)" : ""));
			}

			return;
		}

		switch (packet) {
			case FileChunk chunk -> chunk(chunk);
			case RemoveFile remove -> {
				synchronized (LOCK) {
					PARTIAL.remove(remove.className());
					SIZES.remove(remove.className());
				}

				MODULES.remove(remove.className());
				SOURCES.remove(remove.className());
				DEPENDENCIES.remove(remove.className());
				WAITING.remove(remove.className());
				minecraft.execute(() -> ShaderRenderer.disable(remove.className()));
				recompileUsers(remove.className());
			}
			case Enable enable -> minecraft.execute(() -> {
				ShaderModule module = MODULES.get(enable.className());

				if (module == null && !ShaderRenderer.isEnabled(enable.className())) {
					String waiting = WAITING.get(enable.className());
					report(enable.className(), waiting != null ? ErrorKind.COMPILE : ErrorKind.REQUEST, waiting != null ? waiting
							: enable.className() + " isn't loaded (not sent, or it has compile errors); it starts once it compiles");
				}

				ShaderRenderer.enable(enable.className(), enable.order(), enable.uniforms(), module);
			});
			case Disable disable -> minecraft.execute(() -> ShaderRenderer.disable(disable.className()));
			case DisableAll ignored -> minecraft.execute(ShaderRenderer::disableAll);
			case SetUniforms set when set.className().equals(ShadersCodec.VISTA) -> minecraft.execute(() -> Vista.serverUniforms(set.values(), set.durationMillis(), set.easing()));
			case SetUniforms set -> minecraft.execute(() -> ShaderRenderer.setUniforms(set.className(), set.values(), set.durationMillis(), set.easing()));
			case SetVista vista -> minecraft.execute(() -> Vista.serverEnabled(vista.enabled(), vista.locked()));
			case SetShaderDistance distance -> minecraft.execute(() -> Vista.serverDistance(distance.chunks(), distance.locked()));
			default -> {
			}
		}
	}

	private static String reserved(ShadersPacket packet) {
		String className = switch (packet) {
			case FileChunk chunk -> chunk.className();
			case RemoveFile remove -> remove.className();
			case Enable enable -> enable.className();
			case Disable disable -> disable.className();
			default -> null;
		};

		return className != null && ShadersCodec.isReserved(className) ? className : null;
	}

	private static void chunk(FileChunk chunk) {
		byte[] complete;

		synchronized (LOCK) {
			Partial partial = PARTIAL.get(chunk.className());

			if (chunk.offset() == 0) {
				partial = new Partial(new byte[chunk.totalSize()], 0);
			} else if (partial == null || partial.data.length != chunk.totalSize() || partial.received != chunk.offset()) {
				SkaffySAPIClient.LOGGER.warn("Shader file {} arrived out of order, dropped", chunk.className());
				PARTIAL.remove(chunk.className());
				return;
			}

			System.arraycopy(chunk.data(), 0, partial.data, chunk.offset(), chunk.data().length);
			partial = new Partial(partial.data, partial.received + chunk.data().length);

			if (partial.received < partial.data.length) {
				PARTIAL.put(chunk.className(), partial);
				return;
			}

			PARTIAL.remove(chunk.className());
			long total = chunk.totalSize();

			for (Map.Entry<String, Integer> entry : SIZES.entrySet()) {
				if (!entry.getKey().equals(chunk.className())) {
					total += entry.getValue();
				}
			}

			if (total > ShadersCodec.MAX_TOTAL_SIZE) {
				report(chunk.className(), ErrorKind.COMPILE, "Shader files are over " + ShadersCodec.MAX_TOTAL_SIZE / 1024 / 1024 + " MiB together, " + chunk.className() + " is ignored");
				return;
			}

			SIZES.put(chunk.className(), chunk.totalSize());
			complete = partial.data;
		}

		SOURCES.put(chunk.className(), new String(complete, StandardCharsets.UTF_8));
		compile(chunk.className());
		recompileUsers(chunk.className());
	}

	private static void recompileUsers(String className) {
		for (Map.Entry<String, java.util.Set<String>> entry : List.copyOf(DEPENDENCIES.entrySet())) {
			if (!entry.getKey().equals(className) && entry.getValue().contains(className) && SOURCES.containsKey(entry.getKey())) {
				compile(entry.getKey());
			}
		}
	}

	private static void compile(String className) {
		String source = SOURCES.get(className);

		if (source == null) {
			return;
		}

		long start = System.nanoTime();
		ShaderModule module = null;
		WAITING.remove(className);

		try {
			ShaderCompiler.Result result = ShaderCompiler.compile(className, source, SOURCES::get);
			DEPENDENCIES.put(className, result.dependencies());
			module = result.module();

			if (module != null) {
				MODULES.put(className, module);
				SkaffySAPIClient.LOGGER.info("Compiled shader {} in {} ms", className, (System.nanoTime() - start) / 1_000_000);
			} else if (!result.missing().isEmpty()) {
				MODULES.remove(className);
				WAITING.put(className, result.error().describe());
				SkaffySAPIClient.LOGGER.info("Shader {} waits for {}", className, String.join(", ", result.missing()));
			} else {
				MODULES.remove(className);
				report(className, ErrorKind.COMPILE, result.error().describe());
			}
		} catch (RuntimeException | StackOverflowError e) {
			MODULES.remove(className);
			SkaffySAPIClient.LOGGER.error("Compiling shader {} crashed", className, e);
			report(className, ErrorKind.COMPILE, "The compiler crashed on this file: " + e);
		}

		ShaderModule compiled = module;
		Minecraft.getInstance().execute(() -> ShaderRenderer.fileCompiled(className, compiled));
	}


	public static void load(DiskAssetStore store) {
		Minecraft.getInstance().execute(() -> ShaderRenderer.assets(store));
	}

	public static void activate() {
		List<ShadersPacket> queued;

		synchronized (LOCK) {
			ready = true;
			queued = List.copyOf(QUEUED);
			QUEUED.clear();
		}

		queued.forEach(ClientShaders::send);
		Minecraft.getInstance().execute(Vista::sessionStarted);
	}

	public static void unload() {
		synchronized (LOCK) {
			ready = false;
			PARTIAL.clear();
			SIZES.clear();
			QUEUED.clear();
		}

		MODULES.clear();
		SOURCES.clear();
		DEPENDENCIES.clear();
		WAITING.clear();
		Minecraft.getInstance().execute(() -> {
			ShaderRenderer.clear();
			Vista.sessionEnded();
		});
	}


	static void report(String className, ErrorKind kind, String message) {
		SkaffySAPIClient.LOGGER.warn("[Shader {}] {} error:\n{}", className, kind.name().toLowerCase(Locale.ROOT), message);
		send(new ShadersPacket.Error(className, kind, message));
	}

	static void sendState(String className, boolean running) {
		send(new ShadersPacket.State(className, running));
	}

	static void sendVista(boolean enabled, int chunks, boolean byPlayer) {
		if (SkaffyConnection.supports(ShadersCodec.FEATURE)) {
			send(new ShadersPacket.VistaState(enabled, chunks, byPlayer));
		}
	}

	private static void send(ShadersPacket packet) {
		synchronized (LOCK) {
			if (!ready) {
				if (packet instanceof ShadersPacket.VistaState || packet instanceof ShadersPacket.Error error && ShadersCodec.isReserved(error.className())
						|| packet instanceof ShadersPacket.State state && ShadersCodec.isReserved(state.className())) {
					return;
				}

				if (QUEUED.size() < 256) {
					QUEUED.add(packet);
				}

				return;
			}
		}

		byte[] data;

		try {
			data = ShadersCodec.encode(packet);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Couldn't send a shaders packet: {}", e.getMessage());
			return;
		}

		if (data.length > Protocol.MAX_SERVERBOUND_PAYLOAD) {
			return;
		}

		Minecraft.getInstance().execute(() -> SkaffyConnection.sendFeature(ShadersCodec.CHANNEL, data));
	}
}
