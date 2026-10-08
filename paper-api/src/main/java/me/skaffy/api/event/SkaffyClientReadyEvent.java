package me.skaffy.api.event;

import io.papermc.paper.connection.PlayerConnection;
import me.skaffy.api.SkaffyClient;
import org.bukkit.event.HandlerList;

public class SkaffyClientReadyEvent extends SkaffyClientEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	public SkaffyClientReadyEvent(SkaffyClient client, PlayerConnection connection) {
		super(client, connection);
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
