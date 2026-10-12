package me.skaffy.client.shader.pack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.skaffy.client.shader.lang.ShaderModule;

import org.jspecify.annotations.Nullable;

public final class ShaderPack {
	private static final Pattern MAIN = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+extends\\s+Shader\\b");

	public enum Kind {
		BUILTIN,
		ZIP,
		FOLDER
	}

	public enum State {
		LOADING,
		READY,
		FAILED
	}

	public record Link(String label, String url) {
	}

	public static final class LoadException extends Exception {
		public LoadException(String message) {
			super(message, null, false, false);
		}
	}

	public final String id;
	public final Kind kind;
	public final @Nullable Path path;
	public State state = State.LOADING;
	public final List<String> errors = new ArrayList<>();
	public final List<String> runtimeErrors = new ArrayList<>();

	public String name;
	public String description = "";
	public String license = "";
	public String author = "";
	public String version = "";
	public List<Link> links = List.of();
	public byte @Nullable [] icon;
	public @Nullable PackFiles files;
	public List<String> effects = List.of();
	public Map<String, ShaderModule> modules = Map.of();
	public List<ShaderModule.Option> options = List.of();
	public final Map<String, Object> optionValues = new LinkedHashMap<>();
	PackLibrary.@Nullable Loaded loaded;
	public int generation;
	public int built;

	public ShaderPack(String id, Kind kind, @Nullable Path path) {
		this.id = id;
		this.kind = kind;
		this.path = path;
		this.name = kind == Kind.ZIP && id.toLowerCase(java.util.Locale.ROOT).endsWith(".zip") ? id.substring(0, id.length() - 4) : id;
	}

	public PackFiles read() throws LoadException {
		try {
			return switch (kind) {
				case ZIP -> PackFiles.readZip(path);
				case FOLDER -> PackFiles.readFolder(path);
				case BUILTIN -> throw new IllegalStateException("Built-in packs are read by their owner");
			};
		} catch (PackFiles.PackException e) {
			throw new LoadException(e.getMessage());
		} catch (java.io.IOException e) {
			throw new LoadException("Couldn't read it: " + e.getMessage());
		}
	}

	public static PackLibrary.Loaded load(String pack, PackFiles files, boolean allowReserved) throws LoadException {
		if (!allowReserved && files.topPackage().equals("skaffy")) {
			throw new LoadException("shaders/skaffy/ belongs to the mod; name your pack's folder something else");
		}

		List<String> mains = new ArrayList<>();

		for (Map.Entry<String, String> source : files.sources().entrySet()) {
			Matcher matcher = MAIN.matcher(source.getValue());

			if (matcher.find()) {
				mains.add(source.getKey());
			}
		}

		if (mains.isEmpty()) {
			throw new LoadException("No main file: one .sfy file's class must extend Shader (class MyShader extends Shader { public void onEnable() { ... } })");
		}

		if (mains.size() > 1) {
			throw new LoadException("Only one class may extend Shader, these do: " + String.join(", ", mains));
		}

		return PackLibrary.load(pack, mains.getFirst(), files);
	}

	public void apply(PackLibrary.Loaded loaded) {
		PackLibrary.Builder builder = loaded.builder();
		this.loaded = loaded;
		this.files = builder.files;
		this.name = builder.name;
		this.description = builder.description;
		this.license = builder.license;
		this.author = builder.author;
		this.version = builder.version;
		this.links = List.copyOf(builder.links);
		this.effects = List.copyOf(builder.effects);
		this.icon = builder.icon != null ? builder.files.resources().get(builder.icon) : null;
	}

	public void disable() {
		PackLibrary.Loaded current = loaded;
		loaded = null;

		if (current != null) {
			PackLibrary.disable(id, current);
		}
	}

	public boolean usable() {
		return state == State.READY;
	}
}
