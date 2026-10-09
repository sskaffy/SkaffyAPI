package me.skaffy.client.gui.sfy;

import java.util.function.Supplier;

final class FieldInfo {
	final String name;
	final ClassInfo owner;
	final Type type;
	final boolean isStatic;
	final boolean isFinal;
	int slot = -1;
	Supplier<Object> nativeValue;
	Pos pos = Pos.NONE;

	FieldInfo(String name, ClassInfo owner, Type type, boolean isStatic, boolean isFinal) {
		this.name = name;
		this.owner = owner;
		this.type = type;
		this.isStatic = isStatic;
		this.isFinal = isFinal;
	}
}
