package me.skaffy.api.particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ParticleCurve {
	public static final int MAX_POINTS = 32;

	public record Point(float time, float value, Easing easing) {
	}

	private final List<Point> points;

	private ParticleCurve(List<Point> points) {
		this.points = List.copyOf(points);
	}

	public static ParticleCurve of(float time, float value) {
		return new ParticleCurve(List.of()).to(time, value);
	}

	public static ParticleCurve constant(float value) {
		return of(0, value);
	}

	public ParticleCurve to(float time, float value) {
		return to(time, value, Easing.LINEAR);
	}

	public ParticleCurve to(float time, float value, Easing easing) {
		Objects.requireNonNull(easing, "easing");
		checkTime(time, points.isEmpty() ? 0 : points.getLast().time(), points.size());

		if (!Float.isFinite(value)) {
			throw new IllegalArgumentException("Curve value must be a finite number");
		}

		List<Point> more = new ArrayList<>(points);
		more.add(new Point(time, value, easing));
		return new ParticleCurve(more);
	}

	public List<Point> getPoints() {
		return points;
	}

	static void checkTime(float time, float previous, int count) {
		if (!(time >= 0 && time <= 1)) {
			throw new IllegalArgumentException("Curve times must be from 0 to 1, got " + time);
		}

		if (time < previous) {
			throw new IllegalArgumentException("Curve time " + time + " comes before the previous point at " + previous);
		}

		if (count >= MAX_POINTS) {
			throw new IllegalArgumentException("A curve has at most " + MAX_POINTS + " points");
		}
	}
}
