package me.skaffy.api.event;

import io.papermc.paper.connection.PlayerConnection;
import me.skaffy.api.SkaffyClient;
import org.bukkit.event.HandlerList;

public class SkaffyClientFailedEvent extends SkaffyClientEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final String reason;

	public SkaffyClientFailedEvent(SkaffyClient client, PlayerConnection connection, String reason) {
		super(client, connection);
		this.reason = reason;
	}

	public String getReason() {
		return reason;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
