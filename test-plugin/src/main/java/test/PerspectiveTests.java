package test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import me.skaffy.api.Easing;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.perspective.CameraView;
import me.skaffy.api.perspective.Perspective;
import me.skaffy.api.perspective.PlayerPerspective;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PerspectiveTests {
	private static final Map<String, Perspective> VANILLA = Map.of("first", Perspective.FIRST_PERSON, "back", Perspective.THIRD_PERSON_BACK, "front", Perspective.THIRD_PERSON_FRONT);
	private static final List<String> CAMERAS = List.of("behind", "front", "above", "shoulder", "point", "fixed", "entity", "tilt", "wall");

	boolean command(Player player, String[] args) {
		PlayerPerspective perspective = SkaffyAPI.get().getPerspective();
		String sub = args.length > 1 ? args[1] : "";
		List<String> rest = List.of(args).subList(Math.min(2, args.length), args.length);

		try {
			switch (sub) {
				case "first", "back", "front" -> {
					int duration = rest.isEmpty() ? 0 : Integer.parseInt(rest.getFirst());
					perspective.set(player, VANILLA.get(sub), duration, easing(rest, 1));
					player.sendMessage(Component.text("Perspective " + sub + (duration == 0 ? "" : " over " + duration + " ms")));
				}
				case "camera" -> {
					String preset = rest.getFirst();
					int duration = rest.size() > 1 ? Integer.parseInt(rest.get(1)) : 0;
					perspective.setCamera(player, camera(preset, player), duration, easing(rest, 2));
					player.sendMessage(Component.text("Camera " + preset + (duration == 0 ? "" : " over " + duration + " ms") + " (F5 does nothing now; /skaffytest perspective first ends it)"));
				}
				case "allow" -> {
					Set<Perspective> allowed = EnumSet.noneOf(Perspective.class);
					rest.forEach(name -> allowed.add(VANILLA.get(name)));
					allowed.remove(null);
					perspective.setAllowed(player, allowed);
					player.sendMessage(Component.text("F5 cycles through " + allowed));
				}
				case "allowall" -> {
					perspective.allowAll(player);
					player.sendMessage(Component.text("F5 cycles through all three again"));
				}
				case "get" -> perspective.get(player).whenComplete((value, error) -> player.sendMessage(Component.text(error != null ? "No answer: " + error : "You're in " + value)));
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

	private static CameraView camera(String preset, Player player) {
		return switch (preset) {
			case "behind" -> CameraView.at(0, 2.5, -5).turnWithPlayer(true).lookAtPlayer().build();
			case "front" -> CameraView.at(0, 1.6, 3).turnWithPlayer(true).lookAtPlayer().build();
			case "above" -> CameraView.at(0, 15, 0).look(0, 90).build();
			case "shoulder" -> CameraView.at(-0.7, 1.9, -2.5).turnWithPitch(true).lookWithPlayer().pullInFrontOfWalls(true).build();
			case "point" -> CameraView.at(6, 4, 6).lookAt(0, 1, 0).build();
			case "fixed" -> CameraView.at(4, 2, 0).look(90, 10).build();
			case "entity" -> {
				LivingEntity target = player.getWorld().getNearbyLivingEntities(player.getLocation(), 32).stream()
						.filter(entity -> entity != player)
						.min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
						.orElseThrow(() -> new IllegalArgumentException("no mob within 32 blocks"));
				player.sendMessage(Component.text("Watching the nearest mob: " + target.getName()));
				yield CameraView.at(0, 3, -4).turnWithPlayer(true).lookAt(target).build();
			}
			case "tilt" -> CameraView.at(0, 2, -4).turnWithPlayer(true).lookWithPlayer().roll(20).build();
			case "wall" -> CameraView.at(0, 1.6, -8).turnWithPlayer(true).lookAtPlayer().pullInFrontOfWalls(true).build();
			default -> throw new IllegalArgumentException("no camera preset " + preset);
		};
	}

	private static Easing easing(List<String> args, int index) {
		return args.size() > index ? Easing.valueOf(args.get(index).toUpperCase(Locale.ROOT)) : Easing.EASE_IN_OUT_SINE;
	}

	List<String> tabComplete(String[] args) {
		List<String> easings = Arrays.stream(Easing.values()).map(easing -> easing.name().toLowerCase(Locale.ROOT)).toList();
		Stream<String> options = switch (args.length) {
			case 2 -> Stream.of("first", "back", "front", "camera", "allow", "allowall", "get");
			case 3 -> switch (args[1]) {
				case "first", "back", "front" -> Stream.of("0", "500", "1500");
				case "camera" -> CAMERAS.stream();
				case "allow" -> VANILLA.keySet().stream();
				default -> Stream.empty();
			};
			case 4 -> switch (args[1]) {
				case "first", "back", "front" -> easings.stream();
				case "camera" -> Stream.of("0", "1000", "2000");
				case "allow" -> VANILLA.keySet().stream();
				default -> Stream.empty();
			};
			default -> args[1].equals("camera") ? easings.stream() : args[1].equals("allow") ? VANILLA.keySet().stream() : Stream.empty();
		};

		return options.filter(option -> option.startsWith(args[args.length - 1])).toList();
	}

	private static boolean usage(Player player) {
		player.sendMessage(Component.text("/skaffytest perspective first|back|front [ms] [easing] | camera <" + String.join("|", CAMERAS) + "> [ms] [easing] | allow <first|back|front...> | allowall | get"));
		return true;
	}
}
