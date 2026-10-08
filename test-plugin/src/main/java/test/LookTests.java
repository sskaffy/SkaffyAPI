package test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import me.skaffy.api.AssetRegistry;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.look.ArmType;
import me.skaffy.api.look.BodyPart;
import me.skaffy.api.look.LookCape;
import me.skaffy.api.look.LookSkin;
import me.skaffy.api.look.PlayerLook;
import me.skaffy.api.look.PlayerLooks;
import me.skaffy.api.look.SkinLayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class LookTests {
	private final Map<String, Supplier<PlayerLook>> presets = new LinkedHashMap<>();

	void register(SkaffyAPI api) {
		AssetRegistry assets = api.getAssets();
		assets.register("test_look_skin.png", SkaffyTest.png(skin()));
		assets.register("test_look_cape.png", SkaffyTest.png(cape(new Color(120, 40, 200), new Color(255, 210, 40))));
		assets.register("test_look_elytra.png", SkaffyTest.png(cape(new Color(30, 190, 200), new Color(255, 255, 255))));

		presets.put("zombie", () -> PlayerLook.entity(EntityType.ZOMBIE).build());
		presets.put("babyzombie", () -> PlayerLook.entity(EntityType.ZOMBIE).data("{IsBaby:1b}").build());
		presets.put("chicken", () -> PlayerLook.entity(EntityType.CHICKEN).build());
		presets.put("redsheep", () -> PlayerLook.entity(EntityType.SHEEP).data("{Color:14b}").build());
		presets.put("stone", () -> PlayerLook.entity("minecraft:falling_block").data("{BlockState:\"minecraft:stone\"}").build());
		presets.put("minecart", () -> PlayerLook.entity(EntityType.MINECART).build());
		presets.put("notch", () -> PlayerLook.player().skin(LookSkin.player("Notch")).build());
		presets.put("testskin", () -> PlayerLook.player().skin(LookSkin.asset("test_look_skin.png")).arms(ArmType.SLIM).cape(LookCape.asset("test_look_cape.png")).build());
		presets.put("cape", () -> PlayerLook.player().cape(LookCape.asset("test_look_cape.png")).build());
		presets.put("nocape", () -> PlayerLook.player().cape(LookCape.none()).build());
		presets.put("elytra", () -> PlayerLook.player().cape(LookCape.asset("test_look_cape.png")).elytra("test_look_elytra.png").build());
		presets.put("layersoff", () -> {
			PlayerLook.Builder look = PlayerLook.player();

			for (SkinLayer layer : SkinLayer.values()) {
				look.layer(layer, false);
			}

			return look.build();
		});
		presets.put("headless", () -> PlayerLook.player().hide(BodyPart.HEAD).build());
		presets.put("noarms", () -> PlayerLook.player().hide(BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM).build());
		presets.put("giant", () -> PlayerLook.player().scale(2).build());
		presets.put("tiny", () -> PlayerLook.player().scale(0.4f).build());
		presets.put("bighead", () -> PlayerLook.model("bighead").build());
		presets.put("ownhitbox", () -> PlayerLook.entity(EntityType.CHICKEN).ownHitbox(true).build());
		presets.put("bighitbox", () -> PlayerLook.entity(EntityType.CHICKEN).hitbox(2, 3).build());
		presets.put("notself", () -> PlayerLook.entity(EntityType.ZOMBIE).showToSelf(false).build());
	}

	boolean command(Player player, String[] args) {
		PlayerLooks looks = SkaffyAPI.get().getPlayerLooks();
		String sub = args.length > 1 ? args[1] : "";
		List<String> rest = List.of(args).subList(Math.min(2, args.length), args.length);
		boolean save = rest.contains("save");
		Player target = rest.stream().filter(arg -> !arg.equals("save")).findFirst().map(Bukkit::getPlayerExact).orElse(player);

		try {
			switch (sub) {
				case "reset" -> {
					boolean removed = looks.remove(target);
					player.sendMessage(Component.text(removed ? target.getName() + " looks like a player again" : target.getName() + " had no look"));
				}
				case "wave" -> {
					SkaffyAPI.get().getEntityModels().playAnimation(target, "animation.bighead.wave");
					player.sendMessage(Component.text("Waving (only with the bighead look)"));
				}
				default -> {
					Supplier<PlayerLook> preset = presets.get(sub);

					if (preset == null) {
						return usage(player);
					}

					looks.set(target, preset.get(), save);

					if (sub.equals("elytra")) {
						wearElytra(target);
					}

					player.sendMessage(Component.text(target.getName() + " now looks like " + sub + (save ? " (saved)" : "") + (target == player ? "; press F5 to see it" : "")));
				}
			}
		} catch (RuntimeException e) {
			player.sendMessage(Component.text("Couldn't do that: " + e.getMessage()));
			return usage(player);
		}

		return true;
	}

	private static void wearElytra(Player player) {
		ItemStack chest = player.getInventory().getChestplate();

		if (chest != null && chest.getType() == Material.ELYTRA) {
			return;
		}

		player.getInventory().setChestplate(new ItemStack(Material.ELYTRA));

		if (chest != null && !chest.getType().isAir()) {
			player.getInventory().addItem(chest).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
		}

		player.sendMessage(Component.text("Put an elytra on you: it should be cyan with a white stripe"));
	}

	List<String> tabComplete(String[] args) {
		Stream<String> options = switch (args.length) {
			case 2 -> Stream.concat(presets.keySet().stream(), Stream.of("reset", "wave"));
			default -> Stream.concat(Stream.of("save"), Bukkit.getOnlinePlayers().stream().map(Player::getName));
		};

		return options.filter(option -> option.startsWith(args[args.length - 1])).toList();
	}

	private boolean usage(Player player) {
		player.sendMessage(Component.text("/skaffytest look <" + String.join("|", presets.keySet()) + "> [save] [player] | reset [player] | wave [player]"));
		return true;
	}

	private static BufferedImage skin() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		fill(g, 0, 0, 32, 16, new Color(230, 180, 140));
		fill(g, 8, 8, 8, 8, new Color(240, 195, 155));
		fill(g, 9, 11, 2, 2, Color.WHITE);
		fill(g, 13, 11, 2, 2, Color.WHITE);
		fill(g, 10, 12, 1, 1, Color.BLACK);
		fill(g, 14, 12, 1, 1, Color.BLACK);
		fill(g, 10, 14, 4, 1, new Color(160, 60, 60));
		fill(g, 16, 16, 24, 16, new Color(40, 90, 220));
		fill(g, 40, 16, 16, 16, new Color(240, 200, 30));
		fill(g, 32, 48, 16, 16, new Color(240, 130, 30));
		fill(g, 0, 16, 16, 16, new Color(40, 160, 60));
		fill(g, 16, 48, 16, 16, new Color(20, 110, 40));

		fill(g, 32, 8, 32, 2, new Color(220, 30, 30));
		Color stripe = new Color(255, 255, 255, 220);

		for (int y = 33; y < 48; y += 3) {
			fill(g, 0, y, 56, 1, stripe);
		}

		for (int y = 49; y < 64; y += 3) {
			fill(g, 0, y, 16, 1, stripe);
			fill(g, 48, y, 16, 1, stripe);
		}

		g.dispose();
		return image;
	}

	private static BufferedImage cape(Color color, Color star) {
		BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		fill(g, 0, 0, 64, 32, color);
		fill(g, 5, 6, 2, 6, star);
		fill(g, 3, 8, 6, 2, star);
		fill(g, 27, 4, 14, 2, star);
		g.dispose();
		return image;
	}

	private static void fill(Graphics2D g, int x, int y, int width, int height, Color color) {
		g.setColor(color);
		g.fillRect(x, y, width, height);
	}
}
