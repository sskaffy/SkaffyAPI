package me.skaffy.paper.fov;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import me.skaffy.api.fov.FovAnimation;
import me.skaffy.api.fov.FovChange;
import me.skaffy.api.fov.FovEasing;
import me.skaffy.api.fov.FovInfo;
import me.skaffy.api.fov.PlayerFov;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.fov.FovCodec;
import me.skaffy.protocol.fov.FovPacket;
import me.skaffy.protocol.fov.FovPacket.AnimateFov;
import me.skaffy.protocol.fov.FovPacket.Options;
import me.skaffy.protocol.fov.FovPacket.QueryFov;
import me.skaffy.protocol.fov.FovPacket.ResetFov;
import me.skaffy.protocol.fov.FovPacket.SetFov;
import me.skaffy.protocol.fov.FovPacket.SetSettings;
import me.skaffy.protocol.fov.FovValue;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PaperFov implements PlayerFov, PluginMessageListener {
	private static final long QUERY_TIMEOUT_SECONDS = 5;

	private final Plugin plugin;
	private final Predicate<UUID> ready;
	private final AtomicInteger nextRequest = new AtomicInteger();
	private final Map<Integer, Query> queries = new ConcurrentHashMap<>();

	public PaperFov(Plugin plugin, Predicate<UUID> ready) {
		this.plugin = plugin;
		this.ready = ready;
	}

	@Override
	public void set(Player player, FovChange change) {
		send(player, new SetFov(value(change.fov()), new Options(change.vanillaEffects(), change.zoomHands(), change.scaleSensitivity())));
	}

	@Override
	public void animate(Player player, FovAnimation animation) {
		send(player, new AnimateFov(
				animation.getFrom() == null ? null : value(animation.getFrom()),
				value(animation.getTo()),
				animation.getDurationMillis(),
				easing(animation.getEasing()),
				new Options(animation.hasVanillaEffects(), animation.zoomsHands(), animation.scalesSensitivity())));
	}

	@Override
	public void reset(Player player, int durationMillis, FovEasing easing) {
		send(player, new ResetFov(durationMillis, easing(easing)));
	}

	@Override
	public void setFovSetting(Player player, int fov) {
		if (fov < 30 || fov > 110) {
			throw new IllegalArgumentException("FOV setting must be 30 to 110, got " + fov);
		}

		send(player, new SetSettings(fov, null));
	}

	@Override
	public void setEffectScale(Player player, float effectScale) {
		if (!(effectScale >= 0 && effectScale <= 1)) {
			throw new IllegalArgumentException("FOV effect scale must be 0 to 1, got " + effectScale);
		}

		send(player, new SetSettings(null, effectScale));
	}

	@Override
	public CompletableFuture<FovInfo> get(Player player) {
		if (!ready.test(player.getUniqueId())) {
			return CompletableFuture.failedFuture(new IllegalStateException(player.getName() + " doesn't have Skaffy's API ready"));
		}

		int requestId = nextRequest.getAndIncrement();
		CompletableFuture<FovInfo> future = new CompletableFuture<>();
		queries.put(requestId, new Query(player.getUniqueId(), future));
		future.orTimeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((info, error) -> queries.remove(requestId));
		send(player, new QueryFov(requestId));
		return future;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!FovCodec.CHANNEL.equals(channel)) {
			return;
		}

		FovPacket packet;

		try {
			packet = FovCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			return;
		}

		if (packet instanceof FovPacket.FovInfo info) {
			Query query = queries.get(info.requestId());

			if (query != null && query.player.equals(player.getUniqueId())) {
				query.future.complete(new FovInfo(info.fovSetting(), info.effectScale(), info.currentFov(), info.serverFovActive()));
			}
		}
	}

	private void send(Player player, FovPacket packet) {
		if (ready.test(player.getUniqueId())) {
			player.sendPluginMessage(plugin, FovCodec.CHANNEL, FovCodec.encode(packet));
		}
	}

	private static FovValue value(me.skaffy.api.fov.FovValue value) {
		return new FovValue(value.isMultiplier(), value.value());
	}

	private static me.skaffy.protocol.fov.FovEasing easing(FovEasing easing) {
		return me.skaffy.protocol.fov.FovEasing.valueOf(easing.name());
	}

	private record Query(UUID player, CompletableFuture<FovInfo> future) {
	}
}
