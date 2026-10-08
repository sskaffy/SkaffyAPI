package me.skaffy.client.shader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.shader.lang.ShaderCompiler;
import me.skaffy.client.shader.lang.ShaderModule;
import me.skaffy.protocol.Easing;
import me.skaffy.protocol.shaders.ShadersCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;

public final class Vista {
	public static final String CLASS_NAME = ShadersCodec.VISTA;
	private static final String FOLDER = "/skaffy_shaders/";
	private static final List<String> FILES = List.of("Vista", "Palette", "Sky", "Clouds", "Surface", "Lighting", "Water", "Rays", "Post");
	private static final int DEFAULT_DISTANCE = 16;
	private static final long KEEP_WITHOUT_LEVEL = 10_000;
	private static final Component LOCKED = Component.translatable("skaffys-api.options.locked");

	private static boolean playerEnabled;
	private static int playerDistance = DEFAULT_DISTANCE;
	private static @Nullable Boolean serverEnabled;
	private static int serverDistance;
	private static boolean enabledLocked;
	private static boolean distanceLocked;
	private static final Map<String, Object> SERVER_UNIFORMS = new LinkedHashMap<>();
	private static int version;
	private static boolean applying;
	private static @Nullable CompletableFuture<@Nullable ShaderModule> compiling;
	private static boolean failed;
	private static long lastLevel;

	public static final OptionInstance<Boolean> ENABLED = OptionInstance.createBoolean("skaffys-api.options.vista",
			value -> enabledTooltip(), false, value -> {
				if (!applying) {
					playerEnabled(value);
				}
			});

	public static final OptionInstance<Integer> DISTANCE = new OptionInstance<>("skaffys-api.options.shaderDistance",
			value -> distanceTooltip(),
			(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.chunks", value)),
			new OptionInstance.IntRange(ShadersCodec.MIN_DISTANCE, ShadersCodec.MAX_DISTANCE, false), DEFAULT_DISTANCE, value -> {
				if (!applying) {
					playerDistance(value);
				}
			});

	private Vista() {
	}


	public static boolean enabled() {
		return serverEnabled != null ? serverEnabled : playerEnabled;
	}

	public static int distance() {
		return serverDistance > 0 ? serverDistance : playerDistance;
	}

	public static boolean enabledLocked() {
		return enabledLocked;
	}

	public static boolean distanceLocked() {
		return distanceLocked;
	}

	public static int version() {
		return version;
	}

	public static Tooltip enabledTooltip() {
		return Tooltip.create(enabledLocked ? LOCKED : Component.translatable("skaffys-api.options.vista.tooltip"));
	}

	public static Tooltip distanceTooltip() {
		return Tooltip.create(distanceLocked ? LOCKED : Component.translatable("skaffys-api.options.shaderDistance.tooltip"));
	}

	public static void processOptions(Object access) {
		try {
			playerEnabled = (boolean) process(access, boolean.class).invoke(access, "skaffy_vista", playerEnabled);
			int distance = (int) process(access, int.class).invoke(access, "skaffy_shaderDistance", playerDistance);
			playerDistance = Math.clamp(distance, ShadersCodec.MIN_DISTANCE, ShadersCodec.MAX_DISTANCE);
		} catch (ReflectiveOperationException | RuntimeException e) {
			SkaffySAPIClient.LOGGER.error("Couldn't load or save the Vista settings", e);
		}

		show();
	}

	private static Method process(Object access, Class<?> type) throws NoSuchMethodException {
		for (Class<?> face : access.getClass().getInterfaces()) {
			try {
				Method method = face.getMethod("process", String.class, type);
				method.setAccessible(true);
				return method;
			} catch (NoSuchMethodException ignored) {
			}
		}

		throw new NoSuchMethodException("process(String, " + type + ") on " + access.getClass());
	}

	private static void show() {
		applying = true;

		try {
			ENABLED.set(enabled());
			DISTANCE.set(distance());
		} finally {
			applying = false;
		}

		version++;
	}

	private static void playerEnabled(boolean value) {
		if (enabledLocked) {
			show();
			return;
		}

		playerEnabled = value;
		serverEnabled = null;
		ClientShaders.sendVista(enabled(), distance(), true);
	}

	private static void playerDistance(int value) {
		if (distanceLocked) {
			show();
			return;
		}

		playerDistance = value;
		serverDistance = 0;
		ClientShaders.sendVista(enabled(), distance(), true);
	}


	static void serverEnabled(@Nullable Boolean enabled, boolean locked) {
		serverEnabled = enabled;
		enabledLocked = locked;
		show();
		ClientShaders.sendVista(enabled(), distance(), false);
	}

	static void serverDistance(int chunks, boolean locked) {
		serverDistance = chunks;
		distanceLocked = locked;
		show();
		ClientShaders.sendVista(enabled(), distance(), false);
	}

	static void serverUniforms(Map<String, Object> values, int duration, Easing easing) {
		SERVER_UNIFORMS.putAll(values);

		if (ShaderRenderer.isEnabled(CLASS_NAME)) {
			ShaderRenderer.setUniforms(CLASS_NAME, values, duration, easing);
		}
	}

	static void sessionStarted() {
		ClientShaders.sendVista(enabled(), distance(), false);

		if (ShaderRenderer.isRunning(CLASS_NAME)) {
			ClientShaders.sendState(CLASS_NAME, true);
		}
	}

	static void sessionEnded() {
		boolean uniforms = !SERVER_UNIFORMS.isEmpty();
		serverEnabled = null;
		serverDistance = 0;
		enabledLocked = false;
		distanceLocked = false;
		SERVER_UNIFORMS.clear();

		if (uniforms) {
			ShaderRenderer.resetUniforms(CLASS_NAME);
		}

		show();
	}


	static void update() {
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();

		if (minecraft.level != null) {
			lastLevel = now;
		}

		boolean on = ShaderRenderer.isEnabled(CLASS_NAME);
		boolean wanted = enabled() && !failed && lastLevel != 0 && now - lastLevel < KEEP_WITHOUT_LEVEL;

		if (!wanted) {
			if (on) {
				ShaderRenderer.disable(CLASS_NAME);
			}

			return;
		}

		if (on || minecraft.level == null) {
			return;
		}

		ShaderModule module = module();

		if (module != null) {
			ShaderRenderer.enableBuiltin(CLASS_NAME, module, Vista::asset);

			if (!SERVER_UNIFORMS.isEmpty()) {
				ShaderRenderer.setUniforms(CLASS_NAME, Map.copyOf(SERVER_UNIFORMS), 0, Easing.LINEAR);
			}
		}
	}

	private static @Nullable ShaderModule module() {
		if (compiling == null) {
			compiling = CompletableFuture.supplyAsync(Vista::compile, Util.backgroundExecutor()).exceptionally(error -> {
				SkaffySAPIClient.LOGGER.error("Compiling Vista crashed", error);
				return null;
			});
		}

		if (!compiling.isDone()) {
			return null;
		}

		ShaderModule module = compiling.join();
		failed = module == null;
		return module;
	}

	private static @Nullable ShaderModule compile() {
		long start = System.nanoTime();
		Map<String, String> sources = new HashMap<>();

		for (String file : FILES) {
			try (InputStream stream = Vista.class.getResourceAsStream(FOLDER + "skaffy/vista/" + file + ".sfy")) {
				if (stream == null) {
					SkaffySAPIClient.LOGGER.error("Vista's {}.sfy is missing from the mod", file);
					return null;
				}

				sources.put("skaffy.vista." + file, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			} catch (IOException e) {
				SkaffySAPIClient.LOGGER.error("Couldn't read Vista's {}.sfy", file, e);
				return null;
			}
		}

		ShaderCompiler.Result result = ShaderCompiler.compile(CLASS_NAME, sources.get(CLASS_NAME), sources::get);

		if (result.module() == null) {
			SkaffySAPIClient.LOGGER.error("Vista didn't compile:\n{}", result.error() != null ? result.error().describe() : "missing " + result.missing());
			return null;
		}

		SkaffySAPIClient.LOGGER.info("Compiled Vista in {} ms", (System.nanoTime() - start) / 1_000_000);
		return result.module();
	}

	private static byte @Nullable [] asset(String id) throws IOException {
		try (InputStream stream = Vista.class.getResourceAsStream(FOLDER + "assets/" + id)) {
			return stream == null ? null : stream.readAllBytes();
		}
	}
}
