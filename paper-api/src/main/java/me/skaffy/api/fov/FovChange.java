package me.skaffy.api.fov;

import java.util.Objects;

public record FovChange(FovValue fov, boolean vanillaEffects, boolean zoomHands, boolean scaleSensitivity) {
	public FovChange {
		Objects.requireNonNull(fov, "fov");
	}

	public static FovChange of(FovValue fov) {
		return new FovChange(fov, true, false, false);
	}

	public static FovChange degrees(float degrees) {
		return of(FovValue.degrees(degrees));
	}

	public static FovChange multiplier(float multiplier) {
		return of(FovValue.multiplier(multiplier));
	}

	public FovChange withVanillaEffects(boolean vanillaEffects) {
		return new FovChange(fov, vanillaEffects, zoomHands, scaleSensitivity);
	}

	public FovChange withZoomHands(boolean zoomHands) {
		return new FovChange(fov, vanillaEffects, zoomHands, scaleSensitivity);
	}

	public FovChange withScaleSensitivity(boolean scaleSensitivity) {
		return new FovChange(fov, vanillaEffects, zoomHands, scaleSensitivity);
	}
}
