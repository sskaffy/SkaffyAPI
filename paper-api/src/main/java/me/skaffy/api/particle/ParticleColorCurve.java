package me.skaffy.api.particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;

public final class ParticleColorCurve {
	public record Point(float time, Color color, Easing easing) {
	}

	private final List<Point> points;

	private ParticleColorCurve(List<Point> points) {
		this.points = List.copyOf(points);
	}

	public static ParticleColorCurve of(float time, Color color) {
		return new ParticleColorCurve(List.of()).to(time, color);
	}

	public static ParticleColorCurve constant(Color color) {
		return of(0, color);
	}

	public ParticleColorCurve to(float time, Color color) {
		return to(time, color, Easing.LINEAR);
	}

	public ParticleColorCurve to(float time, Color color, Easing easing) {
		Objects.requireNonNull(color, "color");
		Objects.requireNonNull(easing, "easing");
		ParticleCurve.checkTime(time, points.isEmpty() ? 0 : points.getLast().time(), points.size());
		List<Point> more = new ArrayList<>(points);
		more.add(new Point(time, color, easing));
		return new ParticleColorCurve(more);
	}

	public List<Point> getPoints() {
		return points;
	}
}
