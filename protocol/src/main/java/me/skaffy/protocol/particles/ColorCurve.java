package me.skaffy.protocol.particles;

import java.util.List;

import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record ColorCurve(List<Point> points) {
	public static final ColorCurve NONE = new ColorCurve(List.of());

	public record Point(float time, int rgb, Easing easing) {
		public Point {
			Curve.checkTime(time);
			rgb &= 0xFFFFFF;
		}
	}

	public ColorCurve {
		List<Point> copy = List.copyOf(points);
		Curve.checkPoints(copy.size(), i -> copy.get(i).time());
		points = copy;
	}

	public int at(float time, int fallback) {
		if (points.isEmpty()) {
			return fallback;
		}

		int next = 0;

		while (next < points.size() && points.get(next).time() <= time) {
			next++;
		}

		if (next == 0) {
			return points.getFirst().rgb();
		}

		if (next == points.size()) {
			return points.getLast().rgb();
		}

		Point from = points.get(next - 1);
		Point to = points.get(next);
		float progress = from.easing().apply((time - from.time()) / (to.time() - from.time()));
		int rgb = 0;

		for (int shift = 0; shift <= 16; shift += 8) {
			int a = from.rgb() >> shift & 0xFF;
			int b = to.rgb() >> shift & 0xFF;
			rgb |= Math.round(a + (b - a) * progress) << shift;
		}

		return rgb;
	}

	public static ColorCurve read(PacketReader reader) {
		return new ColorCurve(reader.readList(Curve.MAX_POINTS, r -> new Point(r.readFloat(), r.readInt(), r.readEnum(Easing.values(), "easing"))));
	}

	public void write(PacketWriter writer) {
		writer.writeList(points, (w, point) -> w.writeFloat(point.time()).writeInt(point.rgb()).writeByte(point.easing().ordinal()));
	}
}
