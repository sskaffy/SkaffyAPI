package me.skaffy.client.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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

public final class BedrockAnimation {
	private BedrockAnimation() {
	}

	public static Map<String, AnimationClip> parseFile(JsonObject file, List<String> warnings) {
		Map<String, AnimationClip> animations = new LinkedHashMap<>();

		if (!file.has("animations")) {
			return animations;
		}

		for (Map.Entry<String, JsonElement> entry : file.getAsJsonObject("animations").entrySet()) {
			if (entry.getValue().isJsonObject()) {
				animations.put(entry.getKey(), animation(entry.getKey(), entry.getValue().getAsJsonObject(), warnings));
			}
		}

		return animations;
	}

	private static AnimationClip animation(String name, JsonObject json, List<String> warnings) {
		Loop loop = Loop.ONCE;
		JsonElement loopJson = json.get("loop");

		if (loopJson != null && loopJson.isJsonPrimitive()) {
			if (loopJson.getAsJsonPrimitive().isBoolean()) {
				loop = loopJson.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
			} else if ("hold_on_last_frame".equals(loopJson.getAsString())) {
				loop = Loop.HOLD;
			} else if ("true".equals(loopJson.getAsString())) {
				loop = Loop.LOOP;
			}
		}

		boolean overridePrevious = json.has("override_previous_animation") && json.get("override_previous_animation").getAsBoolean();
		List<BoneTrack> bones = new ArrayList<>();
		float lastKeyframe = 0;

		if (json.has("bones")) {
			for (Map.Entry<String, JsonElement> bone : json.getAsJsonObject("bones").entrySet()) {
				if (!bone.getValue().isJsonObject()) {
					continue;
				}

				JsonObject channels = bone.getValue().getAsJsonObject();
				Channel rotation = channel(Kind.ROTATION_ADD, channels.get("rotation"), 0, warnings);
				Channel position = channel(Kind.POSITION_ADD, channels.get("position"), 0, warnings);
				Channel scale = channel(Kind.SCALE_MULTIPLY, channels.get("scale"), 1, warnings);

				for (Channel channel : new Channel[] {rotation, position, scale}) {
					if (channel != null) {
						lastKeyframe = Math.max(lastKeyframe, channel.lastTime());
					}
				}

				if (rotation != null || position != null || scale != null) {
					bones.add(new BoneTrack(bone.getKey(), rotation, position, scale));
				}
			}
		}

		List<Effect> effects = new ArrayList<>();
		effects(json.get("sound_effects"), EffectKind.SOUND, effects);
		effects(json.get("particle_effects"), EffectKind.PARTICLE, effects);
		effects.sort(Comparator.comparingDouble(Effect::time));
		List<Marker> markers = new ArrayList<>();

		if (json.has("timeline") && json.get("timeline").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("timeline").entrySet()) {
				Float time = time(entry.getKey());

				if (time == null) {
					continue;
				}

				for (JsonElement line : entry.getValue().isJsonArray() ? entry.getValue().getAsJsonArray() : single(entry.getValue())) {
					if (line.isJsonPrimitive()) {
						markers.add(new Marker(time, line.getAsString()));
					}
				}
			}
		}

		markers.sort(Comparator.comparingDouble(Marker::time));

		for (Effect effect : effects) {
			lastKeyframe = Math.max(lastKeyframe, effect.time());
		}

		for (Marker marker : markers) {
			lastKeyframe = Math.max(lastKeyframe, marker.time());
		}

		float length = json.has("animation_length") ? json.get("animation_length").getAsFloat() : lastKeyframe;
		return new AnimationClip(name, Math.max(0, length), loop, overridePrevious, List.copyOf(bones), List.of(), List.copyOf(effects), List.copyOf(markers));
	}

	private static void effects(JsonElement json, EffectKind kind, List<Effect> out) {
		if (json == null || !json.isJsonObject()) {
			return;
		}

		for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
			Float time = time(entry.getKey());

			if (time == null) {
				continue;
			}

			for (JsonElement element : entry.getValue().isJsonArray() ? entry.getValue().getAsJsonArray() : single(entry.getValue())) {
				if (element.isJsonObject() && element.getAsJsonObject().has("effect")) {
					JsonObject point = element.getAsJsonObject();
					String locator = point.has("locator") && point.get("locator").isJsonPrimitive() ? point.get("locator").getAsString() : "";
					out.add(new Effect(time, kind, point.get("effect").getAsString(), locator));
				}
			}
		}
	}

	private static JsonArray single(JsonElement element) {
		JsonArray array = new JsonArray();
		array.add(element);
		return array;
	}

	private static Float time(String key) {
		try {
			return Float.parseFloat(key);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Channel channel(Kind kind, JsonElement json, float fallback, List<String> warnings) {
		if (json == null || json.isJsonNull()) {
			return null;
		}

		List<Keyframe> keyframes = new ArrayList<>();

		if (json.isJsonObject() && !json.getAsJsonObject().has("vector") && !isKeyframeValue(json.getAsJsonObject())) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
				Float time = time(entry.getKey());

				if (time != null) {
					keyframes.add(keyframe(time, entry.getValue(), fallback, warnings));
				}
			}

			keyframes.sort(Comparator.comparingDouble(Keyframe::time));
		} else {
			keyframes.add(keyframe(0, json, fallback, warnings));
		}

		return keyframes.isEmpty() ? null : new MolangChannel(kind, List.copyOf(keyframes));
	}

	private static boolean isKeyframeValue(JsonObject json) {
		return json.has("pre") || json.has("post");
	}

	private static Keyframe keyframe(float time, JsonElement json, float fallback, List<String> warnings) {
		if (json.isJsonObject() && json.getAsJsonObject().has("vector")) {
			json = json.getAsJsonObject().get("vector");
		}

		if (json.isJsonObject()) {
			JsonObject object = json.getAsJsonObject();
			Molang.Expression[] post = object.has("post") ? vector(object.get("post"), fallback, warnings) : null;
			Molang.Expression[] pre = object.has("pre") ? vector(object.get("pre"), fallback, warnings) : post;
			post = post != null ? post : pre;
			Lerp lerp = object.has("lerp_mode") ? switch (object.get("lerp_mode").getAsString()) {
				case "catmullrom" -> Lerp.CATMULLROM;
				case "step" -> Lerp.STEP;
				default -> Lerp.LINEAR;
			} : Lerp.LINEAR;

			if (pre == null) {
				pre = vector(new JsonPrimitive(fallback), fallback, warnings);
				post = pre;
			}

			return new Keyframe(time, pre, post, lerp);
		}

		Molang.Expression[] value = vector(json, fallback, warnings);
		return new Keyframe(time, value, value, Lerp.LINEAR);
	}

	static Molang.Expression[] vector(JsonElement json, float fallback, List<String> warnings) {
		Molang.Expression[] vector = new Molang.Expression[3];

		if (json.isJsonArray()) {
			for (int i = 0; i < 3; i++) {
				vector[i] = i < json.getAsJsonArray().size() ? value(json.getAsJsonArray().get(i), fallback, warnings) : Molang.constant(fallback);
			}
		} else {
			Molang.Expression single = value(json, fallback, warnings);
			vector[0] = single;
			vector[1] = single;
			vector[2] = single;
		}

		return vector;
	}

	static Molang.Expression value(JsonElement json, float fallback, List<String> warnings) {
		if (!json.isJsonPrimitive()) {
			return Molang.constant(fallback);
		}

		JsonPrimitive primitive = json.getAsJsonPrimitive();

		if (primitive.isNumber()) {
			return Molang.constant(primitive.getAsFloat());
		}

		String text = primitive.getAsString().trim();

		if (text.isEmpty()) {
			return Molang.constant(fallback);
		}

		try {
			return Molang.parse(text);
		} catch (RuntimeException e) {
			warnings.add("'" + text + "': " + e.getMessage());
			return Molang.ZERO;
		}
	}
}
