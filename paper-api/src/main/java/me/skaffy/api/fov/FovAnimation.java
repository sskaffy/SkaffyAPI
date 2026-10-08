package me.skaffy.api.fov;

import java.util.Objects;

public final class FovAnimation {
	public static final int MAX_DURATION = 600_000;

	private final FovValue from;
	private final FovValue to;
	private final int durationMillis;
	private final FovEasing easing;
	private final boolean vanillaEffects;
	private final boolean zoomHands;
	private final boolean scaleSensitivity;

	private FovAnimation(Builder builder) {
		this.from = builder.from;
		this.to = builder.to;
		this.durationMillis = builder.durationMillis;
		this.easing = builder.easing;
		this.vanillaEffects = builder.vanillaEffects;
		this.zoomHands = builder.zoomHands;
		this.scaleSensitivity = builder.scaleSensitivity;
	}

	public static Builder to(FovValue to) {
		return new Builder(to);
	}

	public FovValue getFrom() {
		return from;
	}

	public FovValue getTo() {
		return to;
	}

	public int getDurationMillis() {
		return durationMillis;
	}

	public FovEasing getEasing() {
		return easing;
	}

	public boolean hasVanillaEffects() {
		return vanillaEffects;
	}

	public boolean zoomsHands() {
		return zoomHands;
	}

	public boolean scalesSensitivity() {
		return scaleSensitivity;
	}

	public static final class Builder {
		private final FovValue to;
		private FovValue from;
		private int durationMillis = 1000;
		private FovEasing easing = FovEasing.EASE_IN_OUT_SINE;
		private boolean vanillaEffects = true;
		private boolean zoomHands;
		private boolean scaleSensitivity;

		private Builder(FovValue to) {
			this.to = Objects.requireNonNull(to, "to");
		}

		public Builder from(FovValue from) {
			this.from = from;
			return this;
		}

		public Builder duration(int millis) {
			this.durationMillis = millis;
			return this;
		}

		public Builder easing(FovEasing easing) {
			this.easing = Objects.requireNonNull(easing, "easing");
			return this;
		}

		public Builder vanillaEffects(boolean vanillaEffects) {
			this.vanillaEffects = vanillaEffects;
			return this;
		}

		public Builder zoomHands(boolean zoomHands) {
			this.zoomHands = zoomHands;
			return this;
		}

		public Builder scaleSensitivity(boolean scaleSensitivity) {
			this.scaleSensitivity = scaleSensitivity;
			return this;
		}

		public FovAnimation build() {
			if (durationMillis < 0 || durationMillis > MAX_DURATION) {
				throw new IllegalArgumentException("Duration must be 0 to " + MAX_DURATION + " ms, got " + durationMillis);
			}

			return new FovAnimation(this);
		}
	}
}
