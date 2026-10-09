package me.skaffy.client.gui.sfy;

public record LengthValue(double percent, double pixels) {
	public static final LengthValue ZERO = new LengthValue(0, 0);

	public static LengthValue px(double pixels) {
		return new LengthValue(0, pixels);
	}

	public double resolve(double parentSize) {
		return parentSize * percent / 100 + pixels;
	}

	public boolean hasPercent() {
		return percent != 0;
	}

	public LengthValue plus(LengthValue other) {
		return new LengthValue(percent + other.percent, pixels + other.pixels);
	}

	public LengthValue minus(LengthValue other) {
		return new LengthValue(percent - other.percent, pixels - other.pixels);
	}

	public LengthValue times(double factor) {
		return new LengthValue(percent * factor, pixels * factor);
	}

	public LengthValue lerp(LengthValue other, double t) {
		return new LengthValue(percent + (other.percent - percent) * t, pixels + (other.pixels - pixels) * t);
	}

	@Override
	public String toString() {
		if (percent == 0) {
			return Values.number(pixels);
		}

		if (pixels == 0) {
			return Values.number(percent) + "%";
		}

		return Values.number(percent) + "% " + (pixels < 0 ? "- " : "+ ") + Values.number(Math.abs(pixels));
	}
}
