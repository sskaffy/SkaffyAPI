package me.skaffy.client.block;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.pack.PackBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class BlockModels {
	private static final int MAX_PARENTS = 16;

	private final PackBuilder pack;
	private final String namespace;
	private final Map<String, String> exportedModels = new HashMap<>();
	private final Map<String, String> exportedTextures = new HashMap<>();

	BlockModels(PackBuilder pack) {
		this.pack = pack;
		this.namespace = pack.namespace();
	}

	VoxelShape shape(String modelAssetId) {
		JsonObject model = loadAsset(modelAssetId);

		for (int depth = 0; model != null && depth < MAX_PARENTS; depth++) {
			if (model.has("elements")) {
				return elementsShape(model.getAsJsonArray("elements"));
			}

			model = model.has("parent") ? loadVanillaOrAsset(model.get("parent").getAsString(), true) : null;
		}

		return Shapes.empty();
	}

	String exportModel(String modelAssetId, boolean translucent) {
		return export(true, modelAssetId, translucent, 0);
	}

	void addFile(Identifier path, String json) {
		pack.add(path, json);
	}

	private String export(boolean isAsset, String reference, boolean translucent, int depth) {
		String key = (isAsset ? "a:" : "v:") + reference + (translucent ? ":t" : "");
		String existing = exportedModels.get(key);

		if (existing != null) {
			return existing;
		}

		Identifier vanillaId = PackBuilder.vanillaId(reference);
		String path = "block/" + (translucent ? "t/" : "") + (isAsset ? "a/" + reference : "v/" + vanillaId.getNamespace() + "/" + vanillaId.getPath());
		String id = namespace + ":" + path;
		exportedModels.put(key, id);

		JsonObject model = isAsset ? loadAsset(reference) : loadVanilla("models", reference, ".json");

		if (model == null) {
			SkaffySAPIClient.LOGGER.warn("Server block model {} not found", reference);
			model = new JsonObject();
		}

		model = model.deepCopy();

		if (model.has("parent") && depth < MAX_PARENTS) {
			String parent = model.get("parent").getAsString();

			if (!parent.startsWith("builtin/")) {
				boolean parentIsAsset = isAsset && pack.hasAsset(parent);
				model.addProperty("parent", export(parentIsAsset, parent, translucent, depth + 1));
			}
		}

		if (model.has("textures") && model.get("textures").isJsonObject()) {
			JsonObject textures = model.getAsJsonObject("textures");

			for (Map.Entry<String, JsonElement> entry : textures.entrySet().stream().toList()) {
				textures.add(entry.getKey(), rewriteTexture(isAsset, entry.getValue(), translucent));
			}
		}

		pack.add(pack.id("models/" + path + ".json"), model.toString());
		return id;
	}

	private JsonElement rewriteTexture(boolean fromAsset, JsonElement value, boolean translucent) {
		String sprite = value.isJsonObject() ? value.getAsJsonObject().get("sprite").getAsString() : value.getAsString();

		if (sprite.startsWith("#")) {
			return value;
		}

		boolean isAsset = fromAsset && pack.hasAsset(sprite);
		String exported = exportTexture(isAsset, sprite);
		boolean forceTranslucent = translucent || value.isJsonObject() && value.getAsJsonObject().has("force_translucent") && value.getAsJsonObject().get("force_translucent").getAsBoolean();

		if (!forceTranslucent) {
			return new com.google.gson.JsonPrimitive(exported);
		}

		JsonObject object = new JsonObject();
		object.addProperty("sprite", exported);
		object.addProperty("force_translucent", true);
		return object;
	}

	private String exportTexture(boolean isAsset, String reference) {
		String key = (isAsset ? "a:" : "v:") + reference;
		String existing = exportedTextures.get(key);

		if (existing != null) {
			return existing;
		}

		Identifier vanillaId = PackBuilder.vanillaId(reference);
		String path = isAsset ? "block/a/" + reference : "block/v/" + vanillaId.getNamespace() + "/" + vanillaId.getPath();
		String id = namespace + ":" + path;
		exportedTextures.put(key, id);

		byte[] image = isAsset ? pack.readAsset(reference) : pack.readVanilla("textures", reference, ".png");
		byte[] animation = isAsset ? pack.readAsset(reference + ".mcmeta") : pack.readVanilla("textures", reference, ".png.mcmeta");

		if (image == null) {
			SkaffySAPIClient.LOGGER.warn("Server block texture {} not found", reference);
			return id;
		}

		pack.add(pack.id("textures/" + path + ".png"), image);

		if (animation != null) {
			pack.add(pack.id("textures/" + path + ".png.mcmeta"), animation);
		}

		return id;
	}

	private static VoxelShape elementsShape(JsonArray elements) {
		VoxelShape shape = Shapes.empty();

		for (JsonElement element : elements) {
			if (!element.isJsonObject() || !element.getAsJsonObject().has("from") || !element.getAsJsonObject().has("to")) {
				continue;
			}

			double[] from = vector(element.getAsJsonObject().getAsJsonArray("from"));
			double[] to = vector(element.getAsJsonObject().getAsJsonArray("to"));

			if (from == null || to == null) {
				continue;
			}

			VoxelShape box = Block.box(Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2]),
					Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2]));
			shape = Shapes.or(shape, box);
		}

		return shape.optimize();
	}

	private static double[] vector(JsonArray array) {
		if (array == null || array.size() != 3) {
			return null;
		}

		double[] vector = new double[3];

		for (int i = 0; i < 3; i++) {
			vector[i] = Math.clamp(array.get(i).getAsDouble(), -16, 32);
		}

		return vector;
	}

	private JsonObject loadVanillaOrAsset(String reference, boolean allowAsset) {
		if (allowAsset && pack.hasAsset(reference)) {
			return loadAsset(reference);
		}

		return loadVanilla("models", reference, ".json");
	}

	private JsonObject loadAsset(String id) {
		return parse(pack.readAsset(id), id);
	}

	private JsonObject loadVanilla(String folder, String reference, String extension) {
		return parse(pack.readVanilla(folder, reference, extension), reference);
	}

	private static JsonObject parse(byte[] data, String name) {
		if (data == null) {
			return null;
		}

		try {
			JsonElement json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
			return json.isJsonObject() ? json.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("Server block model {} is not valid JSON: {}", name, e.getMessage());
			return null;
		}
	}
}
