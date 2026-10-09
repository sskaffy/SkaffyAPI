package test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import me.skaffy.api.AssetRegistry;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.model.CustomEntityModel;
import me.skaffy.api.model.CustomEntityModels;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;

final class ModelTests {
	private static final Map<String, EntityType> SPAWN_TYPES = Map.of("bighead", EntityType.ZOMBIE, "robot", EntityType.PIG, "crystal", EntityType.INTERACTION);

	void register(SkaffyAPI api) {
		AssetRegistry assets = api.getAssets();
		CustomEntityModels models = api.getEntityModels();

		assets.register("test_bighead.geo.json", json(bigheadGeometry()));
		assets.register("test_bighead.png", SkaffyTest.png(bigheadTexture()));
		assets.register("test_bighead.animation.json", json(bigheadAnimations()));
		models.register(CustomEntityModel.builder("bighead", "test_bighead.geo.json", "test_bighead.png")
				.animation("test_bighead.animation.json", "animation.bighead.wave", true)
				.animation("test_bighead.animation.json", "animation.bighead.hurt")
				.hurt("animation.bighead.hurt")
				.build());

		assets.register("test_robot.geo.json", json(robotGeometry()));
		assets.register("test_robot.png", SkaffyTest.png(robotTexture(false)));
		assets.register("test_robot_glow.png", SkaffyTest.png(robotTexture(true)));
		assets.register("test_robot.animation.json", json(robotAnimations()));
		models.register(CustomEntityModel.builder("robot", "test_robot.geo.json", "test_robot.png")
				.emissiveTexture("test_robot_glow.png")
				.animation("test_robot.animation.json", "animation.robot.idle")
				.animation("test_robot.animation.json", "animation.robot.walk")
				.animation("test_robot.animation.json", "animation.robot.dance", true)
				.idle("animation.robot.idle")
				.walk("animation.robot.walk")
				.build());

		assets.register("test_crystal.geo.json", json(crystalGeometry()));
		assets.register("test_crystal.png", SkaffyTest.png(crystalTexture()));
		assets.register("test_crystal.animation.json", json(crystalAnimations()));
		models.register(CustomEntityModel.builder("crystal", "test_crystal.geo.json", "test_crystal.png")
				.translucent(true)
				.scale(1.5f)
				.animation("test_crystal.animation.json", "animation.crystal.float")
				.idle("animation.crystal.float")
				.build());
	}

	boolean command(Player player, String[] args) {
		CustomEntityModels models = SkaffyAPI.get().getEntityModels();
		String sub = args.length > 1 ? args[1] : "";

		switch (sub) {
			case "spawn" -> {
				CustomEntityModel model = args.length > 2 ? models.getModel(args[2]).orElse(null) : null;

				if (model == null) {
					return usage(player);
				}

				List<String> flags = List.of(args).subList(3, args.length);
				Location at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
				at.setYaw(player.getLocation().getYaw() + 180);
				Entity entity = player.getWorld().spawnEntity(at, SPAWN_TYPES.get(model.getName()));

				if (flags.contains("baby") && entity instanceof Ageable ageable) {
					ageable.setBaby();
				}

				if (entity instanceof Interaction interaction) {
					interaction.setInteractionWidth(1);
					interaction.setInteractionHeight(1.5f);
				}

				models.set(entity, model, flags.contains("save"));
				player.sendMessage(Component.text("Spawned a " + model.getName() + (flags.contains("save") ? " (saved on the entity)" : "")));
			}
			case "set", "personal" -> {
				Entity target = player.getTargetEntity(16);
				CustomEntityModel model = args.length > 2 ? models.getModel(args[2]).orElse(null) : null;

				if (target == null || model == null) {
					player.sendMessage(Component.text(target == null ? "Look at an entity" : "Unknown model"));
					return true;
				}

				if (sub.equals("set")) {
					models.set(target, model);
				} else {
					models.set(player, target, model);
				}

				player.sendMessage(Component.text("Gave " + target.getName() + " the model " + model.getName() + (sub.equals("personal") ? " (only you see it)" : "")));
			}
			case "anim" -> {
				Entity target = player.getTargetEntity(16);

				if (target == null || args.length < 3) {
					player.sendMessage(Component.text("Look at an entity with a model: /skaffytest model anim <wave|dance|stop>"));
					return true;
				}

				if (args[2].equals("stop")) {
					models.stopAnimations(target);
					player.sendMessage(Component.text("Stopped its animations"));
					return true;
				}

				String name = models.get(player, target).flatMap(model -> model.getAnimations().stream()
						.map(CustomEntityModel.ModelAnimation::name)
						.filter(animation -> animation.endsWith("." + args[2]))
						.findFirst()).orElse(null);

				if (name == null) {
					player.sendMessage(Component.text("Its model has no animation called " + args[2]));
					return true;
				}

				models.playAnimation(target, name);
				player.sendMessage(Component.text("Playing " + name));
			}
			case "clear" -> {
				Entity target = player.getTargetEntity(16);

				if (target == null) {
					player.sendMessage(Component.text("Look at an entity"));
					return true;
				}

				boolean removed = models.remove(target) | models.remove(player, target);
				player.sendMessage(Component.text(removed ? "Removed its model" : "It had no model"));
			}
			default -> {
				return usage(player);
			}
		}

		return true;
	}

	List<String> tabComplete(String[] args) {
		Stream<String> options = switch (args.length) {
			case 2 -> Stream.of("spawn", "set", "personal", "anim", "clear");
			case 3 -> switch (args[1]) {
				case "spawn", "set", "personal" -> Stream.of("bighead", "robot", "crystal");
				case "anim" -> Stream.of("wave", "dance", "stop");
				default -> Stream.empty();
			};
			default -> args[1].equals("spawn") ? Stream.of("baby", "save") : Stream.empty();
		};

		return options.filter(option -> option.startsWith(args[args.length - 1])).toList();
	}

	private static boolean usage(Player player) {
		player.sendMessage(Component.text("/skaffytest model spawn <bighead|robot|crystal> [baby] [save] | set <model> | personal <model> | anim <wave|dance|stop> | clear"));
		return true;
	}

	private static JsonObject bigheadGeometry() {
		return geometry("geometry.bighead", 128, 64,
				bone("body", null, v(0, 24, 0), cube(v(-4, 12, -2), v(8, 12, 4), 0, 24)),
				bone("head", null, v(0, 24, 0), cube(v(-6, 24, -6), v(12, 12, 12), 0, 0)),
				bone("hat", "head", v(0, 24, 0), cube(v(-8, 31, -8), v(16, 1, 16), 48, 0)),
				bone("right_arm", null, v(-5, 22, 0), cube(v(-8, 12, -2), v(4, 12, 4), 24, 24)),
				bone("left_arm", null, v(5, 22, 0), cube(v(4, 12, -2), v(4, 12, 4), 40, 24)),
				bone("right_leg", null, v(-1.9f, 12, 0), cube(v(-3.9f, 0, -2), v(4, 12, 4), 56, 24)),
				bone("left_leg", null, v(1.9f, 12, 0), cube(v(-0.1f, 0, -2), v(4, 12, 4), 72, 24)));
	}

	private static BufferedImage bigheadTexture() {
		BufferedImage image = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
		paintBox(image, 0, 0, 12, 12, 12, 0xFF6AA84F);
		fill(image, 12 + 2, 12 + 3, 3, 3, 0xFF111111);
		fill(image, 12 + 7, 12 + 3, 3, 3, 0xFF111111);
		fill(image, 12 + 3, 12 + 8, 6, 2, 0xFF7A1F1F);
		paintBox(image, 48, 0, 16, 1, 16, 0xFF8B5A2B);
		paintBox(image, 0, 24, 8, 12, 4, 0xFF2F5FA8);
		paintBox(image, 24, 24, 4, 12, 4, 0xFF6AA84F);
		paintBox(image, 40, 24, 4, 12, 4, 0xFF6AA84F);
		paintBox(image, 56, 24, 4, 12, 4, 0xFF3B2F6B);
		paintBox(image, 72, 24, 4, 12, 4, 0xFF3B2F6B);
		return image;
	}

	private static JsonObject bigheadAnimations() {
		JsonObject wave = animation(false, 1.2f);
		bones(wave).add("right_arm", channels("rotation", keyframes(0, v(0, 0, 0), 0.2f, v(0, 0, 150), 0.4f, v(0, 0, 110), 0.6f, v(0, 0, 150), 0.8f, v(0, 0, 110), 1.2f, v(0, 0, 0))));
		JsonObject hurt = animation(false, 0.5f);
		JsonObject headShake = new JsonObject();
		JsonArray rotation = new JsonArray();
		rotation.add(0);
		rotation.add("math.sin(q.anim_time * 1440) * 25 * (1 - q.anim_time * 2)");
		rotation.add(0);
		headShake.add("rotation", rotation);
		bones(hurt).add("head", headShake);
		return animations(Map.of("animation.bighead.wave", wave, "animation.bighead.hurt", hurt));
	}

	private static JsonObject robotGeometry() {
		JsonObject head = cube(v(-4, 10, -16), v(8, 8, 8), 0, 0);
		JsonObject faces = new JsonObject();
		String[] names = {"north", "south", "east", "west", "up", "down"};

		for (int i = 0; i < names.length; i++) {
			faces.add(names[i], face(i * 8, 24, 8, 8));
		}

		head.add("uv", faces);
		return geometry("geometry.robot", 64, 64,
				bone("body", null, v(0, 13, 0), cube(v(-5, 7, -8), v(10, 8, 16), 0, 0)),
				bone("head", null, v(0, 12, -8), head),
				bone("antenna", "head", v(0, 18, -12), cube(v(-0.5f, 18, -12.5f), v(1, 4, 1), 48, 24), cube(v(-1, 22, -13), v(2, 2, 2), 52, 24)),
				bone("right_hind_leg", null, v(-3, 6, 6), cube(v(-5, 0, 4), v(4, 6, 4), 0, 40)),
				bone("left_hind_leg", null, v(3, 6, 6), cube(v(1, 0, 4), v(4, 6, 4), 16, 40)),
				bone("right_front_leg", null, v(-3, 6, -5), cube(v(-5, 0, -7), v(4, 6, 4), 32, 40)),
				bone("left_front_leg", null, v(3, 6, -5), cube(v(1, 0, -7), v(4, 6, 4), 48, 40)));
	}

	private static BufferedImage robotTexture(boolean glow) {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

		if (glow) {
			fill(image, 1, 24 + 2, 2, 2, 0xFF40FFFF);
			fill(image, 5, 24 + 2, 2, 2, 0xFF40FFFF);
			fill(image, 52, 24, 8, 4, 0xFFFF3030);
			fill(image, 16, 16, 16, 1, 0xFF40FFFF);
			return image;
		}

		paintBox(image, 0, 0, 10, 8, 16, 0xFF9AA0A6);
		int[] faceColors = {0xFFC8CCD0, 0xFF80868B, 0xFFB0B4B8, 0xFFB0B4B8, 0xFFDADCE0, 0xFF5F6368};

		for (int i = 0; i < 6; i++) {
			fill(image, i * 8, 24, 8, 8, faceColors[i]);
		}

		fill(image, 1, 24 + 2, 2, 2, 0xFF202124);
		fill(image, 5, 24 + 2, 2, 2, 0xFF202124);
		fill(image, 32, 24 + 7, 8, 1, 0xFFE8710A);
		paintBox(image, 48, 24, 1, 4, 1, 0xFF5F6368);
		paintBox(image, 52, 24, 2, 2, 2, 0xFFD93025);

		for (int leg = 0; leg < 4; leg++) {
			paintBox(image, leg * 16, 40, 4, 6, 4, 0xFF5F6368);
		}

		return image;
	}

	private static JsonObject robotAnimations() {
		JsonObject idle = animation(true, 2);
		bones(idle).add("antenna", channels("rotation", molang("0", "0", "math.sin(q.anim_time * 180) * 15")));
		JsonObject walk = animation(true, 0.5f);
		bones(walk).add("antenna", channels("rotation", molang("math.sin(q.anim_time * 720) * 25", "0", "0")));
		bones(walk).add("body", channels("position", molang("0", "math.abs(math.sin(q.anim_time * 720)) * 0.5", "0")));
		JsonObject dance = animation(true, 1);
		bones(dance).add("head", channels("rotation", molang("0", "0", "math.sin(q.anim_time * 360) * 20")));
		bones(dance).add("body", channels("rotation", molang("0", "0", "-math.sin(q.anim_time * 360) * 10")));
		JsonObject antenna = channels("scale", molang("1", "1 + math.sin(q.anim_time * 720) * 0.4", "1"));
		bones(dance).add("antenna", antenna);
		return animations(Map.of("animation.robot.idle", idle, "animation.robot.walk", walk, "animation.robot.dance", dance));
	}

	private static JsonObject crystalGeometry() {
		JsonObject crystal = cube(v(-3, 5, -3), v(6, 6, 6), 0, 0);
		crystal.add("pivot", v(0, 8, 0));
		crystal.add("rotation", v(45, 0, 45));
		return geometry("geometry.crystal", 32, 32,
				bone("crystal", null, v(0, 8, 0), crystal),
				bone("base", null, v(0, 0, 0), cube(v(-4, 0, -4), v(8, 1, 8), 0, 12)));
	}

	private static BufferedImage crystalTexture() {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		paintBox(image, 0, 0, 6, 6, 6, 0xA0C040FF);
		paintBox(image, 0, 12, 8, 1, 8, 0xFF404048);
		return image;
	}

	private static JsonObject crystalAnimations() {
		JsonObject floating = animation(true, 4);
		JsonObject crystal = channels("position", molang("0", "math.sin(q.anim_time * 90) * 2", "0"));
		crystal.add("rotation", molang("0", "q.anim_time * 90", "0"));
		bones(floating).add("crystal", crystal);
		return animations(Map.of("animation.crystal.float", floating));
	}

	private static JsonObject geometry(String identifier, int width, int height, JsonObject... bones) {
		JsonObject description = new JsonObject();
		description.addProperty("identifier", identifier);
		description.addProperty("texture_width", width);
		description.addProperty("texture_height", height);
		JsonObject geometry = new JsonObject();
		geometry.add("description", description);
		JsonArray boneList = new JsonArray();

		for (JsonObject bone : bones) {
			boneList.add(bone);
		}

		geometry.add("bones", boneList);
		JsonArray geometries = new JsonArray();
		geometries.add(geometry);
		JsonObject file = new JsonObject();
		file.addProperty("format_version", "1.12.0");
		file.add("minecraft:geometry", geometries);
		return file;
	}

	private static JsonObject bone(String name, String parent, JsonArray pivot, JsonObject... cubes) {
		JsonObject bone = new JsonObject();
		bone.addProperty("name", name);

		if (parent != null) {
			bone.addProperty("parent", parent);
		}

		bone.add("pivot", pivot);
		JsonArray cubeList = new JsonArray();

		for (JsonObject cube : cubes) {
			cubeList.add(cube);
		}

		bone.add("cubes", cubeList);
		return bone;
	}

	private static JsonObject cube(JsonArray origin, JsonArray size, int u, int v) {
		JsonObject cube = new JsonObject();
		cube.add("origin", origin);
		cube.add("size", size);
		JsonArray uv = new JsonArray();
		uv.add(u);
		uv.add(v);
		cube.add("uv", uv);
		return cube;
	}

	private static JsonObject face(int u, int v, int width, int height) {
		JsonObject face = new JsonObject();
		JsonArray uv = new JsonArray();
		uv.add(u);
		uv.add(v);
		JsonArray size = new JsonArray();
		size.add(width);
		size.add(height);
		face.add("uv", uv);
		face.add("uv_size", size);
		return face;
	}

	private static JsonObject animation(boolean loop, float length) {
		JsonObject animation = new JsonObject();
		animation.addProperty("loop", loop);
		animation.addProperty("animation_length", length);
		animation.add("bones", new JsonObject());
		return animation;
	}

	private static JsonObject bones(JsonObject animation) {
		return animation.getAsJsonObject("bones");
	}

	private static JsonObject channels(String channel, JsonElement value) {
		JsonObject channels = new JsonObject();
		channels.add(channel, value);
		return channels;
	}

	private static JsonObject keyframes(Object... timesAndValues) {
		JsonObject keyframes = new JsonObject();

		for (int i = 0; i < timesAndValues.length; i += 2) {
			keyframes.add(String.valueOf(((Number) timesAndValues[i]).floatValue()), (JsonArray) timesAndValues[i + 1]);
		}

		return keyframes;
	}

	private static JsonArray molang(String x, String y, String z) {
		JsonArray array = new JsonArray();
		array.add(x);
		array.add(y);
		array.add(z);
		return array;
	}

	private static JsonObject animations(Map<String, JsonObject> animations) {
		JsonObject all = new JsonObject();
		animations.forEach(all::add);
		JsonObject file = new JsonObject();
		file.addProperty("format_version", "1.8.0");
		file.add("animations", all);
		return file;
	}

	private static JsonArray v(float x, float y, float z) {
		JsonArray array = new JsonArray();
		array.add(x);
		array.add(y);
		array.add(z);
		return array;
	}

	private static void paintBox(BufferedImage image, int u, int v, int width, int height, int depth, int color) {
		fill(image, u + depth, v, width, depth, shade(color, 1.2f));
		fill(image, u + depth + width, v, width, depth, shade(color, 0.6f));
		fill(image, u, v + depth, depth, height, shade(color, 0.85f));
		fill(image, u + depth, v + depth, width, height, color);
		fill(image, u + depth + width, v + depth, depth, height, shade(color, 0.85f));
		fill(image, u + depth * 2 + width, v + depth, width, height, shade(color, 0.7f));
	}

	private static void fill(BufferedImage image, int x, int y, int width, int height, int argb) {
		for (int i = x; i < x + width && i < image.getWidth(); i++) {
			for (int j = y; j < y + height && j < image.getHeight(); j++) {
				image.setRGB(i, j, argb);
			}
		}
	}

	private static int shade(int argb, float factor) {
		int alpha = argb >>> 24;
		int r = Math.min(255, Math.round((argb >> 16 & 0xFF) * factor));
		int g = Math.min(255, Math.round((argb >> 8 & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return alpha << 24 | r << 16 | g << 8 | b;
	}

	private static byte[] json(JsonObject json) {
		return json.toString().getBytes(StandardCharsets.UTF_8);
	}
}
