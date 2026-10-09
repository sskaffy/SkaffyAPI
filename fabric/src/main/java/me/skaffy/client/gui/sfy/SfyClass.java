package me.skaffy.client.gui.sfy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SfyClass {
	final SfyLibrary lib;
	final String file;
	final String packageName;
	final String simpleName;
	final ClassInfo info;
	final Map<String, ClassInfo> types;
	Object[] statics = new Object[0];
	Object[] staticDefaults = new Object[0];
	boolean importAll;
	java.util.Set<String> imported = java.util.Set.of();
	Object[] instanceDefaults = new Object[0];
	MethodImpl staticInit;
	MethodImpl instanceInit;
	MethodImpl constructor;
	private boolean staticsReady;

	SfyClass(SfyLibrary lib, String file, String packageName, String simpleName, ClassInfo info, Map<String, ClassInfo> types) {
		this.lib = lib;
		this.file = file;
		this.packageName = packageName;
		this.simpleName = simpleName;
		this.info = info;
		this.types = types;
	}

	public static SfyClass compile(SfyLibrary lib, String qualifiedName, String source) throws CompileException {
		lib.freeze();
		int dot = qualifiedName.lastIndexOf('.');
		String file = (dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1)) + ".sfy";
		return new Compiler(lib, file).compileFile(source, qualifiedName);
	}

	public String qualifiedName() {
		return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
	}

	public String simpleName() {
		return simpleName;
	}

	public String file() {
		return file;
	}

	public SfyInstance newInstance() {
		if (!staticsReady) {
			initStatics();
		}

		SfyInstance created = new SfyInstance(this, instanceDefaults.length);
		System.arraycopy(instanceDefaults, 0, created.fields, 0, instanceDefaults.length);
		run(instanceInit, created, simpleName + ".<init>");

		if (constructor != null) {
			run(constructor, created, simpleName + ".<init>");
		}

		return created;
	}

	private void run(MethodImpl impl, Object self, String name) {
		if (impl == null) {
			return;
		}

		try {
			impl.invoke(self, new Object[0]);
		} catch (ScriptException e) {
			e.finish(file, name);
			throw e;
		} catch (StackOverflowError e) {
			throw new ScriptException("Stack overflow in " + name);
		}
	}

	public List<SfyMethod> methods() {
		List<SfyMethod> all = new ArrayList<>();
		info.methods.values().forEach(list -> list.stream().filter(method -> method.impl != null).forEach(method -> all.add(new SfyMethod(method))));
		return all;
	}

	public SfyMethod findMethod(String name, int paramCount) {
		List<MethodInfo> list = info.methods.get(name);

		if (list == null) {
			return null;
		}

		for (MethodInfo method : list) {
			if (method.impl != null && method.params.size() == paramCount) {
				return new SfyMethod(method);
			}
		}

		return null;
	}

	public boolean hasMethod(String name) {
		return info.methods.containsKey(name);
	}

	public Object invoke(SfyInstance self, SfyMethod method, Object[] args) {
		String name = simpleName + "." + method.info.name;

		try {
			return method.info.impl.invoke(method.info.isStatic ? null : self, args);
		} catch (ScriptException e) {
			e.finish(file, name);
			throw e;
		} catch (StackOverflowError e) {
			throw new ScriptException("Stack overflow in " + name);
		}
	}

	public Object[] importArgs(SfyMethod method, List<?> values) {
		List<Type> params = method.info.params;

		if (values.size() != params.size()) {
			throw new IllegalArgumentException(method.signature() + " takes " + params.size() + " values, got " + values.size());
		}

		Object[] args = new Object[params.size()];

		for (int i = 0; i < args.length; i++) {
			args[i] = importValue(values.get(i), params.get(i), method.info.paramNames.get(i));
		}

		return args;
	}

	public void replaceMethod(String source) throws CompileException {
		new Compiler(lib, file).replaceMethod(this, source);
	}

	@SuppressWarnings("unchecked")
	private Object importValue(Object value, Type type, String where) {
		if (value == null) {
			if (type instanceof Type.Prim prim && !prim.boxed) {
				throw new IllegalArgumentException(where + " can't be null, it's " + type);
			}

			return null;
		}

		if (type instanceof Type.Prim prim) {
			return switch (prim.kind) {
				case INT -> {
					if (value instanceof Number number && number.doubleValue() == number.longValue() && number.longValue() == number.intValue()) {
						yield number.intValue();
					}

					throw mismatch(where, type, value);
				}
				case LONG -> {
					if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
						yield number.longValue();
					}

					throw mismatch(where, type, value);
				}
				case DOUBLE -> {
					if (value instanceof Number number) {
						yield number.doubleValue();
					}

					throw mismatch(where, type, value);
				}
				case BOOLEAN -> {
					if (value instanceof Boolean bool) {
						yield bool;
					}

					throw mismatch(where, type, value);
				}
				case CHAR -> {
					if (value instanceof String text && text.length() == 1) {
						yield text.charAt(0);
					}

					if (value instanceof Character character) {
						yield character;
					}

					throw mismatch(where, type, value);
				}
			};
		}

		if (type.isLoose()) {
			return importPlain(value);
		}

		if (!(type instanceof Type.Ref ref)) {
			throw mismatch(where, type, value);
		}

		ClassInfo cls = ref.cls;

		switch (cls.name) {
			case "Object" -> {
				return importPlain(value);
			}
			case "String" -> {
				if (value instanceof String) {
					return value;
				}

				throw mismatch(where, type, value);
			}
			case "List" -> {
				if (value instanceof List<?> list) {
					List<Object> result = new ArrayList<>(list.size());

					for (Object element : list) {
						result.add(importValue(element, boxed(ref.arg(0)), where + "[]"));
					}

					return result;
				}

				throw mismatch(where, type, value);
			}
			case "Map" -> {
				if (value instanceof Map<?, ?> map) {
					Map<Object, Object> result = new LinkedHashMap<>();
					map.forEach((key, element) -> result.put(importValue(key, boxed(ref.arg(0)), where + " key"), importValue(element, boxed(ref.arg(1)), where + "[" + key + "]")));
					return result;
				}

				throw mismatch(where, type, value);
			}
			case "Color" -> {
				if (value instanceof Number number) {
					return new ColorValue(number.intValue());
				}

				if (value instanceof String text && text.startsWith("#") && (text.length() == 7 || text.length() == 9)) {
					try {
						int argb = Integer.parseUnsignedInt(text.substring(1), 16);
						return new ColorValue(text.length() == 7 ? 0xFF000000 | argb : argb);
					} catch (NumberFormatException ignored) {
					}
				}

				throw mismatch(where, type, value);
			}
			case "Length" -> {
				if (value instanceof Number number) {
					return LengthValue.px(number.doubleValue());
				}

				if (value instanceof String text && text.endsWith("%")) {
					try {
						return new LengthValue(Double.parseDouble(text.substring(0, text.length() - 1)), 0);
					} catch (NumberFormatException ignored) {
					}
				}

				throw mismatch(where, type, value);
			}
			case "Duration" -> {
				if (value instanceof Number number) {
					return new DurationValue(number.longValue());
				}

				throw mismatch(where, type, value);
			}
			default -> {
			}
		}

		if (cls.kind == ClassInfo.Kind.RECORD && value instanceof Map<?, ?> map) {
			UserType record = cls.runtime;
			Object[] values = new Object[record.fieldNames.size()];

			for (int i = 0; i < values.length; i++) {
				String field = record.fieldNames.get(i);
				FieldInfo info = cls.fields.get(field);

				if (!map.containsKey(field)) {
					throw new IllegalArgumentException(where + " is a " + cls.name + " but has no " + field);
				}

				values[i] = importValue(map.get(field), info.type, where + "." + field);
			}

			return new RecordValue(record, values);
		}

		if (cls.kind == ClassInfo.Kind.ENUM && value instanceof String name) {
			if (!staticsReady) {
				initStatics();
			}

			for (EnumValue constant : cls.runtime.constants) {
				if (constant.name.equals(name)) {
					return constant;
				}
			}

			throw new IllegalArgumentException(where + ": " + cls.name + " has no constant " + name);
		}

		if (cls.kind == ClassInfo.Kind.NATIVE_ENUM && value instanceof String name) {
			int index = cls.constants.indexOf(name);

			if (index >= 0) {
				return cls.nativeConstants[index];
			}

			throw new IllegalArgumentException(where + ": " + cls.name + " has no constant " + name);
		}

		throw mismatch(where, type, value);
	}

	private void initStatics() {
		staticsReady = true;
		statics = staticDefaults.clone();
		run(staticInit, null, simpleName + ".<static>");
	}

	private static Type boxed(Type type) {
		return type instanceof Type.Prim prim ? prim.box() : type;
	}

	private static Object importPlain(Object value) {
		if (value instanceof List<?> list) {
			List<Object> result = new ArrayList<>(list.size());
			list.forEach(element -> result.add(importPlain(element)));
			return result;
		}

		if (value instanceof Map<?, ?> map) {
			Map<Object, Object> result = new LinkedHashMap<>();
			map.forEach((key, element) -> result.put(importPlain(key), importPlain(element)));
			return result;
		}

		return value;
	}

	private static IllegalArgumentException mismatch(String where, Type type, Object value) {
		return new IllegalArgumentException(where + " should be " + type + ", got " + Values.typeName(value) + " " + Values.str(value));
	}

	public static Object exportValue(Object value) {
		return switch (value) {
			case null -> null;
			case Integer ignored -> value;
			case Long ignored -> value;
			case Double ignored -> value;
			case Boolean ignored -> value;
			case String ignored -> value;
			case Character character -> String.valueOf(character);
			case List<?> list -> {
				List<Object> result = new ArrayList<>(list.size());
				list.forEach(element -> result.add(exportValue(element)));
				yield result;
			}
			case Map<?, ?> map -> {
				Map<Object, Object> result = new LinkedHashMap<>();
				map.forEach((key, element) -> result.put(exportValue(key), exportValue(element)));
				yield result;
			}
			case RecordValue record -> {
				Map<Object, Object> result = new LinkedHashMap<>();

				for (int i = 0; i < record.type.fieldNames.size(); i++) {
					result.put(record.type.fieldNames.get(i), exportValue(record.values[i]));
				}

				yield result;
			}
			case EnumValue constant -> constant.name;
			case Enum<?> constant -> constant.name();
			case ColorValue color -> color.argb();
			case DurationValue duration -> duration.millis();
			case LengthValue length -> length.toString();
			case ExceptionValue exception -> exception.message();
			default -> throw new IllegalArgumentException(Values.typeName(value) + " can't be sent to the server");
		};
	}
}
