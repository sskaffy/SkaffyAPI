package me.skaffy.api.gui;

import java.util.List;
import java.util.Map;

public record GuiMessage(String className, String name, List<Object> values) {
	public GuiMessage {
		values = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
	}

	public int size() {
		return values.size();
	}

	public Object get(int index) {
		return index < values.size() ? values.get(index) : null;
	}

	public String getString(int index) {
		return get(index) instanceof String text ? text : null;
	}

	public int getInt(int index, int fallback) {
		return get(index) instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue()) ? number.intValue() : fallback;
	}

	public long getLong(int index, long fallback) {
		return get(index) instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue()) ? number.longValue() : fallback;
	}

	public double getDouble(int index, double fallback) {
		return get(index) instanceof Number number ? number.doubleValue() : fallback;
	}

	public boolean getBoolean(int index, boolean fallback) {
		return get(index) instanceof Boolean bool ? bool : fallback;
	}

	public List<?> getList(int index) {
		return get(index) instanceof List<?> list ? list : List.of();
	}

	public Map<?, ?> getMap(int index) {
		return get(index) instanceof Map<?, ?> map ? map : Map.of();
	}
}
