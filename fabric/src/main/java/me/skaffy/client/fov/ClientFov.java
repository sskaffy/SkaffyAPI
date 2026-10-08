package me.skaffy.client.fov;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.fov.FovCodec;
import me.skaffy.protocol.fov.FovEasing;
import me.skaffy.protocol.fov.FovPacket;
import me.skaffy.protocol.fov.FovPacket.AnimateFov;
import me.skaffy.protocol.fov.FovPacket.FovInfo;
import me.skaffy.protocol.fov.FovPacket.Options;
import me.skaffy.protocol.fov.FovPacket.QueryFov;
import me.skaffy.protocol.fov.FovPacket.ResetFov;
import me.skaffy.protocol.fov.FovPacket.SetFov;
import me.skaffy.protocol.fov.FovPacket.SetSettings;
import me.skaffy.protocol.fov.FovValue;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

public final class ClientFov {
	private static final float MIN_FOV = 1;
	private static final float MAX_FOV = 170;

	private static @Nullable ServerFov active;

	private ClientFov() {
	}

	public static void receive(byte[] data) {
		FovPacket packet;

		try {
			packet = FovCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed fov packet: {}", e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> handle(packet, minecraft));
	}

	private static void handle(FovPacket packet, Minecraft minecraft) {
		long now = System.nanoTime();

		switch (packet) {
			case SetFov set -> active = new ServerFov(null, 0, set.fov(), now, 0, FovEasing.LINEAR, set.options(), false);
			case AnimateFov animate -> active = new ServerFov(animate.from(), currentBase(now), animate.to(), now, animate.durationMillis(), animate.easing(), animate.options(), false);
			case ResetFov reset -> {
				if (active != null) {
					active = reset.durationMillis() == 0 ? null : new ServerFov(null, currentBase(now), null, now, reset.durationMillis(), reset.easing(), active.options(), true);
				}
			}
			case SetSettings settings -> {
				if (settings.fov() != null) {
					minecraft.options.fov().set(settings.fov());
				}

				if (settings.effectScale() != null) {
					minecraft.options.fovEffectScale().set(settings.effectScale().doubleValue());
				}

				minecraft.options.save();
			}
			case QueryFov query -> {
				float current = minecraft.level != null ? minecraft.gameRenderer.mainCamera().getFov() : playerFov();
				FovInfo info = new FovInfo(query.requestId(), minecraft.options.fov().get(), minecraft.options.fovEffectScale().get().floatValue(), current, active != null);
				SkaffyConnection.sendFeature(FovCodec.CHANNEL, FovCodec.encode(info));
			}
			default -> {
			}
		}
	}

	public static void unload() {
		Minecraft.getInstance().execute(() -> active = null);
	}

	public static float worldFov(float vanilla, boolean panoramic) {
		ServerFov fov = current();

		if (fov == null || panoramic) {
			return vanilla;
		}

		float base = fov.base(System.nanoTime());
		float result = fov.options().vanillaEffects() ? vanilla * base / playerFov() : base;
		return Mth.clamp(result, MIN_FOV, MAX_FOV);
	}

	public static float handFov(float vanilla) {
		ServerFov fov = current();

		if (fov == null || !fov.options().zoomHands()) {
			return vanilla;
		}

		return Mth.clamp(vanilla * fov.base(System.nanoTime()) / playerFov(), MIN_FOV, MAX_FOV);
	}

	public static double sensitivityFactor() {
		ServerFov fov = current();

		if (fov == null || !fov.options().scaleSensitivity()) {
			return 1;
		}

		return Math.min(1, fov.base(System.nanoTime()) / playerFov());
	}

	private static @Nullable ServerFov current() {
		if (active != null && active.resetting() && active.progress(System.nanoTime()) >= 1) {
			active = null;
		}

		return active;
	}

	private static float currentBase(long now) {
		ServerFov fov = current();
		return fov == null ? playerFov() : fov.base(now);
	}

	private static float playerFov() {
		return Minecraft.getInstance().options.fov().get();
	}

	private record ServerFov(@Nullable FovValue from, float fromDegrees, @Nullable FovValue to, long start, int durationMillis, FovEasing easing, Options options, boolean resetting) {
		double progress(long now) {
			return durationMillis == 0 ? 1 : (now - start) / 1_000_000.0 / durationMillis;
		}

		float base(long now) {
			float playerFov = playerFov();
			float end = to == null ? playerFov : to.resolve(playerFov);
			double progress = progress(now);

			if (progress >= 1) {
				return end;
			}

			float begin = from == null ? fromDegrees : from.resolve(playerFov);
			return (float) (begin + (end - begin) * easing.apply(progress));
		}
	}
}
