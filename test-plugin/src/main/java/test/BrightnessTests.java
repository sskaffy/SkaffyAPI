package test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import me.skaffy.api.Easing;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.brightness.BrightnessAnimation;
import me.skaffy.api.brightness.BrightnessChange;
import me.skaffy.api.brightness.PlayerBrightness;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

final class BrightnessTests {
	private static final List<String> FLAGS = List.of("nodarkness", "nonightvision");

	boolean command(Player player, String[] args) {
		PlayerBrightness brightness = SkaffyAPI.get().getBrightness();
		String sub = args.length > 1 ? args[1] : "";
		List<String> rest = List.of(args).subList(Math.min(2, args.length), args.length);

		try {
			switch (sub) {
				case "set" -> {
					BrightnessChange change = BrightnessChange.of(Float.parseFloat(rest.getFirst()))
							.withDarknessEffect(!rest.contains("nodarkness"))
							.withNightVision(!rest.contains("nonightvision"));
					brightness.set(player, change);
					player.sendMessage(Component.text("Server brightness set to " + rest.getFirst() + "% " + flags(rest)));
				}
				case "animate" -> {
					BrightnessAnimation.Builder animation = BrightnessAnimation.to(Float.parseFloat(rest.get(1)))
							.duration(Integer.parseInt(rest.get(2)))
							.darknessEffect(!rest.contains("nodarkness"))
							.nightVision(!rest.contains("nonightvision"));

					if (!rest.getFirst().equals("current")) {
						animation.from(Float.parseFloat(rest.getFirst()));
					}

					if (rest.size() > 3 && !FLAGS.contains(rest.get(3))) {
						animation.easing(Easing.valueOf(rest.get(3).toUpperCase(Locale.ROOT)));
					}

					brightness.animate(player, animation.build());
					player.sendMessage(Component.text("Animating the brightness " + rest.getFirst() + " -> " + rest.get(1) + "% over " + rest.get(2) + " ms " + flags(rest)));
				}
				case "reset" -> {
					int duration = rest.isEmpty() ? 0 : Integer.parseInt(rest.getFirst());
					Easing easing = rest.size() > 1 ? Easing.valueOf(rest.get(1).toUpperCase(Locale.ROOT)) : Easing.EASE_IN_OUT_SINE;
					brightness.reset(player, duration, easing);
					player.sendMessage(Component.text(duration == 0 ? "Brightness reset" : "Resetting the brightness over " + duration + " ms"));
				}
				case "setting" -> {
					brightness.setSetting(player, Float.parseFloat(rest.getFirst()));
					player.sendMessage(Component.text("Your Brightness setting is now " + rest.getFirst() + "%"));
				}
				case "get" -> brightness.get(player).whenComplete((info, error) -> player.sendMessage(Component.text(error != null
						? "No answer: " + error
						: "Setting " + Math.round(info.setting()) + "%, lit with " + Math.round(info.current()) + "%, server brightness " + (info.serverBrightnessActive() ? "active" : "not active"))));
				default -> {
					return usage(player);
				}
			}
		} catch (RuntimeException e) {
			player.sendMessage(Component.text("Couldn't do that: " + e.getMessage()));
			return usage(player);
		}

		return true;
	}

	List<String> tabComplete(String[] args) {
		List<String> easings = Arrays.stream(Easing.values()).map(easing -> easing.name().toLowerCase(Locale.ROOT)).toList();
		Stream<String> options = switch (args.length) {
			case 2 -> Stream.of("set", "animate", "reset", "setting", "get");
			case 3 -> switch (args[1]) {
				case "set" -> Stream.of("-100", "0", "50", "100", "300", "1000");
				case "animate" -> Stream.of("current", "0", "50");
				case "reset" -> Stream.of("0", "1000", "3000");
				case "setting" -> Stream.of("0", "50", "100");
				default -> Stream.empty();
			};
			case 4 -> switch (args[1]) {
				case "set" -> FLAGS.stream();
				case "animate" -> Stream.of("-100", "0", "100", "1000");
				case "reset" -> easings.stream();
				default -> Stream.empty();
			};
			case 5 -> args[1].equals("animate") ? Stream.of("1000", "3000", "10000") : args[1].equals("set") ? FLAGS.stream() : Stream.empty();
			case 6 -> args[1].equals("animate") ? Stream.concat(easings.stream(), FLAGS.stream()) : FLAGS.stream();
			default -> FLAGS.stream();
		};

		return options.filter(option -> option.startsWith(args[args.length - 1])).toList();
	}

	private static String flags(List<String> args) {
		List<String> used = args.stream().filter(FLAGS::contains).toList();
		return used.isEmpty() ? "" : used.toString();
	}

	private static boolean usage(Player player) {
		player.sendMessage(Component.text("/skaffytest brightness set <percent> [nodarkness] [nonightvision] | animate <from|current> <to> <ms> [easing] [flags] | reset [ms] [easing] | setting <0-100> | get"));
		return true;
	}
}
