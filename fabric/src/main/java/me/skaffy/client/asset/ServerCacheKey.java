package me.skaffy.client.asset;

import java.util.Locale;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class ServerCacheKey {
	private ServerCacheKey() {
	}

	public static String of(ServerData server) {
		return server == null ? "local" : of(server.ip);
	}

	public static String of(String address) {
		ServerAddress parsed = ServerAddress.parseString(address.trim());
		String key = parsed.getHost().toLowerCase(Locale.ROOT);

		if (parsed.getPort() != 25565) {
			key += "_" + parsed.getPort();
		}

		key = key.replaceAll("[^a-z0-9._-]", "_");

		return key.replace(".", "").isEmpty() ? "_" + key : key;
	}
}
