package me.skaffy.api.fov;

public record FovValue(boolean isMultiplier, float value) {
	public FovValue {
		if (!(value > 0) || !Float.isFinite(value)) {
			throw new IllegalArgumentException("FOV must be above 0, got " + value);
		}
	}

	public static FovValue degrees(float degrees) {
		return new FovValue(false, degrees);
	}

	public static FovValue multiplier(float multiplier) {
		return new FovValue(true, multiplier);
	}
}
