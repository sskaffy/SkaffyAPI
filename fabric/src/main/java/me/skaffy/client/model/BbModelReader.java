package me.skaffy.client.model;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.blaze3d.platform.NativeImage;

import me.skaffy.client.model.AnimationClip.BoneTrack;
import me.skaffy.client.model.AnimationClip.Channel;
import me.skaffy.client.model.AnimationClip.Effect;
import me.skaffy.client.model.AnimationClip.EffectKind;
import me.skaffy.client.model.AnimationClip.Keyframe;
import me.skaffy.client.model.AnimationClip.Kind;
import me.skaffy.client.model.AnimationClip.Lerp;
import me.skaffy.client.model.AnimationClip.Loop;
import me.skaffy.client.model.AnimationClip.Marker;
import me.skaffy.client.model.AnimationClip.MolangChannel;
import me.skaffy.client.model.ModelData.AlphaMode;
import me.skaffy.client.model.ModelData.Material;
import me.skaffy.client.model.ModelData.Sampling;
import me.skaffy.client.model.ModelData.SpecularKind;
import me.skaffy.client.model.ModelData.TextureAnimation;
import me.skaffy.client.model.ModelData.TextureData;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class BbModelReader {
	private static final String LOOSE_ROOT = "skaffy_root";

	private BbModelReader() {
	}

	public static boolean matches(JsonObject json) {
		return json.has("meta") && json.get("meta").isJsonObject() && (json.has("elements") || json.has("outliner") || json.has("cubes"));
	}

	public static ModelData read(JsonObject file, boolean translucent, List<String> warnings) {
		JsonObject meta = file.getAsJsonObject("meta");
		String version = meta.has("format_version") ? meta.get("format_version").getAsString() : meta.has("format") ? meta.get("format").getAsString() : "4.0";
		boolean newRotations = compareVersions(version, "5.0") >= 0;
		boolean boxUv = meta.has("box_uv") && meta.get("box_uv").getAsBoolean();
		int resolutionWidth = 16;
		int resolutionHeight = 16;

		if (file.has("resolution") && file.get("resolution").isJsonObject()) {
			JsonObject resolution = file.getAsJsonObject("resolution");
			resolutionWidth = Math.max(1, (int) number(resolution, "width", 16));
			resolutionHeight = Math.max(1, (int) number(resolution, "height", 16));
		}

		JsonArray texturesJson = file.has("textures") && file.get("textures").isJsonArray() ? file.getAsJsonArray("textures") : new JsonArray();
		int textureCount = Math.max(1, texturesJson.size());
		int[][] uvSizes = new int[textureCount][];
		List<Material> materials = new ArrayList<>();
		Map<String, JsonObject> groupsOfTextures = new HashMap<>();

		if (file.has("texture_groups") && file.get("texture_groups").isJsonArray()) {
			for (JsonElement group : file.getAsJsonArray("texture_groups")) {
				if (group.isJsonObject() && group.getAsJsonObject().has("uuid")) {
					groupsOfTextures.put(group.getAsJsonObject().get("uuid").getAsString(), group.getAsJsonObject());
				}
			}
		}

		TextureData[] images = new TextureData[texturesJson.size()];

		for (int i = 0; i < texturesJson.size(); i++) {
			JsonObject texture = texturesJson.get(i).getAsJsonObject();
			uvSizes[i] = new int[] {(int) number(texture, "uv_width", resolutionWidth), (int) number(texture, "uv_height", resolutionHeight)};
			images[i] = image(texture, "t" + i, warnings);
		}

		if (texturesJson.isEmpty()) {
			uvSizes[0] = new int[] {resolutionWidth, resolutionHeight};
		}

		for (int i = 0; i < textureCount; i++) {
			JsonObject texture = i < texturesJson.size() ? texturesJson.get(i).getAsJsonObject() : new JsonObject();
			TextureData image = i < images.length ? images[i] : null;
			TextureData normal = null;
			TextureData mer = null;
			String group = string(texture, "group", "");

			if (!group.isEmpty() && groupsOfTextures.containsKey(group) && groupsOfTextures.get(group).has("is_material")
					&& groupsOfTextures.get(group).get("is_material").getAsBoolean()) {
				for (int j = 0; j < texturesJson.size(); j++) {
					JsonObject other = texturesJson.get(j).getAsJsonObject();

					if (j != i && group.equals(string(other, "group", ""))) {
						switch (string(other, "pbr_channel", "color")) {
							case "normal" -> normal = images[j];
							case "mer" -> mer = images[j];
							default -> {
							}
						}
					}
				}
			}

			String renderMode = string(texture, "render_mode", "default");
			boolean glows = renderMode.equals("emissive") || renderMode.equals("additive");
			AlphaMode alpha = image == null || !hasTransparency(image) ? AlphaMode.OPAQUE : translucent ? AlphaMode.BLEND : AlphaMode.MASK;
			materials.add(new Material(string(texture, "name", "texture" + i), image, -1, alpha, 0.1f, string(texture, "render_sides", "auto").equals("double"), false,
					null, new float[3], glows, normal, 1, mer == null ? SpecularKind.NONE : SpecularKind.MER, mer, 0, 1, new float[3], Sampling.PIXELS,
					image == null ? null : animation(texture, image, uvSizes[i])));
		}

		Map<String, JsonObject> elements = new LinkedHashMap<>();

		if (file.has("elements") && file.get("elements").isJsonArray()) {
			for (JsonElement element : file.getAsJsonArray("elements")) {
				if (element.isJsonObject() && element.getAsJsonObject().has("uuid")) {
					elements.put(element.getAsJsonObject().get("uuid").getAsString(), element.getAsJsonObject());
				}
			}
		}

		Map<String, JsonObject> groups = new HashMap<>();

		if (file.has("groups") && file.get("groups").isJsonArray()) {
			for (JsonElement group : file.getAsJsonArray("groups")) {
				if (group.isJsonObject() && group.getAsJsonObject().has("uuid")) {
					groups.put(group.getAsJsonObject().get("uuid").getAsString(), group.getAsJsonObject());
				}
			}
		}

		List<BedrockGeometry.Bone> bones = new ArrayList<>();
		Map<String, String> boneNamesByUuid = new HashMap<>();
		List<JsonObject> looseElements = new ArrayList<>();
		JsonArray outliner = file.has("outliner") && file.get("outliner").isJsonArray() ? file.getAsJsonArray("outliner") : new JsonArray();

		for (JsonElement node : outliner) {
			if (node.isJsonPrimitive()) {
				JsonObject element = elements.get(node.getAsString());

				if (element != null) {
					looseElements.add(element);
				}
			} else if (node.isJsonObject()) {
				group(node.getAsJsonObject(), null, groups, elements, bones, boneNamesByUuid, boxUv, warnings);
			}
		}

		if (outliner.isEmpty()) {
			looseElements.addAll(elements.values());
		}

		if (!looseElements.isEmpty()) {
			bones.add(bone(LOOSE_ROOT, null, new float[3], new float[3], looseElements, boxUv, warnings));
		}

		BedrockGeometry geometry = new BedrockGeometry(resolutionWidth, resolutionHeight, bones);
		GeometryMesher.Result meshed = GeometryMesher.mesh(geometry, uvSizes, texture -> Math.clamp(texture, 0, materials.size() - 1));
		Map<String, AnimationClip> animations = animations(file, boneNamesByUuid, newRotations, warnings);
		return new ModelData(meshed.bones(), meshed.locators(), List.copyOf(materials), meshed.meshes(), animations);
	}

	private static void group(JsonObject node, @Nullable String parent, Map<String, JsonObject> groups, Map<String, JsonObject> elements, List<BedrockGeometry.Bone> bones,
			Map<String, String> boneNamesByUuid, boolean boxUv, List<String> warnings) {
		String uuid = string(node, "uuid", "");
		JsonObject data = groups.getOrDefault(uuid, node);

		if (data.has("export") && !data.get("export").getAsBoolean()) {
			return;
		}

		String name = string(data, "name", "bone");
		float[] origin = vector(data, "origin", 0);
		float[] rotation = vector(data, "rotation", 0);
		List<JsonObject> contents = new ArrayList<>();
		List<JsonObject> children = new ArrayList<>();

		if (node.has("children") && node.get("children").isJsonArray()) {
			for (JsonElement child : node.getAsJsonArray("children")) {
				if (child.isJsonPrimitive()) {
					JsonObject element = elements.get(child.getAsString());

					if (element != null) {
						contents.add(element);
					}
				} else if (child.isJsonObject()) {
					children.add(child.getAsJsonObject());
				}
			}
		}

		boneNamesByUuid.put(uuid, name);
		bones.add(bone(name, parent, origin, rotation, contents, boxUv, warnings));

		for (JsonObject child : children) {
			group(child, name, groups, elements, bones, boneNamesByUuid, boxUv, warnings);
		}
	}

	private static BedrockGeometry.Bone bone(String name, @Nullable String parent, float[] origin, float[] rotation, List<JsonObject> contents, boolean boxUv,
			List<String> warnings) {
		float[] pivot = {-origin[0], origin[1], origin[2]};
		float[] boneRotation = {-rotation[0], -rotation[1], rotation[2]};
		List<BedrockGeometry.Cube> cubes = new ArrayList<>();
		List<BedrockGeometry.Locator> locators = new ArrayList<>();
		List<BedrockGeometry.Polygon> polygons = new ArrayList<>();

		for (JsonObject element : contents) {
			if (element.has("export") && !element.get("export").getAsBoolean()) {
				continue;
			}

			String type = string(element, "type", "cube");

			switch (type) {
				case "cube" -> cubes.add(cube(element, boxUv));
				case "mesh" -> mesh(element, polygons);
				case "locator", "null_object" -> {
					float[] position = vector(element, element.has("position") ? "position" : "from", 0);
					float[] locatorRotation = vector(element, "rotation", 0);
					locators.add(new BedrockGeometry.Locator(string(element, "name", "locator"), new float[] {-position[0], position[1], position[2]},
							new float[] {-locatorRotation[0], -locatorRotation[1], locatorRotation[2]}));
				}
				default -> warnings.add("Element type " + type + " isn't supported, left out");
			}
		}

		return new BedrockGeometry.Bone(name, parent, pivot, boneRotation, List.copyOf(cubes), List.copyOf(locators), List.copyOf(polygons));
	}

	private static BedrockGeometry.Cube cube(JsonObject element, boolean projectBoxUv) {
		float[] from = vector(element, "from", 0);
		float[] to = vector(element, "to", 0);
		float[] size = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
		float[] origin = {-(from[0] + size[0]), from[1], from[2]};
		float[] rotation = element.has("rotation") ? vector(element, "rotation", 0) : null;
		float[] pivot = null;

		if (rotation != null) {
			float[] cubeOrigin = vector(element, "origin", 0);
			pivot = new float[] {-cubeOrigin[0], cubeOrigin[1], cubeOrigin[2]};
			rotation = new float[] {-rotation[0], -rotation[1], rotation[2]};
		}

		float inflate = (float) number(element, "inflate", 0);
		boolean boxUv = element.has("box_uv") ? element.get("box_uv").getAsBoolean() : projectBoxUv;
		boolean mirror = element.has("mirror_uv") && element.get("mirror_uv").getAsBoolean();
		JsonObject faces = element.has("faces") && element.get("faces").isJsonObject() ? element.getAsJsonObject("faces") : new JsonObject();

		if (boxUv) {
			float[] offset = vector(element, "uv_offset", 0, 2);
			int texture = 0;

			for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
				int index = faceTexture(face.getValue());

				if (index >= 0) {
					texture = index;
					break;
				}
			}

			return new BedrockGeometry.Cube(origin, size, pivot, rotation, inflate, mirror, new int[] {Math.round(offset[0]), Math.round(offset[1])}, null, texture);
		}

		Map<String, float[]> uvs = new LinkedHashMap<>();

		for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
			if (!face.getValue().isJsonObject()) {
				continue;
			}

			JsonObject json = face.getValue().getAsJsonObject();

			if (json.has("enabled") && !json.get("enabled").getAsBoolean() || json.has("texture") && json.get("texture").isJsonNull()) {
				continue;
			}

			float[] uv = vector(json, "uv", 0, 4);
			float u = uv[0];
			float v = uv[1];
			float width = uv[2] - uv[0];
			float height = uv[3] - uv[1];

			if (face.getKey().equals("up") || face.getKey().equals("down")) {
				u += width;
				v += height;
				width = -width;
				height = -height;
			}

			int texture = Math.max(0, faceTexture(json));
			uvs.put(face.getKey(), new float[] {u, v, width, height, (float) number(json, "rotation", 0), texture});
		}

		return new BedrockGeometry.Cube(origin, size, pivot, rotation, inflate, false, null, uvs, 0);
	}

	private static int faceTexture(JsonElement face) {
		if (!face.isJsonObject() || !face.getAsJsonObject().has("texture")) {
			return -1;
		}

		JsonElement texture = face.getAsJsonObject().get("texture");
		return texture.isJsonPrimitive() && texture.getAsJsonPrimitive().isNumber() ? texture.getAsInt() : -1;
	}

	private static void mesh(JsonObject element, List<BedrockGeometry.Polygon> out) {
		float[] origin = vector(element, "origin", 0);
		float[] rotation = vector(element, "rotation", 0);
		boolean smooth = "smooth".equals(string(element, "shading", "flat"));
		Matrix4f transform = new Matrix4f().translation(origin[0], origin[1], origin[2])
				.rotateZYX((float) Math.toRadians(rotation[2]), (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[0]));
		Map<String, Vector3f> vertices = new HashMap<>();

		if (element.has("vertices") && element.get("vertices").isJsonObject()) {
			for (Map.Entry<String, JsonElement> vertex : element.getAsJsonObject("vertices").entrySet()) {
				if (vertex.getValue().isJsonArray() && vertex.getValue().getAsJsonArray().size() >= 3) {
					JsonArray array = vertex.getValue().getAsJsonArray();
					vertices.put(vertex.getKey(), transform.transformPosition(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat(), new Vector3f()));
				}
			}
		}

		if (!element.has("faces") || !element.get("faces").isJsonObject()) {
			return;
		}

		record Face(List<String> keys, JsonObject uv, int texture, Vector3f normal) {
		}

		List<Face> faces = new ArrayList<>();
		Map<String, Vector3f> smoothNormals = new HashMap<>();

		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject("faces").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}

			JsonObject face = entry.getValue().getAsJsonObject();

			if (face.has("texture") && face.get("texture").isJsonNull() || !face.has("vertices")) {
				continue;
			}

			List<String> keys = new ArrayList<>();

			for (JsonElement key : face.getAsJsonArray("vertices")) {
				if (vertices.containsKey(key.getAsString())) {
					keys.add(key.getAsString());
				}
			}

			if (keys.size() < 3) {
				continue;
			}

			Vector3f a = vertices.get(keys.get(0));
			Vector3f normal = new Vector3f(vertices.get(keys.get(1))).sub(a).cross(new Vector3f(vertices.get(keys.get(2))).sub(a));

			if (normal.lengthSquared() < 1.0E-12f && keys.size() > 3) {
				normal = new Vector3f(vertices.get(keys.get(2))).sub(a).cross(new Vector3f(vertices.get(keys.get(3))).sub(a));
			}

			normal.normalize();

			if (!Float.isFinite(normal.x)) {
				normal.set(0, 1, 0);
			}

			JsonObject uv = face.has("uv") && face.get("uv").isJsonObject() ? face.getAsJsonObject("uv") : new JsonObject();
			faces.add(new Face(keys, uv, Math.max(0, faceTexture(face)), normal));

			for (String key : keys) {
				smoothNormals.computeIfAbsent(key, k -> new Vector3f()).add(normal);
			}
		}

		for (Face face : faces) {
			int corners = face.keys().size();
			float[] positions = new float[corners * 3];
			float[] uvs = new float[corners * 2];
			float[] normals = smooth ? new float[corners * 3] : null;

			for (int i = 0; i < corners; i++) {
				String key = face.keys().get(i);
				Vector3f position = vertices.get(key);
				positions[i * 3] = -position.x;
				positions[i * 3 + 1] = position.y;
				positions[i * 3 + 2] = position.z;

				if (face.uv().has(key) && face.uv().get(key).isJsonArray()) {
					JsonArray uv = face.uv().getAsJsonArray(key);
					uvs[i * 2] = uv.get(0).getAsFloat();
					uvs[i * 2 + 1] = uv.get(1).getAsFloat();
				}

				if (normals != null) {
					Vector3f normal = new Vector3f(smoothNormals.get(key)).normalize();
					normals[i * 3] = -normal.x;
					normals[i * 3 + 1] = normal.y;
					normals[i * 3 + 2] = normal.z;
				}
			}

			out.add(new BedrockGeometry.Polygon(positions, uvs, face.texture(), new float[] {-face.normal().x, face.normal().y, face.normal().z}, normals));
		}
	}

	private static @Nullable TextureData image(JsonObject texture, String key, List<String> warnings) {
		String source = string(texture, "source", "");
		int comma = source.indexOf(',');

		if (!source.startsWith("data:") || comma < 0) {
			warnings.add("Texture " + string(texture, "name", key) + " isn't saved inside the project (turn on Blockbench's embedded textures), it's left white");
			return null;
		}

		try {
			byte[] bytes = Base64.getDecoder().decode(source.substring(comma + 1).trim());
			return new TextureData(key, ImageDecoding.decode(bytes));
		} catch (Exception e) {
			warnings.add("Texture " + string(texture, "name", key) + " can't be read: " + e.getMessage());
			return null;
		}
	}

	private static boolean hasTransparency(TextureData texture) {
		NativeImage image = texture.image();

		if (image == null) {
			return false;
		}

		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getPixel(x, y) >>> 24) < 255) {
					return true;
				}
			}
		}

		return false;
	}

	private static @Nullable TextureAnimation animation(JsonObject texture, TextureData image, int[] uvSize) {
		int frameHeight = Math.max(1, Math.round(image.width() * (uvSize[1] / (float) Math.max(1, uvSize[0]))));
		int frames = image.height() / frameHeight;

		if (frames <= 1 || image.height() % frameHeight != 0) {
			return null;
		}

		int frameTime = Math.max(1, (int) number(texture, "frame_time", 1));
		int[] order = switch (string(texture, "frame_order_type", "loop")) {
			case "backwards" -> java.util.stream.IntStream.range(0, frames).map(i -> frames - 1 - i).toArray();
			case "back_and_forth" -> java.util.stream.IntStream.concat(java.util.stream.IntStream.range(0, frames), java.util.stream.IntStream.range(1, frames - 1).map(i -> frames - 1 - i))
					.toArray();
			case "custom" -> {
				List<Integer> custom = new ArrayList<>();

				for (String part : string(texture, "frame_order", "").replaceAll("[\\[\\],]", " ").trim().split("\\s+")) {
					try {
						custom.add(Math.clamp(Integer.parseInt(part), 0, frames - 1));
					} catch (NumberFormatException ignored) {
					}
				}

				yield custom.isEmpty() ? java.util.stream.IntStream.range(0, frames).toArray() : custom.stream().mapToInt(Integer::intValue).toArray();
			}
			default -> java.util.stream.IntStream.range(0, frames).toArray();
		};

		return new TextureAnimation(frames, frameTime, order, texture.has("frame_interpolate") && texture.get("frame_interpolate").getAsBoolean());
	}

	private static Map<String, AnimationClip> animations(JsonObject file, Map<String, String> boneNamesByUuid, boolean newRotations, List<String> warnings) {
		Map<String, AnimationClip> animations = new LinkedHashMap<>();

		if (!file.has("animations") || !file.get("animations").isJsonArray()) {
			return animations;
		}

		for (JsonElement element : file.getAsJsonArray("animations")) {
			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject json = element.getAsJsonObject();
			String name = string(json, "name", "animation");
			Loop loop = switch (string(json, "loop", "once")) {
				case "loop" -> Loop.LOOP;
				case "hold" -> Loop.HOLD;
				default -> Loop.ONCE;
			};
			List<BoneTrack> bones = new ArrayList<>();
			List<Effect> effects = new ArrayList<>();
			List<Marker> markers = new ArrayList<>();
			float lastKeyframe = 0;

			if (json.has("animators") && json.get("animators").isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("animators").entrySet()) {
					if (!entry.getValue().isJsonObject()) {
						continue;
					}

					JsonObject animator = entry.getValue().getAsJsonObject();
					JsonArray keyframes = animator.has("keyframes") && animator.get("keyframes").isJsonArray() ? animator.getAsJsonArray("keyframes") : new JsonArray();
					String type = string(animator, "type", "bone");

					if (type.equals("effect") || entry.getKey().equals("effects")) {
						for (JsonElement keyframe : keyframes) {
							lastKeyframe = Math.max(lastKeyframe, effectKeyframe(keyframe.getAsJsonObject(), effects, markers));
						}

						continue;
					}

					if (!type.equals("bone")) {
						continue;
					}

					String bone = boneNamesByUuid.getOrDefault(entry.getKey(), string(animator, "name", entry.getKey()));
					Channel rotation = channel(keyframes, "rotation", Kind.ROTATION_ADD, newRotations, warnings);
					Channel position = channel(keyframes, "position", Kind.POSITION_ADD, newRotations, warnings);
					Channel scale = channel(keyframes, "scale", Kind.SCALE_MULTIPLY, newRotations, warnings);

					for (Channel channel : new Channel[] {rotation, position, scale}) {
						if (channel != null) {
							lastKeyframe = Math.max(lastKeyframe, channel.lastTime());
						}
					}

					if (rotation != null || position != null || scale != null) {
						bones.add(new BoneTrack(bone, rotation, position, scale));
					}
				}
			}

			effects.sort(Comparator.comparingDouble(Effect::time));
			markers.sort(Comparator.comparingDouble(Marker::time));
			float length = json.has("length") ? json.get("length").getAsFloat() : lastKeyframe;
			animations.put(name, new AnimationClip(name, Math.max(0, length), loop, json.has("override") && json.get("override").getAsBoolean(), List.copyOf(bones), List.of(),
					List.copyOf(effects), List.copyOf(markers)));
		}

		return animations;
	}

	private static float effectKeyframe(JsonObject keyframe, List<Effect> effects, List<Marker> markers) {
		float time = (float) number(keyframe, "time", 0);
		String channel = string(keyframe, "channel", "");
		JsonArray points = keyframe.has("data_points") && keyframe.get("data_points").isJsonArray() ? keyframe.getAsJsonArray("data_points") : new JsonArray();

		for (JsonElement element : points) {
			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject point = element.getAsJsonObject();

			switch (channel) {
				case "sound", "particle" -> {
					String effect = string(point, "effect", "");

					if (!effect.isEmpty()) {
						effects.add(new Effect(time, channel.equals("sound") ? EffectKind.SOUND : EffectKind.PARTICLE, effect, string(point, "locator", "")));
					}
				}
				case "timeline" -> {
					String script = string(point, "script", "").trim();

					if (!script.isEmpty()) {
						markers.add(new Marker(time, script));
					}
				}
				default -> {
				}
			}
		}

		return time;
	}

	private static @Nullable Channel channel(JsonArray keyframes, String name, Kind kind, boolean newRotations, List<String> warnings) {
		List<Keyframe> frames = new ArrayList<>();
		float fallback = kind == Kind.SCALE_MULTIPLY ? 1 : 0;
		boolean flipX = newRotations && (kind == Kind.ROTATION_ADD || kind == Kind.POSITION_ADD);
		boolean flipY = newRotations && kind == Kind.ROTATION_ADD;

		for (JsonElement element : keyframes) {
			if (!element.isJsonObject()) {
				continue;
			}

			JsonObject keyframe = element.getAsJsonObject();

			if (!name.equals(string(keyframe, "channel", ""))) {
				continue;
			}

			JsonArray points = keyframe.has("data_points") && keyframe.get("data_points").isJsonArray() ? keyframe.getAsJsonArray("data_points") : new JsonArray();

			if (points.isEmpty()) {
				continue;
			}

			Molang.Expression[] pre = point(points.get(0).getAsJsonObject(), fallback, flipX, flipY, warnings);
			Molang.Expression[] post = points.size() > 1 ? point(points.get(1).getAsJsonObject(), fallback, flipX, flipY, warnings) : pre;
			Lerp lerp = switch (string(keyframe, "interpolation", "linear").toLowerCase(Locale.ROOT)) {
				case "catmullrom" -> Lerp.CATMULLROM;
				case "step" -> Lerp.STEP;
				case "bezier" -> Lerp.BEZIER;
				default -> Lerp.LINEAR;
			};
			float[] leftTime = null;
			float[] leftValue = null;
			float[] rightTime = null;
			float[] rightValue = null;

			if (lerp == Lerp.BEZIER) {
				leftTime = vector(keyframe, "bezier_left_time", -0.1f);
				rightTime = vector(keyframe, "bezier_right_time", 0.1f);
				leftValue = flip(vector(keyframe, "bezier_left_value", 0), flipX, flipY);
				rightValue = flip(vector(keyframe, "bezier_right_value", 0), flipX, flipY);
			}

			frames.add(new Keyframe((float) number(keyframe, "time", 0), pre, post, lerp, leftTime, leftValue, rightTime, rightValue));
		}

		if (frames.isEmpty()) {
			return null;
		}

		frames.sort(Comparator.comparingDouble(Keyframe::time));
		return new MolangChannel(kind, List.copyOf(frames));
	}

	private static Molang.Expression[] point(JsonObject point, float fallback, boolean flipX, boolean flipY, List<String> warnings) {
		Molang.Expression[] vector = new Molang.Expression[3];
		String[] axes = {"x", "y", "z"};

		for (int i = 0; i < 3; i++) {
			Molang.Expression value = point.has(axes[i]) ? BedrockAnimation.value(point.get(axes[i]), fallback, warnings) : Molang.constant(fallback);
			vector[i] = i == 0 && flipX || i == 1 && flipY ? negate(value) : value;
		}

		return vector;
	}

	private static Molang.Expression negate(Molang.Expression expression) {
		return scope -> -expression.evaluate(scope);
	}

	private static float[] flip(float[] values, boolean flipX, boolean flipY) {
		if (flipX) {
			values[0] = -values[0];
		}

		if (flipY) {
			values[1] = -values[1];
		}

		return values;
	}

	static int compareVersions(String a, String b) {
		String[] left = a.split("\\.");
		String[] right = b.split("\\.");

		for (int i = 0; i < Math.max(left.length, right.length); i++) {
			int l = i < left.length ? parseInt(left[i]) : 0;
			int r = i < right.length ? parseInt(right[i]) : 0;

			if (l != r) {
				return Integer.compare(l, r);
			}
		}

		return 0;
	}

	private static int parseInt(String text) {
		try {
			return Integer.parseInt(text.replaceAll("\\D", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String string(JsonObject json, String key, String fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
	}

	private static double number(JsonObject json, String key, double fallback) {
		if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
			return fallback;
		}

		try {
			return json.get(key).getAsDouble();
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static float[] vector(JsonObject json, String key, float fallback) {
		return vector(json, key, fallback, 3);
	}

	private static float[] vector(JsonObject json, String key, float fallback, int length) {
		float[] vector = new float[length];
		JsonElement element = json.get(key);

		for (int i = 0; i < length; i++) {
			float value = fallback;

			if (element != null && element.isJsonArray() && element.getAsJsonArray().size() > i && element.getAsJsonArray().get(i).isJsonPrimitive()) {
				try {
					value = element.getAsJsonArray().get(i).getAsFloat();
				} catch (NumberFormatException ignored) {
				}
			}

			vector[i] = value;
		}

		return vector;
	}
}
