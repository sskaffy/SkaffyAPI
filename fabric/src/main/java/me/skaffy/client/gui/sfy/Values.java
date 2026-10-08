package me.skaffy.client.gui.sfy;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Values {
	private Values() {
	}

	public static String str(Object value) {
		if (value == null) {
			return "null";
		}

		if (value instanceof String string) {
			return string;
		}

		if (value instanceof List<?> list) {
			StringBuilder builder = new StringBuilder("[");
			Iterator<?> iterator = list.iterator();

			while (iterator.hasNext()) {
				Object element = iterator.next();
				builder.append(element == list ? "(this list)" : str(element));

				if (iterator.hasNext()) {
					builder.append(", ");
				}
			}

			return builder.append(']').toString();
		}

		if (value instanceof Map<?, ?> map) {
			StringBuilder builder = new StringBuilder("{");
			Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();

			while (iterator.hasNext()) {
				Map.Entry<?, ?> entry = iterator.next();
				builder.append(str(entry.getKey())).append('=').append(entry.getValue() == map ? "(this map)" : str(entry.getValue()));

				if (iterator.hasNext()) {
					builder.append(", ");
				}
			}

			return builder.append('}').toString();
		}

		return value.toString();
	}

	static String number(double value) {
		return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value) : Double.toString(value);
	}

	public static boolean equal(Object a, Object b) {
		return Objects.equals(a, b);
	}

	public static int hash(Object value) {
		return Objects.hashCode(value);
	}

	public static String typeName(Object value) {
		return switch (value) {
			case null -> "null";
			case Integer ignored -> "int";
			case Long ignored -> "long";
			case Double ignored -> "double";
			case Boolean ignored -> "boolean";
			case Character ignored -> "char";
			case String ignored -> "String";
			case List<?> ignored -> "List";
			case Map<?, ?> ignored -> "Map";
			case RecordValue record -> record.typeName();
			case EnumValue constant -> constant.typeName();
			case ColorValue ignored -> "Color";
			case LengthValue ignored -> "Length";
			case DurationValue ignored -> "Duration";
			case ExceptionValue ignored -> "Exception";
			case SfyInstance instance -> instance.type().simpleName();
			default -> value.getClass().getSimpleName();
		};
	}
}
