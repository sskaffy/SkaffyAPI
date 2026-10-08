package me.skaffy.api.brightness;

public record BrightnessChange(float brightness, boolean darknessEffect, boolean nightVision) {
	public BrightnessChange {
		if (!Float.isFinite(brightness)) {
			throw new IllegalArgumentException("Brightness must be a finite number, got " + brightness);
		}
	}

	public static BrightnessChange of(float brightness) {
		return new BrightnessChange(brightness, true, true);
	}

	public BrightnessChange withDarknessEffect(boolean darknessEffect) {
		return new BrightnessChange(brightness, darknessEffect, nightVision);
	}

	public BrightnessChange withNightVision(boolean nightVision) {
		return new BrightnessChange(brightness, darknessEffect, nightVision);
	}
}
