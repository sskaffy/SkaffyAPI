package me.skaffy.paper.perspective;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import me.skaffy.api.Easing;
import me.skaffy.api.event.perspective.SkaffyPerspectiveChangeEvent;
import me.skaffy.api.perspective.CameraView;
import me.skaffy.api.perspective.Perspective;
import me.skaffy.api.perspective.PlayerPerspective;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.perspective.PerspectiveCodec;
import me.skaffy.protocol.perspective.PerspectivePacket;
import me.skaffy.protocol.perspective.PerspectivePacket.Camera;
import me.skaffy.protocol.perspective.PerspectivePacket.Look;
import me.skaffy.protocol.perspective.PerspectivePacket.PerspectiveChanged;
import me.skaffy.protocol.perspective.PerspectivePacket.PerspectiveInfo;
import me.skaffy.protocol.perspective.PerspectivePacket.QueryPerspective;
import me.skaffy.protocol.perspective.PerspectivePacket.SetAllowed;
import me.skaffy.protocol.perspective.PerspectivePacket.SetCamera;
import me.skaffy.protocol.perspective.PerspectivePacket.SetPerspective;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PaperPerspective implements PlayerPerspective, PluginMessageListener {
	private static final long QUERY_TIMEOUT_SECONDS = 5;

	private final Plugin plugin;
	private final Predicate<UUID> ready;
	private final AtomicInteger nextRequest = new AtomicInteger();
	private final Map<Integer, Query> queries = new ConcurrentHashMap<>();

	public PaperPerspective(Plugin plugin, Predicate<UUID> ready) {
		this.plugin = plugin;
		this.ready = ready;
	}

	@Override
	public void set(Player player, Perspective perspective, int durationMillis, Easing easing) {
		if (perspective == Perspective.CUSTOM) {
			throw new IllegalArgumentException("Use setCamera for a custom camera");
		}

		checkDuration(durationMillis);
		send(player, new SetPerspective(wire(perspective), durationMillis, easing(easing)));
	}

	@Override
	public void setCamera(Player player, CameraView camera, int durationMillis, Easing easing) {
		checkDuration(durationMillis);

		Look look = switch (camera.getLookMode()) {
			case ANGLES -> new Look.Angles(camera.getYaw(), camera.getPitch());
			case POINT -> new Look.Point((float) camera.getLookX(), (float) camera.getLookY(), (float) camera.getLookZ());
			case PLAYER -> new Look.AtPlayer();
			case ENTITY -> new Look.AtEntity(camera.getLookEntity().getEntityId());
			case PLAYER_VIEW -> new Look.PlayerView();
			case BONE_FACING -> new Look.AnchorFacing();
		};

		PerspectivePacket.Turn turn = camera.turnsWithPitch() ? PerspectivePacket.Turn.YAW_AND_PITCH : camera.turnsWithPlayer() ? PerspectivePacket.Turn.YAW : PerspectivePacket.Turn.NONE;
		me.skaffy.protocol.perspective.PerspectivePacket.Anchor anchor = camera.getMount() == null ? new me.skaffy.protocol.perspective.PerspectivePacket.Anchor.Player()
				: new me.skaffy.protocol.perspective.PerspectivePacket.Anchor.Bone(me.skaffy.paper.animation.PaperAnimations.wireId(camera.getMount(), player), camera.getMountBone());
		Camera wire = new Camera((float) camera.getX(), (float) camera.getY(), (float) camera.getZ(), turn, look, camera.getRoll(), camera.pullsInFrontOfWalls(), anchor);
		send(player, new SetCamera(wire, durationMillis, easing(easing)));
	}

	@Override
	public void setAllowed(Player player, Set<Perspective> allowed) {
		int mask = 0;

		for (Perspective perspective : allowed) {
			if (perspective != Perspective.CUSTOM) {
				mask |= 1 << perspective.ordinal();
			}
		}

		if (mask == 0) {
			throw new IllegalArgumentException("At least one vanilla perspective must be allowed");
		}

		send(player, new SetAllowed(mask));
	}

	@Override
	public CompletableFuture<Perspective> get(Player player) {
		if (!ready.test(player.getUniqueId())) {
			return CompletableFuture.failedFuture(new IllegalStateException(player.getName() + " doesn't have Skaffy's API ready"));
		}

		int requestId = nextRequest.getAndIncrement();
		CompletableFuture<Perspective> future = new CompletableFuture<>();
		queries.put(requestId, new Query(player.getUniqueId(), future));
		future.orTimeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((perspective, error) -> queries.remove(requestId));
		send(player, new QueryPerspective(requestId));
		return future;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!PerspectiveCodec.CHANNEL.equals(channel) || !ready.test(player.getUniqueId())) {
			return;
		}

		PerspectivePacket packet;

		try {
			packet = PerspectiveCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			return;
		}

		switch (packet) {
			case PerspectiveInfo info -> {
				Query query = queries.get(info.requestId());

				if (query != null && query.player.equals(player.getUniqueId())) {
					query.future.complete(api(info.perspective()));
				}
			}
			case PerspectiveChanged changed -> new SkaffyPerspectiveChangeEvent(player, api(changed.from()), api(changed.to())).callEvent();
			default -> {
			}
		}
	}

	private void send(Player player, PerspectivePacket packet) {
		if (ready.test(player.getUniqueId())) {
			player.sendPluginMessage(plugin, PerspectiveCodec.CHANNEL, PerspectiveCodec.encode(packet));
		}
	}

	private static void checkDuration(int durationMillis) {
		if (durationMillis < 0 || durationMillis > MAX_DURATION) {
			throw new IllegalArgumentException("Duration must be 0 to " + MAX_DURATION + " ms, got " + durationMillis);
		}
	}

	private static PerspectivePacket.Perspective wire(Perspective perspective) {
		return PerspectivePacket.Perspective.valueOf(perspective.name());
	}

	private static Perspective api(PerspectivePacket.Perspective perspective) {
		return Perspective.valueOf(perspective.name());
	}

	private static me.skaffy.protocol.Easing easing(Easing easing) {
		return me.skaffy.protocol.Easing.valueOf(easing.name());
	}

	private record Query(UUID player, CompletableFuture<Perspective> future) {
	}
}
