package me.skaffy.client.gui.sfy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class SfyLibrary {
	final Map<String, ClassInfo> classes = new LinkedHashMap<>();
	final ClassInfo globals = new ClassInfo("<global>", ClassInfo.Kind.NATIVE, List.of());
	final Map<String, String> libraryNames = new LinkedHashMap<>();
	private String library;
	private final List<Runnable> pending = new ArrayList<>();
	private boolean frozen;
	BiConsumer<SfyInstance, String> guiSwitch;
	BiConsumer<String, String> logSink = (level, message) -> {
	};

	public SfyLibrary() {
		Stdlib.install(this);
	}

	public void onGuiSwitch(BiConsumer<SfyInstance, String> hook) {
		this.guiSwitch = hook;
	}

	public void onLog(BiConsumer<String, String> sink) {
		this.logSink = sink;
	}

	public void library(String packageName) {
		this.library = packageName;
	}

	public void annotation(String name) {
		remember(name);
	}

	private void remember(String name) {
		if (library != null) {
			libraryNames.put(name, library);
		}
	}

	public void type(String declaration, String superName, Class<?> javaClass) {
		ClassInfo info = declare(declaration, ClassInfo.Kind.NATIVE);
		info.javaClass = javaClass;
		pending.add(() -> info.superClass = info.name.equals("Object") ? null : classes.get(superName == null ? "Object" : superName));
	}

	public void valueType(String name, Class<?> javaClass) {
		type(name, null, javaClass);
		classes.get(name).valueType = true;
	}

	public void functional(String declaration, String samName, String returns, String params) {
		ClassInfo info = declare(declaration, ClassInfo.Kind.FUNCTIONAL);
		info.javaClass = Fn.class;
		pending.add(() -> {
			info.superClass = classes.get("Object");
			info.sam = parseMethod(info, samName, false, returns, params);
			info.sam.invoker = (self, args) -> ((Fn) self).call(args);
			info.addMethod(info.sam);
		});
	}

	public <E extends Enum<E>> void enumType(String name, Class<E> type) {
		ClassInfo info = declare(name, ClassInfo.Kind.NATIVE_ENUM);
		info.javaClass = type;
		E[] values = type.getEnumConstants();
		info.nativeConstants = values;

		for (E value : values) {
			info.constants.add(value.name());
		}

		pending.add(() -> {
			info.superClass = classes.get("Object");
			MethodInfo nameMethod = parseMethod(info, "name", false, "String", "");
			nameMethod.invoker = (self, args) -> ((Enum<?>) self).name();
			info.addMethod(nameMethod);
			MethodInfo ordinalMethod = parseMethod(info, "ordinal", false, "int", "");
			ordinalMethod.invoker = (self, args) -> ((Enum<?>) self).ordinal();
			info.addMethod(ordinalMethod);
		});
	}

	public void method(String owner, String name, String returns, String params, Invoker invoker) {
		register(owner, name, false, returns, params, invoker);
	}

	public void staticMethod(String owner, String name, String returns, String params, Invoker invoker) {
		register(owner, name, true, returns, params, invoker);
	}

	public void staticField(String owner, String name, String type, Supplier<Object> value) {
		freezeCheck();
		pending.add(() -> {
			ClassInfo info = require(owner);
			FieldInfo field = new FieldInfo(name, info, parseType(type, info, List.of()), true, true);
			field.nativeValue = value;
			info.fields.put(name, field);
		});
	}

	public void function(String name, String returns, String params, Invoker invoker) {
		freezeCheck();
		remember(name);
		pending.add(() -> {
			MethodInfo method = parseMethod(globals, name, true, returns, params);
			method.invoker = invoker;
			globals.addMethod(method);
		});
	}

	public void constant(String name, String type, Supplier<Object> value) {
		freezeCheck();
		remember(name);
		pending.add(() -> {
			FieldInfo field = new FieldInfo(name, globals, parseType(type, globals, List.of()), true, true);
			field.nativeValue = value;
			globals.fields.put(name, field);
		});
	}

	private void register(String owner, String name, boolean isStatic, String returns, String params, Invoker invoker) {
		freezeCheck();
		pending.add(() -> {
			ClassInfo info = require(owner);
			MethodInfo method = parseMethod(info, name, isStatic, returns, params);
			method.invoker = invoker;
			info.addMethod(method);
		});
	}

	private void freezeCheck() {
		if (frozen) {
			throw new IllegalStateException("The library is already in use");
		}
	}

	private ClassInfo declare(String declaration, ClassInfo.Kind kind) {
		freezeCheck();
		SignatureReader reader = new SignatureReader(declaration);
		String name = reader.word();
		List<String> typeParams = new ArrayList<>();

		if (reader.accept('<')) {
			do {
				typeParams.add(reader.word());
			} while (reader.accept(','));

			reader.expect('>');
		}

		ClassInfo info = new ClassInfo(name, kind, typeParams);
		classes.put(name, info);
		remember(name);
		return info;
	}

	private ClassInfo require(String name) {
		ClassInfo info = classes.get(name);

		if (info == null) {
			throw new IllegalArgumentException("Unknown library type " + name);
		}

		return info;
	}

	synchronized void freeze() {
		if (frozen) {
			return;
		}

		frozen = true;
		pending.forEach(Runnable::run);
		pending.clear();
	}

	ClassInfo find(String name) {
		return classes.get(name);
	}

	private MethodInfo parseMethod(ClassInfo owner, String name, boolean isStatic, String returns, String params) {
		SignatureReader reader = new SignatureReader(returns);
		List<String> methodTypeParams = new ArrayList<>();

		if (reader.accept('<')) {
			do {
				methodTypeParams.add(reader.word());
			} while (reader.accept(','));

			reader.expect('>');
		}

		List<String> scope = new ArrayList<>(isStatic ? List.of() : owner.typeParams);
		scope.addAll(methodTypeParams);
		MethodInfo.Returns mode = MethodInfo.Returns.DECLARED;
		Type returnType;
		String rest = reader.rest().trim();

		switch (rest) {
			case "SELF" -> {
				mode = MethodInfo.Returns.SELF;
				returnType = new Type.Ref(owner, owner.typeParams.stream().map(param -> (Type) new Type.Var(param)).toList());
			}
			case "ARG0" -> {
				mode = MethodInfo.Returns.ARG0;
				returnType = Type.INFER;
			}
			case "void" -> returnType = Type.VOID;
			default -> returnType = parseType(rest, owner, scope);
		}

		List<Type> paramTypes = new ArrayList<>();
		List<String> paramNames = new ArrayList<>();
		boolean varargs = false;

		for (String param : splitTopLevel(params)) {
			String trimmed = param.trim();

			if (trimmed.isEmpty()) {
				continue;
			}

			if (varargs) {
				throw new IllegalArgumentException("Varargs must be last in " + owner.name + "." + name);
			}

			SignatureReader paramReader = new SignatureReader(trimmed);
			Type type = paramReader.type(this, owner, scope);

			if (paramReader.acceptDots()) {
				varargs = true;
			}

			String paramName = paramReader.atEnd() ? "arg" + paramTypes.size() : paramReader.word();
			paramTypes.add(type);
			paramNames.add(paramName);
		}

		return new MethodInfo(name, owner, isStatic, paramTypes, paramNames, varargs, returnType, mode, methodTypeParams);
	}

	Type parseType(String text, ClassInfo owner, List<String> scope) {
		List<String> allScope = new ArrayList<>(owner.typeParams);
		allScope.addAll(scope);
		SignatureReader reader = new SignatureReader(text);
		Type type = reader.type(this, owner, allScope);

		if (!reader.atEnd()) {
			throw new IllegalArgumentException("Bad type " + text);
		}

		return type;
	}

	private static List<String> splitTopLevel(String text) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);

			if (c == '<') {
				depth++;
			} else if (c == '>') {
				depth--;
			} else if (c == ',' && depth == 0) {
				parts.add(text.substring(start, i));
				start = i + 1;
			}
		}

		parts.add(text.substring(start));
		return parts;
	}

	private static final Set<String> PRIMITIVE_NAMES = Set.of("int", "long", "double", "boolean", "char");

	private static final class SignatureReader {
		private final String text;
		private int index;

		SignatureReader(String text) {
			this.text = text;
		}

		void skipSpace() {
			while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
				index++;
			}
		}

		boolean atEnd() {
			skipSpace();
			return index >= text.length();
		}

		boolean accept(char c) {
			skipSpace();

			if (index < text.length() && text.charAt(index) == c) {
				index++;
				return true;
			}

			return false;
		}

		boolean acceptDots() {
			skipSpace();

			if (text.startsWith("...", index)) {
				index += 3;
				return true;
			}

			return false;
		}

		void expect(char c) {
			if (!accept(c)) {
				throw new IllegalArgumentException("Expected " + c + " in " + text);
			}
		}

		String word() {
			skipSpace();
			int start = index;

			while (index < text.length() && (Character.isJavaIdentifierPart(text.charAt(index)))) {
				index++;
			}

			if (start == index) {
				throw new IllegalArgumentException("Expected a name in " + text + " at " + index);
			}

			return text.substring(start, index);
		}

		String rest() {
			return text.substring(index);
		}

		Type type(SfyLibrary library, ClassInfo owner, List<String> scope) {
			String name = word();
			List<Type> args = new ArrayList<>();

			if (accept('<')) {
				do {
					args.add(type(library, owner, scope));
				} while (accept(','));

				expect('>');
			}

			if (scope.contains(name)) {
				return new Type.Var(name);
			}

			if (PRIMITIVE_NAMES.contains(name)) {
				return switch (name) {
					case "int" -> Type.INT;
					case "long" -> Type.LONG;
					case "double" -> Type.DOUBLE;
					case "boolean" -> Type.BOOLEAN;
					default -> Type.CHAR;
				};
			}

			switch (name) {
				case "Integer" -> {
					return Type.INT.box();
				}
				case "Long" -> {
					return Type.LONG.box();
				}
				case "Double" -> {
					return Type.DOUBLE.box();
				}
				case "Boolean" -> {
					return Type.BOOLEAN.box();
				}
				case "Character" -> {
					return Type.CHAR.box();
				}
				default -> {
				}
			}

			ClassInfo info = library.classes.get(name);

			if (info == null) {
				throw new IllegalArgumentException("Unknown type " + name + " in signature " + text);
			}

			return new Type.Ref(info, args);
		}
	}
}
