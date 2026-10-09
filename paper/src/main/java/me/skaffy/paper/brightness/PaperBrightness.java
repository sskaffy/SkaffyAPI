package me.skaffy.paper.brightness;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import me.skaffy.api.Easing;
import me.skaffy.api.brightness.BrightnessAnimation;
import me.skaffy.api.brightness.BrightnessChange;
import me.skaffy.api.brightness.BrightnessInfo;
import me.skaffy.api.brightness.PlayerBrightness;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.brightness.BrightnessCodec;
import me.skaffy.protocol.brightness.BrightnessPacket;
import me.skaffy.protocol.brightness.BrightnessPacket.AnimateBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.Options;
import me.skaffy.protocol.brightness.BrightnessPacket.QueryBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.ResetBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.SetBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.SetSetting;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PaperBrightness implements PlayerBrightness, PluginMessageListener {
	private static final long QUERY_TIMEOUT_SECONDS = 5;

	private final Plugin plugin;
	private final Predicate<UUID> ready;
	private final AtomicInteger nextRequest = new AtomicInteger();
	private final Map<Integer, Query> queries = new ConcurrentHashMap<>();

	public PaperBrightness(Plugin plugin, Predicate<UUID> ready) {
		this.plugin = plugin;
		this.ready = ready;
	}

	@Override
	public void set(Player player, BrightnessChange change) {
		send(player, new SetBrightness(change.brightness(), new Options(change.darknessEffect(), change.nightVision())));
	}

	@Override
	public void animate(Player player, BrightnessAnimation animation) {
		send(player, new AnimateBrightness(animation.getFrom(), animation.getTo(), animation.getDurationMillis(), easing(animation.getEasing()),
				new Options(animation.hasDarknessEffect(), animation.hasNightVision())));
	}

	@Override
	public void reset(Player player, int durationMillis, Easing easing) {
		if (durationMillis < 0 || durationMillis > BrightnessAnimation.MAX_DURATION) {
			throw new IllegalArgumentException("Duration must be 0 to " + BrightnessAnimation.MAX_DURATION + " ms, got " + durationMillis);
		}

		send(player, new ResetBrightness(durationMillis, easing(easing)));
	}

	@Override
	public void setSetting(Player player, float brightness) {
		if (!(brightness >= 0 && brightness <= 100)) {
			throw new IllegalArgumentException("Brightness setting must be 0 to 100, got " + brightness);
		}

		send(player, new SetSetting(brightness));
	}

	@Override
	public CompletableFuture<BrightnessInfo> get(Player player) {
		if (!ready.test(player.getUniqueId())) {
			return CompletableFuture.failedFuture(new IllegalStateException(player.getName() + " doesn't have Skaffy's API ready"));
		}

		int requestId = nextRequest.getAndIncrement();
		CompletableFuture<BrightnessInfo> future = new CompletableFuture<>();
		queries.put(requestId, new Query(player.getUniqueId(), future));
		future.orTimeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((info, error) -> queries.remove(requestId));
		send(player, new QueryBrightness(requestId));
		return future;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!BrightnessCodec.CHANNEL.equals(channel)) {
			return;
		}

		BrightnessPacket packet;

		try {
			packet = BrightnessCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			return;
		}

		if (packet instanceof BrightnessPacket.BrightnessInfo info) {
			Query query = queries.get(info.requestId());

			if (query != null && query.player.equals(player.getUniqueId())) {
				query.future.complete(new BrightnessInfo(info.setting(), info.current(), info.serverBrightnessActive()));
			}
		}
	}

	private void send(Player player, BrightnessPacket packet) {
		if (ready.test(player.getUniqueId())) {
			player.sendPluginMessage(plugin, BrightnessCodec.CHANNEL, BrightnessCodec.encode(packet));
		}
	}

	private static me.skaffy.protocol.Easing easing(Easing easing) {
		return me.skaffy.protocol.Easing.valueOf(easing.name());
	}

	private record Query(UUID player, CompletableFuture<BrightnessInfo> future) {
	}
}
