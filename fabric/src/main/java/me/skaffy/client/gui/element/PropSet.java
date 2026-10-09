package me.skaffy.client.gui.element;

public final class PropSet {
	private final Object[] values = new Object[Prop.COUNT];
	private final boolean[] set = new boolean[Prop.COUNT];

	public void put(Prop prop, Object value) {
		values[prop.ordinal()] = value;
		set[prop.ordinal()] = true;
	}

	public boolean has(Prop prop) {
		return set[prop.ordinal()];
	}

	public Object get(Prop prop) {
		return values[prop.ordinal()];
	}

	public boolean isEmpty() {
		for (boolean value : set) {
			if (value) {
				return false;
			}
		}

		return true;
	}
}
