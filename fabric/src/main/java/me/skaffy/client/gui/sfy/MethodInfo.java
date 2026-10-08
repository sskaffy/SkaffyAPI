package me.skaffy.client.gui.sfy;

import java.util.List;

final class MethodInfo {
	enum Returns {
		DECLARED,
		SELF,
		ARG0
	}

	final String name;
	final ClassInfo owner;
	final boolean isStatic;
	final List<Type> params;
	final List<String> paramNames;
	final boolean varargs;
	final Type returnType;
	final Returns returns;
	final List<String> typeParams;
	Invoker invoker;
	MethodImpl impl;
	boolean isPublic;
	boolean isGui;
	boolean isHud;
	Pos pos = Pos.NONE;

	MethodInfo(String name, ClassInfo owner, boolean isStatic, List<Type> params, List<String> paramNames, boolean varargs, Type returnType, Returns returns, List<String> typeParams) {
		this.name = name;
		this.owner = owner;
		this.isStatic = isStatic;
		this.params = List.copyOf(params);
		this.paramNames = List.copyOf(paramNames);
		this.varargs = varargs;
		this.returnType = returnType;
		this.returns = returns;
		this.typeParams = List.copyOf(typeParams);
	}

	String signature() {
		StringBuilder builder = new StringBuilder(name).append('(');

		for (int i = 0; i < params.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}

			builder.append(params.get(i));

			if (varargs && i == params.size() - 1) {
				builder.append("...");
			}
		}

		return builder.append(')').toString();
	}

	@Override
	public String toString() {
		return owner.name + "." + signature();
	}
}
