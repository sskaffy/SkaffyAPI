package me.skaffy.client.shader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.shader.lang.ShaderCompiler;
import me.skaffy.client.shader.lang.ShaderModule;
import me.skaffy.client.shader.pack.PackFiles;
import me.skaffy.client.shader.pack.PackLibrary;
import me.skaffy.client.shader.pack.ShaderPack;
import me.skaffy.protocol.Easing;
import me.skaffy.protocol.shaders.ShadersPacket.ErrorKind;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;

public final class ShaderPacks {
	public static final String VISTA_ID = "skaffy:vista";
	static final String UNSET = "\u0000unset";
	private static final String VISTA_ROOT = "/skaffy_shaders/vista/";
	private static final List<String> VISTA_FILES = List.of("shaders/skaffy/vista/VistaShader.sfy", "shaders/skaffy/vista/Vista.sfy",
			"shaders/skaffy/vista/Palette.sfy", "shaders/skaffy/vista/Sky.sfy", "shaders/skaffy/vista/Clouds.sfy", "shaders/skaffy/vista/Surface.sfy",
			"shaders/skaffy/vista/Lighting.sfy", "shaders/skaffy/vista/Water.sfy", "shaders/skaffy/vista/Rays.sfy", "shaders/skaffy/vista/Post.sfy",
			"resources/icon.png", "resources/vista_noise.png");
	private static final long KEEP_WITHOUT_LEVEL = 10_000;
	private static final int MAX_RUNTIME_ERRORS = 20;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final List<ShaderPack> PACKS = new ArrayList<>();
	private static boolean scanned;
	private static @Nullable String selected;
	private static @Nullable ShaderPack running;
	private static int runningBuilt;
	private static final List<String> RUNNING_KEYS = new ArrayList<>();
	private static long lastLevel;
	private static int version;
	private static final Map<ShaderPack, Long> RECOMPILE_AT = new HashMap<>();
	private static final long RECOMPILE_DELAY = 400;

	private ShaderPacks() {
	}


	public static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("shaders");
	}

	private static Path settingsFolder() {
		return folder().resolve("settings");
	}

	public static List<ShaderPack> packs() {
		scan();
		return List.copyOf(PACKS);
	}

	public static int version() {
		return version;
	}

	public static @Nullable String selected() {
		return selected;
	}

	public static @Nullable String activeId() {
		return selected != null && !selected.isEmpty() && ShaderOptions.clientShaderAllowed() ? selected : null;
	}

	static @Nullable String selectedForOptions() {
		return selected;
	}

	static void loadSelected(String id) {
		selected = id;
	}

	private static void scan() {
		if (scanned) {
			return;
		}

		scanned = true;
		ShaderPack vista = new ShaderPack(VISTA_ID, ShaderPack.Kind.BUILTIN, null);
		vista.name = "Vista";
		PACKS.add(vista);
		load(vista);
		rescan();
	}

	public static void rescan() {
		if (!scanned) {
			scan();
			return;
		}

		Path folder = folder();

		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			SkaffySAPIClient.LOGGER.warn("Couldn't make the shaders folder {}", folder, e);
		}

		Map<String, Path> found = new LinkedHashMap<>();

		try (Stream<Path> list = Files.list(folder)) {
			for (Path path : list.sorted().toList()) {
				String name = path.getFileName().toString();

				if (name.startsWith(".") || name.equals("settings")) {
					continue;
				}

				if (Files.isDirectory(path) || name.toLowerCase(Locale.ROOT).endsWith(".zip") && Files.isRegularFile(path)) {
					found.put(name, path);
				}
			}
		} catch (IOException e) {
			SkaffySAPIClient.LOGGER.warn("Couldn't list the shaders folder {}", folder, e);
		}

		boolean changed = false;

		for (ShaderPack pack : List.copyOf(PACKS)) {
			if (pack.kind != ShaderPack.Kind.BUILTIN && !found.containsKey(pack.id)) {
				remove(pack);
				changed = true;
			}
		}

		for (Map.Entry<String, Path> entry : found.entrySet()) {
			if (find(entry.getKey()) == null) {
				ShaderPack pack = new ShaderPack(entry.getKey(), Files.isDirectory(entry.getValue()) ? ShaderPack.Kind.FOLDER : ShaderPack.Kind.ZIP, entry.getValue());
				PACKS.add(pack);
				load(pack);
				changed = true;
			}
		}

		if (changed) {
			sort();
		}
	}

	public static void reloadAll() {
		scan();

		for (ShaderPack pack : List.copyOf(PACKS)) {
			pack.disable();
			load(pack);
		}

		rescan();
	}

	private static void remove(ShaderPack pack) {
		if (running == pack) {
			stopRunning();
		}

		pack.disable();
		PACKS.remove(pack);
		version++;
	}

	private static void sort() {
		PACKS.sort(Comparator.<ShaderPack>comparingInt(pack -> pack.kind == ShaderPack.Kind.BUILTIN ? 0 : 1).thenComparing(pack -> pack.name.toLowerCase(Locale.ROOT)));
		version++;
	}

	public static @Nullable ShaderPack find(String id) {
		for (ShaderPack pack : PACKS) {
			if (pack.id.equals(id)) {
				return pack;
			}
		}

		return null;
	}


	private static void load(ShaderPack pack) {
		pack.state = ShaderPack.State.LOADING;
		pack.errors.clear();
		pack.runtimeErrors.clear();
		int generation = ++pack.generation;
		version++;

		CompletableFuture.supplyAsync(() -> {
			try {
				PackFiles files = pack.kind == ShaderPack.Kind.BUILTIN ? builtinFiles() : pack.read();
				PackLibrary.Loaded loaded = ShaderPack.load(pack.id, files, pack.kind == ShaderPack.Kind.BUILTIN);
				Map<String, Object> values = readSettings(pack.id);
				Compiled compiled = compile(loaded.effects(), files, values);
				return new Result(loaded, values, compiled, null);
			} catch (ShaderPack.LoadException e) {
				return new Result(null, Map.of(), null, e.getMessage());
			} catch (IOException | RuntimeException | StackOverflowError e) {
				SkaffySAPIClient.LOGGER.error("Loading shader pack {} crashed", pack.id, e);
				return new Result(null, Map.of(), null, "Loading crashed: " + e);
			}
		}, Util.backgroundExecutor()).thenAcceptAsync(result -> {
			if (pack.generation != generation || !PACKS.contains(pack)) {
				if (result.loaded != null) {
					PackLibrary.disable(pack.id, result.loaded);
				}

				return;
			}

			if (result.failure != null) {
				pack.state = ShaderPack.State.FAILED;
				pack.errors.add(result.failure);
				SkaffySAPIClient.LOGGER.warn("[Shader pack {}] {}", pack.id, result.failure);
			} else {
				pack.apply(result.loaded);
				pack.optionValues.clear();
				pack.optionValues.putAll(result.values);
				finishCompile(pack, result.compiled);
				SkaffySAPIClient.LOGGER.info("Loaded shader pack {} ({})", pack.name, pack.id);
			}

			sort();
		}, Minecraft.getInstance());
	}

	private static PackFiles builtinFiles() throws ShaderPack.LoadException, IOException {
		try {
			return PackFiles.readResources(VISTA_ROOT, VISTA_FILES);
		} catch (PackFiles.PackException e) {
			throw new ShaderPack.LoadException(e.getMessage());
		}
	}

	private record Result(PackLibrary.@Nullable Loaded loaded, Map<String, Object> values, @Nullable Compiled compiled, @Nullable String failure) {
	}

	private record Compiled(Map<String, ShaderModule> modules, List<ShaderModule.Option> options, List<String> errors) {
	}

	private static Compiled compile(List<String> effects, PackFiles files, Map<String, Object> values) {
		Map<String, ShaderModule> modules = new LinkedHashMap<>();
		Map<String, ShaderModule.Option> options = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();

		for (String className : effects) {
			String source = files.sources().get(className);
			ShaderCompiler.Result result = ShaderCompiler.compile(className, source, files.sources()::get, values);

			if (result.module() == null) {
				errors.add(result.error() != null ? result.error().describe() : className + " needs " + String.join(", ", result.missing()));
				continue;
			}

			if (result.module().passes().isEmpty() && result.module().hooks().isEmpty() && !result.module().usesShadows()) {
				SkaffySAPIClient.LOGGER.warn("[Shader pack] {} is registered but has no hooks or passes, so it draws nothing", className);
			}

			modules.put(className, result.module());
			result.module().options().forEach(option -> options.putIfAbsent(option.key(), option));
		}

		return new Compiled(modules, List.copyOf(options.values()), errors);
	}

	private static void finishCompile(ShaderPack pack, Compiled compiled) {
		pack.errors.removeIf(error -> true);
		pack.errors.addAll(compiled.errors());
		pack.options = compiled.options();
		pack.modules = compiled.errors().isEmpty() ? Map.copyOf(compiled.modules()) : Map.of();
		pack.state = compiled.errors().isEmpty() ? ShaderPack.State.READY : ShaderPack.State.FAILED;
		pack.built++;

		for (String error : compiled.errors()) {
			SkaffySAPIClient.LOGGER.warn("[Shader pack {}] compile error:\n{}", pack.id, error);
		}

		version++;
	}

	private static void recompile(ShaderPack pack) {
		PackFiles files = pack.files;

		if (files == null) {
			return;
		}

		int generation = ++pack.generation;
		List<String> effects = pack.effects;
		Map<String, Object> values = Map.copyOf(pack.optionValues);
		CompletableFuture.supplyAsync(() -> compile(effects, files, values), Util.backgroundExecutor()).thenAcceptAsync(compiled -> {
			if (pack.generation == generation && PACKS.contains(pack)) {
				finishCompile(pack, compiled);
			}
		}, Minecraft.getInstance());
	}


	private static Path settingsFile(String id) {
		return settingsFolder().resolve(id.replace(':', '_') + ".json");
	}

	private static Map<String, Object> readSettings(String id) {
		Path file = settingsFile(id);
		Map<String, Object> values = new LinkedHashMap<>();

		if (!Files.isRegularFile(file)) {
			return values;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject json = GSON.fromJson(reader, JsonObject.class);

			if (json == null) {
				return values;
			}

			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				JsonElement value = entry.getValue();

				if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
					values.put(entry.getKey(), value.getAsBoolean());
				} else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
					values.put(entry.getKey(), value.getAsDouble());
				} else if (value.isJsonArray()) {
					List<Double> numbers = new ArrayList<>();
					value.getAsJsonArray().forEach(element -> numbers.add(element.getAsDouble()));
					values.put(entry.getKey(), numbers);
				}
			}
		} catch (IOException | RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("Couldn't read the shader settings {}", file, e);
		}

		return values;
	}

	private static void writeSettings(ShaderPack pack) {
		JsonObject json = new JsonObject();

		for (Map.Entry<String, Object> entry : pack.optionValues.entrySet()) {
			switch (entry.getValue()) {
				case Boolean bool -> json.add(entry.getKey(), new JsonPrimitive(bool));
				case Number number -> json.add(entry.getKey(), new JsonPrimitive(number));
				case List<?> list -> {
					JsonArray array = new JsonArray();
					list.forEach(element -> array.add(((Number) element).doubleValue()));
					json.add(entry.getKey(), array);
				}
				default -> {
				}
			}
		}

		Path file = settingsFile(pack.id);

		try {
			Files.createDirectories(file.getParent());

			if (json.isEmpty()) {
				Files.deleteIfExists(file);
				return;
			}

			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException e) {
			SkaffySAPIClient.LOGGER.warn("Couldn't save the shader settings {}", file, e);
		}
	}

	public static double[] optionValue(ShaderPack pack, ShaderModule.Option option) {
		double[] value = toNumbers(pack.optionValues.get(option.key()));
		return value != null && option.fits(value) ? value : option.defaults().clone();
	}

	private static double @Nullable [] toNumbers(@Nullable Object value) {
		return switch (value) {
			case Boolean bool -> new double[] {bool ? 1 : 0};
			case Number number -> new double[] {number.doubleValue()};
			case List<?> list -> list.stream().mapToDouble(element -> element instanceof Number number ? number.doubleValue() : 0).toArray();
			case null, default -> null;
		};
	}

	public static void setOption(ShaderPack pack, ShaderModule.Option option, double @Nullable [] value) {
		if (value == null || java.util.Arrays.equals(value, option.defaults())) {
			pack.optionValues.remove(option.key());
		} else if (option.isSwitch()) {
			pack.optionValues.put(option.key(), value[0] != 0);
		} else if (option.isColor()) {
			List<Double> list = new ArrayList<>();

			for (double component : value) {
				list.add(component);
			}

			pack.optionValues.put(option.key(), list);
		} else {
			pack.optionValues.put(option.key(), value[0]);
		}

		writeSettings(pack);

		if (option.constant()) {
			RECOMPILE_AT.put(pack, Util.getMillis() + RECOMPILE_DELAY);
		} else if (running == pack) {
			applyUniformOptions(pack);
		}
	}

	public static void resetOptions(ShaderPack pack) {
		boolean constants = pack.options.stream().anyMatch(option -> option.constant() && pack.optionValues.containsKey(option.key()));
		pack.optionValues.clear();
		writeSettings(pack);

		if (constants) {
			recompile(pack);
		} else if (running == pack) {
			applyUniformOptions(pack);
		}
	}


	public static void select(String id) {
		selected = id;
		ShaderOptions.playerPicked();
		ShaderPack pack = find(id);

		if (pack != null) {
			pack.runtimeErrors.clear();
		}

		Minecraft.getInstance().options.save();
		version++;
		update();
		ShaderOptions.sendState(true);
	}

	public static List<String> importFiles(List<Path> paths) {
		List<String> failed = new ArrayList<>();
		Path folder = folder();

		for (Path path : paths) {
			String name = path.getFileName().toString();
			boolean zip = Files.isRegularFile(path) && name.toLowerCase(Locale.ROOT).endsWith(".zip");

			if (!zip && !Files.isDirectory(path)) {
				failed.add(name);
				continue;
			}

			try {
				Files.createDirectories(folder);
				Path target = folder.resolve(freeName(folder, name, zip));

				if (zip) {
					Files.copy(path, target);
				} else {
					copyFolder(path, target);
				}
			} catch (IOException e) {
				SkaffySAPIClient.LOGGER.warn("Couldn't copy {} into the shaders folder", path, e);
				failed.add(name);
			}
		}

		rescan();
		return failed;
	}

	private static String freeName(Path folder, String name, boolean zip) {
		String base = zip ? name.substring(0, name.length() - 4) : name;
		String extension = zip ? name.substring(name.length() - 4) : "";
		String candidate = name;

		for (int i = 2; Files.exists(folder.resolve(candidate)) || candidate.equals("settings"); i++) {
			candidate = base + " (" + i + ")" + extension;
		}

		return candidate;
	}

	private static void copyFolder(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path source : walk.toList()) {
				Path target = to.resolve(from.relativize(source).toString());

				if (Files.isDirectory(source)) {
					Files.createDirectories(target);
				} else {
					Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}
	}


	static void update() {
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();

		if (!RECOMPILE_AT.isEmpty()) {
			for (Map.Entry<ShaderPack, Long> entry : List.copyOf(RECOMPILE_AT.entrySet())) {
				if (entry.getValue() <= now) {
					RECOMPILE_AT.remove(entry.getKey());
					recompile(entry.getKey());
				}
			}
		}

		if (minecraft.level != null) {
			lastLevel = now;
			scan();
		}

		String id = activeId();
		ShaderPack wanted = id != null ? find(id) : null;
		boolean hasWorld = lastLevel != 0 && now - lastLevel < KEEP_WITHOUT_LEVEL;

		if (wanted == null || !wanted.usable() || !hasWorld) {
			if (running != null && (wanted != running || !hasWorld || wanted.state == ShaderPack.State.FAILED)) {
				stopRunning();
			}

			return;
		}

		if (running == wanted && runningBuilt == wanted.built) {
			return;
		}

		if (minecraft.level == null) {
			return;
		}

		if (running != null && running != wanted) {
			stopRunning();
		}

		start(wanted);
	}

	private static void start(ShaderPack pack) {
		List<String> keys = new ArrayList<>();

		for (int i = 0; i < pack.effects.size(); i++) {
			String className = pack.effects.get(i);
			ShaderModule module = pack.modules.get(className);

			if (module != null) {
				String key = ShaderRenderer.CLIENT + className;
				ShaderRenderer.enableClient(key, Integer.MIN_VALUE + i, module, assets(pack));
				keys.add(key);
			}
		}

		for (String old : RUNNING_KEYS) {
			if (!keys.contains(old)) {
				ShaderRenderer.disable(old);
			}
		}

		RUNNING_KEYS.clear();
		RUNNING_KEYS.addAll(keys);
		running = pack;
		runningBuilt = pack.built;
		applyUniformOptions(pack);
		SkaffySAPIClient.LOGGER.info("Shader pack {} is on", pack.name);
	}

	private static void stopRunning() {
		RUNNING_KEYS.forEach(ShaderRenderer::disable);
		RUNNING_KEYS.clear();

		if (running != null) {
			SkaffySAPIClient.LOGGER.info("Shader pack {} is off", running.name);
		}

		running = null;
	}

	private static void applyUniformOptions(ShaderPack pack) {
		for (int i = 0; i < pack.effects.size(); i++) {
			String className = pack.effects.get(i);
			ShaderModule module = pack.modules.get(className);

			if (module == null) {
				continue;
			}

			Map<String, Object> values = new HashMap<>();

			for (ShaderModule.Option option : pack.options) {
				if (option.constant() || module.uniforms().stream().noneMatch(uniform -> uniform.name().equals(option.key()))) {
					continue;
				}

				List<Double> value = new ArrayList<>();

				for (double component : optionValue(pack, option)) {
					value.add(component);
				}

				values.put(option.key(), option.isSwitch() ? (Object) (value.getFirst() != 0) : value.size() == 1 ? (Object) value.getFirst() : value);
			}

			if (!values.isEmpty() && ShaderRenderer.isEnabled(ShaderRenderer.CLIENT + className)) {
				ShaderRenderer.setUniforms(ShaderRenderer.CLIENT + className, values, 0, Easing.LINEAR);
			}
		}
	}

	private static ShaderEffect.Assets assets(ShaderPack pack) {
		PackFiles files = pack.files;
		return id -> files == null ? null : files.resources().get(id);
	}

	static void report(String className, ErrorKind kind, String message) {
		SkaffySAPIClient.LOGGER.warn("[Shader pack {}] {} error in {}:\n{}", running != null ? running.name : "?", kind.name().toLowerCase(Locale.ROOT), className, message);
		ShaderPack pack = running;

		if (pack != null && pack.effects.contains(className) && pack.runtimeErrors.size() < MAX_RUNTIME_ERRORS) {
			pack.runtimeErrors.add(className.substring(className.lastIndexOf('.') + 1) + ": " + message);
			version++;
		}
	}

	public static void shutdown() {
		for (ShaderPack pack : PACKS) {
			pack.disable();
		}
	}
}
