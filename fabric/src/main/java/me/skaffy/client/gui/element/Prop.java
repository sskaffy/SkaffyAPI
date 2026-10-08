package me.skaffy.client.gui.element;

import me.skaffy.client.gui.sfy.LengthValue;

public enum Prop {
	X(Kind.LENGTH),
	Y(Kind.LENGTH),
	WIDTH(Kind.LENGTH),
	HEIGHT(Kind.LENGTH),
	OFFSET_X(Kind.NUMBER),
	OFFSET_Y(Kind.NUMBER),
	ROTATION(Kind.NUMBER),
	SCALE(Kind.NUMBER),
	TRANSPARENCY(Kind.NUMBER),
	COLOR(Kind.COLOR),
	BACKGROUND(Kind.COLOR),
	TEXT_COLOR(Kind.COLOR),
	BORDER_WIDTH(Kind.NUMBER),
	BORDER_COLOR(Kind.COLOR),
	RADIUS_TOP_LEFT(Kind.NUMBER),
	RADIUS_TOP_RIGHT(Kind.NUMBER),
	RADIUS_BOTTOM_RIGHT(Kind.NUMBER),
	RADIUS_BOTTOM_LEFT(Kind.NUMBER),
	SHADOW_COLOR(Kind.COLOR),
	SHADOW_BLUR(Kind.NUMBER),
	SHADOW_X(Kind.NUMBER),
	SHADOW_Y(Kind.NUMBER),
	SHADOW_SPREAD(Kind.NUMBER),
	FONT_SIZE(Kind.NUMBER);

	public enum Kind {
		LENGTH,
		NUMBER,
		COLOR
	}

	public static final Prop[] ALL = values();
	public static final int COUNT = ALL.length;

	public final Kind kind;

	Prop(Kind kind) {
		this.kind = kind;
	}

	public Object lerp(Object from, Object to, double t) {
		if (t >= 1 || from == null || to == null) {
			return t >= 1 || from == null ? to : from;
		}

		return switch (kind) {
			case LENGTH -> ((LengthValue) from).lerp((LengthValue) to, t);
			case NUMBER -> (Double) from + ((Double) to - (Double) from) * t;
			case COLOR -> mixColor((Integer) from, (Integer) to, t);
		};
	}

	static int mixColor(int from, int to, double t) {
		if (t <= 0) {
			return from;
		}

		if (t >= 1) {
			return to;
		}

		int a = channel(from >>> 24, to >>> 24, t);
		int r = channel(from >> 16 & 0xFF, to >> 16 & 0xFF, t);
		int g = channel(from >> 8 & 0xFF, to >> 8 & 0xFF, t);
		int b = channel(from & 0xFF, to & 0xFF, t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int channel(int from, int to, double t) {
		return (int) Math.clamp(Math.round(from + (to - from) * t), 0, 255);
	}
}
