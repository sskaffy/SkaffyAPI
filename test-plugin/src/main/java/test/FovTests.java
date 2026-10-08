package test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.fov.FovAnimation;
import me.skaffy.api.fov.FovChange;
import me.skaffy.api.fov.FovEasing;
import me.skaffy.api.fov.FovValue;
import me.skaffy.api.fov.PlayerFov;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

final class FovTests {
	private static final List<String> FLAGS = List.of("noeffects", "hands", "sensitivity");

	boolean command(Player player, String[] args) {
		PlayerFov fov = SkaffyAPI.get().getFov();
		String sub = args.length > 1 ? args[1] : "";
		List<String> rest = List.of(args).subList(Math.min(2, args.length), args.length);

		try {
			switch (sub) {
				case "set" -> {
					FovChange change = FovChange.of(value(rest.getFirst()))
							.withVanillaEffects(!rest.contains("noeffects"))
							.withZoomHands(rest.contains("hands"))
							.withScaleSensitivity(rest.contains("sensitivity"));
					fov.set(player, change);
					player.sendMessage(Component.text("Server FOV set to " + rest.getFirst() + " " + flags(rest)));
				}
				case "animate" -> {
					FovAnimation.Builder animation = FovAnimation.to(value(rest.get(1)))
							.duration(Integer.parseInt(rest.get(2)))
							.vanillaEffects(!rest.contains("noeffects"))
							.zoomHands(rest.contains("hands"))
							.scaleSensitivity(rest.contains("sensitivity"));

					if (!rest.getFirst().equals("current")) {
						animation.from(value(rest.getFirst()));
					}

					if (rest.size() > 3 && !FLAGS.contains(rest.get(3))) {
						animation.easing(FovEasing.valueOf(rest.get(3).toUpperCase(Locale.ROOT)));
					}

					fov.animate(player, animation.build());
					player.sendMessage(Component.text("Animating the FOV " + rest.getFirst() + " -> " + rest.get(1) + " over " + rest.get(2) + " ms " + flags(rest)));
				}
				case "reset" -> {
					int duration = rest.isEmpty() ? 0 : Integer.parseInt(rest.getFirst());
					FovEasing easing = rest.size() > 1 ? FovEasing.valueOf(rest.get(1).toUpperCase(Locale.ROOT)) : FovEasing.EASE_IN_OUT_SINE;
					fov.reset(player, duration, easing);
					player.sendMessage(Component.text(duration == 0 ? "FOV reset" : "Resetting the FOV over " + duration + " ms"));
				}
				case "get" -> fov.get(player).whenComplete((info, error) -> player.sendMessage(Component.text(error != null
						? "No answer: " + error
						: "FOV setting " + info.fovSetting() + ", effects " + Math.round(info.effectScale() * 100) + "%, on screen "
								+ String.format(Locale.ROOT, "%.1f", info.currentFov()) + "°, server FOV " + (info.serverFovActive() ? "active" : "not active"))));
				case "setting" -> {
					fov.setFovSetting(player, Integer.parseInt(rest.getFirst()));
					player.sendMessage(Component.text("Your FOV setting is now " + rest.getFirst()));
				}
				case "effects" -> {
					fov.setEffectScale(player, Integer.parseInt(rest.getFirst()) / 100f);
					player.sendMessage(Component.text("Your FOV effects are now " + rest.getFirst() + "%"));
				}
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
		List<String> easings = Arrays.stream(FovEasing.values()).map(easing -> easing.name().toLowerCase(Locale.ROOT)).toList();
		Stream<String> options = switch (args.length) {
			case 2 -> Stream.of("set", "animate", "reset", "get", "setting", "effects");
			case 3 -> switch (args[1]) {
				case "set" -> Stream.of("30", "90", "0.5x", "1.5x");
				case "animate" -> Stream.of("current", "70", "1x");
				case "reset" -> Stream.of("0", "500", "1000");
				case "setting" -> Stream.of("70", "90", "110");
				case "effects" -> Stream.of("0", "50", "100");
				default -> Stream.empty();
			};
			case 4 -> switch (args[1]) {
				case "set" -> FLAGS.stream();
				case "animate" -> Stream.of("20", "30", "120", "0.25x");
				case "reset" -> easings.stream();
				default -> Stream.empty();
			};
			case 5 -> args[1].equals("animate") ? Stream.of("500", "1000", "3000") : args[1].equals("set") ? FLAGS.stream() : Stream.empty();
			case 6 -> args[1].equals("animate") ? Stream.concat(easings.stream(), FLAGS.stream()) : FLAGS.stream();
			default -> FLAGS.stream();
		};

		return options.filter(option -> option.startsWith(args[args.length - 1])).toList();
	}

	private static FovValue value(String text) {
		return text.endsWith("x") ? FovValue.multiplier(Float.parseFloat(text.substring(0, text.length() - 1))) : FovValue.degrees(Float.parseFloat(text));
	}

	private static String flags(List<String> args) {
		List<String> used = args.stream().filter(FLAGS::contains).toList();
		return used.isEmpty() ? "" : used.toString();
	}

	private static boolean usage(Player player) {
		player.sendMessage(Component.text("/skaffytest fov set <30|0.5x> [noeffects] [hands] [sensitivity] | animate <from|current> <to> <ms> [easing] [flags] | reset [ms] [easing] | get | setting <30-110> | effects <0-100>"));
		return true;
	}
}
