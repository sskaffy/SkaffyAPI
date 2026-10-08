package me.skaffy.protocol.fov;

import me.skaffy.protocol.ProtocolException;

public sealed interface FovPacket {
	int MAX_DURATION = 600_000;

	record Options(boolean vanillaEffects, boolean zoomHands, boolean scaleSensitivity) {
	}

	record SetFov(FovValue fov, Options options) implements FovPacket {
	}

	record AnimateFov(FovValue from, FovValue to, int durationMillis, FovEasing easing, Options options) implements FovPacket {
		public AnimateFov {
			checkDuration(durationMillis);
		}
	}

	record ResetFov(int durationMillis, FovEasing easing) implements FovPacket {
		public ResetFov {
			checkDuration(durationMillis);
		}
	}

	record SetSettings(Integer fov, Float effectScale) implements FovPacket {
		public SetSettings {
			if (fov != null && (fov < 30 || fov > 110)) {
				throw new ProtocolException("FOV setting must be 30 to 110, got " + fov);
			}

			if (effectScale != null && !(effectScale >= 0 && effectScale <= 1)) {
				throw new ProtocolException("FOV effect scale must be 0 to 1, got " + effectScale);
			}
		}
	}

	record QueryFov(int requestId) implements FovPacket {
	}

	record FovInfo(int requestId, int fovSetting, float effectScale, float currentFov, boolean serverFovActive) implements FovPacket {
	}

	private static void checkDuration(int durationMillis) {
		if (durationMillis < 0 || durationMillis > MAX_DURATION) {
			throw new ProtocolException("Duration must be 0 to " + MAX_DURATION + " ms, got " + durationMillis);
		}
	}
}
