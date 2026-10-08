package test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import me.skaffy.api.AssetRegistry;
import me.skaffy.api.Easing;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.animation.AnimationEntity;
import me.skaffy.api.animation.AnimationPath;
import me.skaffy.api.animation.BoneSettings;
import me.skaffy.api.animation.ItemTransform;
import me.skaffy.api.animation.PlayOptions;
import me.skaffy.api.animation.SkaffyAnimations;
import me.skaffy.api.event.animation.SkaffyAnimationClickEvent;
import me.skaffy.api.event.animation.SkaffyAnimationEndEvent;
import me.skaffy.api.event.animation.SkaffyAnimationMarkerEvent;
import me.skaffy.api.model.AnimationMode;
import me.skaffy.api.model.CustomEntityModel;
import me.skaffy.api.model.CustomEntityModels;
import me.skaffy.api.perspective.CameraView;
import me.skaffy.api.perspective.Perspective;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

final class AnimationTests implements Listener {
	private static final Path IMPORT_FOLDER = Path.of("../../test-models");
	private static final List<String> SUBCOMMANDS = List.of("robot", "walk", "wave", "var", "bone", "look", "stop", "item", "bird", "glide", "turret", "boom",
			"backpack", "cutscene", "personal", "mob", "mobwave", "mobvar", "model", "many", "list", "clear");

	private final List<String> imported = new ArrayList<>();
	private JavaPlugin plugin;
	private CustomEntityModel robot;
	private CustomEntityModel turret;
	private CustomEntityModel explosion;
	private CustomEntityModel backpack;
	private CustomEntityModel camera;
	private CustomEntityModel bird;
	private CustomEntityModel robotMob;
	private CustomEntityModel birdMob;
	private int booms;

	void register(SkaffyAPI api, JavaPlugin plugin) {
		this.plugin = plugin;
		AssetRegistry assets = api.getAssets();
		CustomEntityModels models = api.getEntityModels();
		assets.register("test_anim_robot.bbmodel", AnimationModels.robot());
		assets.register("test_anim_turret.bbmodel", AnimationModels.turret());
		assets.register("test_anim_boom.bbmodel", AnimationModels.explosion());
		assets.register("test_anim_backpack.bbmodel", AnimationModels.backpack());
		assets.register("test_anim_camera.bbmodel", AnimationModels.cameraRig());
		assets.register("test_anim_bird.gltf", AnimationModels.bird());

		robot = register(models, model("anim_robot", "test_anim_robot.bbmodel", "idle", "walk", "wave"));
		turret = register(models, model("anim_turret", "test_anim_turret.bbmodel", "fire"));
		explosion = register(models, model("anim_boom", "test_anim_boom.bbmodel", "boom"));
		backpack = register(models, model("anim_backpack", "test_anim_backpack.bbmodel", "bounce"));
		camera = register(models, model("anim_camera", "test_anim_camera.bbmodel", "orbit"));
		bird = register(models, model("anim_bird", "test_anim_bird.gltf", "flap", "glide"));
		robotMob = register(models, model("anim_robot_mob", "test_anim_robot.bbmodel", "idle", "walk", "wave").idle("idle").walk("walk"));
		birdMob = register(models, model("anim_bird_mob", "test_anim_bird.gltf", "flap", "glide").idle("glide").walk("flap"));
		importModels(models);
	}

	private static CustomEntityModel.Builder model(String name, String asset, String... animations) {
		CustomEntityModel.Builder builder = CustomEntityModel.builder(name, asset);

		for (String animation : animations) {
			builder.animation(asset, animation);
		}

		return builder;
	}

	private static CustomEntityModel register(CustomEntityModels models, CustomEntityModel.Builder builder) {
		CustomEntityModel model = builder.build();
		models.register(model);
		return model;
	}

	private void importModels(CustomEntityModels models) {
		Path folder = IMPORT_FOLDER.toAbsolutePath().normalize();

		if (!Files.isDirectory(folder)) {
			plugin.getLogger().info("No test model folder at " + folder + ", nothing imported");
			return;
		}

		List<Path> sources;

		try (Stream<Path> files = Files.list(folder)) {
			sources = files.filter(file -> Files.isDirectory(file) || file.toString().matches("(?i).*\\.(zip|gltf|glb)$")).sorted().toList();
		} catch (IOException e) {
			plugin.getLogger().warning("Can't list " + folder + ": " + e.getMessage());
			return;
		}

		for (Path source : sources) {
			String name = importName(source.getFileName().toString());

			try {
				models.register(models.importModel(name, source).build());
				imported.add(name);
			} catch (IOException | RuntimeException e) {
				plugin.getLogger().warning("Couldn't import " + source.getFileName() + ": " + e.getMessage());
			}
		}
	}

	static String importName(String file) {
		String[] words = file.replaceAll("(?i)\\.(zip|gltf|glb)$", "").toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
		StringBuilder name = new StringBuilder();

		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}

			if (!name.isEmpty() && name.length() + 1 + word.length() > 24) {
				break;
			}

			name.append(name.isEmpty() ? "" : "_").append(word, 0, Math.min(word.length(), 24));
		}

		return name.isEmpty() ? "model" : name.toString();
	}

	boolean command(Player player, String[] args) {
		SkaffyAnimations animations = SkaffyAPI.get().getAnimations();
		CustomEntityModels models = SkaffyAPI.get().getEntityModels();
		String sub = args.length > 1 ? args[1] : "";

		if (!animations.isSupported(player)) {
			player.sendMessage(Component.text("Your client doesn't show animation entities (no mod, or an old one), so you won't see these"));
		}

		switch (sub) {
			case "robot" -> {
				AnimationEntity entity = animations.spawn("robot", robot, ahead(player, 3));
				entity.play("idle");
				entity.setHitbox(0.8f, 1.7f);
				entity.setShadowRadius(0.5f);
				entity.setItem("sword", "hand", new ItemStack(Material.DIAMOND_SWORD), "thirdperson_righthand", ItemTransform.NONE);
				entity.setBlock("lamp", "left_arm", Material.LANTERN.createBlockData(), ItemTransform.NONE.offset(-0.15, -0.8, -0.15).scale(0.3f));
				player.sendMessage(Component.text("Spawned the robot (idle): click it to wave. Try walk, wave, var speed 2, bone, look, item"));
			}
			case "walk" -> {
				AnimationEntity entity = robot(player);
				Block target = player.getTargetBlockExact(48);

				if (entity == null || target == null) {
					player.sendMessage(Component.text(entity == null ? "Spawn the robot first" : "Look at a block"));
					return true;
				}

				Location from = entity.getLocation();
				Location to = target.getLocation().add(0.5, 1, 0.5);
				Vector direction = to.toVector().subtract(from.toVector()).setY(0);
				float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
				int millis = (int) Math.max(300, direction.length() / 2.5 * 1000);
				from.setYaw(yaw);
				to.setYaw(yaw);
				to.setPitch(0);
				entity.teleport(from);
				entity.play("walk", PlayOptions.DEFAULT.fadeIn(0.25f));
				entity.moveTo(to, millis, Easing.LINEAR);
				Bukkit.getScheduler().runTaskLater(plugin, () -> entity.stop("walk", 0.3f), millis / 50L);
				player.sendMessage(Component.text("Walking there in " + millis + " ms"));
			}
			case "wave" -> withRobot(player, entity -> entity.play("wave", PlayOptions.DEFAULT.fadeIn(0.15f)), "Waving (a marker message comes at the end)");
			case "var" -> {
				if (args.length < 4) {
					player.sendMessage(Component.text("/skaffytest animation var <name> <value>, like var speed 2 (walk reads v.speed)"));
					return true;
				}

				float value = Float.parseFloat(args[3]);
				withRobot(player, entity -> entity.setVariable(args[2], value), "Set v." + args[2] + " = " + value);
			}
			case "bone" -> {
				String mode = args.length > 2 ? args[2] : "";
				BoneSettings settings = switch (mode) {
					case "hide" -> BoneSettings.NONE.hidden(true);
					case "tint" -> BoneSettings.NONE.tint(Color.fromRGB(255, 70, 70));
					case "grow" -> BoneSettings.NONE.scale(1.6);
					case "spin" -> BoneSettings.NONE.rotation(0, 180, 0);
					case "raise" -> BoneSettings.NONE.position(0, 4, 0);
					default -> null;
				};

				if (mode.equals("reset")) {
					withRobot(player, entity -> entity.resetBone("head"), "Head back to its animations");
				} else if (settings == null) {
					player.sendMessage(Component.text("/skaffytest animation bone <hide|tint|grow|spin|raise|reset> (the robot's head)"));
				} else {
					withRobot(player, entity -> entity.setBone("head", settings, 600, Easing.EASE_OUT_BACK), "Head: " + mode + " over 600 ms");
				}
			}
			case "look" -> {
				boolean off = args.length > 2 && args[2].equals("off");
				withRobot(player, entity -> {
					if (off) {
						entity.stopLooking("head");
					} else {
						entity.lookAt("head", player, 70, 35, 160);
					}
				}, off ? "Stopped looking" : "The robot's head follows you (70 degrees sideways, 35 up or down)");
			}
			case "stop" -> withRobot(player, entity -> entity.stopAll(0.5f), "Stopped its animations (0.5 s fade)");
			case "item" -> withRobot(player, entity -> {
				ItemStack held = player.getInventory().getItemInMainHand();

				if (held.getType().isAir()) {
					entity.removeItem("sword");
				} else {
					entity.setItem("sword", "hand", held, "thirdperson_righthand", ItemTransform.NONE);
				}
			}, "The robot holds what you hold (empty hand: nothing)");
			case "bird" -> {
				Location center = player.getLocation().add(0, 4, 0);
				center.setPitch(0);
				AnimationPath.Builder path = AnimationPath.builder().smooth(true).loop(true).faceAlong(true);

				for (int i = 0; i <= 12; i++) {
					double angle = i / 12.0 * Math.PI * 2;
					path.point(i * 900L, center.clone().add(Math.cos(angle) * 7, Math.sin(angle * 2) * 0.8, Math.sin(angle) * 7), i % 3 == 0 ? 0 : -20);
				}

				AnimationEntity entity = animations.spawn("bird", bird, center);
				entity.followPath(path.build());
				entity.play("flap");
				AnimationEntity rider = animations.spawn("rider", robot, center);
				rider.setScale(0.45f, 0, Easing.LINEAR);
				rider.attachTo(entity, "seat", new Vector(), 0, 0, 0);
				rider.play("wave", PlayOptions.DEFAULT.mode(AnimationMode.LOOP));
				player.sendMessage(Component.text("The bird circles above you with a robot riding it; /skaffytest animation glide crossfades to gliding"));
			}
			case "glide" -> {
				AnimationEntity entity = animations.get("bird").orElse(null);

				if (entity == null) {
					player.sendMessage(Component.text("Spawn the bird first"));
					return true;
				}

				boolean gliding = entity.getPlaying().contains("glide");
				entity.play(gliding ? "flap" : "glide", PlayOptions.DEFAULT.fadeIn(0.6f));
				entity.stop(gliding ? "glide" : "flap", 0.6f);
				player.sendMessage(Component.text(gliding ? "Flapping again" : "Gliding"));
			}
			case "turret" -> {
				AnimationEntity entity = animations.spawn("turret", turret, ahead(player, 5));
				entity.setHitbox(1, 1.1f);
				entity.setGlowColor(Color.YELLOW);
				entity.lookAt("head", player, 180, 40, 120);
				player.sendMessage(Component.text("The turret watches you; click it to make it fire"));
			}
			case "boom" -> {
				Block target = player.getTargetBlockExact(48);
				Location at = target != null ? target.getLocation().add(0.5, 1, 0.5) : ahead(player, 6);
				AnimationEntity entity = animations.spawn("boom_" + booms++, explosion, at);
				entity.setFullBright(true);
				entity.play("boom", PlayOptions.DEFAULT.removeWhenDone(true));
				player.sendMessage(Component.text("Boom (removes itself when done)"));
			}
			case "backpack" -> {
				String id = "backpack_" + player.getName().toLowerCase(Locale.ROOT);

				if (args.length > 2 && args[2].equals("off")) {
					animations.get(id).ifPresent(AnimationEntity::remove);
					player.sendMessage(Component.text("Backpack off"));
					return true;
				}

				boolean firstPerson = args.length > 2 && args[2].equals("visible");
				AnimationEntity entity = animations.spawn(id, backpack, player.getLocation());
				entity.attachTo(player, new Vector(0, 0.72, -0.27), 0, 0, 0, true);
				entity.setShowInFirstPerson(firstPerson);
				entity.play("bounce");
				player.sendMessage(Component.text("A backpack on your back (F5 to see it" + (firstPerson ? ", also shown in first person" : "") + "); backpack off removes it"));
			}
			case "cutscene" -> {
				Location at = player.getLocation();
				at.setPitch(0);
				AnimationEntity rig = animations.spawn(player, "cutscene", camera, at);
				rig.play("orbit");
				SkaffyAPI.get().getPerspective().setCamera(player, CameraView.at(0, 0, 0).mountOn(rig, "lens").lookAlongBone().build());
				player.sendMessage(Component.text("Cutscene: 8 seconds around you, ends at its \"end\" marker"));
			}
			case "personal" -> {
				AnimationEntity entity = animations.spawn(player, "mine", robot, ahead(player, 2));
				entity.play("wave", PlayOptions.DEFAULT.mode(AnimationMode.LOOP));
				entity.setHitbox(0.8f, 1.7f);
				player.sendMessage(Component.text("A robot only you see"));
			}
			case "mob" -> {
				Location at = ahead(player, 4);
				Zombie zombie = player.getWorld().spawn(at, Zombie.class, spawned -> spawned.setShouldBurnInDay(false));
				models.set(zombie, robotMob);
				Location side = at.clone().add(at.getDirection().getZ() * 2, 0, -at.getDirection().getX() * 2);
				Pig pig = player.getWorld().spawn(side, Pig.class);
				models.set(pig, birdMob);
				player.sendMessage(Component.text("A zombie with the robot (.bbmodel, automatic idle/walk) and a pig with the bird (glTF). Look at one: mobwave, mobvar speed 2"));
			}
			case "mobwave" -> {
				Entity target = player.getTargetEntity(16);

				if (target == null) {
					player.sendMessage(Component.text("Look at the zombie"));
					return true;
				}

				models.playAnimation(target, "wave", AnimationMode.DEFAULT, 1, 0.2f);
				player.sendMessage(Component.text("Waving (its marker fires too)"));
			}
			case "mobvar" -> {
				Entity target = player.getTargetEntity(16);

				if (target == null || args.length < 4) {
					player.sendMessage(Component.text("Look at the zombie: /skaffytest animation mobvar <name> <value>"));
					return true;
				}

				models.setVariable(target, args[2], Float.parseFloat(args[3]));
				player.sendMessage(Component.text("Set v." + args[2] + " on " + target.getName()));
			}
			case "model" -> {
				CustomEntityModel model = args.length > 2 ? models.getModel(args[2]).orElse(null) : null;

				if (model == null) {
					player.sendMessage(Component.text("/skaffytest animation model <name> [scale]; imported: " + (imported.isEmpty() ? "none" : String.join(", ", imported))
							+ "; any registered model works (robot, crystal, anim_robot, ...)"));
					return true;
				}

				float scale = args.length > 3 ? Float.parseFloat(args[3]) : 1;
				AnimationEntity entity = animations.spawn("model_" + model.getName(), model, ahead(player, 3));

				if (scale != 1) {
					entity.setScale(scale, 0, Easing.LINEAR);
				}

				if (!model.getAnimations().isEmpty()) {
					entity.play(model.getAnimations().getFirst().name(), PlayOptions.DEFAULT.mode(AnimationMode.LOOP));
				}

				player.sendMessage(Component.text("Placed " + model.getName() + (scale != 1 ? " at scale " + scale : "")
						+ (model.getAnimations().isEmpty() ? "" : ", playing " + model.getAnimations().getFirst().name())));
			}
			case "many" -> {
				int count = args.length > 2 ? Math.clamp(Integer.parseInt(args[2]), 1, 4000) : 100;
				int side = (int) Math.ceil(Math.sqrt(count));
				Location origin = player.getLocation();

				for (int i = 0; i < count; i++) {
					Location at = origin.clone().add((i % side - side / 2.0) * 2, 0, (i / side - side / 2.0) * 2);
					at.setYaw(i * 37 % 360);
					AnimationEntity entity = animations.spawn("many_" + i, robot, at);
					entity.play(i % 3 == 0 ? "walk" : "idle", PlayOptions.DEFAULT.startAt(i % 7 * 0.13f));
				}

				player.sendMessage(Component.text("Spawned " + count + " robots around you (clear removes them)"));
			}
			case "list" -> {
				List<String> lines = new ArrayList<>();
				animations.getAll().forEach(entity -> lines.add(describe(entity)));
				animations.getAll(player).forEach(entity -> lines.add(describe(entity) + " (only you)"));
				player.sendMessage(Component.text(lines.isEmpty() ? "No animation entities" : lines.size() + " animation entities:\n" + String.join("\n", lines.subList(0, Math.min(30, lines.size())))));
			}
			case "clear" -> {
				animations.removeAll("");
				animations.removeAll(player, "");
				player.sendMessage(Component.text("Removed every animation entity"));
			}
			default -> player.sendMessage(Component.text("/skaffytest animation <" + String.join("|", SUBCOMMANDS) + ">"));
		}

		return true;
	}

	List<String> tabComplete(String[] args) {
		Stream<String> options = switch (args.length) {
			case 2 -> SUBCOMMANDS.stream();
			case 3 -> switch (args[1]) {
				case "bone" -> Stream.of("hide", "tint", "grow", "spin", "raise", "reset");
				case "look" -> Stream.of("off");
				case "backpack" -> Stream.of("off", "visible");
				case "var", "mobvar" -> Stream.of("speed");
				case "model" -> SkaffyAPI.get().getEntityModels().getModels().stream().map(CustomEntityModel::getName);
				case "many" -> Stream.of("100", "1000");
				default -> Stream.empty();
			};
			default -> args[1].equals("model") ? Stream.of("0.1", "0.5", "1", "2") : Stream.empty();
		};

		return options.filter(option -> option.startsWith(args[args.length - 1])).toList();
	}

	private static AnimationEntity robot(Player player) {
		return SkaffyAPI.get().getAnimations().get("robot").orElse(null);
	}

	private static void withRobot(Player player, java.util.function.Consumer<AnimationEntity> action, String message) {
		AnimationEntity entity = robot(player);

		if (entity == null) {
			player.sendMessage(Component.text("Spawn the robot first: /skaffytest animation robot"));
			return;
		}

		action.accept(entity);
		player.sendMessage(Component.text(message));
	}

	private static Location ahead(Player player, double distance) {
		Location location = player.getLocation();
		Vector forward = location.getDirection().setY(0);

		if (forward.lengthSquared() < 1.0E-4) {
			forward = new Vector(0, 0, 1);
		}

		location.add(forward.normalize().multiply(distance));
		location.setYaw(player.getLocation().getYaw() + 180);
		location.setPitch(0);
		return location;
	}

	private static String describe(AnimationEntity entity) {
		Location at = entity.getLocation();
		return String.format(Locale.ROOT, "%s: %s at %.1f %.1f %.1f, playing %s, seen by %d", entity.getId(), entity.getModel().getName(), at.getX(), at.getY(), at.getZ(),
				entity.getPlaying(), entity.getSeenBy().size());
	}

	private void endCutscene(AnimationEntity rig) {
		Player viewer = rig.getViewer();

		if (viewer != null && viewer.isOnline()) {
			SkaffyAPI.get().getPerspective().set(viewer, Perspective.FIRST_PERSON, 400, Easing.EASE_IN_OUT_SINE);
		}

		rig.remove();
	}

	@EventHandler
	public void onClick(SkaffyAnimationClickEvent event) {
		AnimationEntity entity = event.getAnimationEntity();
		Vector at = event.getPosition();
		event.getPlayer().sendActionBar(Component.text(String.format(Locale.ROOT, "Clicked %s (%s, %s%s) at %.2f %.2f %.2f", entity.getId(), event.getClick(), event.getHand(),
				event.isSneaking() ? ", sneaking" : "", at.getX(), at.getY(), at.getZ())));

		switch (entity.getId()) {
			case "robot", "mine" -> entity.play("wave", PlayOptions.DEFAULT.fadeIn(0.15f));
			case "turret" -> {
				entity.play("fire");
				entity.setBone("head", BoneSettings.NONE.tint(Color.fromRGB(255, 60, 60)), 0, Easing.LINEAR);
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					if (!entity.isRemoved()) {
						entity.setBone("head", BoneSettings.NONE, 400, Easing.EASE_OUT_SINE);
					}
				}, 6);
			}
			default -> {
			}
		}
	}

	@EventHandler
	public void onMarker(SkaffyAnimationMarkerEvent event) {
		AnimationEntity entity = event.getAnimationEntity();

		if (entity != null && event.getMarker().equals("end") && entity.getViewer() != null) {
			endCutscene(entity);
			return;
		}

		Location at = entity != null ? entity.getLocation() : event.getEntity().getLocation();
		String who = entity != null ? entity.getId() : event.getEntity().getName();
		Component message = Component.text(String.format(Locale.ROOT, "Marker \"%s\" of %s on %s at %.2f s", event.getMarker(), event.getAnimation(), who, event.getTime()));

		if (entity != null && entity.getViewer() != null) {
			entity.getViewer().sendMessage(message);
		} else {
			at.getNearbyPlayers(64).forEach(player -> player.sendMessage(message));
		}
	}

	@EventHandler
	public void onEnd(SkaffyAnimationEndEvent event) {
		AnimationEntity entity = event.getAnimationEntity();

		if (entity.getId().startsWith("boom_") || entity.getId().equals("robot")) {
			Component message = Component.text(entity.getId() + " finished " + event.getAnimation() + (event.isRemoving() ? " and is removed" : ""));
			entity.getLocation().getNearbyPlayers(64).forEach(player -> player.sendMessage(message));
		}
	}
}
