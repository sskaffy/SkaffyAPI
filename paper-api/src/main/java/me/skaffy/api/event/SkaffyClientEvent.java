package me.skaffy.api.event;

import io.papermc.paper.connection.PlayerConnection;
import me.skaffy.api.SkaffyClient;
import org.bukkit.event.Event;

public abstract class SkaffyClientEvent extends Event {
	private final SkaffyClient client;
	private final PlayerConnection connection;

	protected SkaffyClientEvent(SkaffyClient client, PlayerConnection connection) {
		super(true);
		this.client = client;
		this.connection = connection;
	}

	public SkaffyClient getClient() {
		return client;
	}

	public PlayerConnection getConnection() {
		return connection;
	}
}
