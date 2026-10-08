package test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import me.skaffy.api.AssetRegistry;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.block.BlockRotation;
import me.skaffy.api.block.CustomBlockType;
import me.skaffy.api.block.CustomBlockType.Tool;
import me.skaffy.api.block.CustomBlockType.Transparency;
import me.skaffy.api.block.CustomBlocks;
import me.skaffy.api.event.SkaffyClientRegisterEvent;
import me.skaffy.api.event.block.SkaffyBlockMinedEvent;
import me.skaffy.api.event.block.SkaffyBlockMiningStartEvent;
import me.skaffy.api.event.block.SkaffyBlockPickEvent;
import me.skaffy.api.event.keybind.SkaffyKeyChangeEvent;
import me.skaffy.api.event.keybind.SkaffyKeyPressEvent;
import me.skaffy.api.event.keybind.SkaffyKeyReleaseEvent;
import me.skaffy.api.event.perspective.SkaffyPerspectiveChangeEvent;
import me.skaffy.api.keybind.CustomKeybind;
import me.skaffy.api.keybind.CustomKeybinds;
import me.skaffy.api.particle.CustomParticleType;
import me.skaffy.api.particle.CustomParticleType.Facing;
import me.skaffy.api.particle.CustomParticleType.RenderMode;
import me.skaffy.api.particle.CustomParticles;
import me.skaffy.api.particle.Easing;
import me.skaffy.api.particle.ParticleColorCurve;
import me.skaffy.api.particle.ParticleCurve;
import me.skaffy.api.particle.ParticleSpawn;
import me.skaffy.api.particle.ParticleStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SkaffyTest extends JavaPlugin implements Listener {
	private List<CustomBlockType> types;
	private final Map<String, ParticleTest> particleTests = new LinkedHashMap<>();
	private final Map<UUID, Map<String, String>> savedKeys = new ConcurrentHashMap<>();
	private final Map<UUID, Set<String>> heldKeybinds = new ConcurrentHashMap<>();
	private final ModelTests modelTests = new ModelTests();
	private final FovTests fovTests = new FovTests();
	private final NameTagTests nameTagTests = new NameTagTests();
	private final GuiTests guiTests = new GuiTests();
	private final PerspectiveTests perspectiveTests = new PerspectiveTests();
	private final BrightnessTests brightnessTests = new BrightnessTests();
	private final LookTests lookTests = new LookTests();
	private final ShaderTests shaderTests = new ShaderTests();
	private final VistaTests vistaTests = new VistaTests();
	private final BuildTests buildTests = new BuildTests();
	private final AnimationTests animationTests = new AnimationTests();

	private interface TestSpawn {
		ParticleSpawn create(CustomParticleType type, Player player, Location front);
	}

	private record ParticleTest(CustomParticleType type, TestSpawn spawn) {
	}

	@Override
	public void onEnable() {
		SkaffyAPI api = SkaffyAPI.get();
		AssetRegistry assets = api.getAssets();
		assets.register("test_ruby.png", png(checker(new Color(200, 20, 40), new Color(150, 10, 30), 255)));
		assets.register("test_glass.png", png(glass()));
		assets.register("test_flower.png", png(flower()));
		assets.register("test_ruby_block.json", json("{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"test_ruby.png\"}}"));
		assets.register("test_slab.json", json("{\"parent\":\"block/slab\",\"textures\":{\"bottom\":\"test_ruby.png\",\"top\":\"test_ruby.png\",\"side\":\"test_ruby.png\"}}"));
		assets.register("test_glass.json", json("{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"test_glass.png\"}}"));
		assets.register("test_flower.json", json("{\"parent\":\"block/cross\",\"textures\":{\"cross\":\"test_flower.png\"}}"));
		assets.register("test_flower_hitbox.json", json("{\"elements\":[{\"from\":[4,0,4],\"to\":[12,12,12]}]}"));
		CustomBlocks blocks = api.getBlocks();
		types = List.of(
				CustomBlockType.builder("ruby_block", "test_ruby_block.json").hardness(3).tool(Tool.PICKAXE).requiresTool(true).build(),
				CustomBlockType.builder("ruby_slab", "test_slab.json").hardness(1).tool(Tool.PICKAXE).build(),
				CustomBlockType.builder("glow_glass", "test_glass.json").transparency(Transparency.TRANSLUCENT).light(15).hardness(0.3f).build(),
				CustomBlockType.builder("test_flower", "test_flower.json").noCollision().hitbox("test_flower_hitbox.json").transparency(Transparency.CUTOUT).hardness(0).build());
		types.forEach(blocks::register);
		registerParticles(api);
		registerKeybinds(api);
		modelTests.register(api);
		nameTagTests.register(api);
		guiTests.register(api, this);
		lookTests.register(api);
		shaderTests.register(api, this);
		getServer().getPluginManager().registerEvents(guiTests, this);
		getServer().getPluginManager().registerEvents(shaderTests, this);
		getServer().getPluginManager().registerEvents(vistaTests, this);
		getServer().getPluginManager().registerEvents(buildTests, this);
		buildTests.register(this);
		animationTests.register(api, this);
		getServer().getPluginManager().registerEvents(animationTests, this);
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		buildTests.clearAll();
	}

	private void registerKeybinds(SkaffyAPI api) {
		CustomKeybinds keybinds = api.getKeybinds();
		keybinds.setCategory("Skaffy Test");
		keybinds.register(CustomKeybind.builder("test_wave", "Wave").defaultKey("key.keyboard.g").build());
		keybinds.register(CustomKeybind.builder("test_modifier", "Combo modifier").defaultKey("key.keyboard.left.alt").build());
		keybinds.register(CustomKeybind.builder("test_mouse", "Mouse button").defaultKey("key.mouse.4").build());
		keybinds.register(CustomKeybind.builder("test_unbound", "Unbound at first").build());
		BuildTests.registerKeybinds(keybinds);
	}

	private void registerParticles(SkaffyAPI api) {
		AssetRegistry assets = api.getAssets();
		assets.register("test_spark.png", png(dot(8, 255)));
		assets.register("test_ring.png", png(ring()));
		assets.register("test_pulse.png", png(pulseStrip()));
		assets.register("test_pulse.png.mcmeta", json("{\"animation\":{\"frametime\":3}}"));

		for (int i = 0; i < 4; i++) {
			assets.register("test_grow_" + i + ".png", png(dot(2 + i * 2, 255)));
		}

		CustomParticles particles = api.getParticles();
		String[] smoke = new String[8];
		String[] cherry = new String[12];

		for (int i = 0; i < 8; i++) {
			smoke[i] = "minecraft:generic_" + (7 - i);
		}

		for (int i = 0; i < 12; i++) {
			cherry[i] = "minecraft:cherry_" + i;
		}

		test(particles, CustomParticleType.builder("test_flame", "minecraft:flame").style(ParticleStyle.FLAME).build(),
				(type, player, at) -> spawn(type, at, 20).spread(0.15).build());
		test(particles, CustomParticleType.builder("test_smoke", smoke).style(ParticleStyle.SMOKE).build(),
				(type, player, at) -> spawn(type, at, 15).spread(0.2).velocity(0, 0.03, 0).build());
		test(particles, CustomParticleType.builder("test_dust", "test_spark.png").style(ParticleStyle.DUST).build(),
				(type, player, at) -> spawn(type, at, 40).spread(0.5).color(org.bukkit.Color.RED).build());
		test(particles, CustomParticleType.builder("test_leaves", cherry).style(ParticleStyle.LEAVES).build(),
				(type, player, at) -> spawn(type, at.clone().add(0, 3, 0), 30).spread(2, 0.5, 2).spreadShape(ParticleSpawn.Spread.BOX).build());
		test(particles, CustomParticleType.builder("test_firefly", "minecraft:firefly").style(ParticleStyle.FIREFLY).build(),
				(type, player, at) -> spawn(type, at, 12).spread(2).spreadShape(ParticleSpawn.Spread.BOX).build());
		test(particles, CustomParticleType.builder("test_portal", "minecraft:generic_0").style(ParticleStyle.PORTAL).color(org.bukkit.Color.fromRGB(0xB040FF)).build(),
				(type, player, at) -> spawn(type, at, 60).spread(1.5).spreadShape(ParticleSpawn.Spread.BOX).build());
		test(particles, CustomParticleType.builder("test_spark", "test_spark.png").style(ParticleStyle.SPARK).velocityRandomness(0.25f)
						.colorOverLife(ParticleColorCurve.of(0, org.bukkit.Color.YELLOW).to(1, org.bukkit.Color.RED)).build(),
				(type, player, at) -> spawn(type, at, 60).velocity(0, 0.1, 0).build());
		test(particles, CustomParticleType.builder("test_bubble", "minecraft:bubble").style(ParticleStyle.BUBBLE).build(),
				(type, player, at) -> spawn(type, at, 20).spread(0.3).build());
		test(particles, CustomParticleType.builder("test_ring", "test_ring.png").facing(Facing.HORIZONTAL).render(RenderMode.TRANSLUCENT).size(1).lifetime(30)
						.light(15).collides(false).sizeOverLife(ParticleCurve.of(0, 0.2f).to(1, 5, Easing.EASE_OUT)).alphaOverLife(ParticleCurve.of(0, 1).to(1, 0)).build(),
				(type, player, at) -> spawn(type, player.getLocation().add(0, 0.1, 0), 1).build());
		test(particles, CustomParticleType.builder("test_fixed", "test_pulse.png").fixed(0, 0).size(1).lifetime(60).light(15).collides(false).friction(1).build(),
				(type, player, at) -> spawn(type, at, 1).rotation(player.getLocation().getYaw(), player.getLocation().getPitch()).build());
		test(particles, CustomParticleType.builder("test_vertical", "test_grow_0.png", "test_grow_1.png", "test_grow_2.png", "test_grow_3.png").facing(Facing.VERTICAL)
						.size(0.5f).lifetime(40).spin(-9, 9).randomAngle(true).collides(false).friction(1).build(),
				(type, player, at) -> spawn(type, at, 6).spread(1, 0, 1).spreadShape(ParticleSpawn.Spread.BOX).build());
		test(particles, CustomParticleType.builder("test_rain", "test_spark.png").size(0.1f).lifetime(100).gravity(0.04f).dieOnGround(true).color(org.bukkit.Color.AQUA).build(),
				(type, player, at) -> spawn(type, at.clone().add(0, 4, 0), 80).spread(3, 0, 3).spreadShape(ParticleSpawn.Spread.BOX).force(true).build());
	}

	private void test(CustomParticles particles, CustomParticleType type, TestSpawn spawn) {
		particles.register(type);
		particleTests.put(type.getName().substring("test_".length()), new ParticleTest(type, spawn));
	}

	private static ParticleSpawn.Builder spawn(CustomParticleType type, Location location, int count) {
		return ParticleSpawn.builder(type, location).count(count);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			return false;
		}

		if (args.length > 0 && args[0].equals("particle")) {
			return particleCommand(player, args);
		}

		if (args.length > 0 && args[0].equals("key")) {
			return keyCommand(player, args);
		}

		if (args.length > 0 && args[0].equals("model")) {
			return modelTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("fov")) {
			return fovTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("nametag")) {
			return nameTagTests.command(player, args, this);
		}

		if (args.length > 0 && args[0].equals("gui")) {
			return guiTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("perspective")) {
			return perspectiveTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("brightness")) {
			return brightnessTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("look")) {
			return lookTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("shader")) {
			return shaderTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("vista")) {
			return vistaTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("build")) {
			return buildTests.command(player, args);
		}

		if (args.length > 0 && args[0].equals("animation")) {
			return animationTests.command(player, args);
		}

		CustomBlocks blocks = SkaffyAPI.get().getBlocks();
		Vector forward = player.getFacing().getDirection();
		Vector side = new Vector(-forward.getZ(), 0, forward.getX());
		Location start = player.getLocation().toBlockLocation().add(forward.clone().multiply(3));
		BlockRotation[] rotations = {BlockRotation.NONE, BlockRotation.NONE, new BlockRotation(180, 0), BlockRotation.NONE, BlockRotation.NONE};
		CustomBlockType[] row = {types.get(0), types.get(1), types.get(1), types.get(2), types.get(3)};
		boolean clear = args.length > 0 && args[0].equals("clear");

		for (int i = 0; i < row.length; i++) {
			Location location = start.clone().add(side.clone().multiply(i - 2));

			if (clear) {
				blocks.remove(location);
				location.getBlock().setType(Material.AIR);
			} else {
				blocks.set(location, row[i], rotations[i]);
			}
		}

		player.sendMessage(Component.text(clear ? "Cleared the test blocks" : "Placed: ruby block, slab, upper slab, glowing glass, flower"));
		return true;
	}

	private boolean particleCommand(Player player, String[] args) {
		if (args.length < 2 || !args[1].equals("all") && !particleTests.containsKey(args[1])) {
			player.sendMessage(Component.text("/skaffytest particle <" + String.join("|", particleTests.keySet()) + "|all>"));
			return true;
		}

		Location front = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3));
		List<String> names = args[1].equals("all") ? List.copyOf(particleTests.keySet()) : List.of(args[1]);

		for (String name : names) {
			ParticleTest test = particleTests.get(name);
			SkaffyAPI.get().getParticles().spawn(test.spawn().create(test.type(), player, front));
		}

		player.sendMessage(Component.text("Spawned " + String.join(", ", names) + (SkaffyAPI.get().isReady(player) ? "" : " (you don't have the mod, so you see nothing)")));
		return true;
	}

	private boolean keyCommand(Player player, String[] args) {
		CustomKeybinds keybinds = SkaffyAPI.get().getKeybinds();

		if (args.length == 1) {
			for (CustomKeybind keybind : keybinds.getKeybinds()) {
				player.sendMessage(Component.text(keybind.getId() + ": " + keybinds.getKey(player, keybind).orElse("(you don't have it)") + ", default " + keybind.getDefaultKey()));
			}

			return true;
		}

		CustomKeybind keybind = keybinds.getKeybind(args[1]).orElse(null);

		if (keybind == null || args.length < 3) {
			player.sendMessage(Component.text("/skaffytest key <" + keybinds.getKeybinds().stream().map(CustomKeybind::getId).reduce((a, b) -> a + "|" + b).orElse("") + "> <key.keyboard.h>"));
			return true;
		}

		boolean sent = keybinds.setKey(player, keybind, args[2]);
		player.sendMessage(Component.text(sent ? "Bound " + keybind.getId() + " to " + args[2] + " from the server" : "You don't have that keybind"));
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return List.of("clear", "particle", "key", "model", "fov", "nametag", "gui", "perspective", "brightness", "look", "shader", "vista", "build", "animation").stream().filter(option -> option.startsWith(args[0])).toList();
		}

		if (args[0].equals("model")) {
			return modelTests.tabComplete(args);
		}

		if (args[0].equals("fov")) {
			return fovTests.tabComplete(args);
		}

		if (args[0].equals("nametag")) {
			return nameTagTests.tabComplete(args);
		}

		if (args.length > 1 && args[0].equals("gui")) {
			return guiTests.tabComplete(args);
		}

		if (args[0].equals("perspective")) {
			return perspectiveTests.tabComplete(args);
		}

		if (args[0].equals("brightness")) {
			return brightnessTests.tabComplete(args);
		}

		if (args[0].equals("look")) {
			return lookTests.tabComplete(args);
		}

		if (args[0].equals("shader")) {
			return shaderTests.tabComplete(args);
		}

		if (args[0].equals("vista")) {
			return vistaTests.tabComplete(args);
		}

		if (args[0].equals("build")) {
			return buildTests.tabComplete(args);
		}

		if (args[0].equals("animation")) {
			return animationTests.tabComplete(args);
		}

		if (args.length == 2 && args[0].equals("particle")) {
			return Stream.concat(particleTests.keySet().stream(), Stream.of("all")).filter(option -> option.startsWith(args[1])).toList();
		}

		if (args.length == 2 && args[0].equals("key")) {
			return SkaffyAPI.get().getKeybinds().getKeybinds().stream().map(CustomKeybind::getId).filter(id -> id.startsWith(args[1])).toList();
		}

		if (args.length == 3 && args[0].equals("key")) {
			return Stream.of("key.keyboard.h", "key.keyboard.j", "key.keyboard.left.shift", "key.mouse.5", CustomKeybind.UNBOUND).filter(key -> key.startsWith(args[2])).toList();
		}

		return List.of();
	}

	@EventHandler
	public void onRegister(SkaffyClientRegisterEvent event) {
		savedKeys.getOrDefault(event.getClient().getPlayerId(), Map.of()).forEach(event::setKey);
	}

	@EventHandler
	public void onKeyPress(SkaffyKeyPressEvent event) {
		if (event.getKeybind().getId().startsWith("build_")) {
			return;
		}

		Player player = event.getPlayer();
		CustomKeybinds keybinds = SkaffyAPI.get().getKeybinds();
		boolean combo = event.getKeybind().getId().equals("test_wave") && keybinds.getKeybind("test_modifier").map(modifier -> heldKeybinds.getOrDefault(player.getUniqueId(), Set.of()).contains(modifier.getId())).orElse(false);
		heldKeybinds.computeIfAbsent(player.getUniqueId(), id -> ConcurrentHashMap.newKeySet()).add(event.getKeybind().getId());
		player.sendActionBar(Component.text((combo ? "Combo! " : "Pressed ") + event.getKeybind().getName() + " (" + event.getKey() + ")"));
	}

	@EventHandler
	public void onKeyRelease(SkaffyKeyReleaseEvent event) {
		if (event.getKeybind().getId().startsWith("build_")) {
			return;
		}

		heldKeybinds.getOrDefault(event.getPlayer().getUniqueId(), Set.of()).remove(event.getKeybind().getId());
		event.getPlayer().sendActionBar(Component.text("Released " + event.getKeybind().getName()));
	}

	@EventHandler
	public void onKeyChange(SkaffyKeyChangeEvent event) {
		savedKeys.computeIfAbsent(event.getPlayer().getUniqueId(), id -> new ConcurrentHashMap<>()).put(event.getKeybind().getId(), event.getKey());
		event.getPlayer().sendMessage(Component.text(event.getKeybind().getName() + ": " + event.getPreviousKey() + " -> " + event.getKey() + (event.isDefault() ? " (default)" : "")));
	}

	@EventHandler
	public void onPerspectiveChange(SkaffyPerspectiveChangeEvent event) {
		event.getPlayer().sendMessage(Component.text("F5: " + event.getFrom() + " -> " + event.getTo()));
	}

	@EventHandler
	public void onMiningStart(SkaffyBlockMiningStartEvent event) {
		event.getPlayer().sendActionBar(Component.text("Mining " + event.getBlock().type().getName()));
	}

	@EventHandler
	public void onMined(SkaffyBlockMinedEvent event) {
		Location location = event.getBlock().location();
		SkaffyAPI.get().getBlocks().remove(location);
		location.getBlock().setType(Material.AIR);
		event.getPlayer().sendMessage(Component.text("Mined " + event.getBlock().type().getName()));
	}

	@EventHandler
	public void onPick(SkaffyBlockPickEvent event) {
		event.getPlayer().sendMessage(Component.text("Picked " + event.getBlock().type().getName()));
	}

	private static BufferedImage checker(Color a, Color b, int alpha) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				Color color = (x / 4 + y / 4) % 2 == 0 ? a : b;
				image.setRGB(x, y, alpha << 24 | color.getRGB() & 0xFFFFFF);
			}
		}

		return image;
	}

	private static BufferedImage glass() {
		BufferedImage image = checker(new Color(120, 220, 255), new Color(90, 190, 240), 110);

		for (int i = 0; i < 16; i++) {
			image.setRGB(i, 0, -1);
			image.setRGB(i, 15, -1);
			image.setRGB(0, i, -1);
			image.setRGB(15, i, -1);
		}

		return image;
	}

	private static BufferedImage flower() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		for (int y = 6; y < 16; y++) {
			image.setRGB(7, y, 0xFF2E7D2E);
			image.setRGB(8, y, 0xFF2E7D2E);
		}

		for (int x = 5; x < 11; x++) {
			for (int y = 2; y < 7; y++) {
				image.setRGB(x, y, 0xFFFFD700);
			}
		}

		return image;
	}

	private static BufferedImage dot(int diameter, int alpha) {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		double radius = diameter / 2.0;

		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				if (Math.hypot(x + 0.5 - 4, y + 0.5 - 4) <= radius) {
					image.setRGB(x, y, alpha << 24 | 0xFFFFFF);
				}
			}
		}

		return image;
	}

	private static BufferedImage ring() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double distance = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);

				if (distance >= 6 && distance <= 7.5) {
					image.setRGB(x, y, 0xC0A0E0FF);
				}
			}
		}

		return image;
	}

	private static BufferedImage pulseStrip() {
		int[] colors = {0xFFFF4040, 0xFFFFB040, 0xFF40FF40, 0xFF4080FF};
		BufferedImage image = new BufferedImage(16, 64, BufferedImage.TYPE_INT_ARGB);

		for (int frame = 0; frame < 4; frame++) {
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					boolean border = x == 0 || y == 0 || x == 15 || y == 15;
					boolean arrow = x >= 7 && x <= 8 && y >= 4 && y <= 12 || y >= 3 && y <= 6 && Math.abs(x - 7.5) <= y - 2.5;
					image.setRGB(x, frame * 16 + y, border || arrow ? 0xFFFFFFFF : colors[frame]);
				}
			}
		}

		return image;
	}

	static byte[] png(BufferedImage image) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static byte[] json(String json) {
		return json.getBytes(StandardCharsets.UTF_8);
	}
}
