package test;

import java.util.ArrayList;
import java.util.List;

import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.event.shader.SkaffyVistaChangeEvent;
import me.skaffy.api.shader.SkaffyShaders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

final class VistaTests implements Listener {
	boolean command(Player player, String[] args) {
		SkaffyShaders shaders = SkaffyAPI.get().getShaders();

		if (!shaders.isSupported(player)) {
			player.sendMessage(Component.text("Your client doesn't support Skaffy's API shaders", NamedTextColor.RED));
			return true;
		}

		String action = args.length > 1 ? args[1] : "";
		boolean lock = args.length > 2 && args[args.length - 1].equals("lock");

		switch (action) {
			case "on", "off" -> {
				shaders.setVista(player, action.equals("on"), lock);
				player.sendMessage(Component.text("Vista " + action + " for this session" + (lock ? ", locked" : "")));
			}
			case "reset" -> {
				shaders.resetVista(player);
				player.sendMessage(Component.text("Vista is back to your own setting, unlocked"));
			}
			case "distance" -> {
				if (args.length < 3) {
					player.sendMessage(Component.text("/skaffytest vista distance <2-32|reset> [lock]"));
					return true;
				}

				int chunks;

				try {
					chunks = args[2].equals("reset") ? 0 : Integer.parseInt(args[2]);
					shaders.setShaderDistance(player, chunks, lock);
				} catch (IllegalArgumentException e) {
					player.sendMessage(Component.text(e instanceof NumberFormatException ? "The distance is a whole number of chunks" : e.getMessage(), NamedTextColor.RED));
					return true;
				}

				player.sendMessage(Component.text(chunks == 0 ? "Shader Distance is back to your own setting" + (lock ? ", locked" : ", unlocked")
						: "Shader Distance " + chunks + " chunks for this session" + (lock ? ", locked" : "")));
			}
			case "set" -> set(player, shaders, args);
			case "status" -> {
				Boolean on = shaders.isVistaOn(player);
				player.sendMessage(Component.text("Vista: " + (on == null ? "unknown" : on ? "on" : "off") + (shaders.isRunning(player, SkaffyShaders.VISTA) ? " (running)" : "")
						+ ", Shader Distance: " + shaders.getShaderDistance(player) + " chunks"));
			}
			default -> player.sendMessage(Component.text("/skaffytest vista on|off [lock] | reset | distance <2-32|reset> [lock] | set <uniform> <values...> | status"));
		}

		return true;
	}

	private static void set(Player player, SkaffyShaders shaders, String[] args) {
		if (args.length < 4) {
			player.sendMessage(Component.text("/skaffytest vista set <uniform> <values...>"));
			return;
		}

		List<Object> values = new ArrayList<>();

		for (int i = 3; i < args.length; i++) {
			try {
				values.add(args[i].equals("true") || args[i].equals("false") ? (Object) Boolean.parseBoolean(args[i])
						: args[i].startsWith("#") ? args[i] : args[i].contains(".") ? (Object) Double.parseDouble(args[i]) : (Object) Integer.parseInt(args[i]));
			} catch (NumberFormatException e) {
				player.sendMessage(Component.text(args[i] + " isn't a number, true/false or #RRGGBB", NamedTextColor.RED));
				return;
			}
		}

		Object value = values.size() == 1 ? values.getFirst() : values;

		try {
			shaders.set(player, SkaffyShaders.VISTA, args[2], value);
			player.sendMessage(Component.text("Set Vista's " + args[2] + " to " + value + " (until you leave)"));
		} catch (IllegalArgumentException e) {
			player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
		}
	}

	@EventHandler
	public void onVistaChange(SkaffyVistaChangeEvent event) {
		event.getPlayer().sendMessage(Component.text("[Vista] " + (event.isEnabled() ? "on" : "off") + ", Shader Distance " + event.getShaderDistance() + " chunks"
				+ (event.isByPlayer() ? " (you changed it in Video Settings)" : " (your client's state)"), NamedTextColor.AQUA));
	}

	List<String> tabComplete(String[] args) {
		if (args.length == 2) {
			return List.of("on", "off", "reset", "distance", "set", "status").stream().filter(option -> option.startsWith(args[1])).toList();
		}

		if (args.length == 3 && (args[1].equals("on") || args[1].equals("off"))) {
			return List.of("lock").stream().filter(option -> option.startsWith(args[2])).toList();
		}

		if (args.length == 3 && args[1].equals("distance")) {
			return List.of("4", "8", "16", "32", "reset").stream().filter(option -> option.startsWith(args[2])).toList();
		}

		if (args.length == 4 && args[1].equals("distance")) {
			return List.of("lock").stream().filter(option -> option.startsWith(args[3])).toList();
		}

		if (args.length == 3 && args[1].equals("set")) {
			return List.of("wind", "shadowDistance", "shadowResolution").stream().filter(option -> option.startsWith(args[2])).toList();
		}

		return List.of();
	}
}
