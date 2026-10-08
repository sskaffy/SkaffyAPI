package me.skaffy.paper.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class AnimationInfo {
	public enum Loop {
		ONCE,
		LOOP,
		HOLD
	}

	public record Marker(float time, String text) {
	}

	public record Clip(String name, float length, Loop loop, List<Marker> markers) {
	}

	private AnimationInfo() {
	}

	public static Map<String, Clip> read(byte[] data) {
		try {
			ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

			if (data.length >= 12 && bytes.getInt(0) == 0x46546C67) {
				int length = bytes.getInt(12);
				return gltf(JsonParser.parseString(new String(data, 20, length, StandardCharsets.UTF_8)).getAsJsonObject());
			}

			JsonObject json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();

			if (json.has("asset") && json.has("accessors") || json.has("asset") && json.has("animations") && json.get("animations").isJsonArray() && !json.has("meta")) {
				return gltf(json);
			}

			if (json.has("meta")) {
				return blockbench(json);
			}

			return bedrock(json);
		} catch (RuntimeException e) {
			return Map.of();
		}
	}

	private static Map<String, Clip> gltf(JsonObject json) {
		Map<String, Clip> clips = new LinkedHashMap<>();
		JsonArray accessors = json.has("accessors") ? json.getAsJsonArray("accessors") : new JsonArray();
		JsonArray animations = json.has("animations") ? json.getAsJsonArray("animations") : new JsonArray();

		for (int i = 0; i < animations.size(); i++) {
			JsonObject animation = animations.get(i).getAsJsonObject();
			String name = animation.has("name") && !animation.get("name").getAsString().isEmpty() ? animation.get("name").getAsString() : "animation" + i;
			float length = 0;

			for (JsonElement sampler : animation.has("samplers") ? animation.getAsJsonArray("samplers") : new JsonArray()) {
				int input = sampler.getAsJsonObject().get("input").getAsInt();

				if (input >= 0 && input < accessors.size()) {
					JsonObject accessor = accessors.get(input).getAsJsonObject();

					if (accessor.has("max") && !accessor.getAsJsonArray("max").isEmpty()) {
						length = Math.max(length, accessor.getAsJsonArray("max").get(0).getAsFloat());
					}
				}
			}

			clips.putIfAbsent(name, new Clip(name, length, Loop.LOOP, List.of()));
		}

		return clips;
	}

	private static Map<String, Clip> blockbench(JsonObject json) {
		Map<String, Clip> clips = new LinkedHashMap<>();

		for (JsonElement element : json.has("animations") ? json.getAsJsonArray("animations") : new JsonArray()) {
			JsonObject animation = element.getAsJsonObject();
			String name = animation.has("name") ? animation.get("name").getAsString() : "animation";
			Loop loop = switch (animation.has("loop") ? animation.get("loop").getAsString() : "once") {
				case "loop" -> Loop.LOOP;
				case "hold" -> Loop.HOLD;
				default -> Loop.ONCE;
			};
			List<Marker> markers = new ArrayList<>();

			if (animation.has("animators") && animation.get("animators").isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : animation.getAsJsonObject("animators").entrySet()) {
					JsonObject animator = entry.getValue().getAsJsonObject();

					if (!entry.getKey().equals("effects") && !"effect".equals(animator.has("type") ? animator.get("type").getAsString() : "")) {
						continue;
					}

					for (JsonElement keyframe : animator.has("keyframes") ? animator.getAsJsonArray("keyframes") : new JsonArray()) {
						JsonObject frame = keyframe.getAsJsonObject();

						if (!"timeline".equals(frame.has("channel") ? frame.get("channel").getAsString() : "")) {
							continue;
						}

						float time = frame.has("time") ? frame.get("time").getAsFloat() : 0;

						for (JsonElement point : frame.has("data_points") ? frame.getAsJsonArray("data_points") : new JsonArray()) {
							String script = point.getAsJsonObject().has("script") ? point.getAsJsonObject().get("script").getAsString().trim() : "";

							if (!script.isEmpty()) {
								markers.add(new Marker(time, script));
							}
						}
					}
				}
			}

			markers.sort(Comparator.comparingDouble(Marker::time));
			clips.putIfAbsent(name, new Clip(name, animation.has("length") ? animation.get("length").getAsFloat() : 0, loop, List.copyOf(markers)));
		}

		return clips;
	}

	private static Map<String, Clip> bedrock(JsonObject json) {
		Map<String, Clip> clips = new LinkedHashMap<>();

		if (!json.has("animations")) {
			return clips;
		}

		for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("animations").entrySet()) {
			JsonObject animation = entry.getValue().getAsJsonObject();
			Loop loop = Loop.ONCE;
			JsonElement loopJson = animation.get("loop");

			if (loopJson != null && loopJson.isJsonPrimitive()) {
				loop = loopJson.getAsJsonPrimitive().isBoolean() ? loopJson.getAsBoolean() ? Loop.LOOP : Loop.ONCE
						: "hold_on_last_frame".equals(loopJson.getAsString()) ? Loop.HOLD : Loop.ONCE;
			}

			List<Marker> markers = new ArrayList<>();
			float last = 0;

			if (animation.has("timeline") && animation.get("timeline").isJsonObject()) {
				for (Map.Entry<String, JsonElement> marker : animation.getAsJsonObject("timeline").entrySet()) {
					float time = Float.parseFloat(marker.getKey());
					last = Math.max(last, time);
					JsonElement value = marker.getValue();

					for (JsonElement line : value.isJsonArray() ? value.getAsJsonArray() : single(value)) {
						markers.add(new Marker(time, line.getAsString()));
					}
				}
			}

			if (animation.has("bones")) {
				last = Math.max(last, lastKeyframe(animation.getAsJsonObject("bones")));
			}

			markers.sort(Comparator.comparingDouble(Marker::time));
			float length = animation.has("animation_length") ? animation.get("animation_length").getAsFloat() : last;
			clips.put(entry.getKey(), new Clip(entry.getKey(), length, loop, List.copyOf(markers)));
		}

		return clips;
	}

	private static float lastKeyframe(JsonObject bones) {
		float last = 0;

		for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
			if (!bone.getValue().isJsonObject()) {
				continue;
			}

			for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
				if (channel.getValue().isJsonObject()) {
					for (String key : channel.getValue().getAsJsonObject().keySet()) {
						try {
							last = Math.max(last, Float.parseFloat(key));
						} catch (NumberFormatException ignored) {
						}
					}
				}
			}
		}

		return last;
	}

	private static JsonArray single(JsonElement element) {
		JsonArray array = new JsonArray();
		array.add(element);
		return array;
	}
}
