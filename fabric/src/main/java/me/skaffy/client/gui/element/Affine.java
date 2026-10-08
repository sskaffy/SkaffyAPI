package me.skaffy.client.gui.element;

public record Affine(double a, double b, double c, double d, double tx, double ty) {
	public static final Affine IDENTITY = new Affine(1, 0, 0, 1, 0, 0);

	public Affine translate(double x, double y) {
		if (x == 0 && y == 0) {
			return this;
		}

		return new Affine(a, b, c, d, a * x + c * y + tx, b * x + d * y + ty);
	}

	public Affine scale(double factor) {
		if (factor == 1) {
			return this;
		}

		return new Affine(a * factor, b * factor, c * factor, d * factor, tx, ty);
	}

	public Affine rotate(double degrees) {
		if (degrees == 0) {
			return this;
		}

		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Affine(a * cos + c * sin, b * cos + d * sin, -a * sin + c * cos, -b * sin + d * cos, tx, ty);
	}

	public double x(double x, double y) {
		return a * x + c * y + tx;
	}

	public double y(double x, double y) {
		return b * x + d * y + ty;
	}

	public double[] inverse(double x, double y) {
		double determinant = a * d - b * c;

		if (Math.abs(determinant) < 1e-12) {
			return null;
		}

		double px = x - tx;
		double py = y - ty;
		return new double[] {(d * px - c * py) / determinant, (-b * px + a * py) / determinant};
	}

	public double[] inverseVector(double x, double y) {
		double determinant = a * d - b * c;

		if (Math.abs(determinant) < 1e-12) {
			return new double[] {0, 0};
		}

		return new double[] {(d * x - c * y) / determinant, (-b * x + a * y) / determinant};
	}

	public double scaleFactor() {
		return Math.sqrt(Math.abs(a * d - b * c));
	}

	public boolean isAxisAligned() {
		return Math.abs(b) < 1e-9 && Math.abs(c) < 1e-9;
	}
}
