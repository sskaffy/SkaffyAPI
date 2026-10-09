package me.skaffy.client.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record BedrockGeometry(int textureWidth, int textureHeight, List<Bone> bones) {
	public record Bone(String name, String parent, float[] pivot, float[] rotation, List<Cube> cubes, List<Locator> locators, List<Polygon> polygons) {
		public Bone(String name, String parent, float[] pivot, float[] rotation, List<Cube> cubes) {
			this(name, parent, pivot, rotation, cubes, List.of(), List.of());
		}
	}

	public record Polygon(float[] positions, float[] uvs, int texture, float[] normal, float @org.jspecify.annotations.Nullable [] normals) {
	}

	public record Locator(String name, float[] offset, float[] rotation) {
	}

	public record Cube(float[] origin, float[] size, float[] pivot, float[] rotation, float inflate, boolean mirror, int[] boxUv, Map<String, float[]> faces, int boxTexture) {
		public Cube(float[] origin, float[] size, float[] pivot, float[] rotation, float inflate, boolean mirror, int[] boxUv, Map<String, float[]> faces) {
			this(origin, size, pivot, rotation, inflate, mirror, boxUv, faces, 0);
		}
	}

	public static BedrockGeometry parse(JsonObject file) {
		JsonObject geometry;
		int textureWidth = 64;
		int textureHeight = 64;

		if (file.has("minecraft:geometry")) {
			JsonArray geometries = file.getAsJsonArray("minecraft:geometry");

			if (geometries.isEmpty()) {
				throw new IllegalArgumentException("The file has no geometry");
			}

			geometry = geometries.get(0).getAsJsonObject();
			JsonObject description = geometry.has("description") ? geometry.getAsJsonObject("description") : new JsonObject();
			textureWidth = description.has("texture_width") ? description.get("texture_width").getAsInt() : textureWidth;
			textureHeight = description.has("texture_height") ? description.get("texture_height").getAsInt() : textureHeight;
		} else {
			geometry = file.entrySet().stream()
					.filter(entry -> entry.getKey().startsWith("geometry.") && entry.getValue().isJsonObject())
					.map(entry -> entry.getValue().getAsJsonObject())
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException("The file has no geometry"));
			textureWidth = geometry.has("texturewidth") ? geometry.get("texturewidth").getAsInt() : textureWidth;
			textureHeight = geometry.has("textureheight") ? geometry.get("textureheight").getAsInt() : textureHeight;
		}

		List<Bone> bones = new ArrayList<>();

		if (geometry.has("bones")) {
			for (JsonElement element : geometry.getAsJsonArray("bones")) {
				bones.add(bone(element.getAsJsonObject()));
			}
		}

		return new BedrockGeometry(Math.max(1, textureWidth), Math.max(1, textureHeight), List.copyOf(bones));
	}

	private static Bone bone(JsonObject json) {
		String name = json.get("name").getAsString();
		String parent = json.has("parent") ? json.get("parent").getAsString() : null;
		float[] pivot = vector(json, "pivot", 0);
		float[] rotation = vector(json, "rotation", 0);
		boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();
		float inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : 0;
		List<Cube> cubes = new ArrayList<>();

		if (json.has("cubes")) {
			for (JsonElement element : json.getAsJsonArray("cubes")) {
				cubes.add(cube(element.getAsJsonObject(), mirror, inflate));
			}
		}

		List<Locator> locators = new ArrayList<>();

		if (json.has("locators") && json.get("locators").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("locators").entrySet()) {
				JsonElement value = entry.getValue();

				if (value.isJsonArray()) {
					JsonObject holder = new JsonObject();
					holder.add("offset", value);
					value = holder;
				}

				if (value.isJsonObject()) {
					locators.add(new Locator(entry.getKey(), vector(value.getAsJsonObject(), "offset", 0), vector(value.getAsJsonObject(), "rotation", 0)));
				}
			}
		}

		return new Bone(name, parent, pivot, rotation, List.copyOf(cubes), List.copyOf(locators), List.of());
	}

	private static Cube cube(JsonObject json, boolean boneMirror, float boneInflate) {
		float[] origin = vector(json, "origin", 0);
		float[] size = vector(json, "size", 0);
		float[] rotation = json.has("rotation") ? vector(json, "rotation", 0) : null;
		float[] pivot = rotation != null ? vector(json, "pivot", 0) : null;
		float inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : boneInflate;
		boolean mirror = json.has("mirror") ? json.get("mirror").getAsBoolean() : boneMirror;
		int[] boxUv = null;
		Map<String, float[]> faces = null;
		JsonElement uv = json.get("uv");

		if (uv != null && uv.isJsonArray()) {
			boxUv = new int[] {Math.round(uv.getAsJsonArray().get(0).getAsFloat()), Math.round(uv.getAsJsonArray().get(1).getAsFloat())};
		} else if (uv != null && uv.isJsonObject()) {
			faces = new LinkedHashMap<>();

			for (Map.Entry<String, JsonElement> face : uv.getAsJsonObject().entrySet()) {
				JsonObject faceJson = face.getValue().getAsJsonObject();
				float[] start = vector(faceJson, "uv", 0, 2);
				float[] faceSize = vector(faceJson, "uv_size", 0, 2);
				float uvRotation = faceJson.has("uv_rotation") ? faceJson.get("uv_rotation").getAsFloat() : 0;
				faces.put(face.getKey(), new float[] {start[0], start[1], faceSize[0], faceSize[1], uvRotation});
			}
		} else {
			boxUv = new int[] {0, 0};
		}

		return new Cube(origin, size, pivot, rotation, inflate, mirror, boxUv, faces);
	}

	private static float[] vector(JsonObject json, String key, float fallback) {
		return vector(json, key, fallback, 3);
	}

	private static float[] vector(JsonObject json, String key, float fallback, int length) {
		float[] vector = new float[length];
		JsonElement element = json.get(key);

		for (int i = 0; i < length; i++) {
			vector[i] = element != null && element.isJsonArray() && element.getAsJsonArray().size() > i ? element.getAsJsonArray().get(i).getAsFloat() : fallback;
		}

		return vector;
	}
}
