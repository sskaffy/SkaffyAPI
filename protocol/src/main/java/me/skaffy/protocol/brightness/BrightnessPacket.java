package me.skaffy.protocol.brightness;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.ProtocolException;

public sealed interface BrightnessPacket {
	int MAX_DURATION = 600_000;

	record Options(boolean darknessEffect, boolean nightVision) {
	}

	record SetBrightness(float brightness, Options options) implements BrightnessPacket {
		public SetBrightness {
			checkFinite(brightness);
		}
	}

	record AnimateBrightness(Float from, float to, int durationMillis, Easing easing, Options options) implements BrightnessPacket {
		public AnimateBrightness {
			if (from != null) {
				checkFinite(from);
			}

			checkFinite(to);
			checkDuration(durationMillis);
		}
	}

	record ResetBrightness(int durationMillis, Easing easing) implements BrightnessPacket {
		public ResetBrightness {
			checkDuration(durationMillis);
		}
	}

	record SetSetting(float brightness) implements BrightnessPacket {
		public SetSetting {
			if (!(brightness >= 0 && brightness <= 100)) {
				throw new ProtocolException("Brightness setting must be 0 to 100, got " + brightness);
			}
		}
	}

	record QueryBrightness(int requestId) implements BrightnessPacket {
	}

	record BrightnessInfo(int requestId, float setting, float current, boolean serverBrightnessActive) implements BrightnessPacket {
	}

	private static void checkFinite(float brightness) {
		if (!Float.isFinite(brightness)) {
			throw new ProtocolException("Brightness must be a finite number, got " + brightness);
		}
	}

	private static void checkDuration(int durationMillis) {
		if (durationMillis < 0 || durationMillis > MAX_DURATION) {
			throw new ProtocolException("Duration must be 0 to " + MAX_DURATION + " ms, got " + durationMillis);
		}
	}
}
