package me.skaffy.client.gui.sfy;

public final class EnumValue {
	final UserType type;
	final int ordinal;
	final String name;
	final Object[] fields;

	EnumValue(UserType type, int ordinal, String name, int fieldCount) {
		this.type = type;
		this.ordinal = ordinal;
		this.name = name;
		this.fields = new Object[fieldCount];
	}

	public String name() {
		return name;
	}

	public int ordinal() {
		return ordinal;
	}

	public String typeName() {
		return type.name;
	}

	@Override
	public String toString() {
		return name;
	}
}
