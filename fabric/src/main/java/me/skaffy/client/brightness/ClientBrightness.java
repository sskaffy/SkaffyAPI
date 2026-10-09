package me.skaffy.client.brightness;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.protocol.Easing;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.brightness.BrightnessCodec;
import me.skaffy.protocol.brightness.BrightnessPacket;
import me.skaffy.protocol.brightness.BrightnessPacket.AnimateBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.BrightnessInfo;
import me.skaffy.protocol.brightness.BrightnessPacket.Options;
import me.skaffy.protocol.brightness.BrightnessPacket.QueryBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.ResetBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.SetBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.SetSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.world.effect.MobEffects;

import org.jspecify.annotations.Nullable;

public final class ClientBrightness {
	private static @Nullable ServerBrightness active;
	private static boolean dirty;

	private ClientBrightness() {
	}

	public static void receive(byte[] data) {
		BrightnessPacket packet;

		try {
			packet = BrightnessCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed brightness packet: {}", e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> handle(packet, minecraft));
	}

	private static void handle(BrightnessPacket packet, Minecraft minecraft) {
		long now = System.nanoTime();
		dirty = true;

		switch (packet) {
			case SetBrightness set -> active = new ServerBrightness(null, 0, set.brightness(), now, 0, Easing.LINEAR, set.options(), false);
			case AnimateBrightness animate -> active = new ServerBrightness(animate.from(), currentBase(now), animate.to(), now, animate.durationMillis(), animate.easing(), animate.options(), false);
			case ResetBrightness reset -> {
				if (active != null) {
					active = reset.durationMillis() == 0 ? null : new ServerBrightness(null, currentBase(now), null, now, reset.durationMillis(), reset.easing(), active.options(), true);
				}
			}
			case SetSetting setting -> {
				minecraft.options.gamma().set(setting.brightness() / 100.0);
				minecraft.options.save();
			}
			case QueryBrightness query -> {
				LocalPlayer player = minecraft.player;
				ServerBrightness brightness = current();
				boolean darknessApplies = player != null && (brightness == null || brightness.options().darknessEffect());
				float base = currentBase(now);
				float darkness = darknessApplies ? darkness(player, 1) * 100 : 0;
				float lit = base >= 0 ? Math.max(0, base - darkness) : base - darkness;
				BrightnessInfo info = new BrightnessInfo(query.requestId(), setting(), lit, brightness != null);
				SkaffyConnection.sendFeature(BrightnessCodec.CHANNEL, BrightnessCodec.encode(info));
			}
			default -> {
			}
		}
	}

	public static void unload() {
		Minecraft.getInstance().execute(() -> {
			active = null;
			dirty = true;
		});
	}

	public static boolean needsUpdate() {
		boolean update = dirty || active != null && active.progress(System.nanoTime()) < 1;
		dirty = false;
		return update;
	}

	public static void apply(LightmapRenderState state, LocalPlayer player, float partialTicks) {
		ServerBrightness brightness = current();

		if (brightness == null) {
			return;
		}

		Options options = brightness.options();
		float value = brightness.base(System.nanoTime()) / 100;
		float darkness = options.darknessEffect() ? darkness(player, partialTicks) : 0;
		state.brightness = value >= 0 ? Math.max(0, value - darkness) : value - darkness;

		if (!options.darknessEffect()) {
			state.darknessEffectScale = 0;
		}

		if (!options.nightVision()) {
			state.nightVisionEffectIntensity = 0;
		}
	}

	private static float darkness(LocalPlayer player, float partialTicks) {
		return player.getEffectBlendFactor(MobEffects.DARKNESS, partialTicks) * Minecraft.getInstance().options.darknessEffectScale().get().floatValue();
	}

	private static @Nullable ServerBrightness current() {
		if (active != null && active.resetting() && active.progress(System.nanoTime()) >= 1) {
			active = null;
		}

		return active;
	}

	private static float currentBase(long now) {
		ServerBrightness brightness = current();
		return brightness == null ? setting() : brightness.base(now);
	}

	private static float setting() {
		return Minecraft.getInstance().options.gamma().get().floatValue() * 100;
	}

	private record ServerBrightness(@Nullable Float from, float fromValue, @Nullable Float to, long start, int durationMillis, Easing easing, Options options, boolean resetting) {
		double progress(long now) {
			return durationMillis == 0 ? 1 : (now - start) / 1_000_000.0 / durationMillis;
		}

		float base(long now) {
			float end = to == null ? setting() : to;
			double progress = progress(now);

			if (progress >= 1) {
				return end;
			}

			float begin = from == null ? fromValue : from;
			return (float) (begin + (end - begin) * easing.apply(progress));
		}
	}
}
