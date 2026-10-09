package me.skaffy.protocol;

public enum Easing {
	LINEAR,
	EASE_IN_SINE,
	EASE_OUT_SINE,
	EASE_IN_OUT_SINE,
	EASE_IN_QUAD,
	EASE_OUT_QUAD,
	EASE_IN_OUT_QUAD,
	EASE_IN_CUBIC,
	EASE_OUT_CUBIC,
	EASE_IN_OUT_CUBIC,
	EASE_IN_QUART,
	EASE_OUT_QUART,
	EASE_IN_OUT_QUART,
	EASE_IN_QUINT,
	EASE_OUT_QUINT,
	EASE_IN_OUT_QUINT,
	EASE_IN_EXPO,
	EASE_OUT_EXPO,
	EASE_IN_OUT_EXPO,
	EASE_IN_CIRC,
	EASE_OUT_CIRC,
	EASE_IN_OUT_CIRC,
	EASE_IN_BACK,
	EASE_OUT_BACK,
	EASE_IN_OUT_BACK,
	EASE_IN_ELASTIC,
	EASE_OUT_ELASTIC,
	EASE_IN_OUT_ELASTIC,
	EASE_IN_BOUNCE,
	EASE_OUT_BOUNCE,
	EASE_IN_OUT_BOUNCE;

	private static final double BACK = 1.70158;
	private static final double BACK_IN_OUT = BACK * 1.525;
	private static final double ELASTIC = 2 * Math.PI / 3;
	private static final double ELASTIC_IN_OUT = 2 * Math.PI / 4.5;

	public double apply(double t) {
		if (t <= 0) {
			return 0;
		}

		if (t >= 1) {
			return 1;
		}

		return switch (this) {
			case LINEAR -> t;
			case EASE_IN_SINE -> 1 - Math.cos(t * Math.PI / 2);
			case EASE_OUT_SINE -> Math.sin(t * Math.PI / 2);
			case EASE_IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1) / 2;
			case EASE_IN_QUAD -> t * t;
			case EASE_OUT_QUAD -> 1 - (1 - t) * (1 - t);
			case EASE_IN_OUT_QUAD -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
			case EASE_IN_CUBIC -> t * t * t;
			case EASE_OUT_CUBIC -> 1 - Math.pow(1 - t, 3);
			case EASE_IN_OUT_CUBIC -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
			case EASE_IN_QUART -> Math.pow(t, 4);
			case EASE_OUT_QUART -> 1 - Math.pow(1 - t, 4);
			case EASE_IN_OUT_QUART -> t < 0.5 ? 8 * Math.pow(t, 4) : 1 - Math.pow(-2 * t + 2, 4) / 2;
			case EASE_IN_QUINT -> Math.pow(t, 5);
			case EASE_OUT_QUINT -> 1 - Math.pow(1 - t, 5);
			case EASE_IN_OUT_QUINT -> t < 0.5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2;
			case EASE_IN_EXPO -> Math.pow(2, 10 * t - 10);
			case EASE_OUT_EXPO -> 1 - Math.pow(2, -10 * t);
			case EASE_IN_OUT_EXPO -> t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
			case EASE_IN_CIRC -> 1 - Math.sqrt(1 - t * t);
			case EASE_OUT_CIRC -> Math.sqrt(1 - Math.pow(t - 1, 2));
			case EASE_IN_OUT_CIRC -> t < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
			case EASE_IN_BACK -> (BACK + 1) * t * t * t - BACK * t * t;
			case EASE_OUT_BACK -> 1 + (BACK + 1) * Math.pow(t - 1, 3) + BACK * Math.pow(t - 1, 2);
			case EASE_IN_OUT_BACK -> t < 0.5
					? Math.pow(2 * t, 2) * ((BACK_IN_OUT + 1) * 2 * t - BACK_IN_OUT) / 2
					: (Math.pow(2 * t - 2, 2) * ((BACK_IN_OUT + 1) * (t * 2 - 2) + BACK_IN_OUT) + 2) / 2;
			case EASE_IN_ELASTIC -> -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * ELASTIC);
			case EASE_OUT_ELASTIC -> Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * ELASTIC) + 1;
			case EASE_IN_OUT_ELASTIC -> t < 0.5
					? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * ELASTIC_IN_OUT)) / 2
					: Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * ELASTIC_IN_OUT) / 2 + 1;
			case EASE_IN_BOUNCE -> 1 - bounceOut(1 - t);
			case EASE_OUT_BOUNCE -> bounceOut(t);
			case EASE_IN_OUT_BOUNCE -> t < 0.5 ? (1 - bounceOut(1 - 2 * t)) / 2 : (1 + bounceOut(2 * t - 1)) / 2;
		};
	}

	private static double bounceOut(double t) {
		double n = 7.5625;
		double d = 2.75;

		if (t < 1 / d) {
			return n * t * t;
		}

		if (t < 2 / d) {
			t -= 1.5 / d;
			return n * t * t + 0.75;
		}

		if (t < 2.5 / d) {
			t -= 2.25 / d;
			return n * t * t + 0.9375;
		}

		t -= 2.625 / d;
		return n * t * t + 0.984375;
	}
}
