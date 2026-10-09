package me.skaffy.client.gui.sfy;

import java.util.List;

final class UserType {
	final String name;
	final List<String> fieldNames;
	final boolean isRecord;
	EnumValue[] constants;

	UserType(String name, List<String> fieldNames, boolean isRecord) {
		this.name = name;
		this.fieldNames = List.copyOf(fieldNames);
		this.isRecord = isRecord;
	}
}
