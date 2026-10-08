package me.skaffy.protocol.particles;

import java.util.List;
import java.util.function.IntToDoubleFunction;

import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record Curve(List<Point> points) {
	public static final int MAX_POINTS = 32;
	public static final Curve NONE = new Curve(List.of());

	public record Point(float time, float value, Easing easing) {
		public Point {
			checkTime(time);

			if (!Float.isFinite(value)) {
				throw new ProtocolException("Curve value must be a finite number");
			}
		}
	}

	public Curve {
		List<Point> copy = List.copyOf(points);
		checkPoints(copy.size(), i -> copy.get(i).time());
		points = copy;
	}

	public float at(float time, float fallback) {
		if (points.isEmpty()) {
			return fallback;
		}

		int next = 0;

		while (next < points.size() && points.get(next).time() <= time) {
			next++;
		}

		if (next == 0) {
			return points.getFirst().value();
		}

		if (next == points.size()) {
			return points.getLast().value();
		}

		Point from = points.get(next - 1);
		Point to = points.get(next);
		float progress = from.easing().apply((time - from.time()) / (to.time() - from.time()));
		return from.value() + (to.value() - from.value()) * progress;
	}

	public static Curve read(PacketReader reader) {
		return new Curve(reader.readList(MAX_POINTS, r -> new Point(r.readFloat(), r.readFloat(), r.readEnum(Easing.values(), "easing"))));
	}

	public void write(PacketWriter writer) {
		writer.writeList(points, (w, point) -> w.writeFloat(point.time()).writeFloat(point.value()).writeByte(point.easing().ordinal()));
	}

	static void checkTime(float time) {
		if (!(time >= 0 && time <= 1)) {
			throw new ProtocolException("Curve times must be from 0 to 1, got " + time);
		}
	}

	static void checkPoints(int count, IntToDoubleFunction time) {
		if (count > MAX_POINTS) {
			throw new ProtocolException("A curve has at most " + MAX_POINTS + " points, got " + count);
		}

		for (int i = 1; i < count; i++) {
			if (time.applyAsDouble(i) < time.applyAsDouble(i - 1)) {
				throw new ProtocolException("Curve points must be sorted by time");
			}
		}
	}
}
