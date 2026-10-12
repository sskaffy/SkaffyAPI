package me.skaffy.client.shader.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.Blaze3D;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.gui.ClientGuis;
import me.skaffy.client.gui.GuiBindings;
import me.skaffy.client.gui.GuiTextures;
import me.skaffy.client.gui.sfy.CompileException;
import me.skaffy.client.gui.sfy.ScriptException;
import me.skaffy.client.gui.sfy.SfyClass;
import me.skaffy.client.gui.sfy.SfyLibrary;
import me.skaffy.client.shader.ShaderOptions;
import me.skaffy.client.shader.ShaderPacks;
import me.skaffy.client.shader.lang.ShaderModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.packs.resources.IoSupplier;

import org.jspecify.annotations.Nullable;

public final class ShaderMenu {
	public static final String CLASS_NAME = "skaffy.menu.ShaderMenu";
	private static final String VANILLA = "";
	private static @Nullable SfyClass compiled;

	private ShaderMenu() {
	}

	public static void open(@Nullable Screen parent) {
		SfyClass cls = compiled();

		if (cls != null) {
			ShaderPacks.rescan();
			ClientGuis.openLocal(cls, "packs", new Object[0], parent);
		}
	}

	public static void close() {
		if (ClientGuis.localOpen(CLASS_NAME)) {
			ClientGuis.closeLocal();
		}
	}

	private static @Nullable SfyClass compiled() {
		if (compiled != null) {
			return compiled;
		}

		try (InputStream stream = ShaderMenu.class.getResourceAsStream("/skaffy_guis/skaffy/menu/ShaderMenu.sfy")) {
			if (stream == null) {
				SkaffySAPIClient.LOGGER.error("The Shader Packs menu is missing from the mod");
				return null;
			}

			compiled = SfyClass.compile(GuiBindings.create(ShaderMenu::functions), CLASS_NAME, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			return compiled;
		} catch (IOException e) {
			SkaffySAPIClient.LOGGER.error("Couldn't read the Shader Packs menu", e);
		} catch (CompileException e) {
			SkaffySAPIClient.LOGGER.error("The Shader Packs menu doesn't compile:\n{}", e.describe());
		}

		return null;
	}


	private static void functions(SfyLibrary lib) {
		lib.function("shaderPacks", "List<String>", "", (self, a) -> {
			List<Object> ids = new ArrayList<>();
			ids.add(VANILLA);
			ShaderPacks.packs().forEach(pack -> ids.add(pack.id));
			return ids;
		});
		lib.function("shaderSelected", "String", "", (self, a) -> {
			String selected = ShaderPacks.selected();
			return selected == null ? VANILLA : selected;
		});
		lib.function("selectShader", "void", "String id", (self, a) -> {
			ShaderPacks.select(text(a[0]));
			return null;
		});
		lib.function("shaderVersion", "int", "", (self, a) -> ShaderPacks.version() * 31 + (ShaderOptions.clientShaderAllowed() ? 1 : 0));
		lib.function("shadersAllowed", "boolean", "", (self, a) -> ShaderOptions.clientShaderAllowed());
		lib.function("shaderInfo", "Map<String, String>", "String id", (self, a) -> info(text(a[0])));
		lib.function("shaderLinkLabels", "List<String>", "String id", (self, a) -> {
			ShaderPack pack = ShaderPacks.find(text(a[0]));
			return pack == null ? new ArrayList<>() : new ArrayList<Object>(pack.links.stream().map(ShaderPack.Link::label).toList());
		});
		lib.function("shaderLinkUrls", "List<String>", "String id", (self, a) -> {
			ShaderPack pack = ShaderPacks.find(text(a[0]));
			return pack == null ? new ArrayList<>() : new ArrayList<Object>(pack.links.stream().map(ShaderPack.Link::url).toList());
		});
		lib.function("shaderProblems", "List<String>", "String id", (self, a) -> {
			ShaderPack pack = ShaderPacks.find(text(a[0]));
			List<Object> problems = new ArrayList<>();

			if (pack != null) {
				pack.errors.forEach(error -> problems.add(error.length() > 4000 ? error.substring(0, 4000) + "…" : error));
				problems.addAll(pack.runtimeErrors);
			}

			return problems;
		});
		lib.function("openShaderFolder", "void", "", (self, a) -> {
			Path folder = ShaderPacks.folder();

			try {
				java.nio.file.Files.createDirectories(folder);
			} catch (IOException e) {
				throw new ScriptException("Couldn't make " + folder);
			}

			Blaze3D.openPath(folder);
			return null;
		});
		lib.function("reloadShaders", "void", "", (self, a) -> {
			ShaderPacks.reloadAll();
			return null;
		});
		lib.function("addDroppedShaders", "List<String>", "", (self, a) -> new ArrayList<Object>(ShaderPacks.importFiles(ClientGuis.droppedFiles())));
		lib.function("lang", "String", "String key, Object... values", (self, a) -> I18n.get(text(a[0]), (Object[]) a[1]));

		lib.function("optionCount", "int", "String id", (self, a) -> options(text(a[0])).size());
		lib.function("optionInfo", "Map<String, String>", "String id, int index", (self, a) -> {
			ShaderModule.Option option = option(a);
			Map<String, Object> info = new LinkedHashMap<>();
			info.put("key", option.key());
			info.put("name", option.name());
			info.put("description", option.description());
			info.put("type", option.isSwitch() ? "switch" : option.isColor() ? "color" : option.choices().isEmpty() ? "slider" : "choice");
			info.put("integer", Boolean.toString(option.typeName().equals("int")));
			info.put("rebuilds", Boolean.toString(option.constant()));
			return info;
		});
		lib.function("optionMin", "double", "String id, int index", (self, a) -> option(a).min());
		lib.function("optionMax", "double", "String id, int index", (self, a) -> option(a).max());
		lib.function("optionStep", "double", "String id, int index", (self, a) -> option(a).step());
		lib.function("optionChoices", "List<Double>", "String id, int index", (self, a) -> new ArrayList<Object>(option(a).choices()));
		lib.function("optionValue", "List<Double>", "String id, int index", (self, a) -> {
			List<Object> value = new ArrayList<>();

			for (double component : ShaderPacks.optionValue(pack(a[0]), option(a))) {
				value.add(component);
			}

			return value;
		});
		lib.function("setOption", "void", "String id, int index, List<Double> value", (self, a) -> {
			List<?> list = (List<?>) a[2];
			double[] value = new double[list.size()];

			for (int i = 0; i < value.length; i++) {
				value[i] = ((Number) list.get(i)).doubleValue();
			}

			ShaderModule.Option option = option(a);

			if (!option.fits(value)) {
				throw new ScriptException("That value doesn't fit " + option.name());
			}

			ShaderPacks.setOption(pack(a[0]), option, value);
			return null;
		});
		lib.function("resetOptions", "void", "String id", (self, a) -> {
			ShaderPacks.resetOptions(pack(a[0]));
			return null;
		});
	}

	private static String text(Object value) {
		if (!(value instanceof String text)) {
			throw new ScriptException("Expected text, got null");
		}

		return text;
	}

	private static ShaderPack pack(Object id) {
		ShaderPack pack = ShaderPacks.find(text(id));

		if (pack == null) {
			throw new ScriptException("There's no shader pack " + id);
		}

		return pack;
	}

	private static List<ShaderModule.Option> options(String id) {
		ShaderPack pack = ShaderPacks.find(id);
		return pack == null ? List.of() : pack.options;
	}

	private static ShaderModule.Option option(Object[] args) {
		List<ShaderModule.Option> options = pack(args[0]).options;
		int index = (Integer) args[1];

		if (index < 0 || index >= options.size()) {
			throw new ScriptException("There's no option " + index);
		}

		return options.get(index);
	}

	private static Map<String, Object> info(String id) {
		Map<String, Object> info = new LinkedHashMap<>();

		if (id.equals(VANILLA)) {
			info.put("name", I18n.get("skaffys-api.shaders.vanilla"));
			info.put("description", I18n.get("skaffys-api.shaders.vanilla.description"));
			info.put("author", "Mojang Studios");
			info.put("version", "");
			info.put("license", "");
			info.put("kind", "vanilla");
			info.put("state", "ready");
			info.put("icon", vanillaIcon());
			info.put("settings", "false");
			return info;
		}

		ShaderPack pack = ShaderPacks.find(id);

		if (pack == null) {
			return info;
		}

		info.put("name", pack.name);
		info.put("description", pack.description);
		info.put("author", pack.author);
		info.put("version", pack.version);
		info.put("license", pack.license);
		info.put("kind", pack.kind.name().toLowerCase(Locale.ROOT));
		info.put("state", !pack.runtimeErrors.isEmpty() ? "failed" : pack.state.name().toLowerCase(Locale.ROOT));
		info.put("settings", Boolean.toString(!pack.options.isEmpty() && pack.state != ShaderPack.State.LOADING));

		if (pack.icon != null) {
			String key = "shader_pack/" + Integer.toHexString(id.hashCode()) + "_" + Integer.toHexString(java.util.Arrays.hashCode(pack.icon));
			GuiTextures.putLocal(key, pack.icon);
			info.put("icon", "@" + key);
		} else {
			info.put("icon", "minecraft:misc/unknown_pack");
		}

		return info;
	}

	private static String vanillaIcon() {
		try {
			IoSupplier<InputStream> icon = Minecraft.getInstance().getVanillaPackResources().fullResources().getRootResource("icons", "icon_128x128.png");

			if (icon != null) {
				try (InputStream stream = icon.get()) {
					GuiTextures.putLocal("vanilla_icon", stream.readAllBytes());
					return "@vanilla_icon";
				}
			}
		} catch (IOException e) {
			SkaffySAPIClient.LOGGER.warn("Couldn't read the game's icon", e);
		}

		return "minecraft:misc/unknown_pack";
	}
}
