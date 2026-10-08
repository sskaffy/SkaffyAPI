package me.skaffy.api.brightness;

import java.util.Objects;

import me.skaffy.api.Easing;

public final class BrightnessAnimation {
	public static final int MAX_DURATION = 600_000;

	private final Float from;
	private final float to;
	private final int durationMillis;
	private final Easing easing;
	private final boolean darknessEffect;
	private final boolean nightVision;

	private BrightnessAnimation(Builder builder) {
		this.from = builder.from;
		this.to = builder.to;
		this.durationMillis = builder.durationMillis;
		this.easing = builder.easing;
		this.darknessEffect = builder.darknessEffect;
		this.nightVision = builder.nightVision;
	}

	public static Builder to(float brightness) {
		return new Builder(brightness);
	}

	public Float getFrom() {
		return from;
	}

	public float getTo() {
		return to;
	}

	public int getDurationMillis() {
		return durationMillis;
	}

	public Easing getEasing() {
		return easing;
	}

	public boolean hasDarknessEffect() {
		return darknessEffect;
	}

	public boolean hasNightVision() {
		return nightVision;
	}

	public static final class Builder {
		private final float to;
		private Float from;
		private int durationMillis = 1000;
		private Easing easing = Easing.EASE_IN_OUT_SINE;
		private boolean darknessEffect = true;
		private boolean nightVision = true;

		private Builder(float to) {
			this.to = to;
		}

		public Builder from(float brightness) {
			this.from = brightness;
			return this;
		}

		public Builder duration(int millis) {
			this.durationMillis = millis;
			return this;
		}

		public Builder easing(Easing easing) {
			this.easing = Objects.requireNonNull(easing, "easing");
			return this;
		}

		public Builder darknessEffect(boolean darknessEffect) {
			this.darknessEffect = darknessEffect;
			return this;
		}

		public Builder nightVision(boolean nightVision) {
			this.nightVision = nightVision;
			return this;
		}

		public BrightnessAnimation build() {
			if (!Float.isFinite(to) || from != null && !Float.isFinite(from)) {
				throw new IllegalArgumentException("Brightness must be a finite number");
			}

			if (durationMillis < 0 || durationMillis > MAX_DURATION) {
				throw new IllegalArgumentException("Duration must be 0 to " + MAX_DURATION + " ms, got " + durationMillis);
			}

			return new BrightnessAnimation(this);
		}
	}
}
