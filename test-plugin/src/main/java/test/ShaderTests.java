package test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.skaffy.api.Easing;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.event.shader.SkaffyShaderErrorEvent;
import me.skaffy.api.event.shader.SkaffyShaderStateEvent;
import me.skaffy.api.shader.SkaffyShaders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

final class ShaderTests implements Listener {
	private static final Map<String, String> PRESETS = new LinkedHashMap<>();

	static {
		for (String name : List.of("Grayscale", "Vignette", "Invert", "Blur", "Bloom", "DepthFog", "Outline", "Dream", "Trail", "Pulse", "Stages",
				"Textures", "Broken", "Waving", "Sunlight", "Toon", "Sky", "Pretty", "Normals")) {
			PRESETS.put(name.equals("DepthFog") ? "fog" : name.toLowerCase(Locale.ROOT), name);
		}

		PRESETS.put("vista", "");
		PRESETS.put("shadows", "ShadowDebug");
		PRESETS.put("lights", "LightDebug");
		PRESETS.put("buffers", "BufferDebug");
	}

	private final Map<UUID, ScheduledTask> pulses = new ConcurrentHashMap<>();
	private JavaPlugin plugin;

	void register(SkaffyAPI api, JavaPlugin owner) {
		plugin = owner;
		BufferedImage noise = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		Random random = new Random(26);

		for (int x = 0; x < 128; x++) {
			for (int y = 0; y < 128; y++) {
				int value = random.nextInt(256);
				noise.setRGB(x, y, 0xFF000000 | value << 16 | value << 8 | value);
			}
		}

		api.getAssets().register("skaffytest_noise.png", SkaffyTest.png(noise));
		List<String> added = api.getShaders().addFiles(owner, "shaders");
		owner.getLogger().info("Shader files: " + added);
	}

	private static String className(String preset) {
		return preset.equals("vista") ? SkaffyShaders.VISTA : "skaffytest." + PRESETS.get(preset);
	}

	boolean command(Player player, String[] args) {
		SkaffyShaders shaders = SkaffyAPI.get().getShaders();

		if (!shaders.isSupported(player)) {
			player.sendMessage(Component.text("Your client doesn't support Skaffy's API shaders", NamedTextColor.RED));
			return true;
		}

		if (args.length < 2) {
			player.sendMessage(Component.text("/skaffytest shader <" + String.join("|", PRESETS.keySet()) + "> [order] | off [preset] | list"
					+ " | set <preset> <uniform> <values...> | animate <preset> <uniform> <ms> <values...> | resend <preset>"));
			return true;
		}

		String action = args[1];

		switch (action) {
			case "off" -> {
				if (args.length > 2 && args[2].equals("vista")) {
					shaders.setVista(player, false);
					player.sendMessage(Component.text("Turned off the built-in Vista for this session"));
				} else if (args.length > 2 && PRESETS.containsKey(args[2])) {
					shaders.disable(player, className(args[2]));
					stopPulse(player, args[2]);
					player.sendMessage(Component.text("Turned off " + args[2]));
				} else {
					shaders.disableAll(player);
					stopPulse(player, "pulse");
					player.sendMessage(Component.text("Turned every shader off"));
				}
			}
			case "list" -> {
				Map<String, Integer> enabled = shaders.getEnabled(player);

				if (enabled.isEmpty()) {
					player.sendMessage(Component.text("No shaders on"));
				}

				enabled.forEach((className, order) -> player.sendMessage(Component.text(className + " (order " + order + ") "
						+ (shaders.isRunning(player, className) ? "running" : "not running yet"), shaders.isRunning(player, className) ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
			}
			case "set", "animate" -> uniform(player, shaders, args, action.equals("animate"));
			case "resend" -> {
				if (args.length < 3 || !PRESETS.containsKey(args[2]) || args[2].equals("vista")) {
					player.sendMessage(Component.text("/skaffytest shader resend <preset> (not vista: it's built into the mod)"));
					return true;
				}

				String source = read(PRESETS.get(args[2]));

				if (source == null) {
					player.sendMessage(Component.text("Couldn't read the file", NamedTextColor.RED));
					return true;
				}

				shaders.addFile(className(args[2]), source + "\n// resent " + System.currentTimeMillis() + "\n");
				player.sendMessage(Component.text("Sent " + args[2] + " again: an enabled effect rebuilds and keeps its uniform values"));
			}
			default -> {
				if (!PRESETS.containsKey(action)) {
					player.sendMessage(Component.text("Unknown preset " + action, NamedTextColor.RED));
					return true;
				}

				if (action.equals("vista")) {
					shaders.setVista(player, true);
					player.sendMessage(Component.text("Turned on the built-in Vista for this session (/skaffytest vista off turns it off)"));
					return true;
				}

				int order = 0;

				if (args.length > 2) {
					try {
						order = Integer.parseInt(args[2]);
					} catch (NumberFormatException e) {
						player.sendMessage(Component.text("The order is a whole number", NamedTextColor.RED));
						return true;
					}
				}

				shaders.enable(player, className(action), order);
				player.sendMessage(Component.text("Turned on " + action + " (order " + order + "); it starts when your client has built it"));

				if (action.equals("pulse")) {
					startPulse(player, shaders);
				}
			}
		}

		return true;
	}

	private void uniform(Player player, SkaffyShaders shaders, String[] args, boolean animate) {
		int first = animate ? 5 : 4;

		if (args.length <= first || !PRESETS.containsKey(args[2])) {
			player.sendMessage(Component.text(animate ? "/skaffytest shader animate <preset> <uniform> <ms> <values...>" : "/skaffytest shader set <preset> <uniform> <values...>"));
			return;
		}

		List<Object> values = new ArrayList<>();

		for (int i = first; i < args.length; i++) {
			String text = args[i];

			if (text.startsWith("#") || text.equals("true") || text.equals("false")) {
				values.add(text.startsWith("#") ? text : Boolean.parseBoolean(text));
				continue;
			}

			try {
				values.add(text.contains(".") ? (Object) Double.parseDouble(text) : (Object) Integer.parseInt(text));
			} catch (NumberFormatException e) {
				player.sendMessage(Component.text(text + " isn't a number, true/false or #RRGGBB", NamedTextColor.RED));
				return;
			}
		}

		Object value = values.size() == 1 ? values.getFirst() : values;

		try {
			if (animate) {
				shaders.animate(player, className(args[2]), args[3], value, Integer.parseInt(args[4]), Easing.EASE_IN_OUT_SINE);
			} else {
				shaders.set(player, className(args[2]), args[3], value);
			}

			player.sendMessage(Component.text((animate ? "Animating " : "Set ") + args[3] + " to " + value));
		} catch (IllegalArgumentException e) {
			player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
		}
	}

	private void startPulse(Player player, SkaffyShaders shaders) {
		stopPulse(player, "pulse");
		boolean[] up = {true};
		ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
			shaders.animate(player, className("pulse"), "strength", up[0] ? 1.0 : 0.0, up[0] ? 450 : 750, up[0] ? Easing.EASE_OUT_QUAD : Easing.EASE_IN_QUAD);
			up[0] = !up[0];
		}, null, 1, 12);

		if (task != null) {
			pulses.put(player.getUniqueId(), task);
		}
	}

	private void stopPulse(Player player, String preset) {
		if (preset.equals("pulse")) {
			ScheduledTask task = pulses.remove(player.getUniqueId());

			if (task != null) {
				task.cancel();
			}
		}
	}

	private String read(String name) {
		try (InputStream stream = plugin.getResource("shaders/skaffytest/" + name.replace('.', '/') + ".sfy")) {
			return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	@EventHandler
	public void onShaderError(SkaffyShaderErrorEvent event) {
		String message = event.getMessage();
		String shortMessage = message.length() > 400 ? message.substring(0, 400) + "..." : message;
		event.getPlayer().sendMessage(Component.text("[" + event.getClassName() + "] " + event.getKind().name().toLowerCase(Locale.ROOT) + ": " + shortMessage, NamedTextColor.RED));
	}

	@EventHandler
	public void onShaderState(SkaffyShaderStateEvent event) {
		event.getPlayer().sendMessage(Component.text(event.getClassName() + (event.isRunning() ? " is running" : " stopped"), event.isRunning() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
	}

	List<String> tabComplete(String[] args) {
		if (args.length == 2) {
			return Stream.concat(PRESETS.keySet().stream(), Stream.of("off", "list", "set", "animate", "resend")).filter(option -> option.startsWith(args[1])).toList();
		}

		if (args.length == 3 && List.of("off", "set", "animate", "resend").contains(args[1])) {
			return PRESETS.keySet().stream().filter(option -> option.startsWith(args[2])).toList();
		}

		return List.of();
	}
}
