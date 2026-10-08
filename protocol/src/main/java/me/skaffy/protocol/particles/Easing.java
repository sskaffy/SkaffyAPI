package me.skaffy.protocol.particles;

public enum Easing {
	LINEAR,
	EASE_IN,
	EASE_OUT,
	EASE_IN_OUT;

	public float apply(float t) {
		return switch (this) {
			case LINEAR -> t;
			case EASE_IN -> t * t;
			case EASE_OUT -> 1 - (1 - t) * (1 - t);
			case EASE_IN_OUT -> t < 0.5f ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t);
		};
	}
}
