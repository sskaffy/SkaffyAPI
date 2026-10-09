package me.skaffy.api.animation;

import me.skaffy.api.model.AnimationMode;

public record PlayOptions(AnimationMode mode, float speed, float fadeIn, float startAt, boolean removeWhenDone) {
	public static final PlayOptions DEFAULT = new PlayOptions(AnimationMode.DEFAULT, 1, 0, 0, false);

	public PlayOptions {
		if (mode == null) {
			throw new IllegalArgumentException("Missing mode");
		}

		if (!(speed > 0) || !Float.isFinite(speed)) {
			throw new IllegalArgumentException("Speed must be above 0, got " + speed);
		}

		if (!(fadeIn >= 0) || fadeIn > 600 || !(startAt >= 0) || !Float.isFinite(startAt)) {
			throw new IllegalArgumentException("Fade in is 0 to 600 seconds and the start at least 0");
		}
	}

	public PlayOptions mode(AnimationMode mode) {
		return new PlayOptions(mode, speed, fadeIn, startAt, removeWhenDone);
	}

	public PlayOptions speed(float speed) {
		return new PlayOptions(mode, speed, fadeIn, startAt, removeWhenDone);
	}

	public PlayOptions fadeIn(float seconds) {
		return new PlayOptions(mode, speed, seconds, startAt, removeWhenDone);
	}

	public PlayOptions startAt(float seconds) {
		return new PlayOptions(mode, speed, fadeIn, seconds, removeWhenDone);
	}

	public PlayOptions removeWhenDone(boolean remove) {
		return new PlayOptions(mode, speed, fadeIn, startAt, remove);
	}
}
