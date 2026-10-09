package test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.event.gui.SkaffyGuiCloseEvent;
import me.skaffy.api.event.gui.SkaffyGuiOpenEvent;
import me.skaffy.api.gui.SkaffyGuis;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

final class GuiTests implements Listener {
	private static final List<String> PRESETS = List.of("showcase", "shop", "search", "settings", "scroll", "animations");
	private static final List<String> OTHERS = List.of("close", "coins", "replace", "link", "state", "resend", "broken", "crash");
	private static final List<String> FAKE_PLAYERS = List.of("Notch", "jeb_", "Dinnerbone", "Grumm", "Technoblade", "Dream", "Skeppy", "BadBoyHalo", "Tommyinnit", "Ph1LzA");

	record Offer(String id, String name, String item, int price) {
	}

	private static final List<Offer> OFFERS = List.of(
			new Offer("apple", "Golden Apple", "minecraft:golden_apple", 25),
			new Offer("sword", "Sharp Sword", "minecraft:diamond_sword[enchantments={sharpness:5}]", 120),
			new Offer("pearls", "Ender Pearls", "minecraft:ender_pearl", 40),
			new Offer("rockets", "Rockets", "minecraft:firework_rocket", 15),
			new Offer("totem", "Totem", "minecraft:totem_of_undying", 300),
			new Offer("beacon", "Beacon", "minecraft:beacon", 500),
			new Offer("elytra", "Elytra", "minecraft:elytra", 800),
			new Offer("cake", "Cake", "minecraft:cake", 5));

	private final Map<UUID, Integer> coins = new ConcurrentHashMap<>();
	private JavaPlugin plugin;

	void register(SkaffyAPI api, JavaPlugin owner) {
		plugin = owner;
		SkaffyGuis guis = api.getGuis();
		List<String> added = guis.addFiles(owner, "guis");
		owner.getLogger().info("GUI files: " + added);

		guis.onMessage("skaffytest.Shop", "buy", (player, message) -> {
			String id = message.getString(0);
			Offer offer = OFFERS.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);

			if (offer == null) {
				return;
			}

			int balance = coins.getOrDefault(player.getUniqueId(), 100);

			if (balance < offer.price()) {
				player.sendMessage(Component.text("Not enough coins (the server checked)", NamedTextColor.RED));
				return;
			}

			coins.put(player.getUniqueId(), balance - offer.price());
			guis.run(player, "skaffytest.Shop", "setCoins", balance - offer.price());
			guis.run(player, "skaffytest.Shop", "bought", offer.name());
		});

		guis.onMessage("skaffytest.Search", "search", (player, message) -> {
			String query = message.getString(0) == null ? "" : message.getString(0).toLowerCase(Locale.ROOT);
			List<String> names = Stream.concat(Bukkit.getOnlinePlayers().stream().map(Player::getName), FAKE_PLAYERS.stream())
					.distinct()
					.filter(name -> name.toLowerCase(Locale.ROOT).contains(query))
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.toList();
			guis.run(player, "skaffytest.Search", "showResults", names);
		});

		guis.onMessage("skaffytest.Search", "pick", (player, message) -> {
			player.sendMessage(Component.text("You picked " + message.getString(0)));
			guis.close(player);
		});

		guis.onMessage("skaffytest.Settings", "save", (player, message) -> player.sendMessage(Component.text("Saved settings: " + message.values(), NamedTextColor.GREEN)));
		guis.onMessage("skaffytest.Showcase", "typed", (player, message) -> player.sendMessage(Component.text("The showcase sent: " + message.getString(0))));
	}

	boolean command(Player player, String[] args) {
		SkaffyGuis guis = SkaffyAPI.get().getGuis();
		String sub = args.length > 1 ? args[1] : "";

		if (!guis.isSupported(player) && !sub.equals("state")) {
			player.sendMessage(Component.text("Your client doesn't support Skaffy's API GUIs", NamedTextColor.RED));
			return true;
		}

		switch (sub) {
			case "showcase" -> guis.open(player, "skaffytest.Showcase", "main");
			case "shop" -> guis.open(player, "skaffytest.Shop", "main", coins.getOrDefault(player.getUniqueId(), 100), OFFERS);
			case "search" -> guis.open(player, "skaffytest.Search", "main");
			case "settings" -> guis.open(player, "skaffytest.Settings", "main", player.getName(), 40, true, "Normal");
			case "scroll" -> guis.open(player, "skaffytest.Scroll", "main");
			case "animations" -> guis.open(player, "skaffytest.Animations", "main");
			case "close" -> guis.close(player);
			case "coins" -> {
				int amount = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
				guis.open(player, "skaffytest.Shop", "main", coins.getOrDefault(player.getUniqueId(), 100), OFFERS);
				coins.put(player.getUniqueId(), amount);
				Bukkit.getScheduler().runTaskLater(plugin, () -> guis.call(player, "skaffytest.Shop", "setCoins", amount).whenComplete((value, error) ->
						player.sendMessage(error == null ? Component.text("setCoins(" + amount + ") ran") : Component.text("setCoins failed: " + error.getMessage(), NamedTextColor.RED))), 40);
			}
			case "replace" -> {
				guis.replaceMethod(player, "skaffytest.Shop", "void showCoins() { coinText.text(\"Wallet: \" + coins + \" (replaced)\"); }");
				guis.open(player, "skaffytest.Shop", "main", coins.getOrDefault(player.getUniqueId(), 100), OFFERS);
				Bukkit.getScheduler().runTaskLater(plugin, () -> guis.run(player, "skaffytest.Shop", "setCoins", coins.getOrDefault(player.getUniqueId(), 100)), 40);
			}
			case "link" -> guis.openLink(player, "https://papermc.io");
			case "state" -> player.sendMessage(Component.text("Supported: " + guis.isSupported(player) + ", open: "
					+ guis.getOpen(player).map(open -> open.className() + "." + open.screen()).orElse("nothing") + ", files: " + guis.getFiles()));
			case "resend" -> player.sendMessage(Component.text("Sent again: " + guis.addFiles(plugin, "guis")));
			case "broken" -> {
				guis.addFile("skaffytest.Broken", "package skaffytest;\n\nimport skaffy.gui.*;\n\npublic class Broken {\n    @Gui\n    public void main() {\n        int coins = \"many\";\n        box().colour(#FF0000);\n    }\n}\n");
				Bukkit.getScheduler().runTaskLater(plugin, () -> guis.open(player, "skaffytest.Broken", "main"), 5);
				player.sendMessage(Component.text("Sent a file with mistakes; the console shows the compile errors"));
			}
			case "crash" -> {
				guis.addFile("skaffytest.Crash", "package skaffytest;\n\nimport skaffy.gui.*;\n\npublic class Crash {\n    @Gui\n    public void main() {\n        add(text(\"The next line throws\"));\n        List<Integer> numbers = new ArrayList<>();\n        helper(numbers);\n    }\n\n    void helper(List<Integer> numbers) {\n        numbers.get(3);\n    }\n}\n");
				Bukkit.getScheduler().runTaskLater(plugin, () -> guis.open(player, "skaffytest.Crash", "main"), 5);
				player.sendMessage(Component.text("Opened a GUI that throws; the console shows the error with its trace"));
			}
			default -> {
				player.sendMessage(Component.text("/skaffytest gui <" + String.join("|", PRESETS) + "|" + String.join("|", OTHERS) + ">"));
				return true;
			}
		}

		return true;
	}

	List<String> tabComplete(String[] args) {
		if (args.length == 2) {
			List<String> options = new ArrayList<>(PRESETS);
			options.addAll(OTHERS);
			return options.stream().filter(option -> option.startsWith(args[1])).toList();
		}

		return List.of();
	}

	@EventHandler
	public void onOpen(SkaffyGuiOpenEvent event) {
		event.getPlayer().sendActionBar(Component.text("GUI " + event.getClassName() + "." + event.getScreen() + " opened", NamedTextColor.GRAY));
	}

	@EventHandler
	public void onClose(SkaffyGuiCloseEvent event) {
		event.getPlayer().sendActionBar(Component.text("GUI " + event.getClassName() + " closed: " + event.getReason(), NamedTextColor.GRAY));
	}
}
