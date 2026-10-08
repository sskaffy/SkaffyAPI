package me.skaffy.client.gui.sfy;

import java.util.List;

public final class RecordValue {
	final UserType type;
	final Object[] values;

	RecordValue(UserType type, Object[] values) {
		this.type = type;
		this.values = values;
	}

	public String typeName() {
		return type.name;
	}

	public List<String> fieldNames() {
		return type.fieldNames;
	}

	public Object value(int index) {
		return values[index];
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof RecordValue record) || record.type != type) {
			return false;
		}

		for (int i = 0; i < type.fieldNames.size(); i++) {
			if (!Values.equal(values[i], record.values[i])) {
				return false;
			}
		}

		return true;
	}

	@Override
	public int hashCode() {
		int hash = type.name.hashCode();

		for (int i = 0; i < type.fieldNames.size(); i++) {
			hash = hash * 31 + Values.hash(values[i]);
		}

		return hash;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(type.name).append('[');

		for (int i = 0; i < type.fieldNames.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}

			builder.append(type.fieldNames.get(i)).append('=').append(Values.str(values[i]));
		}

		return builder.append(']').toString();
	}

	Object[] rawValues() {
		return values;
	}
}
