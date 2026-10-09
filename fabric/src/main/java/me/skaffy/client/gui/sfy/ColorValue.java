package me.skaffy.client.gui.sfy;

import java.util.Locale;

public record ColorValue(int argb) {
	public static final ColorValue WHITE = new ColorValue(0xFFFFFFFF);
	public static final ColorValue BLACK = new ColorValue(0xFF000000);
	public static final ColorValue TRANSPARENT = new ColorValue(0);

	public int alpha() {
		return argb >>> 24;
	}

	public int red() {
		return argb >> 16 & 0xFF;
	}

	public int green() {
		return argb >> 8 & 0xFF;
	}

	public int blue() {
		return argb & 0xFF;
	}

	public ColorValue mix(ColorValue other, double t) {
		if (t <= 0) {
			return this;
		}

		if (t >= 1) {
			return other;
		}

		return new ColorValue(channel(alpha(), other.alpha(), t) << 24 | channel(red(), other.red(), t) << 16
				| channel(green(), other.green(), t) << 8 | channel(blue(), other.blue(), t));
	}

	private static int channel(int from, int to, double t) {
		return Math.clamp(Math.round(from + (to - from) * t), 0, 255);
	}

	@Override
	public String toString() {
		return alpha() == 255 ? String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF) : String.format(Locale.ROOT, "#%08X", argb);
	}
}
