package me.skaffy.client.shader.lang;

import java.util.Arrays;
import java.util.Locale;

record Const(ShType type, double[] values) {
	static Const of(ShType type, double... values) {
		return new Const(type, values);
	}

	static Const ofInt(int value) {
		return new Const(ShType.INT, new double[] {value});
	}

	double scalar() {
		return values[0];
	}

	int intValue() {
		return (int) values[0];
	}

	boolean boolValue() {
		return values[0] != 0;
	}

	String glsl() {
		if (type.components() == 1) {
			return scalarGlsl(type.base(), values[0]);
		}

		StringBuilder code = new StringBuilder(type.glsl()).append('(');

		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				code.append(", ");
			}

			code.append(scalarGlsl(type.base(), values[i]));
		}

		return code.append(')').toString();
	}

	static String scalarGlsl(ShType.Base base, double value) {
		return switch (base) {
			case FLOAT -> floatGlsl(value);
			case INT -> (int) value == Integer.MIN_VALUE ? "(-2147483647 - 1)" : Integer.toString((int) value);
			case BOOL -> value != 0 ? "true" : "false";
		};
	}

	static String floatGlsl(double value) {
		float f = (float) value;

		if (Float.isNaN(f)) {
			return "(0.0 / 0.0)";
		}

		if (Float.isInfinite(f)) {
			return f > 0 ? "(1.0 / 0.0)" : "(-1.0 / 0.0)";
		}

		String text = Float.toString(f).toLowerCase(Locale.ROOT);

		if (!text.contains(".") && !text.contains("e")) {
			text += ".0";
		}

		return text;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Const that && type.equals(that.type) && Arrays.equals(values, that.values);
	}

	@Override
	public int hashCode() {
		return type.hashCode() * 31 + Arrays.hashCode(values);
	}

	@Override
	public String toString() {
		return glsl();
	}
}
