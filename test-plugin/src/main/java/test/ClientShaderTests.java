package test;

import java.util.List;

import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.event.shader.SkaffyClientShaderChangeEvent;
import me.skaffy.api.shader.ClientShaderMode;
import me.skaffy.api.shader.SkaffyShaders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

final class ClientShaderTests implements Listener {
	boolean command(Player player, String[] args) {
		SkaffyShaders shaders = SkaffyAPI.get().getShaders();

		if (!shaders.isSupported(player)) {
			player.sendMessage(Component.text("Your client doesn't support Skaffy's API shaders", NamedTextColor.RED));
			return true;
		}

		String action = args.length > 1 ? args[1] : "";

		switch (action) {
			case "allow" -> {
				shaders.setClientShaders(player, ClientShaderMode.ALLOWED);
				player.sendMessage(Component.text("Your own shader is allowed again (the one you used last comes back)"));
			}
			case "off" -> {
				shaders.setClientShaders(player, ClientShaderMode.OFF);
				player.sendMessage(Component.text("Turned your own shader off; you can pick one again in Video Settings → Shaders"));
			}
			case "force" -> {
				shaders.setClientShaders(player, ClientShaderMode.FORCED_OFF);
				player.sendMessage(Component.text("Forced your own shader off: the Shaders button can't be used until /skaffytest clientshaders allow or you leave"));
			}
			case "status" -> {
				Boolean on = shaders.isClientShaderOn(player);
				player.sendMessage(Component.text("Mode: " + shaders.getClientShaderMode(player) + ", your shader: " + (on == null ? "unknown" : on ? "on" : "off")));
			}
			default -> player.sendMessage(Component.text("/skaffytest clientshaders allow | off | force | status"));
		}

		return true;
	}

	@EventHandler
	public void onClientShaderChange(SkaffyClientShaderChangeEvent event) {
		event.getPlayer().sendMessage(Component.text("[Client shader] " + (event.isShaderOn() ? "on" : "off") + (event.isByPlayer() ? " (you changed it)" : " (your client's state)"),
				NamedTextColor.AQUA));
	}

	List<String> tabComplete(String[] args) {
		if (args.length == 2) {
			return List.of("allow", "off", "force", "status").stream().filter(option -> option.startsWith(args[1])).toList();
		}

		return List.of();
	}
}
