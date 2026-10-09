package test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.skaffy.api.AssetRegistry;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.nametag.NameTag;
import me.skaffy.api.nametag.NameTagBackground;
import me.skaffy.api.nametag.NameTagLine;
import me.skaffy.api.nametag.NameTagObject;
import me.skaffy.api.nametag.NameTags;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class NameTagTests {
	private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
	private final Map<String, Function<Player, NameTag.Builder>> presets = new LinkedHashMap<>();
	private ScheduledTask counter;

	void register(SkaffyAPI api) {
		AssetRegistry assets = api.getAssets();
		NameTags nameTags = api.getNameTags();
		assets.register("test_tag_coin.png", SkaffyTest.png(coin()));
		assets.register("test_tag_wide.png", SkaffyTest.png(wide()));
		assets.register("test_tag_pulse.png", SkaffyTest.png(pulse()));
		assets.register("test_tag_pulse.png.mcmeta", "{\"animation\":{\"frametime\":5}}".getBytes(StandardCharsets.UTF_8));
		nameTags.registerSprite("test_tag_coin.png");
		nameTags.registerSprite("test_tag_wide.png");
		nameTags.registerSprite("test_tag_pulse.png");

		presets.put("basic", player -> NameTag.builder().line("Basic tag"));
		presets.put("lines", player -> lines());
		presets.put("perline", player -> lines().background(NameTagBackground.vanilla().style(NameTagBackground.Style.PER_LINE).color(0x203060).transparency(30)));
		presets.put("gradient", player -> NameTag.builder()
				.line(NameTagObject.text("Rainbow gradient over a long line").gradient(0xFF0000, 0xFF8000, 0xFFFF00, 0x00FF00, 0x0080FF, 0x8000FF))
				.line(NameTagObject.text("Two colors ").gradient(0xFF55FF, 0x55FFFF).bold(), NameTagObject.text("then plain")));
		presets.put("styles", player -> NameTag.builder()
				.line(NameTagObject.text("bold ").bold(), NameTagObject.text("italic ").italic(), NameTagObject.text("under").underlined(),
						NameTagObject.text(" strike ").strikethrough(), NameTagObject.text("magic").obfuscated())
				.line(NameTagObject.text("uniform ").font(NameTagObject.Font.UNIFORM), NameTagObject.text("alt ").font(NameTagObject.Font.ALT),
						NameTagObject.text("illager").font(NameTagObject.Font.ILLAGERALT))
				.line(NameTagObject.text("bold italic underlined gradient").gradient(0xFFAA00, 0xFF5555).bold().italic().underlined()));
		presets.put("transparency", player -> NameTag.builder()
				.line(NameTagObject.text("0% "), NameTagObject.text("25% ").transparency(25), NameTagObject.text("50% ").transparency(50),
						NameTagObject.text("75% ").transparency(75), NameTagObject.text("90%").transparency(90))
				.line(NameTagObject.text("background 60% see-through, red").color(0xFFFF55))
				.background(NameTagBackground.vanilla().color(0x800000).transparency(60)));
		presets.put("shadow", player -> NameTag.builder()
				.line(NameTagObject.text("vanilla shadow ").shadow(), NameTagObject.text("red shadow").shadowColor(0xFF0000))
				.line(NameTagObject.text("blue shadow 50% ").color(0xFFFF55).shadowColor(0x0000FF).shadowTransparency(50),
						NameTagObject.text("half text, shadow").transparency(50).shadow())
				.background(NameTagBackground.none()));
		presets.put("sprites", player -> NameTag.builder()
				.line(NameTagObject.vanillaSprite("minecraft:block/diamond_ore"), NameTagObject.vanillaSprite("item/diamond"),
						NameTagObject.vanillaSprite("block/grass_block_top").color(0x7CBD6B), NameTagObject.vanillaSprite("block/grass_block_top"),
						NameTagObject.vanillaSprite("gui/sprites/hud/heart/full"), NameTagObject.vanillaSprite("block/water_still").color(0x3F76E4),
						NameTagObject.vanillaSprite("block/nope_not_real"))
				.line(NameTagObject.text("coin "), NameTagObject.sprite("test_tag_coin.png").shadow(), NameTagObject.text(" wide "),
						NameTagObject.sprite("test_tag_wide.png"), NameTagObject.text(" pulse "), NameTagObject.sprite("test_tag_pulse.png"),
						NameTagObject.text(" x"))
				.line(NameTagObject.text("underlined "), NameTagObject.sprite("test_tag_coin.png").underlined(), NameTagObject.text(" half ").transparency(50),
						NameTagObject.sprite("test_tag_coin.png").transparency(50)));
		presets.put("heads", player -> NameTag.builder()
				.line(NameTagObject.head(player), NameTagObject.text(" you  "), NameTagObject.head(player).hat(false), NameTagObject.text(" no hat"))
				.line(NameTagObject.head("Notch"), NameTagObject.text(" by name  "), NameTagObject.head(NOTCH).shadow(), NameTagObject.text(" by UUID, shadow")));
		presets.put("bgshadow", player -> NameTag.builder()
				.line(NameTagObject.text("Background shadow").color(0xFFFF55))
				.line(NameTagObject.text("darkness 60, offset 2 2"))
				.background(NameTagBackground.vanilla().color(0x3050A0).transparency(20).shadowDarkness(60).shadowOffset(2, 2)));
		presets.put("bgshadowperline", player -> lines()
				.background(NameTagBackground.vanilla().style(NameTagBackground.Style.PER_LINE).color(0x2E8B57).transparency(0).shadowDarkness(70).shadowOffset(-2, 3)));
		presets.put("block", player -> NameTag.builder().line("render: block").line("hidden behind blocks, seen through glass").renderMode(NameTag.RenderMode.BLOCK));
		presets.put("always", player -> NameTag.builder().line("render: always").line("on top of everything").renderMode(NameTag.RenderMode.ALWAYS));
		presets.put("sneakhide", player -> NameTag.builder().line("sneak: hide").sneakMode(NameTag.SneakMode.HIDE));
		presets.put("softhide", player -> NameTag.builder().line("sneak: soft hide").sneakMode(NameTag.SneakMode.SOFT_HIDE));
		presets.put("sneakshow", player -> NameTag.builder().line("sneak: show").sneakMode(NameTag.SneakMode.SHOW));
		presets.put("invisible", player -> NameTag.builder().line("shown while invisible").showWhenInvisible(true));
		presets.put("big", player -> NameTag.builder().line("Scale 2, 0.5 up").line("left aligned, longer line").scale(2).offset(0, 0.5, 0).alignment(NameTag.Alignment.LEFT));
		presets.put("right", player -> NameTag.builder().line("right").line("aligned to the right").line("third").alignment(NameTag.Alignment.RIGHT)
				.background(NameTagBackground.vanilla().style(NameTagBackground.Style.PER_LINE)));
		presets.put("far", player -> NameTag.builder().line("only within 8 blocks").maxDistance(8));
		presets.put("fullbright", player -> NameTag.builder().line(NameTagObject.text("full bright").color(0x55FF55)).fullBright(true).renderMode(NameTag.RenderMode.BLOCK));
		presets.put("padding", player -> NameTag.builder().line("padding 6 x 3, gap 4").line("second line")
				.lineGap(4).background(NameTagBackground.vanilla().padding(6, 3).style(NameTagBackground.Style.PER_LINE)));
		presets.put("minimessage", player -> NameTag.builder()
				.lines(MiniMessage.miniMessage().deserialize("<gradient:#ff5555:#5555ff>MiniMessage gradient</gradient> <bold><green>bold</green></bold>\n"
						+ "<shadow:#ff0000>red shadow</shadow> <font:alt>alt font</font> <u>under</u>"))
				.line(Component.text("sprite ").append(Component.object(ObjectContents.sprite(Key.key("minecraft", "block/gold_block"))))
						.append(Component.text(" head ")).append(Component.object(ObjectContents.playerHead(player.getName())))
						.append(Component.text(" coin ")).append(Component.object(ObjectContents.sprite(Key.key("skaffy", "test_tag_coin.png"))))));
		presets.put("empty", player -> NameTag.empty().toBuilder());
	}

	private static NameTag.Builder lines() {
		return NameTag.builder()
				.line(NameTagObject.text("Line one").color(0xFF5555))
				.line(NameTagObject.text("A much longer second line").color(0xFFAA00))
				.line(NameTagObject.text("3").color(0xFFFF55))
				.line(NameTagLine.empty())
				.line(NameTagObject.text("Line five after an empty line").color(0x55FFFF));
	}

	boolean command(Player player, String[] args, Plugin plugin) {
		NameTags nameTags = SkaffyAPI.get().getNameTags();
		String sub = args.length > 1 ? args[1] : "";
		boolean self = args.length > 2 && args[2].equals("self");
		Entity target = self ? player : target(player);
		boolean own = target == player;

		switch (sub) {
			case "spawn" -> {
				Location at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
				Entity zombie = player.getWorld().spawnEntity(at, EntityType.ZOMBIE);

				if (zombie instanceof Mob mob) {
					mob.setAI(false);
					mob.setSilent(true);
					mob.setPersistent(true);
				}

				player.sendMessage(Component.text("Spawned a still zombie to put name tags on"));
			}
			case "clear" -> {
				boolean removed = nameTags.remove(target) | nameTags.remove(player, target);
				stopCounter();
				player.sendMessage(Component.text(removed ? "Removed the name tag of " + describe(target, player) : describe(target, player) + " had no name tag"));
			}
			case "personal" -> {
				nameTags.set(player, target, NameTag.builder().line(NameTagObject.text("Only you see this").color(0xFF55FF)).showToSelf(own).build());
				player.sendMessage(Component.text("Set a tag only you see on " + describe(target, player)));
			}
			case "save" -> {
				nameTags.set(target, NameTag.builder().line(NameTagObject.text("Saved tag").color(0x55FF55)).line("survives restarts").showToSelf(own).build(), true);
				player.sendMessage(Component.text("Saved a tag on " + describe(target, player)));
			}
			case "counter" -> {
				startCounter(plugin, nameTags, target, own);
				player.sendMessage(Component.text("Counting on " + describe(target, player) + " for 30 seconds (only line 2 is sent each time)"));
			}
			default -> {
				Function<Player, NameTag.Builder> preset = presets.get(sub);

				if (preset == null) {
					return usage(player);
				}

				NameTag.Builder builder = preset.apply(player);

				if (own) {
					builder.showToSelf(true);
				}

				if (sub.equals("invisible") && target instanceof LivingEntity living) {
					living.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30 * 20, 0));
				}

				try {
					nameTags.set(target, builder.build());
					player.sendMessage(Component.text("Name tag '" + sub + "' on " + describe(target, player) + (SkaffyAPI.get().isReady(player) ? "" : " (you don't have the mod, so you see vanilla's)")));
				} catch (IllegalArgumentException e) {
					player.sendMessage(Component.text("Rejected: " + e.getMessage()));
				}
			}
		}

		return true;
	}

	private void startCounter(Plugin plugin, NameTags nameTags, Entity target, boolean own) {
		stopCounter();
		nameTags.set(target, NameTag.builder().line(NameTagObject.text("Counter").color(0xFFAA00).bold()).line("0").line(NameTagObject.text("third line stays").color(0xAAAAAA)).showToSelf(own).build());
		int[] count = {0};
		counter = target.getScheduler().runAtFixedRate(plugin, task -> {
			count[0]++;

			if (count[0] > 30 || nameTags.get(target).isEmpty()) {
				task.cancel();
				return;
			}

			NameTagObject[] hearts = new NameTagObject[1 + count[0] % 10];
			hearts[0] = NameTagObject.text(count[0] + " ").color(0x55FF55);

			for (int i = 1; i < hearts.length; i++) {
				hearts[i] = NameTagObject.vanillaSprite("gui/sprites/hud/heart/full");
			}

			nameTags.setLine(target, 1, NameTagLine.of(hearts));
		}, null, 20, 20);
	}

	private void stopCounter() {
		if (counter != null) {
			counter.cancel();
			counter = null;
		}
	}

	private static Entity target(Player player) {
		Entity target = player.getTargetEntity(32);
		return target != null ? target : player;
	}

	private static String describe(Entity target, Player player) {
		return target == player ? "you" : target.getType().name().toLowerCase();
	}

	private boolean usage(Player player) {
		player.sendMessage(Component.text("/skaffytest nametag <" + String.join("|", presets.keySet()) + "> [self]"));
		player.sendMessage(Component.text("/skaffytest nametag <spawn|counter|personal|save|clear>"));
		return true;
	}

	List<String> tabComplete(String[] args) {
		if (args.length == 2) {
			return Stream.concat(presets.keySet().stream(), Stream.of("spawn", "counter", "personal", "save", "clear")).filter(option -> option.startsWith(args[1])).toList();
		}

		if (args.length == 3) {
			return Stream.of("self").filter(option -> option.startsWith(args[2])).toList();
		}

		return List.of();
	}

	private static BufferedImage coin() {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);

		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				double distance = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);

				if (distance <= 4) {
					image.setRGB(x, y, distance > 3 ? 0xFFB8860B : 0xFFFFD700);
				}
			}
		}

		return image;
	}

	private static BufferedImage wide() {
		BufferedImage image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);

		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 8; y++) {
				boolean border = x == 0 || y == 0 || x == 15 || y == 7;
				int red = 255 - x * 16;
				int green = x * 16;
				image.setRGB(x, y, border ? 0xFFFFFFFF : 0xFF000000 | red << 16 | green << 8);
			}
		}

		return image;
	}

	private static BufferedImage pulse() {
		int[] colors = {0xFFFF4040, 0xFFFFB040, 0xFF40FF40, 0xFF4080FF};
		BufferedImage image = new BufferedImage(8, 32, BufferedImage.TYPE_INT_ARGB);

		for (int frame = 0; frame < 4; frame++) {
			for (int x = 0; x < 8; x++) {
				for (int y = 0; y < 8; y++) {
					boolean border = x == 0 || y == 0 || x == 7 || y == 7;
					image.setRGB(x, frame * 8 + y, border ? 0xFFFFFFFF : colors[frame]);
				}
			}
		}

		return image;
	}
}
