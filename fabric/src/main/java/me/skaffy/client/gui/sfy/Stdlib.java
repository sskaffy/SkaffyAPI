package me.skaffy.client.gui.sfy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class Stdlib {
	private Stdlib() {
	}

	static void install(SfyLibrary lib) {
		lib.type("Object", null, Object.class);
		lib.type("String", null, String.class);
		lib.classes.get("String").valueType = true;
		lib.type("Math", null, Void.class);
		lib.type("Integer", null, Integer.class);
		lib.type("Double", null, Double.class);
		lib.type("List<T>", null, List.class);
		lib.type("Map<K, V>", null, Map.class);
		lib.type("Exception", null, ExceptionValue.class);
		lib.type("Logger", null, Logger.class);
		lib.valueType("Color", ColorValue.class);
		lib.valueType("Length", LengthValue.class);
		lib.valueType("Duration", DurationValue.class);
		lib.functional("Runnable", "run", "void", "");
		lib.functional("Consumer<T>", "accept", "void", "T value");
		lib.functional("BiConsumer<A, B>", "accept", "void", "A first, B second");
		lib.functional("Supplier<T>", "get", "T", "");
		lib.functional("Function<T, R>", "apply", "R", "T value");
		lib.functional("Predicate<T>", "test", "boolean", "T value");
		lib.functional("Comparator<T>", "compare", "int", "T first, T second");

		object(lib);
		string(lib);
		math(lib);
		lists(lib);
		maps(lib);
		misc(lib);
	}

	private static void object(SfyLibrary lib) {
		lib.method("Object", "equals", "boolean", "Object other", (self, args) -> Values.equal(self, args[0]));
		lib.method("Object", "hashCode", "int", "", (self, args) -> Values.hash(self));
		lib.method("Object", "toString", "String", "", (self, args) -> Values.str(self));
	}

	private static void string(SfyLibrary lib) {
		lib.method("String", "length", "int", "", (self, args) -> ((String) self).length());
		lib.method("String", "charAt", "char", "int index", (self, args) -> ((String) self).charAt((Integer) args[0]));
		lib.method("String", "substring", "String", "int start", (self, args) -> ((String) self).substring((Integer) args[0]));
		lib.method("String", "substring", "String", "int start, int end", (self, args) -> ((String) self).substring((Integer) args[0], (Integer) args[1]));
		lib.method("String", "indexOf", "int", "String text", (self, args) -> ((String) self).indexOf((String) args[0]));
		lib.method("String", "indexOf", "int", "char character", (self, args) -> ((String) self).indexOf((Character) args[0]));
		lib.method("String", "contains", "boolean", "String text", (self, args) -> ((String) self).contains((String) args[0]));
		lib.method("String", "startsWith", "boolean", "String text", (self, args) -> ((String) self).startsWith((String) args[0]));
		lib.method("String", "endsWith", "boolean", "String text", (self, args) -> ((String) self).endsWith((String) args[0]));
		lib.method("String", "toLowerCase", "String", "", (self, args) -> ((String) self).toLowerCase(Locale.ROOT));
		lib.method("String", "toUpperCase", "String", "", (self, args) -> ((String) self).toUpperCase(Locale.ROOT));
		lib.method("String", "trim", "String", "", (self, args) -> ((String) self).trim());
		lib.method("String", "replace", "String", "String target, String replacement", (self, args) -> ((String) self).replace((String) args[0], (String) args[1]));
		lib.method("String", "replace", "String", "char target, char replacement", (self, args) -> ((String) self).replace((Character) args[0], (Character) args[1]));
		lib.method("String", "split", "List<String>", "String separator", (self, args) -> split((String) self, (String) args[0]));
		lib.method("String", "isEmpty", "boolean", "", (self, args) -> ((String) self).isEmpty());
		lib.method("String", "repeat", "String", "int count", (self, args) -> ((String) self).repeat((Integer) args[0]));
		lib.method("String", "compareTo", "int", "String other", (self, args) -> ((String) self).compareTo((String) args[0]));
		lib.staticMethod("String", "format", "String", "String format, Object... values", (self, args) -> format((String) args[0], (Object[]) args[1]));
		lib.staticMethod("String", "valueOf", "String", "Object value", (self, args) -> Values.str(args[0]));
	}

	static List<Object> split(String text, String separator) {
		List<Object> parts = new ArrayList<>();

		if (separator.isEmpty()) {
			for (int i = 0; i < text.length(); i++) {
				parts.add(String.valueOf(text.charAt(i)));
			}

			return parts;
		}

		int start = 0;

		while (true) {
			int found = text.indexOf(separator, start);

			if (found < 0) {
				parts.add(text.substring(start));
				return parts;
			}

			parts.add(text.substring(start, found));
			start = found + separator.length();
		}
	}

	private static String format(String format, Object[] values) {
		Object[] converted = new Object[values.length];

		for (int i = 0; i < values.length; i++) {
			Object value = values[i];
			converted[i] = value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Character
					|| value instanceof Boolean || value instanceof String || value == null ? value : Values.str(value);
		}

		try {
			return String.format(Locale.ROOT, format, converted);
		} catch (java.util.IllegalFormatException e) {
			throw new ScriptException("Bad format \"" + format + "\": " + e.getMessage());
		}
	}

	private static void math(SfyLibrary lib) {
		lib.staticField("Math", "PI", "double", () -> Math.PI);
		lib.staticMethod("Math", "abs", "int", "int value", (self, args) -> Math.abs((Integer) args[0]));
		lib.staticMethod("Math", "abs", "long", "long value", (self, args) -> Math.abs((Long) args[0]));
		lib.staticMethod("Math", "abs", "double", "double value", (self, args) -> Math.abs((Double) args[0]));
		lib.staticMethod("Math", "min", "int", "int a, int b", (self, args) -> Math.min((Integer) args[0], (Integer) args[1]));
		lib.staticMethod("Math", "min", "long", "long a, long b", (self, args) -> Math.min((Long) args[0], (Long) args[1]));
		lib.staticMethod("Math", "min", "double", "double a, double b", (self, args) -> Math.min((Double) args[0], (Double) args[1]));
		lib.staticMethod("Math", "max", "int", "int a, int b", (self, args) -> Math.max((Integer) args[0], (Integer) args[1]));
		lib.staticMethod("Math", "max", "long", "long a, long b", (self, args) -> Math.max((Long) args[0], (Long) args[1]));
		lib.staticMethod("Math", "max", "double", "double a, double b", (self, args) -> Math.max((Double) args[0], (Double) args[1]));
		lib.staticMethod("Math", "floor", "double", "double value", (self, args) -> Math.floor((Double) args[0]));
		lib.staticMethod("Math", "ceil", "double", "double value", (self, args) -> Math.ceil((Double) args[0]));
		lib.staticMethod("Math", "round", "long", "double value", (self, args) -> Math.round((Double) args[0]));
		lib.staticMethod("Math", "sqrt", "double", "double value", (self, args) -> Math.sqrt((Double) args[0]));
		lib.staticMethod("Math", "pow", "double", "double base, double exponent", (self, args) -> Math.pow((Double) args[0], (Double) args[1]));
		lib.staticMethod("Math", "sin", "double", "double radians", (self, args) -> Math.sin((Double) args[0]));
		lib.staticMethod("Math", "cos", "double", "double radians", (self, args) -> Math.cos((Double) args[0]));
		lib.staticMethod("Math", "atan2", "double", "double y, double x", (self, args) -> Math.atan2((Double) args[0], (Double) args[1]));
		lib.staticMethod("Math", "random", "double", "", (self, args) -> ThreadLocalRandom.current().nextDouble());
		lib.staticMethod("Math", "clamp", "int", "int value, int min, int max", (self, args) -> clamp((Integer) args[0], (Integer) args[1], (Integer) args[2]));
		lib.staticMethod("Math", "clamp", "long", "long value, long min, long max", (self, args) -> clamp((Long) args[0], (Long) args[1], (Long) args[2]));
		lib.staticMethod("Math", "clamp", "double", "double value, double min, double max", (self, args) -> clamp((Double) args[0], (Double) args[1], (Double) args[2]));
		lib.staticMethod("Math", "lerp", "double", "double from, double to, double t", (self, args) -> {
			double from = (Double) args[0];
			return from + ((Double) args[1] - from) * (Double) args[2];
		});
		lib.staticMethod("Integer", "parseInt", "int", "String text", (self, args) -> {
			try {
				return Integer.parseInt(((String) args[0]).trim());
			} catch (NumberFormatException e) {
				throw new ScriptException("\"" + args[0] + "\" is not a whole number");
			}
		});
		lib.staticMethod("Double", "parseDouble", "double", "String text", (self, args) -> {
			try {
				return Double.parseDouble(((String) args[0]).trim());
			} catch (NumberFormatException e) {
				throw new ScriptException("\"" + args[0] + "\" is not a number");
			}
		});
	}

	private static <T extends Comparable<T>> T clamp(T value, T min, T max) {
		if (min.compareTo(max) > 0) {
			throw new ScriptException("Math.clamp: min " + min + " is greater than max " + max);
		}

		return value.compareTo(min) < 0 ? min : value.compareTo(max) > 0 ? max : value;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> list(Object self) {
		return (List<Object>) self;
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> map(Object self) {
		return (Map<Object, Object>) self;
	}

	private static void lists(SfyLibrary lib) {
		lib.staticMethod("List", "of", "<T> List<T>", "T... values", (self, args) -> new ArrayList<>(java.util.Arrays.asList((Object[]) args[0])));
		lib.method("List", "add", "boolean", "T value", (self, args) -> list(self).add(args[0]));
		lib.method("List", "add", "void", "int index, T value", (self, args) -> {
			list(self).add((Integer) args[0], args[1]);
			return null;
		});
		lib.method("List", "get", "T", "int index", (self, args) -> list(self).get((Integer) args[0]));
		lib.method("List", "set", "T", "int index, T value", (self, args) -> list(self).set((Integer) args[0], args[1]));
		lib.method("List", "remove", "T", "int index", (self, args) -> list(self).remove((int) (Integer) args[0]));
		lib.method("List", "remove", "boolean", "Object value", (self, args) -> list(self).remove(args[0]));
		lib.method("List", "size", "int", "", (self, args) -> list(self).size());
		lib.method("List", "isEmpty", "boolean", "", (self, args) -> list(self).isEmpty());
		lib.method("List", "contains", "boolean", "Object value", (self, args) -> list(self).contains(args[0]));
		lib.method("List", "indexOf", "int", "Object value", (self, args) -> list(self).indexOf(args[0]));
		lib.method("List", "clear", "void", "", (self, args) -> {
			list(self).clear();
			return null;
		});
		lib.method("List", "sort", "void", "Comparator<T> comparator", (self, args) -> {
			Fn comparator = (Fn) args[0];
			list(self).sort((a, b) -> (Integer) comparator.call(a, b));
			return null;
		});
		lib.method("List", "sort", "void", "", (self, args) -> {
			list(self).sort(Stdlib::naturalOrder);
			return null;
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static int naturalOrder(Object a, Object b) {
		if (a instanceof Comparable first && b != null && a.getClass() == b.getClass()) {
			return first.compareTo(b);
		}

		if (a instanceof EnumValue first && b instanceof EnumValue second) {
			return Integer.compare(first.ordinal, second.ordinal);
		}

		throw new ScriptException("sort() without a comparator only sorts numbers, text, chars and enums; pass (a, b) -> ...");
	}

	static final Comparator<Object> NATURAL = Stdlib::naturalOrder;

	private static void maps(SfyLibrary lib) {
		lib.method("Map", "put", "V", "K key, V value", (self, args) -> map(self).put(args[0], args[1]));
		lib.method("Map", "get", "V", "Object key", (self, args) -> map(self).get(args[0]));
		lib.method("Map", "getOrDefault", "V", "Object key, V fallback", (self, args) -> map(self).getOrDefault(args[0], args[1]));
		lib.method("Map", "containsKey", "boolean", "Object key", (self, args) -> map(self).containsKey(args[0]));
		lib.method("Map", "remove", "V", "Object key", (self, args) -> map(self).remove(args[0]));
		lib.method("Map", "size", "int", "", (self, args) -> map(self).size());
		lib.method("Map", "keys", "List<K>", "", (self, args) -> new ArrayList<>(map(self).keySet()));
		lib.method("Map", "values", "List<V>", "", (self, args) -> new ArrayList<>(map(self).values()));
	}

	static Map<Object, Object> newMap() {
		return new LinkedHashMap<>();
	}

	private static void misc(SfyLibrary lib) {
		lib.method("Exception", "getMessage", "String", "", (self, args) -> ((ExceptionValue) self).message());
		lib.constant("LOGGER", "Logger", () -> new Logger(lib));
		lib.method("Logger", "info", "void", "String message, Object... values", (self, args) -> ((Logger) self).log("info", (String) args[0], (Object[]) args[1]));
		lib.method("Logger", "warn", "void", "String message, Object... values", (self, args) -> ((Logger) self).log("warn", (String) args[0], (Object[]) args[1]));
		lib.method("Logger", "error", "void", "String message, Object... values", (self, args) -> ((Logger) self).log("error", (String) args[0], (Object[]) args[1]));

		lib.staticMethod("Color", "rgb", "Color", "int red, int green, int blue", (self, args) -> new ColorValue(0xFF000000 | channel(args[0]) << 16 | channel(args[1]) << 8 | channel(args[2])));
		lib.staticMethod("Color", "argb", "Color", "int alpha, int red, int green, int blue", (self, args) -> new ColorValue(channel(args[0]) << 24 | channel(args[1]) << 16 | channel(args[2]) << 8 | channel(args[3])));
		lib.staticMethod("Color", "hsb", "Color", "double hue, double saturation, double brightness", (self, args) -> hsb((Double) args[0], (Double) args[1], (Double) args[2]));
		lib.method("Color", "red", "int", "", (self, args) -> ((ColorValue) self).red());
		lib.method("Color", "green", "int", "", (self, args) -> ((ColorValue) self).green());
		lib.method("Color", "blue", "int", "", (self, args) -> ((ColorValue) self).blue());
		lib.method("Color", "alpha", "int", "", (self, args) -> ((ColorValue) self).alpha());
		lib.method("Color", "withAlpha", "Color", "int alpha", (self, args) -> new ColorValue(channel(args[0]) << 24 | ((ColorValue) self).argb() & 0xFFFFFF));
		lib.method("Color", "mix", "Color", "Color other, double t", (self, args) -> ((ColorValue) self).mix((ColorValue) args[0], (Double) args[1]));
		lib.staticMethod("Length", "percent", "Length", "double percent", (self, args) -> new LengthValue((Double) args[0], 0));
		lib.staticMethod("Length", "px", "Length", "double pixels", (self, args) -> LengthValue.px((Double) args[0]));
		lib.method("Duration", "millis", "long", "", (self, args) -> ((DurationValue) self).millis());
	}

	private static int channel(Object value) {
		return Math.clamp((Integer) value, 0, 255);
	}

	static ColorValue hsb(double hue, double saturation, double brightness) {
		double s = Math.clamp(saturation, 0, 1);
		double v = Math.clamp(brightness, 0, 1);
		double h = ((hue % 360) + 360) % 360 / 60;
		int sector = (int) Math.floor(h);
		double f = h - sector;
		double p = v * (1 - s);
		double q = v * (1 - s * f);
		double t = v * (1 - s * (1 - f));
		double[] rgb = switch (sector) {
			case 0 -> new double[] {v, t, p};
			case 1 -> new double[] {q, v, p};
			case 2 -> new double[] {p, v, t};
			case 3 -> new double[] {p, q, v};
			case 4 -> new double[] {t, p, v};
			default -> new double[] {v, p, q};
		};
		return new ColorValue(0xFF000000 | (int) Math.round(rgb[0] * 255) << 16 | (int) Math.round(rgb[1] * 255) << 8 | (int) Math.round(rgb[2] * 255));
	}

	static final class Logger {
		private final SfyLibrary lib;

		Logger(SfyLibrary lib) {
			this.lib = lib;
		}

		Object log(String level, String message, Object[] values) {
			StringBuilder builder = new StringBuilder();
			int next = 0;
			int start = 0;

			while (true) {
				int found = message.indexOf("{}", start);

				if (found < 0 || next >= values.length) {
					builder.append(message, start, message.length());
					break;
				}

				builder.append(message, start, found).append(Values.str(values[next++]));
				start = found + 2;
			}

			lib.logSink.accept(level, builder.toString());
			return null;
		}

		@Override
		public String toString() {
			return "LOGGER";
		}
	}
}
