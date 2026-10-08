package test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.blockshape.BlockShapes;
import me.skaffy.api.event.keybind.SkaffyKeyPressEvent;
import me.skaffy.api.event.keybind.SkaffyKeyReleaseEvent;
import me.skaffy.api.gui.HudPart;
import me.skaffy.api.gui.SkaffyGuis;
import me.skaffy.api.keybind.CustomKeybind;
import me.skaffy.api.keybind.CustomKeybinds;
import me.skaffy.api.perspective.CameraView;
import me.skaffy.api.perspective.Perspective;
import me.skaffy.api.shape.Shape;
import me.skaffy.api.shape.ShapePlacement;
import me.skaffy.api.shape.SkaffyShapes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class BuildTests implements Listener {
	static final String HUD = "skaffytest.BuildHud";
	private static final String[] PIECE_KEYS = {"build_wall", "build_floor", "build_ramp", "build_cone"};
	private static final List<String> ALWAYS_KEYS = List.of("build_wall", "build_floor", "build_ramp", "build_cone", "build_edit", "build_rotate");
	private static final List<String> MODE_KEYS = List.of("build_use", "build_alt");
	private static final double[] CAMERA = {-0.55, 1.8, -2.4};
	private static final double REACH = 5;
	private static final double EDIT_REACH = 6;
	private static final double SNAP_STEP = 0.2;
	private static final double HIT_DAMAGE = 50;
	private static final long HIT_COOLDOWN = 400;
	private static final int MATERIAL_COST = 10;
	private static final long HOLD_TO_REPEAT = 300;
	private static final Color PREVIEW = Color.fromARGB(0x5533AAFF);
	private static final Color PREVIEW_BLOCKED = Color.fromARGB(0x55FF4040);
	private static final Color CELL = Color.fromARGB(0x383399FF);
	private static final Color CELL_HOVER = Color.fromARGB(0x704FC3FF);
	private static final Color CELL_SELECTED = Color.fromARGB(0xB0FFFFFF);
	private static final Color OUTLINE = Color.fromARGB(0xA0BFE6FF);
	private static final Color OUTLINE_HOVER = Color.fromARGB(0xE0DDF3FF);
	private static final Color OUTLINE_SELECTED = Color.fromARGB(0xFFFFFFFF);

	private JavaPlugin plugin;
	private final Map<UUID, Builder> builders = new ConcurrentHashMap<>();
	private final Map<String, BuildPiece> pieces = new LinkedHashMap<>();
	private final Map<String, BuildPiece> slots = new HashMap<>();
	private final Map<UUID, Map<Long, Map<String, List<BoundingBox>>>> blocks = new HashMap<>();
	private final Map<UUID, Set<Long>> barriers = new HashMap<>();
	private final Map<UUID, Integer> gridY = new HashMap<>();
	private int nextId;

	private enum Mode {
		NONE,
		BUILD,
		EDIT
	}

	private static final class Builder {
		Mode mode = Mode.NONE;
		Mode beforeEdit = Mode.NONE;
		BuildPiece.Kind piece = BuildPiece.Kind.WALL;
		BuildPiece.Material material = BuildPiece.Material.WOOD;
		int rotation;
		boolean placeHeld;
		long placePressed;
		boolean camera = true;
		final int[] materials = {999, 999, 999};
		long lastHit;
		String previewKey = "";
		String targetKey = "";
		Edit edit;
	}

	private static final class Edit {
		final BuildPiece piece;
		final boolean[] selected;
		final List<Integer> path;
		boolean dragging;
		boolean adding;
		boolean reset;
		int hovered = -1;
		Vector lastDirection;
		String shownKey = "";

		Edit(BuildPiece piece) {
			this.piece = piece;
			this.selected = piece.removed.clone();
			this.path = new ArrayList<>(piece.path);
		}
	}

	static void registerKeybinds(CustomKeybinds keybinds) {
		String[][] binds = {
				{"build_wall", "Build: wall", "key.keyboard.q"},
				{"build_floor", "Build: floor", "key.keyboard.f"},
				{"build_ramp", "Build: ramp", "key.keyboard.c"},
				{"build_cone", "Build: cone", "key.keyboard.v"},
				{"build_edit", "Build: edit", "key.keyboard.g"},
				{"build_rotate", "Build: rotate", "key.keyboard.r"},
				{"build_use", "Build: place / select", "key.mouse.left"},
				{"build_alt", "Build: material / reset", "key.mouse.right"}};

		for (String[] bind : binds) {
			keybinds.register(CustomKeybind.builder(bind[0], bind[1]).defaultKey(bind[2]).active(false).winsOverVanilla(true).build());
		}
	}

	void register(JavaPlugin owner) {
		plugin = owner;
		Bukkit.getGlobalRegionScheduler().runAtFixedRate(owner, task -> tickAll(), 1, 1);
	}


	boolean command(Player player, String[] args) {
		String action = args.length > 1 ? args[1] : builders.containsKey(player.getUniqueId()) ? "off" : "on";

		switch (action) {
			case "on" -> {
				if (!SkaffyAPI.get().getShapes().isSupported(player)) {
					player.sendMessage(Component.text("Your client doesn't have Skaffy's API shapes", NamedTextColor.RED));
					return true;
				}

				start(player);
				player.sendMessage(Component.text("Building on: Q wall, F floor, C ramp, V cone, G edit, R rotate; left click places (hold to keep placing), "
						+ "right click switches material; 1-9 leave build mode. /skaffytest build off to stop.", NamedTextColor.AQUA));
			}
			case "off" -> {
				stop(player);
				player.sendMessage(Component.text("Building off"));
			}
			case "clear" -> {
				int count = pieces.size();
				clearAll();
				player.sendMessage(Component.text("Removed " + count + " pieces"));
			}
			case "camera" -> {
				Builder builder = builders.get(player.getUniqueId());

				if (builder == null) {
					player.sendMessage(Component.text("Turn building on first", NamedTextColor.RED));
					return true;
				}

				builder.camera = !builder.camera;
				camera(player, builder.camera);
				player.sendMessage(Component.text("Shoulder camera " + (builder.camera ? "on" : "off")));
			}
			case "grid" -> {
				gridY.put(player.getWorld().getUID(), (int) Math.floor(player.getLocation().getY()));
				player.sendMessage(Component.text("Floors of new pieces line up with your feet now (pieces already placed keep their grid)"));
			}
			default -> player.sendMessage(Component.text("/skaffytest build [on|off|clear|camera|grid]"));
		}

		return true;
	}

	List<String> tabComplete(String[] args) {
		return args.length == 2 ? List.of("on", "off", "clear", "camera", "grid").stream().filter(option -> option.startsWith(args[1])).toList() : List.of();
	}

	private void start(Player player) {
		Builder builder = new Builder();
		builders.put(player.getUniqueId(), builder);
		gridY.putIfAbsent(player.getWorld().getUID(), (int) Math.floor(player.getLocation().getY()));
		SkaffyAPI api = SkaffyAPI.get();
		CustomKeybinds keybinds = api.getKeybinds();

		for (String id : ALWAYS_KEYS) {
			keybinds.getKeybind(id).ifPresent(keybind -> keybinds.setActive(player, keybind, true));
		}

		SkaffyGuis guis = api.getGuis();
		guis.showHud(player, 10, HUD, "main");
		guis.setHudPart(player, HudPart.CROSSHAIR, false);
		hud(player, "setMaterials", builder.materials[0], builder.materials[1], builder.materials[2]);
		hud(player, "setMaterial", builder.material.ordinal());
		hud(player, "setPiece", builder.piece.ordinal());
		camera(player, true);
	}

	private void stop(Player player) {
		Builder builder = builders.remove(player.getUniqueId());

		if (builder == null) {
			return;
		}

		SkaffyAPI api = SkaffyAPI.get();
		CustomKeybinds keybinds = api.getKeybinds();

		for (String id : ALWAYS_KEYS) {
			keybinds.getKeybind(id).ifPresent(keybind -> keybinds.setActive(player, keybind, false));
		}

		for (String id : MODE_KEYS) {
			keybinds.getKeybind(id).ifPresent(keybind -> keybinds.setActive(player, keybind, false));
		}

		api.getGuis().hideHud(player, HUD);
		api.getGuis().resetHudParts(player);
		api.getShapes().hideAll(player, "build_");
		camera(player, false);
	}

	private static void camera(Player player, boolean on) {
		if (on) {
			SkaffyAPI.get().getPerspective().setCamera(player, CameraView.at(CAMERA[0], CAMERA[1], CAMERA[2]).turnWithPitch(true).lookWithPlayer().pullInFrontOfWalls(true).build());
		} else {
			SkaffyAPI.get().getPerspective().set(player, Perspective.FIRST_PERSON);
		}
	}

	private void hud(Player player, String method, Object... args) {
		SkaffyAPI.get().getGuis().run(player, HUD, method, args);
	}


	private void setMode(Player player, Builder builder, Mode mode) {
		if (builder.mode == mode) {
			return;
		}

		builder.mode = mode;
		builder.placeHeld = false;
		CustomKeybinds keybinds = SkaffyAPI.get().getKeybinds();

		for (String id : MODE_KEYS) {
			keybinds.getKeybind(id).ifPresent(keybind -> keybinds.setActive(player, keybind, mode != Mode.NONE));
		}

		SkaffyGuis guis = SkaffyAPI.get().getGuis();
		guis.setHudPart(player, HudPart.HOTBAR, mode == Mode.NONE);
		guis.setHudPart(player, HudPart.HELD_ITEM_NAME, mode == Mode.NONE);
		hud(player, "setMode", mode.name().toLowerCase());

		if (mode != Mode.BUILD) {
			SkaffyAPI.get().getShapes().hide(player, "build_preview");
			builder.previewKey = "";
		}

		if (mode != Mode.EDIT) {
			SkaffyAPI.get().getShapes().hide(player, "build_edit");
		}
	}

	@EventHandler
	public void onKeyPress(SkaffyKeyPressEvent event) {
		Player player = event.getPlayer();
		Builder builder = builders.get(player.getUniqueId());
		String id = event.getKeybind().getId();

		if (builder == null || !id.startsWith("build_")) {
			return;
		}

		for (int i = 0; i < PIECE_KEYS.length; i++) {
			if (id.equals(PIECE_KEYS[i])) {
				if (builder.mode == Mode.EDIT) {
					confirmEdit(player, builder);
				}

				builder.piece = BuildPiece.Kind.values()[i];
				hud(player, "setPiece", i);
				setMode(player, builder, Mode.BUILD);
				return;
			}
		}

		switch (id) {
			case "build_edit" -> {
				if (builder.mode == Mode.EDIT) {
					confirmEdit(player, builder);
				} else {
					startEdit(player, builder);
				}
			}
			case "build_rotate" -> {
				builder.rotation = (builder.rotation + 1) % 4;
				builder.previewKey = "";
			}
			case "build_use" -> {
				if (builder.mode == Mode.BUILD) {
					builder.placeHeld = true;
					builder.placePressed = System.currentTimeMillis();
					tryPlace(player, builder, choose(player, builder));
				} else if (builder.mode == Mode.EDIT) {
					beginDrag(player, builder);
				}
			}
			case "build_alt" -> {
				if (builder.mode == Mode.BUILD) {
					builder.material = BuildPiece.Material.values()[(builder.material.ordinal() + 1) % 3];
					hud(player, "setMaterial", builder.material.ordinal());
				} else if (builder.mode == Mode.EDIT) {
					resetEdit(player, builder);
				}
			}
			default -> {
			}
		}
	}

	@EventHandler
	public void onKeyRelease(SkaffyKeyReleaseEvent event) {
		Builder builder = builders.get(event.getPlayer().getUniqueId());

		if (builder == null || !event.getKeybind().getId().equals("build_use")) {
			return;
		}

		builder.placeHeld = false;

		if (builder.mode == Mode.EDIT && builder.edit != null && builder.edit.dragging) {
			follow(event.getPlayer(), builder, builder.edit);
			confirmEdit(event.getPlayer(), builder);
		}
	}

	@EventHandler
	public void onSlot(PlayerItemHeldEvent event) {
		Builder builder = builders.get(event.getPlayer().getUniqueId());

		if (builder != null && builder.mode != Mode.NONE) {
			if (builder.mode == Mode.EDIT) {
				confirmEdit(event.getPlayer(), builder);
			}

			setMode(event.getPlayer(), builder, Mode.NONE);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		builders.remove(event.getPlayer().getUniqueId());
	}


	private record Ray(Vector start, Vector direction) {
		Vector at(double distance) {
			return start.clone().add(direction.clone().multiply(distance));
		}
	}

	private static Vector cameraPosition(Player player, Vector direction) {
		Location feet = player.getLocation();
		double eye = player.getEyeHeight();
		double yaw = Math.atan2(direction.getX(), direction.getZ());
		double pitch = Math.asin(Math.clamp(direction.getY(), -1, 1));
		double x = CAMERA[0];
		double y = CAMERA[1] - eye;
		double z = CAMERA[2];
		double y1 = y * Math.cos(pitch) + z * Math.sin(pitch);
		double z1 = z * Math.cos(pitch) - y * Math.sin(pitch);
		double x2 = x * Math.cos(yaw) + z1 * Math.sin(yaw);
		double z2 = z1 * Math.cos(yaw) - x * Math.sin(yaw);
		return new Vector(feet.getX() + x2, feet.getY() + y1 + eye, feet.getZ() + z2);
	}

	private Ray ray(Player player, Builder builder) {
		return ray(player, builder, player.getEyeLocation().getDirection());
	}

	private Ray ray(Player player, Builder builder, Vector direction) {
		Vector eye = player.getEyeLocation().toVector();

		if (!builder.camera) {
			return new Ray(eye, direction);
		}

		Vector camera = cameraPosition(player, direction);
		double toEye = eye.clone().subtract(camera).dot(direction);
		return new Ray(camera.add(direction.clone().multiply(Math.max(0, toEye))), direction);
	}

	private boolean ours(Block block) {
		return block.getType() == Material.BARRIER && barriers.getOrDefault(block.getWorld().getUID(), Set.of()).contains(BuildPiece.key(block.getX(), block.getY(), block.getZ()));
	}

	private double terrainDistance(World world, Ray ray, double reach) {
		RayTraceResult hit = world.rayTraceBlocks(ray.start().toLocation(world), ray.direction(), reach, FluidCollisionMode.NEVER, true, block -> !ours(block));
		return hit == null ? reach : hit.getHitPosition().distance(ray.start());
	}

	private double hitDistance(World world, Ray ray, double reach) {
		double distance = terrainDistance(world, ray, reach);

		for (BuildPiece piece : pieces.values()) {
			if (piece.world != world || piece.center().distance(ray.start()) > distance + 3) {
				continue;
			}

			double[] hit = piece.hit(ray.start(), ray.direction(), distance, false);

			if (hit != null) {
				distance = Math.min(distance, hit[0]);
			}
		}

		return distance;
	}

	private int tileY(World world, double y) {
		return Math.floorDiv((int) Math.floor(y) - gridY.getOrDefault(world.getUID(), 0), BuildPiece.TILE);
	}

	private BuildPiece candidateAt(Player player, Builder builder, Vector point) {
		World world = player.getWorld();
		int tx = Math.floorDiv((int) Math.floor(point.getX()), BuildPiece.TILE);
		int tz = Math.floorDiv((int) Math.floor(point.getZ()), BuildPiece.TILE);
		int ty = tileY(world, point.getY());
		Location feet = player.getLocation();
		int px = Math.floorDiv(feet.getBlockX(), BuildPiece.TILE);
		int pz = Math.floorDiv(feet.getBlockZ(), BuildPiece.TILE);
		Vector look = feet.getDirection();
		int facing = Math.abs(look.getX()) > Math.abs(look.getZ()) ? look.getX() > 0 ? 3 : 1 : look.getZ() > 0 ? 0 : 2;
		BuildPiece.Kind kind = builder.piece;
		int axis = 0;
		int ptx = tx;
		int ptz = tz;

		if (kind == BuildPiece.Kind.WALL) {
			boolean own = tx == px && tz == pz;
			axis = facing == 1 || facing == 3 ? 0 : 2;

			switch (facing) {
				case 3 -> ptx = own ? px + 1 : tx;
				case 1 -> ptx = own ? px : tx + 1;
				case 0 -> ptz = own ? pz + 1 : tz;
				default -> ptz = own ? pz : tz + 1;
			}

			if (own) {
				if (axis == 0) {
					ptz = pz;
				} else {
					ptx = px;
				}
			}
		}

		int rampFacing = (facing + builder.rotation) % 4;
		BuildPiece piece = new BuildPiece("build/" + nextId, kind, world, ptx, ty, ptz, axis, rampFacing, player.getUniqueId(), builder.material);
		piece.baseY = gridY.getOrDefault(world.getUID(), 0);
		return piece;
	}

	private record Choice(BuildPiece piece, boolean valid) {
	}

	private Choice choose(Player player, Builder builder) {
		Ray ray = ray(player, builder);
		double end = Math.max(0, hitDistance(player.getWorld(), ray, REACH) - 0.05);
		BuildPiece aimed = null;
		Set<String> tried = new HashSet<>();

		for (double distance = end; ; distance -= SNAP_STEP) {
			double at = Math.max(0, distance);
			BuildPiece piece = candidateAt(player, builder, ray.at(at));

			if (tried.add(piece.slot()) && !slots.containsKey(piece.slot())) {
				if (aimed == null) {
					aimed = piece;
				}

				if (supported(piece)) {
					return new Choice(piece, true);
				}
			}

			if (at == 0) {
				break;
			}
		}

		return aimed == null ? null : new Choice(aimed, false);
	}

	private Object[] lookedAt(Player player, Builder builder, boolean grid) {
		Ray ray = ray(player, builder);
		World world = player.getWorld();
		double best = terrainDistance(world, ray, grid ? EDIT_REACH : REACH);
		Object[] found = null;

		for (BuildPiece piece : pieces.values()) {
			if (piece.world != world || piece.center().distance(ray.start()) > best + 3) {
				continue;
			}

			double[] hit = piece.hit(ray.start(), ray.direction(), best, grid);

			if (hit != null && hit[0] < best) {
				best = hit[0];
				found = new Object[] {piece, (int) hit[1]};
			}
		}

		return found;
	}


	private void tickAll() {
		for (Map.Entry<UUID, Builder> entry : builders.entrySet()) {
			Player player = Bukkit.getPlayer(entry.getKey());

			if (player != null) {
				player.getScheduler().run(plugin, task -> tick(player, entry.getValue()), null);
			}
		}

		for (BuildPiece piece : List.copyOf(pieces.values())) {
			if (!piece.built) {
				Bukkit.getRegionScheduler().run(plugin, new Location(piece.world, piece.originX(), piece.originY(), piece.originZ()), task -> grow(piece));
			}
		}
	}

	private void tick(Player player, Builder builder) {
		if (builder.mode == Mode.BUILD) {
			Choice choice = choose(player, builder);
			String key = choice == null ? "" : choice.piece().slot() + "|" + choice.piece().facing + "|" + choice.valid();

			if (!key.equals(builder.previewKey)) {
				builder.previewKey = key;

				if (choice == null) {
					SkaffyAPI.get().getShapes().hide(player, "build_preview");
				} else {
					SkaffyAPI.get().getShapes().show(player, "build_preview", choice.piece().shape(true, choice.valid() ? PREVIEW : PREVIEW_BLOCKED), placement(choice.piece()));
				}
			}

			if (builder.placeHeld && System.currentTimeMillis() - builder.placePressed >= HOLD_TO_REPEAT) {
				tryPlace(player, builder, choice);
			}
		} else if (builder.mode == Mode.EDIT && builder.edit != null) {
			tickEdit(player, builder);
		}

		Object[] target = builder.mode == Mode.EDIT ? null : lookedAt(player, builder, false);
		String targetKey = target == null ? "" : ((BuildPiece) target[0]).id + "|" + (int) ((BuildPiece) target[0]).hp;

		if (!targetKey.equals(builder.targetKey)) {
			builder.targetKey = targetKey;

			if (target == null) {
				hud(player, "clearTarget");
			} else {
				BuildPiece piece = (BuildPiece) target[0];
				hud(player, "setTarget", piece.material.label + " " + piece.kind.label, (int) Math.ceil(piece.hp), piece.material.maxHp);
			}
		}
	}

	private static ShapePlacement placement(BuildPiece piece) {
		return ShapePlacement.at(piece.world, piece.originX(), piece.originY(), piece.originZ());
	}


	private void tryPlace(Player player, Builder builder, Choice choice) {
		if (choice == null || !choice.valid() || slots.containsKey(choice.piece().slot())) {
			return;
		}

		BuildPiece piece = choice.piece();

		int index = builder.material.ordinal();
		builder.materials[index] -= MATERIAL_COST;

		if (builder.materials[index] < MATERIAL_COST) {
			builder.materials[index] = 999;
		}

		hud(player, "setMaterials", builder.materials[0], builder.materials[1], builder.materials[2]);
		nextId++;
		pieces.put(piece.id, piece);
		slots.put(piece.slot(), piece);
		applyCollision(piece, true);
		draw(piece);
		lift(piece);
		builder.previewKey = "";
		player.getWorld().playSound(piece.center().toLocation(player.getWorld()), sound(piece.material), 0.8f, 1.2f);
	}

	private void lift(BuildPiece piece) {
		if (piece.kind == BuildPiece.Kind.WALL) {
			return;
		}

		List<BoundingBox> boxes = new ArrayList<>();
		piece.collisionCache.forEach((key, inBlock) -> {
			int[] at = BuildPiece.unkey(key);
			inBlock.forEach(box -> boxes.add(box.clone().shift(at[0], at[1], at[2])));
		});

		for (Player other : piece.world.getPlayers()) {
			BoundingBox body = other.getBoundingBox();

			if (!body.overlaps(BoundingBox.of(piece.center(), 2.6, 2.6, 2.6))) {
				continue;
			}

			double feet = body.getMinY();

			for (int round = 0; round < 16; round++) {
				BoundingBox moved = body.clone().shift(0, feet - body.getMinY(), 0);
				double top = feet;

				for (BoundingBox box : boxes) {
					if (box.overlaps(moved)) {
						top = Math.max(top, box.getMaxY());
					}
				}

				if (top <= feet) {
					break;
				}

				feet = top;
			}

			double rise = feet - body.getMinY();

			if (rise > 1e-4 && rise <= 3.5) {
				Location target = other.getLocation();
				target.setY(feet + 0.001);
				other.getScheduler().run(plugin, task -> other.teleportAsync(target), null);
			}
		}
	}

	private static Sound sound(BuildPiece.Material material) {
		return switch (material) {
			case WOOD -> Sound.BLOCK_WOOD_PLACE;
			case BRICK -> Sound.BLOCK_STONE_PLACE;
			case METAL -> Sound.BLOCK_METAL_PLACE;
		};
	}

	private boolean supported(BuildPiece piece) {
		World world = piece.world;
		Map<Long, Map<String, List<BoundingBox>>> worldBlocks = blocks.getOrDefault(world.getUID(), Map.of());

		for (long key : piece.collision().keySet()) {
			int[] at = BuildPiece.unkey(key);

			for (int[] offset : new int[][] {{0, 0, 0}, {0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
				int x = at[0] + offset[0];
				int y = at[1] + offset[1];
				int z = at[2] + offset[2];

				if (worldBlocks.containsKey(BuildPiece.key(x, y, z)) || terrain(world, x, y, z)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean terrain(World world, int x, int y, int z) {
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return false;
		}

		Block block = world.getBlockAt(x, y, z);
		return block.getType().isSolid() && !(block.getType() == Material.BARRIER && barriers.getOrDefault(world.getUID(), Set.of()).contains(BuildPiece.key(x, y, z)));
	}

	private void grow(BuildPiece piece) {
		if (!pieces.containsKey(piece.id) || piece.built) {
			return;
		}

		piece.hp += piece.material.maxHp * 0.9 / (piece.material.buildSeconds * 20.0);

		if (piece.hp >= piece.material.maxHp) {
			piece.hp = piece.material.maxHp;
			piece.built = true;
			draw(piece);
		} else if (Bukkit.getCurrentTick() % 10 == 0) {
			draw(piece);
		}
	}

	private void draw(BuildPiece piece) {
		double progress = piece.hp / piece.material.maxHp;
		Color tint = piece.built ? Color.WHITE : Color.fromARGB((int) Math.round(255 * (0.45 + 0.5 * progress)), 200, 225, 255);
		SkaffyAPI.get().getShapes().place(piece.id, piece.shape(false, tint), placement(piece));
	}


	private void applyCollision(BuildPiece piece, boolean add) {
		World world = piece.world;
		Map<Long, Map<String, List<BoundingBox>>> worldBlocks = blocks.computeIfAbsent(world.getUID(), id -> new HashMap<>());
		Set<Long> ours = barriers.computeIfAbsent(world.getUID(), id -> new HashSet<>());
		BlockShapes shapes = SkaffyAPI.get().getBlockShapes();
		Map<Long, List<BoundingBox>> own = piece.collisionCache != null ? piece.collisionCache : piece.collision();

		for (Map.Entry<Long, List<BoundingBox>> entry : own.entrySet()) {
			long key = entry.getKey();
			Map<String, List<BoundingBox>> inBlock = worldBlocks.computeIfAbsent(key, k -> new LinkedHashMap<>());

			if (add) {
				inBlock.put(piece.id, entry.getValue());
			} else {
				inBlock.remove(piece.id);
			}

			int[] at = BuildPiece.unkey(key);
			Block block = world.getBlockAt(at[0], at[1], at[2]);

			if (inBlock.isEmpty()) {
				worldBlocks.remove(key);
				shapes.remove(block);

				if (ours.remove(key) && block.getType() == Material.BARRIER) {
					block.setType(Material.AIR, false);
				}

				continue;
			}

			boolean air = block.getType().isAir();

			if (!air && !ours.contains(key)) {
				continue;
			}

			if (air) {
				block.setType(Material.BARRIER, false);
				ours.add(key);
			}

			List<BoundingBox> union = new ArrayList<>();
			inBlock.values().forEach(union::addAll);

			if (union.size() > BlockShapes.MAX_BOXES) {
				union.sort(Comparator.comparingDouble(BoundingBox::getVolume).reversed());
				union = new ArrayList<>(union.subList(0, BlockShapes.MAX_BOXES));
			}

			shapes.set(block, union);
		}

		piece.collisionCache = add ? own : null;
	}


	@EventHandler
	public void onHit(PlayerInteractEvent event) {
		if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
			return;
		}

		BuildPiece piece = pieceAt(event.getClickedBlock(), event.getPlayer());

		if (piece != null) {
			event.setCancelled(true);
			hit(event.getPlayer(), piece);
		}
	}

	@EventHandler
	public void onBreak(BlockBreakEvent event) {
		Block block = event.getBlock();

		if (barriers.getOrDefault(block.getWorld().getUID(), Set.of()).contains(BuildPiece.key(block.getX(), block.getY(), block.getZ()))) {
			event.setCancelled(true);
			BuildPiece piece = pieceAt(block, event.getPlayer());

			if (piece != null) {
				hit(event.getPlayer(), piece);
			}
		}
	}

	private BuildPiece pieceAt(Block block, Player player) {
		Map<String, List<BoundingBox>> inBlock = blocks.getOrDefault(block.getWorld().getUID(), Map.of()).get(BuildPiece.key(block.getX(), block.getY(), block.getZ()));

		if (inBlock == null) {
			return null;
		}

		Vector eye = player.getEyeLocation().toVector();
		return inBlock.keySet().stream().map(pieces::get).filter(piece -> piece != null)
				.min(Comparator.comparingDouble(piece -> piece.center().distanceSquared(eye))).orElse(null);
	}

	private void hit(Player player, BuildPiece piece) {
		long now = System.currentTimeMillis();
		Builder builder = builders.get(player.getUniqueId());
		long last = builder != null ? builder.lastHit : 0;

		if (now - last < HIT_COOLDOWN) {
			return;
		}

		if (builder != null) {
			builder.lastHit = now;
		}

		piece.hp -= HIT_DAMAGE;
		World world = piece.world;
		world.playSound(piece.center().toLocation(world), piece.material == BuildPiece.Material.WOOD ? Sound.BLOCK_WOOD_HIT : piece.material == BuildPiece.Material.BRICK
				? Sound.BLOCK_STONE_HIT : Sound.BLOCK_METAL_HIT, 1, 1);

		if (piece.hp <= 0) {
			destroy(piece);
			collapse(world);
		}
	}

	private void destroy(BuildPiece piece) {
		if (pieces.remove(piece.id) == null) {
			return;
		}

		slots.remove(piece.slot());
		applyCollision(piece, false);
		SkaffyAPI.get().getShapes().remove(piece.id);
		Location center = piece.center().toLocation(piece.world);
		piece.world.spawnParticle(Particle.BLOCK, center, 40, 0.8, 0.8, 0.8, Bukkit.createBlockData(Material.matchMaterial(piece.material.item)));
		piece.world.playSound(center, piece.material == BuildPiece.Material.WOOD ? Sound.BLOCK_WOOD_BREAK : piece.material == BuildPiece.Material.BRICK
				? Sound.BLOCK_STONE_BREAK : Sound.BLOCK_METAL_BREAK, 1, 1);

		for (Map.Entry<UUID, Builder> entry : builders.entrySet()) {
			Builder builder = entry.getValue();

			if (builder.edit != null && builder.edit.piece == piece) {
				Player player = Bukkit.getPlayer(entry.getKey());
				builder.edit = null;

				if (player != null) {
					setMode(player, builder, builder.beforeEdit);
				}
			}
		}
	}

	private void collapse(World world) {
		Map<Long, Map<String, List<BoundingBox>>> worldBlocks = blocks.getOrDefault(world.getUID(), Map.of());
		Set<String> supported = new HashSet<>();
		Deque<BuildPiece> queue = new ArrayDeque<>();

		for (BuildPiece piece : pieces.values()) {
			if (piece.world == world && grounded(piece)) {
				supported.add(piece.id);
				queue.add(piece);
			}
		}

		while (!queue.isEmpty()) {
			BuildPiece piece = queue.poll();

			for (long key : piece.collisionCache.keySet()) {
				int[] at = BuildPiece.unkey(key);

				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							Map<String, List<BoundingBox>> near = worldBlocks.get(BuildPiece.key(at[0] + dx, at[1] + dy, at[2] + dz));

							if (near == null) {
								continue;
							}

							for (String id : near.keySet()) {
								if (supported.add(id)) {
									queue.add(pieces.get(id));
								}
							}
						}
					}
				}
			}
		}

		for (BuildPiece piece : List.copyOf(pieces.values())) {
			if (piece.world == world && !supported.contains(piece.id)) {
				destroy(piece);
			}
		}
	}

	private boolean grounded(BuildPiece piece) {
		for (long key : piece.collisionCache.keySet()) {
			int[] at = BuildPiece.unkey(key);

			for (int[] offset : new int[][] {{0, 0, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
				if (terrain(piece.world, at[0] + offset[0], at[1] + offset[1], at[2] + offset[2])) {
					return true;
				}
			}
		}

		return false;
	}


	private void startEdit(Player player, Builder builder) {
		Object[] target = lookedAt(player, builder, false);

		if (target == null) {
			target = lookedAt(player, builder, true);
		}

		if (target == null) {
			player.sendActionBar(Component.text("Look at one of your pieces to edit it", NamedTextColor.GRAY));
			return;
		}

		BuildPiece piece = (BuildPiece) target[0];

		if (!piece.owner.equals(player.getUniqueId())) {
			player.sendActionBar(Component.text("Only the player who built it can edit it", NamedTextColor.RED));
			return;
		}

		Edit edit = new Edit(piece);
		edit.lastDirection = player.getEyeLocation().getDirection();
		edit.hovered = pick(player, builder, edit, edit.lastDirection);
		builder.beforeEdit = builder.mode;
		builder.edit = edit;
		setMode(player, builder, Mode.EDIT);
	}

	private int pick(Player player, Builder builder, Edit edit, Vector direction) {
		Ray ray = ray(player, builder, direction);
		double[] hit = edit.piece.hit(ray.start(), ray.direction(), EDIT_REACH, true);
		return hit == null ? -1 : (int) hit[1];
	}

	private void follow(Player player, Builder builder, Edit edit) {
		Vector now = player.getEyeLocation().getDirection();
		Vector before = edit.lastDirection == null ? now : edit.lastDirection;
		edit.lastDirection = now;
		int steps = Math.clamp((int) Math.ceil(Math.toDegrees(before.angle(now)) / 1.5), 1, 24);

		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			Vector direction = before.clone().multiply(1 - t).add(now.clone().multiply(t));

			if (direction.lengthSquared() > 1e-9) {
				hover(edit, pick(player, builder, edit, direction.normalize()));
			}
		}
	}

	private static void hover(Edit edit, int cell) {
		if (cell == edit.hovered) {
			return;
		}

		edit.hovered = cell;

		if (!edit.dragging || cell < 0) {
			return;
		}

		if (edit.piece.kind == BuildPiece.Kind.RAMP) {
			int last = edit.path.getLast();
			boolean next = Math.abs(cell % 2 - last % 2) + Math.abs(cell / 2 - last / 2) == 1;

			if (next && !edit.path.contains(cell)) {
				edit.path.add(cell);
			}
		} else {
			edit.selected[cell] = edit.adding;
		}
	}

	private void beginDrag(Player player, Builder builder) {
		Edit edit = builder.edit;

		if (edit == null) {
			return;
		}

		follow(player, builder, edit);

		if (edit.hovered < 0) {
			return;
		}

		edit.dragging = true;

		if (edit.piece.kind == BuildPiece.Kind.RAMP) {
			edit.path.clear();
			edit.path.add(edit.hovered);
			return;
		}

		edit.adding = !edit.selected[edit.hovered];
		edit.selected[edit.hovered] = edit.adding;
	}

	private void tickEdit(Player player, Builder builder) {
		Edit edit = builder.edit;
		follow(player, builder, edit);
		String key = edit.hovered + "|" + java.util.Arrays.toString(edit.selected) + "|" + edit.path;

		if (!key.equals(edit.shownKey)) {
			edit.shownKey = key;
			SkaffyAPI.get().getShapes().show(player, "build_edit", editGrid(edit), placement(edit.piece));
		}
	}

	private static Shape editGrid(Edit edit) {
		BuildPiece piece = edit.piece;
		Shape.Builder builder = Shape.overlay();

		for (int cell = 0; cell < piece.kind.cells; cell++) {
			boolean selected = piece.kind == BuildPiece.Kind.RAMP ? edit.path.contains(cell) : edit.selected[cell];
			boolean hovered = cell == edit.hovered;
			piece.gridCell(builder, cell, selected ? CELL_SELECTED : hovered ? CELL_HOVER : CELL, selected ? OUTLINE_SELECTED : hovered ? OUTLINE_HOVER : OUTLINE,
					selected ? 3 : hovered ? 2 : 1.5f);
		}

		return builder.build();
	}

	private void confirmEdit(Player player, Builder builder) {
		Edit edit = builder.edit;

		if (edit == null) {
			return;
		}

		BuildPiece piece = edit.piece;
		boolean valid = piece.validEdit(edit.selected);

		if (!valid && !java.util.Arrays.equals(edit.selected, piece.removed)) {
			player.sendActionBar(Component.text("That edit isn't possible", NamedTextColor.RED));
		}

		if (pieces.containsKey(piece.id)) {
			applyCollision(piece, false);

			if (piece.kind == BuildPiece.Kind.RAMP) {
				if (edit.reset) {
					piece.path.clear();
				} else if (edit.path.size() >= 2) {
					piece.path.clear();
					piece.path.addAll(edit.path);
				}
			} else if (valid) {
				System.arraycopy(edit.selected, 0, piece.removed, 0, piece.removed.length);
			}

			applyCollision(piece, true);
			draw(piece);
			collapse(piece.world);
		}

		builder.edit = null;
		setMode(player, builder, builder.beforeEdit);
	}

	private void resetEdit(Player player, Builder builder) {
		Edit edit = builder.edit;

		if (edit == null) {
			return;
		}

		java.util.Arrays.fill(edit.selected, false);
		edit.path.clear();
		edit.reset = true;
		confirmEdit(player, builder);
	}


	void clearAll() {
		for (BuildPiece piece : List.copyOf(pieces.values())) {
			pieces.remove(piece.id);
			slots.remove(piece.slot());
			applyCollision(piece, false);
			SkaffyAPI.get().getShapes().remove(piece.id);
		}

		for (Map.Entry<UUID, Set<Long>> entry : barriers.entrySet()) {
			World world = Bukkit.getWorld(entry.getKey());

			if (world == null) {
				continue;
			}

			for (long key : entry.getValue()) {
				int[] at = BuildPiece.unkey(key);
				Block block = world.getBlockAt(at[0], at[1], at[2]);
				SkaffyAPI.get().getBlockShapes().remove(block);

				if (block.getType() == Material.BARRIER) {
					block.setType(Material.AIR, false);
				}
			}

			entry.getValue().clear();
		}

		blocks.clear();
	}
}
