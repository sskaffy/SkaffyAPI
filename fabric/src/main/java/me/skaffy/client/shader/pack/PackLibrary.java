package me.skaffy.client.shader.pack;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.gui.sfy.CompileException;
import me.skaffy.client.gui.sfy.ScriptException;
import me.skaffy.client.gui.sfy.SfyClass;
import me.skaffy.client.gui.sfy.SfyInstance;
import me.skaffy.client.gui.sfy.SfyLibrary;
import me.skaffy.client.gui.sfy.SfyMethod;

import org.jspecify.annotations.Nullable;

public final class PackLibrary {
	public static final int MAX_EFFECTS = 16;
	public static final int MAX_LINKS = 8;
	private static final SfyLibrary LIBRARY = create();
	private static final ThreadLocal<Builder> CURRENT = new ThreadLocal<>();

	public record ClassRef(String name) {
		@Override
		public String toString() {
			return name + ".class";
		}
	}

	public static final class Builder {
		final String pack;
		final String mainClass;
		final PackFiles files;
		final boolean enabling;
		@Nullable String name;
		@Nullable String icon;
		String description = "";
		String license = "";
		String author = "";
		String version = "";
		final List<ShaderPack.Link> links = new ArrayList<>();
		final List<String> effects = new ArrayList<>();

		Builder(String pack, String mainClass, PackFiles files, boolean enabling) {
			this.pack = pack;
			this.mainClass = mainClass;
			this.files = files;
			this.enabling = enabling;
		}
	}

	private PackLibrary() {
	}

	private static SfyLibrary create() {
		SfyLibrary lib = new SfyLibrary();
		lib.library("skaffy.shader");
		lib.extensibleType("Shader", Object.class);
		lib.valueType("Class", ClassRef.class);
		lib.onClassLiteral(ClassRef::new);
		lib.method("Shader", "name", "void", "String name", (self, args) -> {
			builder("name").name = text(args[0], "name", 64, false);
			return null;
		});
		lib.method("Shader", "description", "void", "String description", (self, args) -> {
			builder("description").description = text(args[0], "description", 4096, true);
			return null;
		});
		lib.method("Shader", "license", "void", "String license", (self, args) -> {
			builder("license").license = text(args[0], "license", 256, true);
			return null;
		});
		lib.method("Shader", "author", "void", "String author", (self, args) -> {
			builder("author").author = text(args[0], "author", 128, true);
			return null;
		});
		lib.method("Shader", "version", "void", "String version", (self, args) -> {
			builder("version").version = text(args[0], "version", 32, true);
			return null;
		});
		lib.method("Shader", "icon", "void", "String path", (self, args) -> {
			Builder builder = builder("icon");
			String path = text(args[0], "icon", 256, false);

			if (!builder.files.resources().containsKey(path)) {
				throw new ScriptException("icon(\"" + path + "\"): there's no resources/" + path + " (icons are PNGs in resources/)");
			}

			builder.icon = path;
			return null;
		});
		lib.method("Shader", "link", "void", "String label, String url", (self, args) -> {
			Builder builder = builder("link");
			String label = text(args[0], "the link's label", 32, false);
			String url = text(args[1], "the link", 512, false);

			if (builder.links.size() >= MAX_LINKS) {
				throw new ScriptException("A pack has at most " + MAX_LINKS + " links");
			}

			if (!url.startsWith("https://")) {
				throw new ScriptException("Links must start with https://, got " + url);
			}

			try {
				URI uri = new URI(url);

				if (uri.getHost() == null) {
					throw new ScriptException("Not a link: " + url);
				}
			} catch (java.net.URISyntaxException e) {
				throw new ScriptException("Not a link: " + url);
			}

			builder.links.add(new ShaderPack.Link(label, url));
			return null;
		});
		lib.method("Shader", "register", "void", "Class shader", (self, args) -> {
			Builder builder = builder("register");

			if (!(args[0] instanceof ClassRef ref)) {
				throw new ScriptException("register(...) takes a class, like register(World.class)");
			}

			if (ref.name().equals(builder.mainClass)) {
				throw new ScriptException("The main file can't register itself; register the shader classes it uses");
			}

			if (builder.effects.contains(ref.name())) {
				throw new ScriptException(ref.name() + " is already registered");
			}

			if (builder.effects.size() >= MAX_EFFECTS) {
				throw new ScriptException("A pack registers at most " + MAX_EFFECTS + " shader classes");
			}

			builder.effects.add(ref.name());
			return null;
		});
		lib.onLog((level, message) -> {
			Builder builder = CURRENT.get();
			String pack = builder != null ? builder.pack : "?";

			switch (level) {
				case "warn" -> SkaffySAPIClient.LOGGER.warn("[Shader pack {}] {}", pack, message);
				case "error" -> SkaffySAPIClient.LOGGER.error("[Shader pack {}] {}", pack, message);
				default -> SkaffySAPIClient.LOGGER.info("[Shader pack {}] {}", pack, message);
			}
		});
		return lib;
	}

	private static Builder builder(String method) {
		Builder builder = CURRENT.get();

		if (builder == null || !builder.enabling) {
			throw new ScriptException(method + "(...) only works in onEnable");
		}

		return builder;
	}

	private static String text(Object value, String what, int max, boolean blankOk) {
		if (!(value instanceof String text)) {
			throw new ScriptException(what + " can't be null");
		}

		if (!blankOk && text.isBlank()) {
			throw new ScriptException(what + " can't be empty");
		}

		if (text.length() > max) {
			throw new ScriptException(what + " is at most " + max + " characters");
		}

		return text;
	}

	public record Loaded(SfyClass cls, SfyInstance instance, Builder builder) {
		public List<String> effects() {
			return List.copyOf(builder.effects);
		}
	}

	static Loaded load(String pack, String mainClass, PackFiles files) throws ShaderPack.LoadException {
		SfyClass cls;

		try {
			cls = SfyClass.compile(LIBRARY, mainClass, files.sources().get(mainClass), Set.copyOf(files.sources().keySet()));
		} catch (CompileException e) {
			throw new ShaderPack.LoadException(e.describe());
		}

		if (!"Shader".equals(cls.superName())) {
			throw new ShaderPack.LoadException(cls.file() + ": the main file's class must extend Shader");
		}

		SfyMethod onEnable = cls.findMethod("onEnable", 0);

		if (onEnable == null || onEnable.isStatic()) {
			throw new ShaderPack.LoadException(cls.file() + ": the main file needs public void onEnable() { ... } (it names the pack and registers its shader classes)");
		}

		Builder builder = new Builder(pack, mainClass, files, true);
		CURRENT.set(builder);
		SfyInstance instance;

		try {
			instance = cls.newInstance();
			cls.invoke(instance, onEnable, new Object[0]);
		} catch (ScriptException e) {
			throw new ShaderPack.LoadException(cls.file() + ": onEnable failed: " + e.describe());
		} catch (RuntimeException | StackOverflowError e) {
			throw new ShaderPack.LoadException(cls.file() + ": onEnable failed: " + e);
		} finally {
			CURRENT.remove();
		}

		if (builder.name == null) {
			throw new ShaderPack.LoadException(cls.file() + ": onEnable must give the pack a name: name(\"My Shader\");");
		}

		if (builder.effects.isEmpty()) {
			throw new ShaderPack.LoadException(cls.file() + ": onEnable must register at least one shader class: register(World.class);");
		}

		return new Loaded(cls, instance, builder);
	}

	public static void disable(String pack, Loaded loaded) {
		SfyMethod onDisable = loaded.cls().findMethod("onDisable", 0);

		if (onDisable == null || onDisable.isStatic()) {
			return;
		}

		CURRENT.set(new Builder(pack, loaded.builder().mainClass, loaded.builder().files, false));

		try {
			loaded.cls().invoke(loaded.instance(), onDisable, new Object[0]);
		} catch (ScriptException e) {
			SkaffySAPIClient.LOGGER.warn("[Shader pack {}] onDisable failed: {}", pack, e.describe());
		} catch (RuntimeException | StackOverflowError e) {
			SkaffySAPIClient.LOGGER.warn("[Shader pack {}] onDisable failed", pack, e);
		} finally {
			CURRENT.remove();
		}
	}
}
