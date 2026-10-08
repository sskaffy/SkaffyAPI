package me.skaffy.client.gui.sfy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClassInfo {
	enum Kind {
		NATIVE,
		FUNCTIONAL,
		NATIVE_ENUM,
		MAIN,
		RECORD,
		ENUM
	}

	final String name;
	final Kind kind;
	final List<String> typeParams;
	ClassInfo superClass;
	Class<?> javaClass;
	boolean valueType;
	final Map<String, List<MethodInfo>> methods = new LinkedHashMap<>();
	final Map<String, FieldInfo> fields = new LinkedHashMap<>();
	MethodInfo sam;
	final List<String> constants = new ArrayList<>();
	Object[] nativeConstants;
	final List<MethodInfo> constructors = new ArrayList<>();
	final Map<String, ClassInfo> nested = new LinkedHashMap<>();
	ClassInfo outer;
	UserType runtime;

	ClassInfo(String name, Kind kind, List<String> typeParams) {
		this.name = name;
		this.kind = kind;
		this.typeParams = List.copyOf(typeParams);
	}

	boolean isUser() {
		return kind == Kind.MAIN || kind == Kind.RECORD || kind == Kind.ENUM;
	}

	boolean isEnum() {
		return kind == Kind.ENUM || kind == Kind.NATIVE_ENUM;
	}

	void addMethod(MethodInfo method) {
		methods.computeIfAbsent(method.name, key -> new ArrayList<>()).add(method);
	}

	List<MethodInfo> findMethods(String name) {
		List<MethodInfo> found = new ArrayList<>();

		for (ClassInfo type = this; type != null; type = type.superClass) {
			List<MethodInfo> own = type.methods.get(name);

			if (own != null) {
				for (MethodInfo method : own) {
					boolean overridden = found.stream().anyMatch(existing -> existing.params.equals(method.params));

					if (!overridden) {
						found.add(method);
					}
				}
			}
		}

		return found;
	}

	FieldInfo findField(String name) {
		for (ClassInfo type = this; type != null; type = type.superClass) {
			FieldInfo field = type.fields.get(name);

			if (field != null) {
				return field;
			}
		}

		return null;
	}

	boolean isSubclassOf(ClassInfo other) {
		for (ClassInfo type = this; type != null; type = type.superClass) {
			if (type == other) {
				return true;
			}
		}

		return false;
	}

	int constantIndex(String constant) {
		return constants.indexOf(constant);
	}

	@Override
	public String toString() {
		return name;
	}
}
