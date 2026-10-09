package me.skaffy.paper;

import java.util.Collection;
import java.util.Comparator;

import me.skaffy.api.SkaffyClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

final class SkaffyCommand implements CommandExecutor {
	private final ClientManager clients;

	SkaffyCommand(ClientManager clients) {
		this.clients = clients;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		Collection<PaperClient> all = clients.all();
		sender.sendMessage(Component.text("Skaffy's API: " + all.size() + " of " + Bukkit.getOnlinePlayers().size() + " players have the mod", NamedTextColor.GOLD));

		all.stream().sorted(Comparator.comparing(PaperClient::getPlayerName)).forEach(client -> {
			Component state = switch (client.getState()) {
				case READY -> Component.text("ready", NamedTextColor.GREEN);
				case FAILED -> Component.text("failed: " + client.getFailureReason().orElse("unknown"), NamedTextColor.RED);
				case REGISTERING, LOADING -> Component.text(client.getState().name().toLowerCase(), NamedTextColor.YELLOW);
			};

			sender.sendMessage(Component.text(" " + client.getPlayerName() + " (" + client.getModVersion() + "): ", NamedTextColor.GRAY).append(state));
		});

		return true;
	}
}
