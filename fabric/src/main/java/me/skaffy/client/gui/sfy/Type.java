package me.skaffy.client.gui.sfy;

import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract sealed class Type permits Type.Prim, Type.Special, Type.Ref, Type.Var {
	enum PrimKind {
		INT("int", "Integer"),
		LONG("long", "Long"),
		DOUBLE("double", "Double"),
		BOOLEAN("boolean", "Boolean"),
		CHAR("char", "Character");

		final String name;
		final String boxedName;

		PrimKind(String name, String boxedName) {
			this.name = name;
			this.boxedName = boxedName;
		}
	}

	static final Prim INT = new Prim(PrimKind.INT, false);
	static final Prim LONG = new Prim(PrimKind.LONG, false);
	static final Prim DOUBLE = new Prim(PrimKind.DOUBLE, false);
	static final Prim BOOLEAN = new Prim(PrimKind.BOOLEAN, false);
	static final Prim CHAR = new Prim(PrimKind.CHAR, false);
	static final Special VOID = new Special("void");
	static final Special NULL = new Special("null");
	static final Special ERROR = new Special("<error>");
	static final Special INFER = new Special("?");

	boolean isNumeric() {
		return this instanceof Prim prim && prim.kind != PrimKind.BOOLEAN;
	}

	boolean isPrimitive() {
		return this instanceof Prim;
	}

	boolean isBoolean() {
		return this instanceof Prim prim && prim.kind == PrimKind.BOOLEAN;
	}

	boolean is(PrimKind kind) {
		return this instanceof Prim prim && prim.kind == kind;
	}

	boolean isReference() {
		return this instanceof Ref || this == NULL || this instanceof Prim prim && prim.boxed;
	}

	boolean isLoose() {
		return this == ERROR || this == INFER;
	}

	ClassInfo classInfo() {
		return this instanceof Ref ref ? ref.cls : null;
	}

	Type subst(Map<String, Type> bindings) {
		return this;
	}

	static final class Prim extends Type {
		final PrimKind kind;
		final boolean boxed;

		Prim(PrimKind kind, boolean boxed) {
			this.kind = kind;
			this.boxed = boxed;
		}

		Prim unboxed() {
			return switch (kind) {
				case INT -> INT;
				case LONG -> LONG;
				case DOUBLE -> DOUBLE;
				case BOOLEAN -> BOOLEAN;
				case CHAR -> CHAR;
			};
		}

		Prim box() {
			return boxed ? this : new Prim(kind, true);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Prim prim && prim.kind == kind;
		}

		@Override
		public int hashCode() {
			return kind.hashCode();
		}

		@Override
		public String toString() {
			return boxed ? kind.boxedName : kind.name;
		}
	}

	static final class Special extends Type {
		private final String name;

		Special(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	static final class Ref extends Type {
		final ClassInfo cls;
		final List<Type> args;

		Ref(ClassInfo cls, List<Type> args) {
			this.cls = cls;
			this.args = List.copyOf(args);
		}

		Type arg(int index) {
			return index < args.size() ? args.get(index) : INFER;
		}

		@Override
		Type subst(Map<String, Type> bindings) {
			if (args.isEmpty()) {
				return this;
			}

			return new Ref(cls, args.stream().map(arg -> arg.subst(bindings)).toList());
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Ref ref && ref.cls == cls && ref.args.equals(args);
		}

		@Override
		public int hashCode() {
			return Objects.hash(cls.name, args);
		}

		@Override
		public String toString() {
			if (args.isEmpty()) {
				return cls.name;
			}

			StringBuilder builder = new StringBuilder(cls.name).append('<');

			for (int i = 0; i < args.size(); i++) {
				if (i > 0) {
					builder.append(", ");
				}

				builder.append(args.get(i));
			}

			return builder.append('>').toString();
		}
	}

	static final class Var extends Type {
		final String name;

		Var(String name) {
			this.name = name;
		}

		@Override
		Type subst(Map<String, Type> bindings) {
			Type bound = bindings.get(name);
			return bound != null ? bound : INFER;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Var var && var.name.equals(name);
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
