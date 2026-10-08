package me.skaffy.client.gui.sfy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import me.skaffy.client.gui.sfy.Frame.Ev;
import me.skaffy.client.gui.sfy.Frame.Ex;
import me.skaffy.client.gui.sfy.Type.PrimKind;

final class Compiler {
	private static final int MAX_ERRORS = 50;

	private final SfyLibrary lib;
	private final String file;
	private final List<CompileError> errors = new ArrayList<>();
	private SfyClass output;
	private ClassInfo main;
	private final Map<String, ClassInfo> userTypes = new LinkedHashMap<>();
	private final List<Object> staticDefaults = new ArrayList<>();
	private final List<Object> instanceDefaults = new ArrayList<>();
	private final Map<ClassInfo, List<Object>> enumFieldDefaults = new HashMap<>();
	private final Map<ClassInfo, List<FieldInit>> enumFieldInits = new HashMap<>();
	private final Map<ClassInfo, MethodInfo> enumConstructors = new HashMap<>();
	private final Map<ClassInfo, Ast.Constructor> recordConstructors = new HashMap<>();
	private final List<FieldInit> staticInits = new ArrayList<>();
	private final List<FieldInit> instanceInits = new ArrayList<>();
	private final List<PendingBody> bodies = new ArrayList<>();
	private Ast.Constructor mainConstructor;
	private boolean importAll;
	private final Set<String> imported = new HashSet<>();
	private final Set<String> reportedImports = new HashSet<>();

	private final ClassInfo objectCls;
	private final ClassInfo stringCls;
	private final ClassInfo listCls;
	private final ClassInfo mapCls;
	private final ClassInfo exceptionCls;
	private final ClassInfo colorCls;
	private final ClassInfo lengthCls;
	private final ClassInfo durationCls;
	private final Type objectType;
	private final Type stringType;
	private final Type exceptionType;
	private final Type colorType;
	private final Type lengthType;
	private final Type durationType;

	private FuncCtx ctx;

	private record FieldInit(FieldInfo field, Ast.Expr init) {
	}

	private record PendingBody(MethodInfo method, List<Ast.Param> params, Ast.Block body, ClassInfo owner, boolean isConstructor) {
	}

	private record T(Type type, Ev ev) {
	}

	Compiler(SfyLibrary lib, String file) {
		this.lib = lib;
		this.file = file;
		objectCls = lib.find("Object");
		stringCls = lib.find("String");
		listCls = lib.find("List");
		mapCls = lib.find("Map");
		exceptionCls = lib.find("Exception");
		colorCls = lib.find("Color");
		lengthCls = lib.find("Length");
		durationCls = lib.find("Duration");
		objectType = new Type.Ref(objectCls, List.of());
		stringType = new Type.Ref(stringCls, List.of());
		exceptionType = new Type.Ref(exceptionCls, List.of());
		colorType = new Type.Ref(colorCls, List.of());
		lengthType = new Type.Ref(lengthCls, List.of());
		durationType = new Type.Ref(durationCls, List.of());
	}


	SfyClass compileFile(String source, String qualifiedName) throws CompileException {
		Ast.Unit unit = Parser.parseFile(file, source);
		checkName(unit, qualifiedName);
		checkImports(unit);
		declareTypes(unit.type());
		output = new SfyClass(lib, file, unit.packageName(), unit.type().name(), main, userTypes);
		output.importAll = importAll;
		output.imported = Set.copyOf(imported);
		declareMembers(unit.type());
		throwIfErrors();
		compileBodies(unit.type());
		throwIfErrors();
		output.staticDefaults = staticDefaults.toArray();
		output.instanceDefaults = instanceDefaults.toArray();
		return output;
	}

	void replaceMethod(SfyClass target, String source) throws CompileException {
		output = target;
		main = target.info;
		userTypes.putAll(target.types);
		importAll = target.importAll;
		imported.addAll(target.imported);
		Ast.Method method = Parser.parseMethod(file, source, main.name);
		Type returnType = method.returnType() == null ? Type.VOID : resolveType(method.returnType());
		List<Type> params = method.params().stream().map(param -> resolveType(param.type())).toList();
		throwIfErrors();
		MethodInfo existing = null;

		for (MethodInfo candidate : main.methods.getOrDefault(method.name(), List.of())) {
			if (candidate.impl != null && candidate.params.equals(params)) {
				existing = candidate;
			}
		}

		if (existing == null) {
			throw new CompileException(List.of(new CompileError(file, method.pos(), "There is no method " + method.name() + params.stream().map(Object::toString).toList().toString().replace('[', '(').replace(']', ')') + " to replace")));
		}

		if (!existing.returnType.equals(returnType)) {
			throw new CompileException(List.of(new CompileError(file, method.pos(), "The new " + method.name() + " must return " + existing.returnType + " like the old one")));
		}

		compileMethodBody(new PendingBody(existing, method.params(), method.body(), main, false));
		throwIfErrors();
	}

	private void throwIfErrors() throws CompileException {
		if (!errors.isEmpty()) {
			throw new CompileException(errors);
		}
	}

	private void checkImports(Ast.Unit unit) {
		boolean any = false;

		for (Ast.Import imp : unit.imports()) {
			if (imp.name().equals("skaffy.shader") || imp.name().startsWith("skaffy.shader.")) {
				error(imp.pos(), "This file imports skaffy.shader, so it's a shader file; GUI files import skaffy.gui.*");
				continue;
			}

			if (imp.wildcard()) {
				if (lib.libraryNames.containsValue(imp.name())) {
					importAll = true;
					any = true;
				} else {
					error(imp.pos(), "Unknown import " + imp.name() + ".* (GUI files import skaffy.gui.*)");
				}

				continue;
			}

			int dot = imp.name().lastIndexOf('.');
			String pkg = dot < 0 ? "" : imp.name().substring(0, dot);
			String simple = imp.name().substring(dot + 1);

			if (!lib.libraryNames.containsValue(pkg)) {
				error(imp.pos(), "Unknown import " + imp.name() + " (GUI files import skaffy.gui.* or single names from it)");
			} else if (!pkg.equals(lib.libraryNames.get(simple))) {
				error(imp.pos(), pkg + " has no " + simple + didYouMean(simple, lib.libraryNames.keySet()));
			} else {
				imported.add(simple);
				any = true;
			}
		}

		if (!any) {
			error(unit.packagePos(), "A GUI file needs import skaffy.gui.*; (or single names like import skaffy.gui.Box;) after its package");
		}
	}

	private boolean requireImport(String name, Pos pos) {
		String pkg = lib.libraryNames.get(name);

		if (pkg == null || importAll || imported.contains(name)) {
			return true;
		}

		if (reportedImports.add(name)) {
			error(pos, name + " comes from " + pkg + ": add import " + pkg + "." + name + "; (or import " + pkg + ".*;)");
		}

		return false;
	}

	private void checkName(Ast.Unit unit, String qualifiedName) {
		int dot = qualifiedName.lastIndexOf('.');
		String expectedPackage = dot < 0 ? "" : qualifiedName.substring(0, dot);
		String expectedClass = dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);

		if (!unit.packageName().equals(expectedPackage)) {
			error(unit.packagePos(), "This file is " + qualifiedName + ", so it must start with: package " + (expectedPackage.isEmpty() ? "<something>" : expectedPackage) + ";");
		}

		if (!unit.type().name().equals(expectedClass)) {
			error(unit.type().pos(), "The class must be called " + expectedClass + " like the file");
		}
	}


	private void declareTypes(Ast.ClassDecl decl) {
		main = new ClassInfo(decl.name(), ClassInfo.Kind.MAIN, List.of());
		main.superClass = objectCls;
		userTypes.put(main.name, main);
		checkBuiltinName(decl.name(), decl.pos());

		for (Ast.Member member : decl.members()) {
			String name;
			ClassInfo.Kind kind;

			if (member instanceof Ast.RecordDecl record) {
				name = record.name();
				kind = ClassInfo.Kind.RECORD;
			} else if (member instanceof Ast.EnumDecl declared) {
				name = declared.name();
				kind = ClassInfo.Kind.ENUM;
			} else {
				continue;
			}

			if (userTypes.containsKey(name)) {
				error(member.pos(), name + " is already declared");
				continue;
			}

			checkBuiltinName(name, member.pos());
			ClassInfo info = new ClassInfo(name, kind, List.of());
			info.superClass = objectCls;
			info.outer = main;
			main.nested.put(name, info);
			userTypes.put(name, info);
			userTypes.put(main.name + "." + name, info);
		}
	}

	private void checkBuiltinName(String name, Pos pos) {
		if (lib.find(name) != null || Set.of("Integer", "Long", "Double", "Boolean", "Character", "Object", "ArrayList", "HashMap", "LinkedHashMap").contains(name)) {
			error(pos, name + " is already a built-in type, pick another name");
		}
	}

	private void declareMembers(Ast.ClassDecl decl) {
		for (Ast.Member member : decl.members()) {
			switch (member) {
				case Ast.Field field -> declareField(main, field);
				case Ast.Method method -> declareMethod(main, method);
				case Ast.Constructor constructor -> {
					if (!constructor.params().isEmpty()) {
						error(constructor.pos(), "The GUI class is created when it opens, so its constructor can't have parameters");
					} else if (mainConstructor != null) {
						error(constructor.pos(), "Only one constructor");
					} else {
						mainConstructor = constructor;
					}
				}
				case Ast.RecordDecl record -> declareRecord(record);
				case Ast.EnumDecl declared -> declareEnum(declared);
			}
		}
	}

	private void declareField(ClassInfo owner, Ast.Field field) {
		Type type = resolveType(field.type());
		boolean isStatic = field.modifiers().has("static");

		if (owner.fields.containsKey(field.name())) {
			error(field.pos(), "Field " + field.name() + " is already declared");
			return;
		}

		if (owner.kind == ClassInfo.Kind.RECORD && !isStatic) {
			error(field.pos(), "Records only have their components; make " + field.name() + " static or a component");
			return;
		}

		checkAnnotations(field.modifiers(), false);
		FieldInfo info = new FieldInfo(field.name(), owner, type, isStatic, field.modifiers().has("final"));
		info.pos = field.pos();

		if (isStatic) {
			info.slot = staticDefaults.size();
			staticDefaults.add(defaultValue(type));
		} else if (owner == main) {
			info.slot = instanceDefaults.size();
			instanceDefaults.add(defaultValue(type));
		} else {
			List<Object> defaults = enumFieldDefaults.computeIfAbsent(owner, key -> new ArrayList<>());
			info.slot = defaults.size();
			defaults.add(defaultValue(type));
		}

		owner.fields.put(field.name(), info);

		if (info.isFinal && field.init() == null && isStatic) {
			error(field.pos(), "Static final field " + field.name() + " needs a value");
		}

		if (field.init() != null) {
			FieldInit init = new FieldInit(info, field.init());

			if (isStatic) {
				staticInits.add(init);
			} else if (owner == main) {
				instanceInits.add(init);
			} else {
				enumFieldInits.computeIfAbsent(owner, key -> new ArrayList<>()).add(init);
			}
		}
	}

	private void checkAnnotations(Ast.Modifiers modifiers, boolean method) {
		for (Ast.Annotation annotation : modifiers.annotations()) {
			if (!(method && (annotation.name().equals("Gui") || annotation.name().equals("Hud") || annotation.name().equals("Override")))) {
				error(annotation.pos(), "Unknown annotation @" + annotation.name() + (method ? " (only @Gui and @Hud exist)" : ""));
			} else if (annotation.name().equals("Gui") || annotation.name().equals("Hud")) {
				requireImport(annotation.name(), annotation.pos());
			}
		}
	}

	private MethodInfo declareMethod(ClassInfo owner, Ast.Method method) {
		Type returnType = method.returnType() == null ? Type.VOID : resolveType(method.returnType());
		List<Type> params = new ArrayList<>();
		List<String> names = new ArrayList<>();

		for (Ast.Param param : method.params()) {
			if (param.type() != null && param.type().varargs()) {
				error(param.pos(), "Your own methods can't take varargs (...), use a List");
			}

			if (param.type() == null) {
				error(param.pos(), "Method parameters need a type (var only works for local variables)");
			}

			params.add(param.type() == null ? Type.ERROR : resolveType(param.type()));

			if (names.contains(param.name())) {
				error(param.pos(), "Parameter " + param.name() + " is declared twice");
			}

			names.add(param.name());
		}

		checkAnnotations(method.modifiers(), true);
		boolean isStatic = method.modifiers().has("static");
		MethodInfo info = new MethodInfo(method.name(), owner, isStatic, params, names, false, returnType, MethodInfo.Returns.DECLARED, List.of());
		info.isPublic = method.modifiers().has("public");
		info.isGui = method.modifiers().annotated("Gui");
		info.isHud = method.modifiers().annotated("Hud");
		info.pos = method.pos();

		if (info.isGui && info.isHud) {
			error(method.pos(), "A method is either a screen (@Gui) or a HUD layer (@Hud), not both");
		}

		if (info.isGui || info.isHud) {
			String kind = info.isGui ? "@Gui" : "@Hud";

			if (owner != main) {
				error(method.pos(), kind + " methods belong in the class itself, not in records or enums");
			}

			if (isStatic) {
				error(method.pos(), kind + " methods can't be static");
			}

			if (returnType != Type.VOID) {
				error(method.pos(), kind + " methods return void");
			}
		}

		for (MethodInfo existing : owner.methods.getOrDefault(method.name(), List.of())) {
			if (existing.impl != null && existing.params.equals(params)) {
				error(method.pos(), "Method " + info.signature() + " is already declared");
			}
		}

		info.impl = new MethodImpl(owner.name + "." + method.name(), file, params.size());
		owner.addMethod(info);
		bodies.add(new PendingBody(info, method.params(), method.body(), owner, false));
		return info;
	}

	private void declareRecord(Ast.RecordDecl record) {
		ClassInfo info = userTypes.get(record.name());
		List<String> names = new ArrayList<>();
		List<Type> types = new ArrayList<>();

		for (Ast.Param component : record.components()) {
			if (component.type() == null) {
				error(component.pos(), "Record components need a type");
				continue;
			}

			if (names.contains(component.name())) {
				error(component.pos(), "Component " + component.name() + " is declared twice");
				continue;
			}

			Type type = resolveType(component.type());
			FieldInfo field = new FieldInfo(component.name(), info, type, false, true);
			field.slot = names.size();
			field.pos = component.pos();
			info.fields.put(component.name(), field);
			names.add(component.name());
			types.add(type);
		}

		info.runtime = new UserType(record.name(), names, true);
		MethodInfo constructor = new MethodInfo("<init>", info, true, types, names, false, new Type.Ref(info, List.of()), MethodInfo.Returns.DECLARED, List.of());
		info.constructors.add(constructor);

		for (Ast.Member member : record.members()) {
			switch (member) {
				case Ast.Field field -> declareField(info, field);
				case Ast.Method method -> declareMethod(info, method);
				case Ast.Constructor declared -> {
					if (!declared.compact() && !declared.params().stream().map(param -> param.type() == null ? Type.ERROR : resolveType(param.type())).toList().equals(types)) {
						error(declared.pos(), "Records only have their main constructor in .sfy: " + record.name() + "(" + String.join(", ", types.stream().map(Object::toString).toList()) + ")");
					} else if (recordConstructors.containsKey(info)) {
						error(declared.pos(), "Only one constructor");
					} else {
						recordConstructors.put(info, declared);
					}
				}
				default -> error(member.pos(), "Records and enums can't be declared inside a record");
			}
		}

		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			boolean declaredByUser = info.methods.getOrDefault(name, List.of()).stream().anyMatch(method -> method.params.isEmpty());

			if (!declaredByUser) {
				int index = i;
				MethodInfo accessor = new MethodInfo(name, info, false, List.of(), List.of(), false, types.get(i), MethodInfo.Returns.DECLARED, List.of());
				accessor.invoker = (self, args) -> ((RecordValue) self).values[index];
				info.addMethod(accessor);
			}
		}
	}

	private void declareEnum(Ast.EnumDecl declared) {
		ClassInfo info = userTypes.get(declared.name());
		Type self = new Type.Ref(info, List.of());

		for (Ast.EnumConstant constant : declared.constants()) {
			if (info.constants.contains(constant.name())) {
				error(constant.pos(), "Constant " + constant.name() + " is declared twice");
			}

			info.constants.add(constant.name());
		}

		info.runtime = new UserType(declared.name(), List.of(), false);
		enumFieldDefaults.putIfAbsent(info, new ArrayList<>());

		for (Ast.Member member : declared.members()) {
			switch (member) {
				case Ast.Field field -> declareField(info, field);
				case Ast.Method method -> declareMethod(info, method);
				case Ast.Constructor constructor -> {
					if (enumConstructors.containsKey(info)) {
						error(constructor.pos(), "Only one constructor");
						continue;
					}

					List<Type> params = constructor.params().stream().map(param -> param.type() == null ? Type.ERROR : resolveType(param.type())).toList();
					MethodInfo method = new MethodInfo("<init>", info, false, params, constructor.params().stream().map(Ast.Param::name).toList(), false, Type.VOID, MethodInfo.Returns.DECLARED, List.of());
					method.impl = new MethodImpl(info.name + ".<init>", file, params.size());
					enumConstructors.put(info, method);
					bodies.add(new PendingBody(method, constructor.params(), constructor.body(), info, true));
				}
				default -> error(member.pos(), "Records and enums can't be declared inside an enum");
			}
		}

		MethodInfo name = new MethodInfo("name", info, false, List.of(), List.of(), false, stringType, MethodInfo.Returns.DECLARED, List.of());
		name.invoker = (value, args) -> ((EnumValue) value).name;
		info.addMethod(name);
		MethodInfo ordinal = new MethodInfo("ordinal", info, false, List.of(), List.of(), false, Type.INT, MethodInfo.Returns.DECLARED, List.of());
		ordinal.invoker = (value, args) -> ((EnumValue) value).ordinal;
		info.addMethod(ordinal);
		UserType runtime = info.runtime;
		MethodInfo values = new MethodInfo("values", info, true, List.of(), List.of(), false, new Type.Ref(listCls, List.of(self)), MethodInfo.Returns.DECLARED, List.of());
		values.invoker = (value, args) -> new ArrayList<>(List.of(runtime.constants));
		info.addMethod(values);
		MethodInfo valueOf = new MethodInfo("valueOf", info, true, List.of(stringType), List.of("name"), false, self, MethodInfo.Returns.DECLARED, List.of());
		valueOf.invoker = (value, args) -> {
			for (EnumValue constant : runtime.constants) {
				if (constant.name.equals(args[0])) {
					return constant;
				}
			}

			throw new ScriptException(runtime.name + " has no constant " + args[0]);
		};
		info.addMethod(valueOf);
	}


	private void compileBodies(Ast.ClassDecl decl) {
		List<Ex> staticParts = new ArrayList<>();
		int staticLocals = 1;
		ctx = new FuncCtx(null, main, true, Type.VOID, false, main.name + ".<static>");

		for (Ast.Member member : decl.members()) {
			if (member instanceof Ast.EnumDecl declared) {
				staticParts.add(enumConstants(userTypes.get(declared.name()), declared));
			}
		}

		staticLocals = Math.max(staticLocals, ctx.maxSlots);

		for (FieldInit init : staticInits) {
			ctx = new FuncCtx(null, init.field.owner, true, Type.VOID, false, main.name + ".<static>");
			T value = coerce(expr(init.init, init.field.type), init.field.type, init.init.pos(), init.init);
			int slot = init.field.slot;
			SfyClass cls = output;
			Ev ev = value.ev;
			staticLocals = Math.max(staticLocals, ctx.maxSlots);
			staticParts.add(frame -> {
				cls.statics[slot] = ev.ev(frame);
				return Frame.NORMAL;
			});
		}

		MethodImpl staticInit = new MethodImpl(main.name + ".<static>", file, 0);
		staticInit.set(staticLocals, sequence(staticParts));
		output.staticInit = staticInit;

		ctx = new FuncCtx(null, main, false, Type.VOID, false, main.name + ".<init>");
		ctx.inConstructor = true;
		List<Ex> instanceParts = new ArrayList<>();

		for (FieldInit init : instanceInits) {
			T value = coerce(expr(init.init, init.field.type), init.field.type, init.init.pos(), init.init);
			int slot = init.field.slot;
			Ev ev = value.ev;
			instanceParts.add(frame -> {
				((SfyInstance) frame.self).fields[slot] = ev.ev(frame);
				return Frame.NORMAL;
			});
		}

		MethodImpl instanceInit = new MethodImpl(main.name + ".<init>", file, 0);
		instanceInit.set(Math.max(1, ctx.maxSlots), sequence(instanceParts));
		output.instanceInit = instanceInit;

		if (mainConstructor != null) {
			MethodInfo constructor = new MethodInfo("<init>", main, false, List.of(), List.of(), false, Type.VOID, MethodInfo.Returns.DECLARED, List.of());
			constructor.impl = new MethodImpl(main.name + ".<init>", file, 0);
			compileBody(new PendingBody(constructor, List.of(), mainConstructor.body(), main, true));
			output.constructor = constructor.impl;
		}

		for (PendingBody body : bodies) {
			compileMethodBody(body);
		}

		for (Map.Entry<ClassInfo, Ast.Constructor> entry : recordConstructors.entrySet()) {
			compileRecordConstructor(entry.getKey(), entry.getValue());
		}
	}

	private void compileMethodBody(PendingBody body) {
		compileBody(body);
	}

	private void compileBody(PendingBody body) {
		MethodInfo method = body.method;
		boolean isStatic = method.isStatic && !body.isConstructor;
		ctx = new FuncCtx(null, body.owner, isStatic, method.returnType, false, method.impl.name);
		ctx.inConstructor = body.isConstructor;

		for (int i = 0; i < body.params.size(); i++) {
			Ast.Param param = body.params.get(i);
			declareLocal(param.name(), method.params.get(i), param.isFinal(), true, param.pos());
		}

		Ex code = block(body.body, false);

		if (method.returnType != Type.VOID && completes(body.body)) {
			error(body.body.pos(), "Method " + method.name + " must return " + article(method.returnType) + " (there's a way to reach the end without a return)");
		}

		closeScope();
		method.impl.set(Math.max(1, ctx.maxSlots), code);
	}

	private void compileRecordConstructor(ClassInfo record, Ast.Constructor declared) {
		MethodInfo canonical = record.constructors.getFirst();
		ctx = new FuncCtx(null, record, false, Type.VOID, false, record.name + ".<init>");
		ctx.inConstructor = true;
		List<Integer> slots = new ArrayList<>();

		for (int i = 0; i < canonical.params.size(); i++) {
			String name = declared.compact() ? canonical.paramNames.get(i) : declared.params().get(i).name();
			slots.add(declareLocal(name, canonical.params.get(i), false, true, declared.pos()).slot);
		}

		Ex body = block(declared.body(), false);
		closeScope();
		boolean compact = declared.compact();
		Ex full = frame -> {
			int status = body.ex(frame);

			if (compact) {
				Object[] values = ((RecordValue) frame.self).values;

				for (int i = 0; i < slots.size(); i++) {
					values[i] = frame.locals[slots.get(i)];
				}
			}

			return status == Frame.RETURN ? Frame.NORMAL : status;
		};
		MethodImpl impl = new MethodImpl(record.name + ".<init>", file, canonical.params.size());
		impl.set(Math.max(1, ctx.maxSlots), full);
		canonical.impl = impl;
	}

	private Ex enumConstants(ClassInfo info, Ast.EnumDecl declared) {
		MethodInfo constructor = enumConstructors.get(info);
		List<Ev[]> argsPerConstant = new ArrayList<>();

		for (Ast.EnumConstant constant : declared.constants()) {
			List<Type> params = constructor == null ? List.of() : constructor.params;

			if (constant.args().size() != params.size()) {
				error(constant.pos(), info.name + "." + constant.name() + " needs " + params.size() + " values for the constructor");
				argsPerConstant.add(new Ev[0]);
				continue;
			}

			Ev[] args = new Ev[params.size()];

			for (int i = 0; i < args.length; i++) {
				args[i] = coerce(expr(constant.args().get(i), params.get(i)), params.get(i), constant.args().get(i).pos()).ev;
			}

			argsPerConstant.add(args);
		}

		List<FieldInit> inits = enumFieldInits.getOrDefault(info, List.of());
		FuncCtx previous = ctx;
		ctx = new FuncCtx(null, info, false, Type.VOID, false, info.name + ".<init>");
		ctx.inConstructor = true;
		List<Ex> fieldParts = new ArrayList<>();

		for (FieldInit init : inits) {
			T value = coerce(expr(init.init, init.field.type), init.field.type, init.init.pos());
			int slot = init.field.slot;
			Ev ev = value.ev;
			fieldParts.add(frame -> {
				((EnumValue) frame.self).fields[slot] = ev.ev(frame);
				return Frame.NORMAL;
			});
		}

		MethodImpl fieldInit = new MethodImpl(info.name + ".<init>", file, 0);
		fieldInit.set(Math.max(1, ctx.maxSlots), sequence(fieldParts));
		ctx = previous;
		UserType runtime = info.runtime;
		Object[] defaults = enumFieldDefaults.getOrDefault(info, List.of()).toArray();
		List<String> names = List.copyOf(info.constants);
		return frame -> {
			EnumValue[] constants = new EnumValue[names.size()];
			runtime.constants = constants;

			for (int i = 0; i < constants.length; i++) {
				EnumValue constant = new EnumValue(runtime, i, names.get(i), defaults.length);
				System.arraycopy(defaults, 0, constant.fields, 0, defaults.length);
				constants[i] = constant;
			}

			for (int i = 0; i < constants.length; i++) {
				Ev[] args = argsPerConstant.get(i);
				Object[] values = new Object[args.length];

				for (int a = 0; a < args.length; a++) {
					values[a] = args[a].ev(frame);
				}

				fieldInit.invoke(constants[i], new Object[0]);

				if (constructor != null) {
					constructor.impl.invoke(constants[i], values);
				}
			}

			return Frame.NORMAL;
		};
	}

	private static Ex sequence(List<Ex> parts) {
		Ex[] array = parts.toArray(new Ex[0]);
		return frame -> {
			for (Ex part : array) {
				int status = part.ex(frame);

				if (status != Frame.NORMAL) {
					return status;
				}
			}

			return Frame.NORMAL;
		};
	}


	private Type resolveType(Ast.TypeRef ref) {
		if (ref.varargs()) {
			error(ref.pos(), "Varargs (...) only exist in built-in methods");
		}

		String name = ref.name();

		switch (name) {
			case "int" -> {
				return Type.INT;
			}
			case "long" -> {
				return Type.LONG;
			}
			case "double" -> {
				return Type.DOUBLE;
			}
			case "boolean" -> {
				return Type.BOOLEAN;
			}
			case "char" -> {
				return Type.CHAR;
			}
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
			case "Float", "Short", "Byte" -> {
				error(ref.pos(), "There is no " + name + " in .sfy, use " + (name.equals("Float") ? "Double" : "Integer"));
				return Type.ERROR;
			}
			default -> {
			}
		}

		ClassInfo info = userTypes.get(name);

		if (info == null) {
			String alias = switch (name) {
				case "ArrayList", "LinkedList" -> "List";
				case "HashMap", "LinkedHashMap" -> "Map";
				case "RuntimeException", "Throwable" -> "Exception";
				default -> name;
			};
			info = lib.find(alias);

			if (info != null && (alias.equals("Math") || alias.equals("Integer") || alias.equals("Double"))) {
				error(ref.pos(), name + " is not a type you can use here");
				return Type.ERROR;
			}

			if (info != null && !requireImport(info.name, ref.pos())) {
				return Type.ERROR;
			}
		}

		if (info == null) {
			String hint = switch (name) {
				case "Set", "HashSet", "TreeSet" -> " (use a List, or a Map with Boolean values)";
				case "TreeMap" -> " (use Map; keys keep the order they were added in)";
				case "Array", "Arrays" -> " (use List)";
				case "Thread", "File", "Path", "Files", "System", "Runtime", "Class" -> " (.sfy files can't use that)";
				case "IllegalArgumentException", "IllegalStateException", "NullPointerException", "NumberFormatException", "IndexOutOfBoundsException", "ArithmeticException" -> " (every exception in .sfy is an Exception)";
				default -> "";
			};
			error(ref.pos(), "Unknown type " + name + hint);
			return Type.ERROR;
		}

		if (ref.args().size() != info.typeParams.size()) {
			if (info.typeParams.isEmpty()) {
				error(ref.pos(), info.name + " doesn't take type arguments");
			} else {
				error(ref.pos(), info.name + " needs " + info.typeParams.size() + " type argument" + (info.typeParams.size() == 1 ? "" : "s") + ", like " + info.name + "<" + String.join(", ", info.typeParams.stream().map(param -> "String").toList()) + ">");
			}

			return new Type.Ref(info, info.typeParams.stream().map(param -> (Type) Type.INFER).toList());
		}

		List<Type> args = new ArrayList<>();

		for (Ast.TypeRef arg : ref.args()) {
			Type type = resolveType(arg);
			args.add(type instanceof Type.Prim prim ? prim.box() : type);
		}

		return new Type.Ref(info, args);
	}

	private static Object defaultValue(Type type) {
		if (type instanceof Type.Prim prim && !prim.boxed) {
			return switch (prim.kind) {
				case INT -> 0;
				case LONG -> 0L;
				case DOUBLE -> 0.0;
				case BOOLEAN -> false;
				case CHAR -> '\0';
			};
		}

		return null;
	}

	private boolean assignable(Type from, Type to) {
		if (from.isLoose() || to.isLoose() || from.equals(to)) {
			return from != Type.VOID || to.isLoose();
		}

		if (from == Type.VOID || to == Type.VOID) {
			return false;
		}

		if (to instanceof Type.Ref ref && ref.cls == objectCls) {
			return true;
		}

		if (from == Type.NULL) {
			return to.isReference();
		}

		if (from instanceof Type.Prim a && to instanceof Type.Prim b) {
			return switch (a.kind) {
				case INT -> b.kind == PrimKind.LONG || b.kind == PrimKind.DOUBLE;
				case LONG -> b.kind == PrimKind.DOUBLE;
				case CHAR -> b.kind == PrimKind.INT || b.kind == PrimKind.LONG || b.kind == PrimKind.DOUBLE;
				default -> false;
			};
		}

		if (from instanceof Type.Prim prim && to instanceof Type.Ref ref) {
			if (ref.cls == lengthCls) {
				return prim.kind == PrimKind.INT || prim.kind == PrimKind.LONG || prim.kind == PrimKind.DOUBLE;
			}

			if (ref.cls == durationCls) {
				return prim.kind == PrimKind.INT || prim.kind == PrimKind.LONG;
			}

			return false;
		}

		if (from instanceof Type.Ref a && to instanceof Type.Ref b) {
			if (a.cls == b.cls) {
				for (int i = 0; i < b.args.size(); i++) {
					Type x = a.arg(i);
					Type y = b.arg(i);

					if (!x.isLoose() && !y.isLoose() && !x.equals(y)) {
						return false;
					}
				}

				return true;
			}

			return a.cls.isSubclassOf(b.cls);
		}

		return false;
	}

	private T coerce(T value, Type target, Pos pos) {
		return coerce(value, target, pos, null);
	}

	private T coerce(T value, Type target, Pos pos, Ast.Expr source) {
		Type from = value.type;

		if (target == null || target.isLoose() || from == Type.ERROR) {
			return new T(target == null || target.isLoose() ? from : target, value.ev);
		}

		if (target.is(PrimKind.CHAR) && from.is(PrimKind.INT) && source instanceof Ast.Literal literal && literal.value() instanceof Integer number && number >= 0 && number <= 0xFFFF) {
			char converted = (char) (int) number;
			return new T(target, frame -> converted);
		}

		if (!assignable(from, target)) {
			String hint = "";

			if (from.isNumeric() && target.isNumeric()) {
				hint = " (write (" + ((Type.Prim) target).unboxed() + ") in front to convert it)";
			} else if (from.is(PrimKind.INT) && target instanceof Type.Ref ref && ref.cls == colorCls) {
				hint = " (write colors as #RRGGBB)";
			} else if (from.equals(stringType) && target.isNumeric()) {
				hint = " (use Integer.parseInt or Double.parseDouble)";
			} else if (target.equals(stringType) && from.isPrimitive()) {
				hint = " (use \"\" + value or String.valueOf(value))";
			}

			error(pos, "Expected " + target + " but this is " + from + hint);
			return new T(Type.ERROR, value.ev);
		}

		return new T(target, convert(value.ev, from, target, pos));
	}

	private Ev convert(Ev ev, Type from, Type to, Pos pos) {
		if (to instanceof Type.Ref ref && ref.cls == lengthCls && from instanceof Type.Prim prim) {
			return switch (prim.kind) {
				case INT -> frame -> LengthValue.px(unbox(ev.ev(frame), pos).intValue());
				case LONG, DOUBLE -> frame -> LengthValue.px(unbox(ev.ev(frame), pos).doubleValue());
				default -> ev;
			};
		}

		if (to instanceof Type.Ref ref && ref.cls == durationCls && from instanceof Type.Prim) {
			return frame -> new DurationValue(unbox(ev.ev(frame), pos).longValue());
		}

		if (from instanceof Type.Prim a && to instanceof Type.Prim b) {
			boolean nullable = a.boxed;

			if (a.kind == b.kind) {
				if (nullable && !b.boxed) {
					int line = pos.line();
					return frame -> {
						Object value = ev.ev(frame);

						if (value == null) {
							throw new ScriptException("This " + a + " is null, it can't be used as " + b, line);
						}

						return value;
					};
				}

				return ev;
			}

			return numeric(ev, a.kind, b.kind, pos);
		}

		return ev;
	}

	private static Number unbox(Object value, Pos pos) {
		if (value == null) {
			throw new ScriptException("A number here is null", pos.line());
		}

		return value instanceof Character character ? (Number) (int) character : (Number) value;
	}

	private static Ev numeric(Ev ev, PrimKind from, PrimKind to, Pos pos) {
		return switch (to) {
			case INT -> switch (from) {
				case LONG -> frame -> (int) (long) (Long) nonNull(ev.ev(frame), pos);
				case DOUBLE -> frame -> (int) (double) (Double) nonNull(ev.ev(frame), pos);
				case CHAR -> frame -> (int) (Character) nonNull(ev.ev(frame), pos);
				default -> ev;
			};
			case LONG -> switch (from) {
				case INT -> frame -> (long) (Integer) nonNull(ev.ev(frame), pos);
				case DOUBLE -> frame -> (long) (double) (Double) nonNull(ev.ev(frame), pos);
				case CHAR -> frame -> (long) (Character) nonNull(ev.ev(frame), pos);
				default -> ev;
			};
			case DOUBLE -> switch (from) {
				case INT -> frame -> (double) (Integer) nonNull(ev.ev(frame), pos);
				case LONG -> frame -> (double) (Long) nonNull(ev.ev(frame), pos);
				case CHAR -> frame -> (double) (Character) nonNull(ev.ev(frame), pos);
				default -> ev;
			};
			case CHAR -> switch (from) {
				case INT -> frame -> (char) (int) (Integer) nonNull(ev.ev(frame), pos);
				case LONG -> frame -> (char) (long) (Long) nonNull(ev.ev(frame), pos);
				case DOUBLE -> frame -> (char) (double) (Double) nonNull(ev.ev(frame), pos);
				default -> ev;
			};
			case BOOLEAN -> ev;
		};
	}

	private static Object nonNull(Object value, Pos pos) {
		if (value == null) {
			throw new ScriptException("A number here is null", pos.line());
		}

		return value;
	}

	private static PrimKind promote(Type a, Type b) {
		PrimKind x = ((Type.Prim) a).kind;
		PrimKind y = ((Type.Prim) b).kind;

		if (x == PrimKind.DOUBLE || y == PrimKind.DOUBLE) {
			return PrimKind.DOUBLE;
		}

		if (x == PrimKind.LONG || y == PrimKind.LONG) {
			return PrimKind.LONG;
		}

		return PrimKind.INT;
	}

	private static Type.Prim prim(PrimKind kind) {
		return switch (kind) {
			case INT -> Type.INT;
			case LONG -> Type.LONG;
			case DOUBLE -> Type.DOUBLE;
			case BOOLEAN -> Type.BOOLEAN;
			case CHAR -> Type.CHAR;
		};
	}

	private T toKind(T value, PrimKind kind, Pos pos) {
		Type.Prim from = (Type.Prim) value.type;

		if (from.kind == kind) {
			return new T(prim(kind), convert(value.ev, from, prim(kind), pos));
		}

		return new T(prim(kind), numeric(value.ev, from.kind, kind, pos));
	}

	private boolean isRef(Type type, ClassInfo cls) {
		return type instanceof Type.Ref ref && ref.cls == cls;
	}


	private final class FuncCtx {
		final FuncCtx outer;
		final ClassInfo owner;
		final boolean isStatic;
		final Type returnType;
		final boolean isLambda;
		final String name;
		boolean inConstructor;
		int nextSlot;
		int maxSlots;
		Scope scope;
		final Map<Local, Local> captures = new IdentityHashMap<>();
		final List<Local> captureSources = new ArrayList<>();
		int loops;
		int breakables;
		final Deque<SwitchValue> switchValues = new ArrayDeque<>();

		FuncCtx(FuncCtx outer, ClassInfo owner, boolean isStatic, Type returnType, boolean isLambda, String name) {
			this.outer = outer;
			this.owner = owner;
			this.isStatic = isStatic;
			this.returnType = returnType;
			this.isLambda = isLambda;
			this.name = name;
			this.scope = new Scope(null, 0);
		}
	}

	private static final class Scope {
		final Scope parent;
		final int startSlot;
		final Map<String, Local> vars = new HashMap<>();

		Scope(Scope parent, int startSlot) {
			this.parent = parent;
			this.startSlot = startSlot;
		}
	}

	private static final class Local {
		final String name;
		final Type type;
		final int slot;
		final boolean isFinal;
		final boolean hasInit;
		final boolean capture;
		int writes;
		Pos capturedAt;
		boolean pattern;

		Local(String name, Type type, int slot, boolean isFinal, boolean hasInit, boolean capture) {
			this.name = name;
			this.type = type;
			this.slot = slot;
			this.isFinal = isFinal;
			this.hasInit = hasInit;
			this.capture = capture;
		}

		boolean effectivelyFinal() {
			return hasInit ? writes == 0 : writes <= 1;
		}

		Ev load() {
			int index = slot;
			return capture ? frame -> frame.captured[index] : frame -> frame.locals[index];
		}
	}

	private static final class SwitchValue {
		Type type;

		SwitchValue(Type type) {
			this.type = type;
		}
	}

	private void openScope() {
		ctx.scope = new Scope(ctx.scope, ctx.nextSlot);
	}

	private void closeScope() {
		Scope scope = ctx.scope;

		for (Local local : scope.vars.values()) {
			if (local.capturedAt != null && !local.effectivelyFinal()) {
				error(local.capturedAt, "A lambda uses " + local.name + ", but " + local.name + " changes after it's set; copy it into a new variable first (like Java)");
			}
		}

		ctx.scope = scope.parent == null ? scope : scope.parent;
		ctx.nextSlot = scope.startSlot;
	}

	private Local declareLocal(String name, Type type, boolean isFinal, boolean hasInit, Pos pos) {
		Local existing = findLocalHere(name);

		if (existing != null && !existing.pattern) {
			error(pos, "Variable " + name + " is already declared");
		}

		int slot = ctx.nextSlot++;
		ctx.maxSlots = Math.max(ctx.maxSlots, ctx.nextSlot);
		Local local = new Local(name, type, slot, isFinal, hasInit, false);
		ctx.scope.vars.put(name, local);
		return local;
	}

	private boolean visibleLocal(String name) {
		for (FuncCtx in = ctx; in != null; in = in.outer) {
			if (findLocalIn(in, name)) {
				return true;
			}
		}

		return false;
	}

	private Local findLocalHere(String name) {
		for (Scope scope = ctx.scope; scope != null; scope = scope.parent) {
			Local local = scope.vars.get(name);

			if (local != null) {
				return local;
			}
		}

		return null;
	}

	private Local findLocal(String name, Pos pos) {
		return findLocal(ctx, name, pos);
	}

	private Local findLocal(FuncCtx in, String name, Pos pos) {
		for (Scope scope = in.scope; scope != null; scope = scope.parent) {
			Local local = scope.vars.get(name);

			if (local != null) {
				return local;
			}
		}

		if (in.outer == null) {
			return null;
		}

		Local outer = findLocal(in.outer, name, pos);

		if (outer == null) {
			return null;
		}

		Local captured = in.captures.get(outer);

		if (captured == null) {
			if (outer.capturedAt == null) {
				outer.capturedAt = pos;
			}

			captured = new Local(name, outer.type, in.captureSources.size(), true, true, true);
			captured.capturedAt = pos;
			in.captures.put(outer, captured);
			in.captureSources.add(outer);
			Scope root = in.scope;

			while (root.parent != null) {
				root = root.parent;
			}

			root.vars.put(name, captured);
		}

		return captured;
	}



	private Ex block(Ast.Block block, boolean unused) {
		openScope();
		List<Ex> parts = new ArrayList<>();

		for (Ast.Stmt statement : block.statements()) {
			parts.add(statement(statement));
		}

		closeScope();
		return sequence(parts);
	}

	private Ex statement(Ast.Stmt statement) {
		return switch (statement) {
			case Ast.Block block -> block(block, true);
			case Ast.LocalVar local -> localVar(local);
			case Ast.ExprStmt expression -> {
				T value = expr(expression.expr(), null);
				Ev ev = value.ev;
				yield frame -> {
					ev.ev(frame);
					return Frame.NORMAL;
				};
			}
			case Ast.If branch -> ifStatement(branch);
			case Ast.While loop -> whileLoop(loop);
			case Ast.DoWhile loop -> doWhileLoop(loop);
			case Ast.For loop -> forLoop(loop);
			case Ast.ForEach loop -> forEachLoop(loop);
			case Ast.Return ret -> returnStatement(ret);
			case Ast.Break brk -> {
				if (ctx.breakables == 0) {
					error(brk.pos(), "break outside of a loop or switch");
				}

				yield frame -> Frame.BREAK;
			}
			case Ast.Continue cont -> {
				if (ctx.loops == 0) {
					error(cont.pos(), "continue outside of a loop");
				}

				yield frame -> Frame.CONTINUE;
			}
			case Ast.Throw thrown -> throwStatement(thrown);
			case Ast.Try attempt -> tryStatement(attempt);
			case Ast.Switch choice -> switchStatement(choice);
			case Ast.Yield yielded -> yieldStatement(yielded);
			case Ast.Empty ignored -> frame -> Frame.NORMAL;
		};
	}

	private Ex localVar(Ast.LocalVar declaration) {
		List<Ex> parts = new ArrayList<>();

		for (Ast.Declarator declarator : declaration.declarators()) {
			Type type;
			Ev init;

			if (declaration.type() == null) {
				if (declarator.init() == null) {
					error(declarator.pos(), "var needs a value: var " + declarator.name() + " = ...;");
					type = Type.ERROR;
					init = frame -> null;
				} else if (declarator.init() instanceof Ast.Lambda) {
					error(declarator.pos(), "A lambda needs a type, for example Runnable " + declarator.name() + " = () -> ...;");
					type = Type.ERROR;
					init = frame -> null;
				} else {
					T value = expr(declarator.init(), null);

					if (value.type == Type.NULL) {
						error(declarator.pos(), "var can't be null, write the type");
					}

					if (value.type == Type.VOID) {
						error(declarator.pos(), "That method returns nothing");
					}

					type = value.type == Type.NULL || value.type == Type.VOID ? Type.ERROR : value.type;
					init = value.ev;
				}
			} else {
				type = resolveType(declaration.type());

				if (declarator.init() == null) {
					Object value = defaultValue(type);
					init = frame -> value;
				} else {
					init = coerce(expr(declarator.init(), type), type, declarator.init().pos(), declarator.init()).ev;
				}
			}

			Local local = declareLocal(declarator.name(), type, declaration.isFinal(), declarator.init() != null, declarator.pos());
			int slot = local.slot;
			Ev value = init;
			parts.add(frame -> {
				frame.locals[slot] = value.ev(frame);
				return Frame.NORMAL;
			});
		}

		return sequence(parts);
	}

	private Ev condition(Ast.Expr expression) {
		return coerce(expr(expression, Type.BOOLEAN), Type.BOOLEAN, expression.pos()).ev;
	}

	private Ex ifStatement(Ast.If branch) {
		Ev condition = condition(branch.condition());
		openScope();
		Ex then = statement(branch.then());
		closeScope();
		Ex otherwise = null;

		if (branch.otherwise() != null) {
			openScope();
			otherwise = statement(branch.otherwise());
			closeScope();
		}

		Ex elseBranch = otherwise;
		return frame -> {
			if ((Boolean) condition.ev(frame)) {
				return then.ex(frame);
			}

			return elseBranch == null ? Frame.NORMAL : elseBranch.ex(frame);
		};
	}

	private Ex loopBody(Ast.Stmt body) {
		ctx.loops++;
		ctx.breakables++;
		openScope();
		Ex code = statement(body);
		closeScope();
		ctx.loops--;
		ctx.breakables--;
		return code;
	}

	private Ex whileLoop(Ast.While loop) {
		Ev condition = condition(loop.condition());
		Ex body = loopBody(loop.body());
		return frame -> {
			while ((Boolean) condition.ev(frame)) {
				int status = body.ex(frame);

				if (status == Frame.BREAK) {
					break;
				}

				if (status == Frame.RETURN || status == Frame.YIELD) {
					return status;
				}
			}

			return Frame.NORMAL;
		};
	}

	private Ex doWhileLoop(Ast.DoWhile loop) {
		Ex body = loopBody(loop.body());
		Ev condition = condition(loop.condition());
		return frame -> {
			do {
				int status = body.ex(frame);

				if (status == Frame.BREAK) {
					break;
				}

				if (status == Frame.RETURN || status == Frame.YIELD) {
					return status;
				}
			} while ((Boolean) condition.ev(frame));

			return Frame.NORMAL;
		};
	}

	private Ex forLoop(Ast.For loop) {
		openScope();
		List<Ex> init = new ArrayList<>();

		for (Ast.Stmt statement : loop.init()) {
			init.add(statement(statement));
		}

		Ex initCode = sequence(init);
		Ev condition = loop.condition() == null ? frame -> Boolean.TRUE : condition(loop.condition());
		Ev[] updates = new Ev[loop.updates().size()];

		for (int i = 0; i < updates.length; i++) {
			updates[i] = expr(loop.updates().get(i), null).ev;
		}

		Ex body = loopBody(loop.body());
		closeScope();
		return frame -> {
			initCode.ex(frame);

			while ((Boolean) condition.ev(frame)) {
				int status = body.ex(frame);

				if (status == Frame.BREAK) {
					break;
				}

				if (status == Frame.RETURN || status == Frame.YIELD) {
					return status;
				}

				for (Ev update : updates) {
					update.ev(frame);
				}
			}

			return Frame.NORMAL;
		};
	}

	private Ex forEachLoop(Ast.ForEach loop) {
		T iterable = expr(loop.iterable(), null);
		Type element;

		if (iterable.type instanceof Type.Ref ref && ref.cls == listCls) {
			element = ref.arg(0);
		} else {
			if (iterable.type != Type.ERROR) {
				error(loop.iterable().pos(), "for-each goes through a List, this is " + iterable.type + (isRef(iterable.type, mapCls) ? " (use map.keys() or map.values())" : ""));
			}

			element = Type.ERROR;
		}

		openScope();
		Type declared = loop.type() == null ? element : resolveType(loop.type());

		if (!assignable(element, declared)) {
			error(loop.pos(), "The list holds " + element + ", not " + declared);
		}

		Ev read = convert(frame -> frame.result, element, declared, loop.pos());
		Local local = declareLocal(loop.name(), declared, loop.isFinal(), true, loop.pos());
		int slot = local.slot;
		Ex body = loopBody(loop.body());
		closeScope();
		Ev list = iterable.ev;
		int line = loop.pos().line();
		return frame -> {
			Object value = list.ev(frame);

			if (value == null) {
				throw new ScriptException("The list is null", line);
			}

			try {
				for (Object item : (List<?>) value) {
					frame.result = item;
					frame.locals[slot] = read.ev(frame);
					int status = body.ex(frame);

					if (status == Frame.BREAK) {
						break;
					}

					if (status == Frame.RETURN || status == Frame.YIELD) {
						return status;
					}
				}
			} catch (ConcurrentModificationException e) {
				throw new ScriptException("The list was changed while a for-each loop went through it", line);
			}

			return Frame.NORMAL;
		};
	}

	private Ex returnStatement(Ast.Return ret) {
		Type expected = ctx.returnType;

		if (!ctx.switchValues.isEmpty()) {
			error(ret.pos(), "Can't return from inside a switch expression, use yield");
		}

		if (ret.value() == null) {
			if (expected != Type.VOID && !expected.isLoose()) {
				error(ret.pos(), "Return " + article(expected) + " here");
			}

			return frame -> {
				frame.result = null;
				return Frame.RETURN;
			};
		}

		if (expected == Type.VOID) {
			error(ret.pos(), (ctx.inConstructor ? "Constructors" : ctx.isLambda ? "This lambda" : "Void methods") + " can't return a value");
			return frame -> Frame.RETURN;
		}

		Ev value = coerce(expr(ret.value(), expected), expected, ret.value().pos(), ret.value()).ev;
		return frame -> {
			frame.result = value.ev(frame);
			return Frame.RETURN;
		};
	}

	private Ex throwStatement(Ast.Throw thrown) {
		T value = expr(thrown.value(), exceptionType);

		if (!isRef(value.type, exceptionCls) && value.type != Type.ERROR) {
			error(thrown.pos(), "You can only throw an Exception: throw new Exception(\"...\");");
		}

		Ev ev = value.ev;
		int line = thrown.pos().line();
		return frame -> {
			Object exception = ev.ev(frame);
			String message = exception instanceof ExceptionValue value1 ? value1.message() : "null";
			throw new ScriptException(message, line);
		};
	}

	private Ex tryStatement(Ast.Try attempt) {
		Ex body = block(attempt.body(), true);
		Ex handler = null;
		int slot = -1;

		if (attempt.catches().size() > 1) {
			error(attempt.catches().get(1).pos(), "One catch is enough: every exception in .sfy is an Exception");
		}

		if (!attempt.catches().isEmpty()) {
			Ast.Catch clause = attempt.catches().getFirst();
			Type type = resolveType(clause.type());

			if (!isRef(type, exceptionCls) && type != Type.ERROR) {
				error(clause.pos(), "Catch Exception: catch (Exception e)");
			}

			openScope();
			slot = declareLocal(clause.name(), exceptionType, false, true, clause.pos()).slot;
			handler = block(clause.body(), true);
			closeScope();
		}

		Ex finallyCode = attempt.finallyBlock() == null ? null : block(attempt.finallyBlock(), true);
		Ex catchCode = handler;
		int exceptionSlot = slot;
		return frame -> {
			int status;

			try {
				try {
					status = body.ex(frame);
				} catch (ScriptException e) {
					if (catchCode == null) {
						throw e;
					}

					frame.locals[exceptionSlot] = new ExceptionValue(e.scriptMessage());
					status = catchCode.ex(frame);
				}
			} catch (ScriptException e) {
				if (finallyCode != null) {
					Object saved = frame.result;
					int finallyStatus = finallyCode.ex(frame);

					if (finallyStatus != Frame.NORMAL) {
						return finallyStatus;
					}

					frame.result = saved;
				}

				throw e;
			}

			if (finallyCode != null) {
				Object saved = frame.result;
				int finallyStatus = finallyCode.ex(frame);

				if (finallyStatus != Frame.NORMAL) {
					return finallyStatus;
				}

				frame.result = saved;
			}

			return status;
		};
	}

	private Ex yieldStatement(Ast.Yield yielded) {
		if (ctx.switchValues.isEmpty()) {
			error(yielded.pos(), "yield only works inside a switch expression");
			return frame -> Frame.NORMAL;
		}

		SwitchValue target = ctx.switchValues.peek();
		T value = expr(yielded.value(), target.type);

		if (target.type == null) {
			target.type = value.type;
		}

		Ev ev = coerce(value, target.type, yielded.pos()).ev;
		return frame -> {
			frame.result = ev.ev(frame);
			return Frame.YIELD;
		};
	}


	private record SwitchPlan(Ev key, Map<Object, Integer> cases, int defaultCase, Type selectorType) {
	}

	private SwitchPlan planSwitch(Ast.Expr selector, List<Ast.Case> cases, Pos pos) {
		T value = expr(selector, null);
		Type type = value.type;
		Ev ev = value.ev;
		int line = pos.line();
		Ev key;
		ClassInfo enumType = null;

		if (type.is(PrimKind.INT) || type.is(PrimKind.CHAR) || isRef(type, stringCls)) {
			key = frame -> {
				Object v = ev.ev(frame);

				if (v == null) {
					throw new ScriptException("switch on null", line);
				}

				return v;
			};
		} else if (type instanceof Type.Ref ref && ref.cls.isEnum()) {
			enumType = ref.cls;
			key = frame -> {
				Object v = ev.ev(frame);

				if (v instanceof EnumValue constant) {
					return constant.ordinal;
				}

				if (v instanceof Enum<?> constant) {
					return constant.ordinal();
				}

				throw new ScriptException("switch on null", line);
			};
		} else {
			if (type != Type.ERROR) {
				error(selector.pos(), "switch works on int, char, String and enums, not " + type);
			}

			key = frame -> null;
		}

		Map<Object, Integer> map = new HashMap<>();
		int defaultCase = -1;

		for (int i = 0; i < cases.size(); i++) {
			Ast.Case clause = cases.get(i);

			if (clause.isDefault()) {
				if (defaultCase >= 0) {
					error(clause.pos(), "Only one default");
				}

				defaultCase = i;
			}

			for (Ast.Expr label : clause.labels()) {
				Object constant = labelValue(label, type, enumType);

				if (constant != null && map.put(constant, i) != null) {
					error(label.pos(), "Case " + constant + " appears twice");
				}
			}
		}

		return new SwitchPlan(key, map, defaultCase, type);
	}

	private Object labelValue(Ast.Expr label, Type selector, ClassInfo enumType) {
		if (enumType != null) {
			String name = label instanceof Ast.Name bare ? bare.name() : label instanceof Ast.Select select ? select.name() : null;
			int index = name == null ? -1 : enumType.constantIndex(name);

			if (index < 0) {
				error(label.pos(), enumType.name + " has no constant " + (name == null ? label : name));
				return null;
			}

			return index;
		}

		Object value = null;

		if (label instanceof Ast.Literal literal) {
			value = literal.value();
		} else if (label instanceof Ast.Unary unary && unary.op().equals("-") && unary.operand() instanceof Ast.Literal literal) {
			if (literal.value() instanceof Integer number) {
				value = -number;
			} else if (literal.value() instanceof Double number) {
				value = -number;
			}
		}

		if (selector.is(PrimKind.INT)) {
			if (value instanceof Integer) {
				return value;
			}

			if (value instanceof Character character) {
				return (int) character;
			}
		} else if (selector.is(PrimKind.CHAR)) {
			if (value instanceof Character) {
				return value;
			}

			if (value instanceof Integer number && number >= 0 && number <= 0xFFFF) {
				return (char) (int) number;
			}
		} else if (isRef(selector, stringCls)) {
			if (value instanceof String) {
				return value;
			}
		} else {
			return null;
		}

		error(label.pos(), "Case labels must be " + selector + " values written out, like 1, 'a' or \"text\"");
		return null;
	}

	private Ex switchStatement(Ast.Switch choice) {
		SwitchPlan plan = planSwitch(choice.selector(), choice.cases(), choice.pos());
		ctx.breakables++;
		openScope();
		Ex result;

		if (choice.arrows()) {
			Ex[] bodies = new Ex[choice.cases().size()];

			for (int i = 0; i < bodies.length; i++) {
				Ast.Case clause = choice.cases().get(i);

				if (clause.arrowValue() != null) {
					T value = expr(clause.arrowValue(), null);
					Ev ev = value.ev;
					bodies[i] = frame -> {
						ev.ev(frame);
						return Frame.NORMAL;
					};
				} else {
					openScope();
					bodies[i] = statement(clause.body().getFirst());
					closeScope();
				}
			}

			result = frame -> {
				Integer index = plan.cases.get(plan.key.ev(frame));
				int target = index != null ? index : plan.defaultCase;

				if (target < 0) {
					return Frame.NORMAL;
				}

				int status = bodies[target].ex(frame);
				return status == Frame.BREAK ? Frame.NORMAL : status;
			};
		} else {
			List<Ex> all = new ArrayList<>();
			int[] starts = new int[choice.cases().size()];

			for (int i = 0; i < starts.length; i++) {
				starts[i] = all.size();

				for (Ast.Stmt statement : choice.cases().get(i).body()) {
					all.add(statement(statement));
				}
			}

			Ex[] statements = all.toArray(new Ex[0]);
			result = frame -> {
				Integer index = plan.cases.get(plan.key.ev(frame));
				int target = index != null ? index : plan.defaultCase;

				if (target < 0) {
					return Frame.NORMAL;
				}

				for (int i = starts[target]; i < statements.length; i++) {
					int status = statements[i].ex(frame);

					if (status == Frame.BREAK) {
						return Frame.NORMAL;
					}

					if (status != Frame.NORMAL) {
						return status;
					}
				}

				return Frame.NORMAL;
			};
		}

		closeScope();
		ctx.breakables--;
		return result;
	}

	private T switchExpression(Ast.SwitchExpr choice, Type expected) {
		SwitchPlan plan = planSwitch(choice.selector(), choice.cases(), choice.pos());

		if (plan.defaultCase < 0) {
			boolean exhaustive = plan.selectorType instanceof Type.Ref ref && ref.cls.isEnum() && plan.cases.size() == ref.cls.constants.size();

			if (!exhaustive && plan.selectorType != Type.ERROR) {
				error(choice.pos(), "This switch needs a default (or every enum constant) so it always has a value");
			}
		}

		Type type = expected != null && !expected.isLoose() ? expected : null;
		openScope();
		T[] arrowValues = new T[choice.cases().size()];

		if (choice.arrows()) {
			for (int i = 0; i < arrowValues.length; i++) {
				Ast.Case clause = choice.cases().get(i);

				if (clause.arrowValue() != null) {
					arrowValues[i] = expr(clause.arrowValue(), type);
				}
			}

			if (type == null) {
				for (T arrow : arrowValues) {
					if (arrow != null && arrow.type != Type.ERROR) {
						type = type == null ? arrow.type : unify(type, arrow.type);
					}
				}
			}
		}

		SwitchValue value = new SwitchValue(type);
		ctx.switchValues.push(value);
		Ex[] bodies = new Ex[choice.cases().size()];
		int[] starts = new int[bodies.length];
		List<Ex> flat = new ArrayList<>();

		for (int i = 0; i < bodies.length; i++) {
			Ast.Case clause = choice.cases().get(i);

			if (choice.arrows()) {
				if (clause.arrowValue() != null) {
					T result = coerceOrAdopt(arrowValues[i], value, clause.arrowValue());
					Ev ev = result.ev;
					bodies[i] = frame -> {
						frame.result = ev.ev(frame);
						return Frame.YIELD;
					};
				} else {
					Ast.Stmt body = clause.body().getFirst();

					if (body instanceof Ast.Block && completes(body)) {
						error(clause.pos(), "This case must yield a value (yield ...;) or throw");
					}

					openScope();
					bodies[i] = statement(body);
					closeScope();
				}
			} else {
				starts[i] = flat.size();

				for (Ast.Stmt statement : clause.body()) {
					flat.add(statement(statement));
				}
			}
		}

		ctx.switchValues.pop();
		closeScope();
		Type resultType = value.type == null ? Type.ERROR : value.type;
		int line = choice.pos().line();

		if (choice.arrows()) {
			return new T(resultType, frame -> {
				Integer index = plan.cases.get(plan.key.ev(frame));
				int target = index != null ? index : plan.defaultCase;

				if (target < 0) {
					throw new ScriptException("No case matches", line);
				}

				int status = bodies[target].ex(frame);

				if (status == Frame.YIELD) {
					return frame.result;
				}

				throw new ScriptException("The switch case ended without a value", line);
			});
		}

		Ex[] statements = flat.toArray(new Ex[0]);
		return new T(resultType, frame -> {
			Integer index = plan.cases.get(plan.key.ev(frame));
			int target = index != null ? index : plan.defaultCase;

			if (target < 0) {
				throw new ScriptException("No case matches", line);
			}

			for (int i = starts[target]; i < statements.length; i++) {
				int status = statements[i].ex(frame);

				if (status == Frame.YIELD) {
					return frame.result;
				}

				if (status != Frame.NORMAL) {
					throw new ScriptException("The switch ended without a value", line);
				}
			}

			throw new ScriptException("The switch ended without a value", line);
		});
	}

	private T coerceOrAdopt(T value, SwitchValue target, Ast.Expr source) {
		if (target.type == null) {
			target.type = value.type;
			return value;
		}

		return coerce(value, target.type, source.pos(), source);
	}

	private Type unify(Type a, Type b) {
		if (a.equals(b) || b == Type.NULL && a.isReference()) {
			return a;
		}

		if (a == Type.NULL && b.isReference()) {
			return b;
		}

		if (a.isNumeric() && b.isNumeric()) {
			return prim(promote(a, b));
		}

		if (a == Type.NULL && b instanceof Type.Prim prim) {
			return prim.box();
		}

		if (b == Type.NULL && a instanceof Type.Prim prim) {
			return prim.box();
		}

		if (assignable(a, b)) {
			return b;
		}

		if (assignable(b, a)) {
			return a;
		}

		return objectType;
	}


	private boolean completes(Ast.Stmt statement) {
		return switch (statement) {
			case Ast.Return ignored -> false;
			case Ast.Throw ignored -> false;
			case Ast.Yield ignored -> false;
			case Ast.Break ignored -> false;
			case Ast.Continue ignored -> false;
			case Ast.Block block -> {
				for (Ast.Stmt inner : block.statements()) {
					if (!completes(inner)) {
						yield false;
					}
				}

				yield true;
			}
			case Ast.If branch -> branch.otherwise() == null || completes(branch.then()) || completes(branch.otherwise());
			case Ast.While loop -> !isTrue(loop.condition()) || breaks(loop.body());
			case Ast.DoWhile loop -> !isTrue(loop.condition()) || breaks(loop.body());
			case Ast.For loop -> loop.condition() != null && !isTrue(loop.condition()) || breaks(loop.body());
			case Ast.Try attempt -> {
				boolean body = completes(attempt.body()) || attempt.catches().stream().anyMatch(clause -> completes(clause.body()));
				yield body && (attempt.finallyBlock() == null || completes(attempt.finallyBlock()));
			}
			case Ast.Switch choice -> {
				boolean hasDefault = choice.cases().stream().anyMatch(Ast.Case::isDefault);

				if (!hasDefault) {
					yield true;
				}

				if (choice.arrows()) {
					yield choice.cases().stream().anyMatch(clause -> clause.arrowValue() != null || completes(clause.body().getFirst()) || breaks(clause.body().getFirst()));
				}

				Ast.Case last = choice.cases().getLast();
				boolean anyBreak = choice.cases().stream().anyMatch(clause -> clause.body().stream().anyMatch(this::breaks));
				yield anyBreak || last.body().isEmpty() || completes(last.body().getLast());
			}
			default -> true;
		};
	}

	private static boolean isTrue(Ast.Expr condition) {
		return condition instanceof Ast.Literal literal && Boolean.TRUE.equals(literal.value());
	}

	private boolean breaks(Ast.Stmt statement) {
		return switch (statement) {
			case Ast.Break ignored -> true;
			case Ast.Block block -> block.statements().stream().anyMatch(this::breaks);
			case Ast.If branch -> breaks(branch.then()) || branch.otherwise() != null && breaks(branch.otherwise());
			case Ast.Try attempt -> breaks(attempt.body()) || attempt.catches().stream().anyMatch(clause -> breaks(clause.body()))
					|| attempt.finallyBlock() != null && breaks(attempt.finallyBlock());
			default -> false;
		};
	}


	private T expr(Ast.Expr expression, Type expected) {
		return switch (expression) {
			case Ast.Literal literal -> literal(literal);
			case Ast.Name name -> name(name, expected);
			case Ast.Select select -> select(select, expected);
			case Ast.Call call -> call(call, expected);
			case Ast.New created -> newExpression(created, expected);
			case Ast.Unary unary -> unary(unary);
			case Ast.Binary binary -> binary(binary);
			case Ast.Assign assign -> assign(assign);
			case Ast.IncDec incDec -> incDec(incDec);
			case Ast.Conditional conditional -> conditional(conditional, expected);
			case Ast.Cast cast -> cast(cast);
			case Ast.InstanceOf test -> instanceOf(test);
			case Ast.Lambda lambda -> lambda(lambda, expected);
			case Ast.SwitchExpr choice -> switchExpression(choice, expected);
			case Ast.This self -> thisExpression(self);
		};
	}

	private T literal(Ast.Literal literal) {
		Object value = literal.value();
		Type type = switch (value) {
			case null -> Type.NULL;
			case Integer ignored -> Type.INT;
			case Long ignored -> Type.LONG;
			case Double ignored -> Type.DOUBLE;
			case Character ignored -> Type.CHAR;
			case String ignored -> stringType;
			case Boolean ignored -> Type.BOOLEAN;
			case Ast.ColorLit ignored -> colorType;
			case Ast.PercentLit ignored -> lengthType;
			case Ast.DurationLit ignored -> durationType;
			default -> Type.ERROR;
		};
		Object runtime = switch (value) {
			case null -> null;
			case Ast.ColorLit color -> new ColorValue(color.argb());
			case Ast.PercentLit percent -> new LengthValue(percent.percent(), 0);
			case Ast.DurationLit duration -> new DurationValue(duration.millis());
			default -> value;
		};
		return new T(type, frame -> runtime);
	}

	private T thisExpression(Ast.This self) {
		if (ctx.isStatic) {
			error(self.pos(), "There is no 'this' in static code");
			return bad();
		}

		return new T(new Type.Ref(ctx.owner, List.of()), frame -> frame.self);
	}

	private T bad() {
		return new T(Type.ERROR, frame -> {
			throw new IllegalStateException("Code with compile errors ran");
		});
	}

	private T name(Ast.Name name, Type expected) {
		Local local = findLocal(name.name(), name.pos());

		if (local != null) {
			return new T(local.type, local.load());
		}

		FieldInfo field = findField(name.name());

		if (field != null) {
			return fieldLoad(field, null, name.pos());
		}

		T constant = enumConstant(name.name(), expected);

		if (constant != null) {
			return constant;
		}

		FieldInfo global = lib.globals.fields.get(name.name());

		if (global != null) {
			requireImport(name.name(), name.pos());
			return fieldLoad(global, null, name.pos());
		}

		if (typeByName(name.name()) != null) {
			error(name.pos(), name.name() + " is a type, not a value");
		} else if (expected instanceof Type.Ref ref && ref.cls.isEnum()) {
			error(name.pos(), ref.cls.name + " has no constant " + name.name() + didYouMean(name.name(), ref.cls.constants));
		} else {
			error(name.pos(), "Unknown name " + name.name() + didYouMean(name.name(), visibleNames()));
		}

		return bad();
	}

	private T enumConstant(String name, Type expected) {
		if (!(expected instanceof Type.Ref ref) || !ref.cls.isEnum()) {
			return null;
		}

		int index = ref.cls.constantIndex(name);
		return index < 0 ? null : constantLoad(ref.cls, index);
	}

	private T constantLoad(ClassInfo type, int index) {
		Type result = new Type.Ref(type, List.of());

		if (type.kind == ClassInfo.Kind.NATIVE_ENUM) {
			Object constant = type.nativeConstants[index];
			return new T(result, frame -> constant);
		}

		UserType runtime = type.runtime;
		return new T(result, frame -> runtime.constants[index]);
	}

	private FieldInfo findField(String name) {
		ClassInfo owner = ctx.owner;
		FieldInfo field = owner.fields.get(name);

		if (field != null) {
			return field;
		}

		if (owner != main) {
			FieldInfo outer = main.fields.get(name);

			if (outer != null && outer.isStatic) {
				return outer;
			}
		}

		return null;
	}

	private T fieldLoad(FieldInfo field, Ev target, Pos pos) {
		if (field.nativeValue != null) {
			return new T(field.type, frame -> field.nativeValue.get());
		}

		int slot = field.slot;

		if (field.isStatic) {
			SfyClass cls = output;
			return new T(field.type, frame -> cls.statics[slot]);
		}

		if (target == null) {
			if (ctx.isStatic) {
				error(pos, "Field " + field.name + " belongs to an instance and can't be used in static code" + (ctx.owner != main && field.owner == main ? " (records and enums can't use the GUI class's fields)" : ""));
				return bad();
			}

			if (field.owner != ctx.owner) {
				error(pos, "Field " + field.name + " can't be used here");
				return bad();
			}

			target = frame -> frame.self;
		}

		Ev object = target;
		int line = pos.line();
		String fieldName = field.name;
		return new T(field.type, switch (field.owner.kind) {
			case MAIN -> frame -> ((SfyInstance) nonNullTarget(object.ev(frame), fieldName, line)).fields[slot];
			case RECORD -> frame -> ((RecordValue) nonNullTarget(object.ev(frame), fieldName, line)).values[slot];
			case ENUM -> frame -> ((EnumValue) nonNullTarget(object.ev(frame), fieldName, line)).fields[slot];
			default -> frame -> null;
		});
	}

	private static Object nonNullTarget(Object value, String member, int line) {
		if (value == null) {
			throw new ScriptException("Can't use " + member + " because the value is null", line);
		}

		return value;
	}

	private ClassInfo typeByName(String name) {
		ClassInfo info = userTypes.get(name);

		if (info != null) {
			return info;
		}

		return lib.find(name);
	}

	private ClassInfo typeOf(Ast.Expr expression) {
		if (expression instanceof Ast.Name name) {
			if (visibleLocal(name.name()) || findField(name.name()) != null || lib.globals.fields.containsKey(name.name())) {
				return null;
			}

			ClassInfo type = typeByName(name.name());

			if (type != null && userTypes.get(name.name()) == null) {
				requireImport(type.name, name.pos());
			}

			return type;
		}

		if (expression instanceof Ast.Select select && select.target() instanceof Ast.Name outer && outer.name().equals(main.name)) {
			ClassInfo nested = main.nested.get(select.name());

			if (nested != null && typeOf(outer) == main) {
				return nested;
			}
		}

		return null;
	}

	private T select(Ast.Select select, Type expected) {
		ClassInfo type = typeOf(select.target());

		if (type != null) {
			int index = type.isEnum() ? type.constantIndex(select.name()) : -1;

			if (index >= 0) {
				return constantLoad(type, index);
			}

			FieldInfo field = type.fields.get(select.name());

			if (field != null && field.isStatic) {
				return fieldLoad(field, null, select.pos());
			}

			error(select.pos(), type.name + " has no static field or constant " + select.name());
			return bad();
		}

		if (select.target() instanceof Ast.Name name && name.name().equals("System") && !visibleLocal("System")) {
			error(select.pos(), "Use LOGGER.info(...) to print");
			return bad();
		}

		T target = expr(select.target(), null);

		if (target.type == Type.ERROR) {
			return bad();
		}

		ClassInfo cls = target.type.classInfo();

		if (cls != null && cls.isUser()) {
			FieldInfo field = cls.fields.get(select.name());

			if (field != null) {
				if (field.isStatic) {
					return fieldLoad(field, null, select.pos());
				}

				return fieldLoad(field, target.ev, select.pos());
			}
		}

		String hint = switch (select.name()) {
			case "length" -> isRef(target.type, stringCls) ? " (use length())" : "";
			case "size" -> " (use size())";
			default -> cls != null && !cls.findMethods(select.name()).isEmpty() ? " (it's a method: " + select.name() + "())" : "";
		};
		error(select.pos(), target.type + " has no field " + select.name() + hint);
		return bad();
	}


	private record Pre(Ast.Expr expr, T typed, int lambdaArity, String bareName) {
		boolean isLambda() {
			return lambdaArity >= 0;
		}
	}

	private record Choice(MethodInfo method, Map<String, Type> bindings) {
	}

	private List<Pre> preType(List<Ast.Expr> args) {
		List<Pre> result = new ArrayList<>();

		for (Ast.Expr arg : args) {
			if (arg instanceof Ast.Lambda lambda) {
				result.add(new Pre(arg, null, lambda.params().size(), null));
			} else if (arg instanceof Ast.Name name && findLocal(name.name(), name.pos()) == null && findField(name.name()) == null
					&& !lib.globals.fields.containsKey(name.name()) && typeByName(name.name()) == null) {
				result.add(new Pre(arg, null, -1, name.name()));
			} else {
				result.add(new Pre(arg, expr(arg, null), -1, null));
			}
		}

		return result;
	}

	private T call(Ast.Call call, Type expected) {
		if (call.target() == null) {
			return unqualifiedCall(call, expected);
		}

		ClassInfo type = typeOf(call.target());

		if (type == mapCls && call.name().equals("of")) {
			return mapOf(call, expected);
		}

		if (type != null) {
			List<MethodInfo> methods = type.findMethods(call.name()).stream().filter(method -> method.isStatic).toList();

			if (methods.isEmpty()) {
				boolean instanceOnly = !type.findMethods(call.name()).isEmpty();
				error(call.pos(), type.name + " has no static method " + call.name() + (instanceOnly ? " (it needs an instance)" : ""));
				preType(call.args());
				return bad();
			}

			return invoke(methods, null, null, call.args(), expected, call.pos(), type.name + "." + call.name());
		}

		T target = expr(call.target(), null);

		if (target.type == Type.ERROR) {
			preType(call.args());
			return bad();
		}

		if (target.type instanceof Type.Prim prim && !prim.boxed) {
			error(call.pos(), prim + " has no methods");
			preType(call.args());
			return bad();
		}

		Type receiver = target.type instanceof Type.Prim prim ? prim : target.type;
		ClassInfo cls = receiver.classInfo();

		if (cls == null) {
			error(call.pos(), receiver + " has no methods");
			preType(call.args());
			return bad();
		}

		List<MethodInfo> methods = cls.findMethods(call.name()).stream().filter(method -> !method.isStatic).toList();

		if (methods.isEmpty()) {
			error(call.pos(), receiver + " has no method " + call.name() + suggestion(cls, call.name()));
			preType(call.args());
			return bad();
		}

		return invoke(methods, target, receiver, call.args(), expected, call.pos(), call.name());
	}

	private T mapOf(Ast.Call call, Type expected) {
		List<Ast.Expr> args = call.args();

		if (args.size() % 2 != 0) {
			error(call.pos(), "Map.of takes keys and values in pairs: Map.of(\"a\", 1, \"b\", 2)");
			return bad();
		}

		Type keyType = null;
		Type valueType = null;

		if (expected instanceof Type.Ref ref && ref.cls == mapCls) {
			keyType = ref.arg(0).isLoose() ? null : ref.arg(0);
			valueType = ref.arg(1).isLoose() ? null : ref.arg(1);
		}

		List<T> keys = new ArrayList<>();
		List<T> values = new ArrayList<>();

		for (int i = 0; i < args.size(); i += 2) {
			keys.add(expr(args.get(i), keyType));
			values.add(expr(args.get(i + 1), valueType));
		}

		Type k = keyType != null ? keyType : shared(keys);
		Type v = valueType != null ? valueType : shared(values);
		Ev[] keyEvs = new Ev[keys.size()];
		Ev[] valueEvs = new Ev[values.size()];

		for (int i = 0; i < keyEvs.length; i++) {
			keyEvs[i] = coerce(keys.get(i), k, args.get(i * 2).pos(), args.get(i * 2)).ev;
			valueEvs[i] = coerce(values.get(i), v, args.get(i * 2 + 1).pos(), args.get(i * 2 + 1)).ev;
		}

		return new T(new Type.Ref(mapCls, List.of(k, v)), frame -> {
			Map<Object, Object> map = Stdlib.newMap();

			for (int i = 0; i < keyEvs.length; i++) {
				map.put(keyEvs[i].ev(frame), valueEvs[i].ev(frame));
			}

			return map;
		});
	}

	private Type shared(List<T> values) {
		Type result = null;

		for (T value : values) {
			if (value.type == Type.NULL || value.type.isLoose()) {
				continue;
			}

			result = result == null ? value.type : unify(result, value.type);
		}

		if (result == null) {
			return Type.INFER;
		}

		return result instanceof Type.Prim prim ? prim.box() : result;
	}

	private String suggestion(ClassInfo cls, String name) {
		String special = switch (name) {
			case "length" -> cls == listCls ? " (use size())" : "";
			case "keySet" -> " (use keys())";
			case "stream", "forEach" -> " (use a for-each loop)";
			case "println", "print" -> " (use LOGGER.info(...))";
			default -> "";
		};

		if (!special.isEmpty()) {
			return special;
		}

		List<String> names = new ArrayList<>();

		for (ClassInfo type = cls; type != null; type = type.superClass) {
			names.addAll(type.methods.keySet());
		}

		return didYouMean(name, names);
	}

	private static String didYouMean(String name, Iterable<String> candidates) {
		String best = null;
		int bestDistance = 3;

		for (String candidate : candidates) {
			if (candidate.startsWith("<")) {
				continue;
			}

			int distance = candidate.equalsIgnoreCase(name) ? 0 : distance(name.toLowerCase(java.util.Locale.ROOT), candidate.toLowerCase(java.util.Locale.ROOT));

			if (distance < bestDistance && distance <= Math.max(1, name.length() / 3)) {
				best = candidate;
				bestDistance = distance;
			}
		}

		return best == null ? "" : " (did you mean " + best + "?)";
	}

	private static int distance(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];

		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}

		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;

			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}

			int[] swap = previous;
			previous = current;
			current = swap;
		}

		return previous[b.length()];
	}

	private List<String> visibleNames() {
		List<String> names = new ArrayList<>();

		for (FuncCtx in = ctx; in != null; in = in.outer) {
			for (Scope scope = in.scope; scope != null; scope = scope.parent) {
				names.addAll(scope.vars.keySet());
			}
		}

		names.addAll(ctx.owner.fields.keySet());
		names.addAll(main.fields.keySet());
		names.addAll(lib.globals.fields.keySet());
		return names;
	}

	private static String article(Type type) {
		String name = type.toString();
		return (!name.isEmpty() && "aeiouAEIOU".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
	}

	private T unqualifiedCall(Ast.Call call, Type expected) {
		ClassInfo owner = ctx.owner;
		List<MethodInfo> own = owner.findMethods(call.name()).stream().filter(method -> method.impl != null || method.invoker != null).toList();

		if (!own.isEmpty()) {
			List<MethodInfo> usable = ctx.isStatic ? own.stream().filter(method -> method.isStatic).toList() : own;

			if (usable.isEmpty()) {
				error(call.pos(), call.name() + "() belongs to an instance and can't be called from static code");
				preType(call.args());
				return bad();
			}

			return invoke(usable, null, null, call.args(), expected, call.pos(), call.name());
		}

		if (owner != main) {
			List<MethodInfo> outer = main.methods.getOrDefault(call.name(), List.of());

			if (!outer.isEmpty()) {
				List<MethodInfo> statics = outer.stream().filter(method -> method.isStatic).toList();

				if (statics.isEmpty()) {
					error(call.pos(), call.name() + "() belongs to the GUI class; records and enums can only call its static methods");
					preType(call.args());
					return bad();
				}

				return invoke(statics, null, null, call.args(), expected, call.pos(), call.name());
			}
		}

		List<MethodInfo> globals = lib.globals.methods.getOrDefault(call.name(), List.of());

		if (!globals.isEmpty()) {
			requireImport(call.name(), call.pos());
			return invoke(globals, null, null, call.args(), expected, call.pos(), call.name());
		}

		if (visibleLocal(call.name())) {
			Local local = findLocal(call.name(), call.pos());
			ClassInfo cls = local.type.classInfo();

			if (cls != null && cls.sam != null) {
				error(call.pos(), call.name() + " is a " + local.type + "; call it with " + call.name() + "." + cls.sam.name + "(...)");
				preType(call.args());
				return bad();
			}
		}

		String hint = switch (call.name()) {
			case "println", "print", "printf" -> " (use LOGGER.info(...))";
			default -> {
				List<String> names = new ArrayList<>(owner.methods.keySet());
				names.addAll(main.methods.keySet());
				names.addAll(lib.globals.methods.keySet());
				yield didYouMean(call.name(), names);
			}
		};
		error(call.pos(), "Unknown method " + call.name() + "()" + hint);
		preType(call.args());
		return bad();
	}

	private T invoke(List<MethodInfo> candidates, T target, Type receiver, List<Ast.Expr> args, Type expected, Pos pos, String description) {
		List<Pre> pre = preType(args);

		for (Pre arg : pre) {
			if (arg.typed != null && arg.typed.type == Type.ERROR) {
				return bad();
			}
		}

		Map<String, Type> receiverBindings = new HashMap<>();

		if (receiver instanceof Type.Ref ref) {
			for (int i = 0; i < ref.cls.typeParams.size(); i++) {
				receiverBindings.put(ref.cls.typeParams.get(i), ref.arg(i));
			}

		}

		List<Choice> applicable = new ArrayList<>();

		for (MethodInfo method : candidates) {
			Map<String, Type> bindings = applicable(method, pre, receiverBindings);

			if (bindings != null) {
				applicable.add(new Choice(method, bindings));
			}
		}

		if (applicable.isEmpty()) {
			StringBuilder found = new StringBuilder();

			for (int i = 0; i < pre.size(); i++) {
				if (i > 0) {
					found.append(", ");
				}

				Pre arg = pre.get(i);
				found.append(arg.isLambda() ? "lambda" : arg.bareName != null ? arg.bareName : arg.typed.type.toString());
			}

			StringBuilder options = new StringBuilder();

			for (MethodInfo method : candidates) {
				options.append("\n    ").append(signature(method, receiverBindings));
			}

			boolean bare = pre.stream().anyMatch(arg -> arg.bareName != null);
			error(pos, "No " + description + "(...) takes (" + found + ")" + (bare ? " (a name there is unknown)" : "") + "; there is:" + options);
			return bad();
		}

		Choice choice = mostSpecific(applicable, pos, description);
		MethodInfo method = choice.method;
		Map<String, Type> bindings = new HashMap<>(choice.bindings);

		if (expected != null && method.returns == MethodInfo.Returns.DECLARED) {
			unifyInto(method.returnType, expected, bindings, method.typeParams, true);
		}

		int fixed = method.varargs ? method.params.size() - 1 : method.params.size();
		Ev[] argEvs = new Ev[fixed + (method.varargs ? 1 : 0)];

		for (int i = 0; i < fixed; i++) {
			argEvs[i] = finishArg(pre.get(i), method.params.get(i).subst(bindings));
		}

		if (method.varargs) {
			Type element = method.params.getLast().subst(bindings);
			Ev[] rest = new Ev[pre.size() - fixed];

			for (int i = 0; i < rest.length; i++) {
				rest[i] = finishArg(pre.get(fixed + i), element);
			}

			argEvs[fixed] = frame -> {
				Object[] values = new Object[rest.length];

				for (int i = 0; i < rest.length; i++) {
					values[i] = rest[i].ev(frame);
				}

				return values;
			};
		}

		Type returnType = switch (method.returns) {
			case SELF -> receiver != null ? receiver : method.returnType.subst(bindings);
			case ARG0 -> pre.isEmpty() || pre.getFirst().typed == null ? Type.ERROR : pre.getFirst().typed.type;
			case DECLARED -> method.returnType.subst(bindings);
		};

		if (returnType instanceof Type.Prim prim && prim.boxed && method.returnType instanceof Type.Var) {
			returnType = prim;
		}

		return new T(returnType, callCode(method, target, argEvs, pos));
	}

	private static String signature(MethodInfo method, Map<String, Type> bindings) {
		StringBuilder builder = new StringBuilder(method.name).append('(');

		for (int i = 0; i < method.params.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}

			Type param = method.params.get(i);
			Type shown = param instanceof Type.Var var && !bindings.containsKey(var.name) ? param : param.subst(bindings);
			builder.append(shown.isLoose() ? param : shown);

			if (method.varargs && i == method.params.size() - 1) {
				builder.append("...");
			}
		}

		return builder.append(')').toString();
	}

	private Map<String, Type> applicable(MethodInfo method, List<Pre> args, Map<String, Type> receiverBindings) {
		int params = method.params.size();

		if (method.varargs ? args.size() < params - 1 : args.size() != params) {
			return null;
		}

		Map<String, Type> bindings = new HashMap<>(receiverBindings);

		for (int i = 0; i < args.size(); i++) {
			Type param = method.varargs && i >= params - 1 ? method.params.getLast() : method.params.get(i);
			Pre arg = args.get(i);

			if (arg.isLambda()) {
				Type target = param.subst(bindings);
				ClassInfo functional = target.classInfo();

				if (functional == null || functional.sam == null || functional.sam.params.size() != arg.lambdaArity) {
					return null;
				}
			} else if (arg.bareName != null) {
				Type target = param.subst(bindings);
				ClassInfo enumType = target.classInfo();

				if (enumType == null || !enumType.isEnum() || enumType.constantIndex(arg.bareName) < 0) {
					return null;
				}
			} else {
				unifyInto(param, arg.typed.type, bindings, method.typeParams, false);
			}
		}

		for (int i = 0; i < args.size(); i++) {
			Pre arg = args.get(i);

			if (arg.typed == null) {
				continue;
			}

			Type param = method.varargs && i >= params - 1 ? method.params.getLast() : method.params.get(i);

			if (!assignable(arg.typed.type, param.subst(bindings))) {
				return null;
			}
		}

		return bindings;
	}

	private void unifyInto(Type pattern, Type actual, Map<String, Type> bindings, List<String> methodVars, boolean onlyUnbound) {
		if (pattern instanceof Type.Var var) {
			if (!methodVars.contains(var.name) && bindings.containsKey(var.name)) {
				return;
			}

			if (actual == Type.NULL || actual.isLoose() || actual == Type.VOID) {
				return;
			}

			Type boxed = actual instanceof Type.Prim prim ? prim.box() : actual;
			Type existing = bindings.get(var.name);

			if (existing == null || existing.isLoose()) {
				bindings.put(var.name, boxed);
			} else if (!onlyUnbound && !assignable(boxed, existing)) {
				if (assignable(existing, boxed)) {
					bindings.put(var.name, boxed);
				} else if (existing.isNumeric() && boxed.isNumeric()) {
					bindings.put(var.name, prim(promote(existing, boxed)).box());
				} else {
					bindings.put(var.name, objectType);
				}
			}

			return;
		}

		if (pattern instanceof Type.Ref ref && actual instanceof Type.Ref actualRef && ref.cls == actualRef.cls) {
			for (int i = 0; i < ref.args.size(); i++) {
				unifyInto(ref.arg(i), actualRef.arg(i), bindings, methodVars, onlyUnbound);
			}
		}
	}

	private Choice mostSpecific(List<Choice> choices, Pos pos, String description) {
		if (choices.size() == 1) {
			return choices.getFirst();
		}

		List<Choice> fixed = choices.stream().filter(choice -> !choice.method.varargs).toList();
		List<Choice> pool = fixed.isEmpty() ? choices : fixed;

		for (Choice candidate : pool) {
			boolean best = true;

			for (Choice other : pool) {
				if (other != candidate && !moreSpecific(candidate.method, other.method)) {
					best = false;
					break;
				}
			}

			if (best) {
				return candidate;
			}
		}

		StringBuilder options = new StringBuilder();

		for (Choice choice : pool) {
			options.append("\n    ").append(signature(choice.method, choice.bindings));
		}

		error(pos, "The call to " + description + " fits more than one method; make the types clearer:" + options);
		return pool.getFirst();
	}

	private boolean moreSpecific(MethodInfo a, MethodInfo b) {
		if (a.params.size() != b.params.size()) {
			return a.params.size() < b.params.size();
		}

		for (int i = 0; i < a.params.size(); i++) {
			Type x = a.params.get(i);
			Type y = b.params.get(i);

			if (x instanceof Type.Var || y instanceof Type.Var) {
				continue;
			}

			ClassInfo xf = x.classInfo();
			ClassInfo yf = y.classInfo();

			if (xf != null && yf != null && xf.sam != null && yf.sam != null) {
				continue;
			}

			if (!assignable(x, y)) {
				return false;
			}
		}

		return true;
	}

	private Ev finishArg(Pre arg, Type param) {
		if (arg.isLambda()) {
			return lambda((Ast.Lambda) arg.expr, param).ev;
		}

		if (arg.bareName != null) {
			T constant = enumConstant(arg.bareName, param);
			return constant != null ? constant.ev : bad().ev;
		}

		return coerce(arg.typed, param, arg.expr.pos(), arg.expr).ev;
	}

	private Ev callCode(MethodInfo method, T target, Ev[] args, Pos pos) {
		int line = pos.line();
		String name = method.owner.kind == ClassInfo.Kind.NATIVE && method.owner == lib.globals ? method.name : method.owner.name + "." + method.name;
		boolean isStatic = method.isStatic;
		Ev targetEv = target == null ? null : target.ev;
		boolean implicitSelf = target == null && !isStatic;

		if (method.invoker != null) {
			Invoker invoker = method.invoker;
			return frame -> {
				Object self = null;

				if (!isStatic) {
					self = implicitSelf ? frame.self : targetEv.ev(frame);

					if (self == null) {
						throw new ScriptException("Can't call " + method.name + "() because the value is null", line);
					}
				}

				Object[] values = new Object[args.length];

				for (int i = 0; i < args.length; i++) {
					values[i] = args[i].ev(frame);
				}

				try {
					return invoker.call(self, values);
				} catch (ScriptException e) {
					if (e.pendingLine <= 0) {
						e.pendingLine = line;
					}

					throw e;
				} catch (StackOverflowError e) {
					throw new ScriptException("Stack overflow: methods call each other too deep", line);
				} catch (Exception e) {
					throw new ScriptException(describe(e, name), line);
				}
			};
		}

		MethodImpl impl = method.impl;
		boolean gui = method.isGui || method.isHud;
		SfyLibrary library = lib;
		String file = this.file;
		return frame -> {
			Object self = null;

			if (!isStatic) {
				self = implicitSelf ? frame.self : targetEv.ev(frame);

				if (self == null) {
					throw new ScriptException("Can't call " + method.name + "() because the value is null", line);
				}
			}

			Object[] values = new Object[args.length];

			for (int i = 0; i < args.length; i++) {
				values[i] = args[i].ev(frame);
			}

			if (gui && library.guiSwitch != null) {
				library.guiSwitch.accept((SfyInstance) self, method.name);
			}

			try {
				return impl.invoke(self, values);
			} catch (ScriptException e) {
				e.leave(file, name, line);
				throw e;
			} catch (StackOverflowError e) {
				throw new ScriptException("Stack overflow: " + name + " calls itself (or others) too deep", line);
			}
		};
	}

	static String describe(Exception e, String method) {
		String message = e.getMessage();

		return switch (e) {
			case IndexOutOfBoundsException ignored -> message != null ? message : "Index out of range";
			case NullPointerException ignored -> "Something passed to " + method + "() is null";
			case ArithmeticException ignored -> message;
			case ConcurrentModificationException ignored -> "A list was changed while it was being gone through";
			case ClassCastException ignored -> "Wrong type in " + method + "(): " + message;
			case IllegalArgumentException ignored -> message != null ? message : method + "(): bad value";
			case IllegalStateException ignored -> message != null ? message : method + "() can't be used right now";
			case UnsupportedOperationException ignored -> method + "() can't change this";
			default -> e.getClass().getSimpleName() + (message != null ? ": " + message : "") + " in " + method + "()";
		};
	}

	private T newExpression(Ast.New created, Type expected) {
		String name = created.type().name();

		switch (name) {
			case "ArrayList", "LinkedList" -> {
				Type type = collectionType(created.type(), listCls, expected);

				if (created.args().size() > 1) {
					error(created.pos(), "new ArrayList<>() takes nothing or a list to copy");
					return bad();
				}

				if (created.args().size() == 1) {
					Ev copy = coerce(expr(created.args().getFirst(), type), type, created.args().getFirst().pos()).ev;
					int line = created.pos().line();
					return new T(type, frame -> {
						Object source = copy.ev(frame);

						if (source == null) {
							throw new ScriptException("Can't copy a null list", line);
						}

						return new ArrayList<>((List<?>) source);
					});
				}

				return new T(type, frame -> new ArrayList<>());
			}
			case "HashMap", "LinkedHashMap" -> {
				Type type = collectionType(created.type(), mapCls, expected);

				if (created.args().size() > 1) {
					error(created.pos(), "new HashMap<>() takes nothing or a map to copy");
					return bad();
				}

				if (created.args().size() == 1) {
					Ev copy = coerce(expr(created.args().getFirst(), type), type, created.args().getFirst().pos()).ev;
					int line = created.pos().line();
					return new T(type, frame -> {
						Object source = copy.ev(frame);

						if (source == null) {
							throw new ScriptException("Can't copy a null map", line);
						}

						return new LinkedHashMap<>((Map<?, ?>) source);
					});
				}

				return new T(type, frame -> Stdlib.newMap());
			}
			case "List", "Map" -> {
				error(created.pos(), "Write new " + (name.equals("List") ? "ArrayList" : "HashMap") + "<>()");
				return bad();
			}
			case "Exception", "RuntimeException" -> {
				if (created.args().size() > 1) {
					error(created.pos(), "new Exception(\"message\")");
					return bad();
				}

				if (created.args().isEmpty()) {
					return new T(exceptionType, frame -> new ExceptionValue(null));
				}

				Ev message = coerce(expr(created.args().getFirst(), stringType), stringType, created.args().getFirst().pos()).ev;
				return new T(exceptionType, frame -> new ExceptionValue((String) message.ev(frame)));
			}
			default -> {
			}
		}

		Type type = resolveType(created.type());

		if (type == Type.ERROR) {
			preType(created.args());
			return bad();
		}

		ClassInfo cls = type.classInfo();

		if (cls == null || cls.kind != ClassInfo.Kind.RECORD) {
			String hint = cls != null && cls.kind == ClassInfo.Kind.NATIVE && !lib.globals.methods.getOrDefault(lowerFirst(cls.name), List.of()).isEmpty()
					? " (use " + lowerFirst(cls.name) + "(...))" : "";
			error(created.pos(), "Can't create a " + type + " with new" + (cls == main ? " (the GUI class is created when it opens)" : "") + hint);
			preType(created.args());
			return bad();
		}

		MethodInfo constructor = cls.constructors.getFirst();
		List<Pre> pre = preType(created.args());

		if (pre.size() != constructor.params.size()) {
			error(created.pos(), cls.name + " needs " + constructor.params.size() + " values: " + cls.name + "(" + String.join(", ", constructor.params.stream().map(Object::toString).toList()) + ")");
			return bad();
		}

		Ev[] args = new Ev[pre.size()];

		for (int i = 0; i < args.length; i++) {
			args[i] = finishArg(pre.get(i), constructor.params.get(i));
		}

		UserType runtime = cls.runtime;
		int line = created.pos().line();
		String file = this.file;
		return new T(type, frame -> {
			Object[] values = new Object[args.length];

			for (int i = 0; i < args.length; i++) {
				values[i] = args[i].ev(frame);
			}

			RecordValue record = new RecordValue(runtime, values.clone());
			MethodImpl impl = constructor.impl;

			if (impl != null) {
				try {
					impl.invoke(record, values);
				} catch (ScriptException e) {
					e.leave(file, runtime.name + ".<init>", line);
					throw e;
				}
			}

			return record;
		});
	}

	private static String lowerFirst(String name) {
		return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

	private Type collectionType(Ast.TypeRef ref, ClassInfo cls, Type expected) {
		if (ref.args().isEmpty()) {
			if (expected instanceof Type.Ref target && target.cls == cls) {
				return expected;
			}

			return new Type.Ref(cls, cls.typeParams.stream().map(param -> (Type) Type.INFER).toList());
		}

		return resolveType(new Ast.TypeRef(cls.name, ref.args(), false, ref.pos()));
	}


	private T unary(Ast.Unary unary) {
		T operand = expr(unary.operand(), null);
		Type type = operand.type;
		Pos pos = unary.pos();

		if (type == Type.ERROR) {
			return bad();
		}

		switch (unary.op()) {
			case "!" -> {
				Ev ev = coerce(operand, Type.BOOLEAN, pos).ev;
				return new T(Type.BOOLEAN, frame -> !(Boolean) ev.ev(frame));
			}
			case "-", "+" -> {
				boolean negate = unary.op().equals("-");

				if (isRef(type, lengthCls)) {
					Ev ev = operand.ev;
					return new T(lengthType, negate ? frame -> ((LengthValue) ev.ev(frame)).times(-1) : ev);
				}

				if (!type.isNumeric()) {
					error(pos, "'" + unary.op() + "' needs a number, this is " + type);
					return bad();
				}

				PrimKind kind = promote(type, Type.INT);
				Ev ev = toKind(operand, kind, pos).ev;

				if (!negate) {
					return new T(prim(kind), ev);
				}

				return new T(prim(kind), switch (kind) {
					case INT -> frame -> -(Integer) ev.ev(frame);
					case LONG -> frame -> -(Long) ev.ev(frame);
					default -> frame -> -(Double) ev.ev(frame);
				});
			}
			case "~" -> {
				if (!(type.is(PrimKind.INT) || type.is(PrimKind.LONG) || type.is(PrimKind.CHAR))) {
					error(pos, "'~' needs an int or long");
					return bad();
				}

				PrimKind kind = promote(type, Type.INT);
				Ev ev = toKind(operand, kind, pos).ev;
				return new T(prim(kind), kind == PrimKind.LONG ? frame -> ~(Long) ev.ev(frame) : frame -> ~(Integer) ev.ev(frame));
			}
			default -> {
				error(pos, "Unknown operator " + unary.op());
				return bad();
			}
		}
	}

	private T binary(Ast.Binary binary) {
		String op = binary.op();
		Pos pos = binary.pos();

		if (op.equals("&&") || op.equals("||")) {
			Ev left = condition(binary.left());
			Ev right = condition(binary.right());
			return new T(Type.BOOLEAN, op.equals("&&")
					? frame -> (Boolean) left.ev(frame) && (Boolean) right.ev(frame)
					: frame -> (Boolean) left.ev(frame) || (Boolean) right.ev(frame));
		}

		if (op.equals("==") || op.equals("!=")) {
			return equality(binary, op.equals("!="));
		}

		T left = expr(binary.left(), null);
		T right = expr(binary.right(), null);

		if (left.type == Type.ERROR || right.type == Type.ERROR) {
			return bad();
		}

		return arithmetic(op, left, right, pos);
	}

	private T arithmetic(String op, T left, T right, Pos pos) {
		Type a = left.type;
		Type b = right.type;
		int line = pos.line();

		if (op.equals("+") && (isRef(a, stringCls) || isRef(b, stringCls))) {
			Ev l = left.ev;
			Ev r = right.ev;
			return new T(stringType, frame -> Values.str(l.ev(frame)).concat(Values.str(r.ev(frame))));
		}

		if (isRef(a, lengthCls) || isRef(b, lengthCls)) {
			return lengthArithmetic(op, left, right, pos);
		}

		if (isRef(a, durationCls) && isRef(b, durationCls) && (op.equals("+") || op.equals("-"))) {
			Ev l = left.ev;
			Ev r = right.ev;
			boolean plus = op.equals("+");
			return new T(durationType, frame -> {
				long x = ((DurationValue) l.ev(frame)).millis();
				long y = ((DurationValue) r.ev(frame)).millis();
				return new DurationValue(plus ? x + y : x - y);
			});
		}

		switch (op) {
			case "<", ">", "<=", ">=" -> {
				if (!a.isNumeric() || !b.isNumeric()) {
					error(pos, "'" + op + "' compares numbers, not " + a + " and " + b + (isRef(a, stringCls) ? " (use compareTo)" : ""));
					return bad();
				}

				PrimKind kind = promote(a, b);
				Ev l = toKind(left, kind, pos).ev;
				Ev r = toKind(right, kind, pos).ev;
				return new T(Type.BOOLEAN, comparison(op, kind, l, r));
			}
			case "&", "|", "^" -> {
				if (a.isBoolean() && b.isBoolean()) {
					Ev l = coerce(left, Type.BOOLEAN, pos).ev;
					Ev r = coerce(right, Type.BOOLEAN, pos).ev;
					return new T(Type.BOOLEAN, switch (op) {
						case "&" -> frame -> (Boolean) l.ev(frame) & (Boolean) r.ev(frame);
						case "|" -> frame -> (Boolean) l.ev(frame) | (Boolean) r.ev(frame);
						default -> frame -> (Boolean) l.ev(frame) ^ (Boolean) r.ev(frame);
					});
				}

				if (!integral(a) || !integral(b)) {
					error(pos, "'" + op + "' needs two booleans or two whole numbers");
					return bad();
				}

				PrimKind kind = promote(a, b);
				Ev l = toKind(left, kind, pos).ev;
				Ev r = toKind(right, kind, pos).ev;

				if (kind == PrimKind.LONG) {
					return new T(Type.LONG, switch (op) {
						case "&" -> frame -> (Long) l.ev(frame) & (Long) r.ev(frame);
						case "|" -> frame -> (Long) l.ev(frame) | (Long) r.ev(frame);
						default -> frame -> (Long) l.ev(frame) ^ (Long) r.ev(frame);
					});
				}

				return new T(Type.INT, switch (op) {
					case "&" -> frame -> (Integer) l.ev(frame) & (Integer) r.ev(frame);
					case "|" -> frame -> (Integer) l.ev(frame) | (Integer) r.ev(frame);
					default -> frame -> (Integer) l.ev(frame) ^ (Integer) r.ev(frame);
				});
			}
			case "<<", ">>", ">>>" -> {
				if (!integral(a) || !integral(b)) {
					error(pos, "'" + op + "' needs whole numbers");
					return bad();
				}

				PrimKind kind = promote(a, Type.INT);
				Ev l = toKind(left, kind, pos).ev;
				Ev r = toKind(right, promote(b, Type.INT), pos).ev;
				boolean longShift = promote(b, Type.INT) == PrimKind.LONG;

				if (kind == PrimKind.LONG) {
					return new T(Type.LONG, frame -> {
						long x = (Long) l.ev(frame);
						int y = longShift ? (int) (long) (Long) r.ev(frame) : (Integer) r.ev(frame);
						return op.equals("<<") ? x << y : op.equals(">>") ? x >> y : x >>> y;
					});
				}

				return new T(Type.INT, frame -> {
					int x = (Integer) l.ev(frame);
					int y = longShift ? (int) (long) (Long) r.ev(frame) : (Integer) r.ev(frame);
					return op.equals("<<") ? x << y : op.equals(">>") ? x >> y : x >>> y;
				});
			}
			case "+", "-", "*", "/", "%" -> {
				if (!a.isNumeric() || !b.isNumeric()) {
					error(pos, "'" + op + "' needs numbers, not " + a + " and " + b);
					return bad();
				}

				PrimKind kind = promote(a, b);
				Ev l = toKind(left, kind, pos).ev;
				Ev r = toKind(right, kind, pos).ev;
				return new T(prim(kind), math(op, kind, l, r, line));
			}
			default -> {
				error(pos, "Unknown operator " + op);
				return bad();
			}
		}
	}

	private static boolean integral(Type type) {
		return type.is(PrimKind.INT) || type.is(PrimKind.LONG) || type.is(PrimKind.CHAR);
	}

	private static Ev math(String op, PrimKind kind, Ev l, Ev r, int line) {
		return switch (kind) {
			case INT -> switch (op) {
				case "+" -> frame -> (Integer) l.ev(frame) + (Integer) r.ev(frame);
				case "-" -> frame -> (Integer) l.ev(frame) - (Integer) r.ev(frame);
				case "*" -> frame -> (Integer) l.ev(frame) * (Integer) r.ev(frame);
				case "/" -> frame -> {
					int x = (Integer) l.ev(frame);
					int y = (Integer) r.ev(frame);

					if (y == 0) {
						throw new ScriptException("Division by zero", line);
					}

					return x / y;
				};
				default -> frame -> {
					int x = (Integer) l.ev(frame);
					int y = (Integer) r.ev(frame);

					if (y == 0) {
						throw new ScriptException("Division by zero (in %)", line);
					}

					return x % y;
				};
			};
			case LONG -> switch (op) {
				case "+" -> frame -> (Long) l.ev(frame) + (Long) r.ev(frame);
				case "-" -> frame -> (Long) l.ev(frame) - (Long) r.ev(frame);
				case "*" -> frame -> (Long) l.ev(frame) * (Long) r.ev(frame);
				case "/" -> frame -> {
					long x = (Long) l.ev(frame);
					long y = (Long) r.ev(frame);

					if (y == 0) {
						throw new ScriptException("Division by zero", line);
					}

					return x / y;
				};
				default -> frame -> {
					long x = (Long) l.ev(frame);
					long y = (Long) r.ev(frame);

					if (y == 0) {
						throw new ScriptException("Division by zero (in %)", line);
					}

					return x % y;
				};
			};
			default -> switch (op) {
				case "+" -> frame -> (Double) l.ev(frame) + (Double) r.ev(frame);
				case "-" -> frame -> (Double) l.ev(frame) - (Double) r.ev(frame);
				case "*" -> frame -> (Double) l.ev(frame) * (Double) r.ev(frame);
				case "/" -> frame -> (Double) l.ev(frame) / (Double) r.ev(frame);
				default -> frame -> (Double) l.ev(frame) % (Double) r.ev(frame);
			};
		};
	}

	private static Ev comparison(String op, PrimKind kind, Ev l, Ev r) {
		return switch (kind) {
			case INT -> switch (op) {
				case "<" -> frame -> (Integer) l.ev(frame) < (Integer) r.ev(frame);
				case ">" -> frame -> (Integer) l.ev(frame) > (Integer) r.ev(frame);
				case "<=" -> frame -> (Integer) l.ev(frame) <= (Integer) r.ev(frame);
				default -> frame -> (Integer) l.ev(frame) >= (Integer) r.ev(frame);
			};
			case LONG -> switch (op) {
				case "<" -> frame -> (Long) l.ev(frame) < (Long) r.ev(frame);
				case ">" -> frame -> (Long) l.ev(frame) > (Long) r.ev(frame);
				case "<=" -> frame -> (Long) l.ev(frame) <= (Long) r.ev(frame);
				default -> frame -> (Long) l.ev(frame) >= (Long) r.ev(frame);
			};
			default -> switch (op) {
				case "<" -> frame -> (Double) l.ev(frame) < (Double) r.ev(frame);
				case ">" -> frame -> (Double) l.ev(frame) > (Double) r.ev(frame);
				case "<=" -> frame -> (Double) l.ev(frame) <= (Double) r.ev(frame);
				default -> frame -> (Double) l.ev(frame) >= (Double) r.ev(frame);
			};
		};
	}

	private T lengthArithmetic(String op, T left, T right, Pos pos) {
		Type a = left.type;
		Type b = right.type;
		boolean aLength = isRef(a, lengthCls);
		boolean bLength = isRef(b, lengthCls);

		switch (op) {
			case "+", "-" -> {
				if ((aLength || a.isNumeric()) && (bLength || b.isNumeric())) {
					Ev l = coerce(left, lengthType, pos).ev;
					Ev r = coerce(right, lengthType, pos).ev;
					boolean plus = op.equals("+");
					return new T(lengthType, frame -> {
						LengthValue x = (LengthValue) l.ev(frame);
						LengthValue y = (LengthValue) r.ev(frame);
						return plus ? x.plus(y) : x.minus(y);
					});
				}
			}
			case "*" -> {
				if (aLength != bLength && (aLength ? b.isNumeric() : a.isNumeric())) {
					Ev length = aLength ? left.ev : right.ev;
					Ev factor = coerce(aLength ? right : left, Type.DOUBLE, pos).ev;
					return new T(lengthType, frame -> ((LengthValue) length.ev(frame)).times((Double) factor.ev(frame)));
				}
			}
			case "/" -> {
				if (aLength && b.isNumeric()) {
					Ev length = left.ev;
					Ev divisor = coerce(right, Type.DOUBLE, pos).ev;
					int line = pos.line();
					return new T(lengthType, frame -> {
						double d = (Double) divisor.ev(frame);

						if (d == 0) {
							throw new ScriptException("Division by zero", line);
						}

						return ((LengthValue) length.ev(frame)).times(1 / d);
					});
				}
			}
			default -> {
			}
		}

		error(pos, "Lengths can be added, subtracted, multiplied and divided by numbers; '" + op + "' doesn't work with " + a + " and " + b);
		return bad();
	}

	private T equality(Ast.Binary binary, boolean negate) {
		T left;
		T right;

		if (binary.right() instanceof Ast.Name name && isBare(name)) {
			left = expr(binary.left(), null);
			right = expr(binary.right(), left.type);
		} else if (binary.left() instanceof Ast.Name name && isBare(name)) {
			right = expr(binary.right(), null);
			left = expr(binary.left(), right.type);
		} else {
			left = expr(binary.left(), null);
			right = expr(binary.right(), null);
		}

		if (left.type == Type.ERROR || right.type == Type.ERROR) {
			return bad();
		}

		Type a = left.type;
		Type b = right.type;
		Pos pos = binary.pos();

		if (a.isNumeric() && b.isNumeric()) {
			boolean nullable = a.isReference() || b.isReference();

			if (!nullable) {
				PrimKind kind = promote(a, b);
				Ev l = toKind(left, kind, pos).ev;
				Ev r = toKind(right, kind, pos).ev;
				return new T(Type.BOOLEAN, frame -> Objects.equals(l.ev(frame), r.ev(frame)) != negate);
			}

			PrimKind kind = promote(a, b);
			Ev l = left.ev;
			Ev r = right.ev;
			return new T(Type.BOOLEAN, frame -> {
				Object x = l.ev(frame);
				Object y = r.ev(frame);

				if (x == null || y == null) {
					return (x == y) != negate;
				}

				return numbersEqual(x, y, kind) != negate;
			});
		}

		if ((a.isBoolean() || a == Type.NULL) && b.isBoolean() || a.isBoolean() && b == Type.NULL
				|| a.is(PrimKind.CHAR) && b.is(PrimKind.CHAR)) {
			Ev l = left.ev;
			Ev r = right.ev;
			return new T(Type.BOOLEAN, frame -> Objects.equals(l.ev(frame), r.ev(frame)) != negate);
		}

		if (a instanceof Type.Prim && !a.isReference() && b == Type.NULL || b instanceof Type.Prim && !b.isReference() && a == Type.NULL) {
			error(pos, "A " + (a == Type.NULL ? b : a) + " is never null");
			return bad();
		}

		if (!(assignable(a, b) || assignable(b, a))) {
			error(pos, "Can't compare " + a + " with " + b);
			return bad();
		}

		Ev l = left.ev;
		Ev r = right.ev;
		return new T(Type.BOOLEAN, frame -> sameValue(l.ev(frame), r.ev(frame)) != negate);
	}

	private boolean isBare(Ast.Name name) {
		return findLocal(name.name(), name.pos()) == null && findField(name.name()) == null && !lib.globals.fields.containsKey(name.name()) && typeByName(name.name()) == null;
	}

	private static boolean numbersEqual(Object x, Object y, PrimKind kind) {
		Number a = x instanceof Character c ? (Number) (int) c : (Number) x;
		Number b = y instanceof Character c ? (Number) (int) c : (Number) y;
		return kind == PrimKind.DOUBLE ? a.doubleValue() == b.doubleValue() : a.longValue() == b.longValue();
	}

	private static boolean sameValue(Object a, Object b) {
		if (a == b) {
			return true;
		}

		if (a == null || b == null) {
			return false;
		}

		return (a instanceof String || a instanceof Number || a instanceof Character || a instanceof Boolean
				|| a instanceof ColorValue || a instanceof LengthValue || a instanceof DurationValue) && a.equals(b);
	}

	private T conditional(Ast.Conditional conditional, Type expected) {
		Ev condition = condition(conditional.condition());
		T then = expr(conditional.then(), expected);
		T otherwise = expr(conditional.otherwise(), expected);
		Type type = expected != null && !expected.isLoose() ? expected : unify(then.type, otherwise.type);

		if (then.type == Type.ERROR || otherwise.type == Type.ERROR) {
			return bad();
		}

		Ev a = coerce(then, type, conditional.then().pos(), conditional.then()).ev;
		Ev b = coerce(otherwise, type, conditional.otherwise().pos(), conditional.otherwise()).ev;
		return new T(type, frame -> (Boolean) condition.ev(frame) ? a.ev(frame) : b.ev(frame));
	}

	private T cast(Ast.Cast cast) {
		Type target = resolveType(cast.type());
		T operand = expr(cast.operand(), target);
		Type from = operand.type;
		Pos pos = cast.pos();

		if (from == Type.ERROR || target == Type.ERROR) {
			return bad();
		}

		if (from.isNumeric() && target.isNumeric()) {
			Type.Prim a = (Type.Prim) from;
			Type.Prim b = (Type.Prim) target;
			Ev ev = convert(operand.ev, a, a.unboxed(), pos);
			return new T(target, a.kind == b.kind ? ev : numeric(ev, a.kind, b.kind, pos));
		}

		if (assignable(from, target)) {
			return new T(target, convert(operand.ev, from, target, pos));
		}

		if (target instanceof Type.Prim prim && (isRef(from, objectCls) || from instanceof Type.Prim)) {
			Ev ev = operand.ev;
			int line = pos.line();
			Class<?> expectedClass = boxClass(prim.kind);
			return new T(target, frame -> {
				Object value = ev.ev(frame);

				if (!expectedClass.isInstance(value)) {
					throw new ScriptException("Can't cast " + Values.typeName(value) + " to " + prim, line);
				}

				return value;
			});
		}

		if (assignable(target, from) || isRef(from, objectCls)) {
			Ev ev = operand.ev;
			int line = pos.line();
			Type checked = target;
			return new T(target, frame -> {
				Object value = ev.ev(frame);

				if (value != null && !isInstance(checked, value)) {
					throw new ScriptException("Can't cast " + Values.typeName(value) + " to " + checked, line);
				}

				return value;
			});
		}

		error(pos, "Can't cast " + from + " to " + target);
		return bad();
	}

	private static Class<?> boxClass(PrimKind kind) {
		return switch (kind) {
			case INT -> Integer.class;
			case LONG -> Long.class;
			case DOUBLE -> Double.class;
			case BOOLEAN -> Boolean.class;
			case CHAR -> Character.class;
		};
	}

	private static boolean isInstance(Type type, Object value) {
		if (value == null) {
			return false;
		}

		if (type instanceof Type.Prim prim) {
			return boxClass(prim.kind).isInstance(value);
		}

		if (!(type instanceof Type.Ref ref)) {
			return true;
		}

		ClassInfo cls = ref.cls;
		return switch (cls.kind) {
			case RECORD -> value instanceof RecordValue record && record.type == cls.runtime;
			case ENUM -> value instanceof EnumValue constant && constant.type == cls.runtime;
			case MAIN -> value instanceof SfyInstance;
			case FUNCTIONAL -> value instanceof Fn;
			default -> cls.javaClass == null || cls.javaClass == Object.class || cls.javaClass.isInstance(value);
		};
	}

	private T instanceOf(Ast.InstanceOf test) {
		T operand = expr(test.operand(), null);
		Type type = resolveType(test.type());

		if (operand.type == Type.ERROR || type == Type.ERROR) {
			return bad();
		}

		if (!operand.type.isReference() && !isRef(operand.type, objectCls)) {
			error(test.pos(), "instanceof needs an object, this is " + operand.type);
			return bad();
		}

		Ev ev = operand.ev;

		if (test.binding() == null) {
			return new T(Type.BOOLEAN, frame -> isInstance(type, ev.ev(frame)));
		}

		Local local = declareLocal(test.binding(), type, false, true, test.pos());
		local.pattern = true;
		int slot = local.slot;
		return new T(Type.BOOLEAN, frame -> {
			Object value = ev.ev(frame);

			if (isInstance(type, value)) {
				frame.locals[slot] = value;
				return true;
			}

			return false;
		});
	}


	private interface Place {
		Object target(Frame frame);

		Object get(Frame frame, Object target);

		void set(Frame frame, Object target, Object value);
	}

	private record PlaceInfo(Type type, Place place) {
	}

	private PlaceInfo place(Ast.Expr expression) {
		if (expression instanceof Ast.Name name) {
			Local local = findLocal(name.name(), name.pos());

			if (local != null) {
				if (local.capture) {
					error(name.pos(), "A lambda can't change " + name.name() + " (a variable from outside it)");
				} else if (local.isFinal) {
					error(name.pos(), name.name() + " is final");
				}

				local.writes++;
				int slot = local.slot;
				return new PlaceInfo(local.type, new Place() {
					public Object target(Frame frame) {
						return null;
					}

					public Object get(Frame frame, Object target) {
						return frame.locals[slot];
					}

					public void set(Frame frame, Object target, Object value) {
						frame.locals[slot] = value;
					}
				});
			}

			FieldInfo field = findField(name.name());

			if (field != null) {
				return fieldPlace(field, null, name.pos());
			}

			if (lib.globals.fields.containsKey(name.name())) {
				error(name.pos(), name.name() + " can't be changed");
				return null;
			}

			error(name.pos(), "Unknown variable " + name.name());
			return null;
		}

		if (expression instanceof Ast.Select select) {
			ClassInfo type = typeOf(select.target());

			if (type != null) {
				FieldInfo field = type.fields.get(select.name());

				if (field == null || !field.isStatic || field.nativeValue != null) {
					error(select.pos(), type.name + "." + select.name() + " can't be changed");
					return null;
				}

				return fieldPlace(field, null, select.pos());
			}

			T target = expr(select.target(), null);
			ClassInfo cls = target.type.classInfo();
			FieldInfo field = cls == null ? null : cls.fields.get(select.name());

			if (field == null) {
				if (target.type != Type.ERROR) {
					error(select.pos(), target.type + " has no field " + select.name());
				}

				return null;
			}

			return fieldPlace(field, target.ev, select.pos());
		}

		error(expression.pos(), "Only variables and fields can be assigned");
		return null;
	}

	private PlaceInfo fieldPlace(FieldInfo field, Ev target, Pos pos) {
		if (field.owner.kind == ClassInfo.Kind.RECORD && !field.isStatic && !ctx.inConstructor) {
			error(pos, "Records can't be changed; make a new one");
			return null;
		}

		if (field.isFinal && !(ctx.inConstructor && field.owner == ctx.owner && !field.isStatic)) {
			error(pos, field.name + " is final");
			return null;
		}

		if (field.nativeValue != null) {
			error(pos, field.name + " can't be changed");
			return null;
		}

		int slot = field.slot;

		if (field.isStatic) {
			SfyClass cls = output;
			return new PlaceInfo(field.type, new Place() {
				public Object target(Frame frame) {
					return null;
				}

				public Object get(Frame frame, Object t) {
					return cls.statics[slot];
				}

				public void set(Frame frame, Object t, Object value) {
					cls.statics[slot] = value;
				}
			});
		}

		if (target == null) {
			if (ctx.isStatic || field.owner != ctx.owner) {
				error(pos, "Field " + field.name + " belongs to an instance and can't be changed from here");
				return null;
			}

			target = frame -> frame.self;
		}

		Ev object = target;
		int line = pos.line();
		String name = field.name;
		ClassInfo.Kind kind = field.owner.kind;
		return new PlaceInfo(field.type, new Place() {
			public Object target(Frame frame) {
				return nonNullTarget(object.ev(frame), name, line);
			}

			public Object get(Frame frame, Object t) {
				return switch (kind) {
					case MAIN -> ((SfyInstance) t).fields[slot];
					case RECORD -> ((RecordValue) t).values[slot];
					default -> ((EnumValue) t).fields[slot];
				};
			}

			public void set(Frame frame, Object t, Object value) {
				switch (kind) {
					case MAIN -> ((SfyInstance) t).fields[slot] = value;
					case RECORD -> ((RecordValue) t).values[slot] = value;
					default -> ((EnumValue) t).fields[slot] = value;
				}
			}
		});
	}

	private T assign(Ast.Assign assign) {
		PlaceInfo info = place(assign.target());

		if (info == null) {
			expr(assign.value(), null);
			return bad();
		}

		Place place = info.place;
		Type type = info.type;
		Pos pos = assign.pos();

		if (assign.op().equals("=")) {
			Ev value = coerce(expr(assign.value(), type), type, assign.value().pos(), assign.value()).ev;
			return new T(type, frame -> {
				Object target = place.target(frame);
				Object result = value.ev(frame);
				place.set(frame, target, result);
				return result;
			});
		}

		String op = assign.op().substring(0, assign.op().length() - 1);
		Object[] holder = new Object[1];
		T current = new T(type, frame -> holder[0]);
		T right = expr(assign.value(), null);

		if (right.type == Type.ERROR || type == Type.ERROR) {
			return bad();
		}

		T combined = arithmetic(op, current, right, pos);

		if (combined.type == Type.ERROR) {
			return bad();
		}

		Ev result = castBack(combined, type, pos);
		return new T(type, frame -> {
			Object target = place.target(frame);
			Object saved = holder[0];
			holder[0] = place.get(frame, target);

			try {
				Object value = result.ev(frame);
				place.set(frame, target, value);
				return value;
			} finally {
				holder[0] = saved;
			}
		});
	}

	private Ev castBack(T value, Type type, Pos pos) {
		if (value.type.isNumeric() && type.isNumeric()) {
			Type.Prim from = (Type.Prim) value.type;
			Type.Prim to = (Type.Prim) type;
			return from.kind == to.kind ? value.ev : numeric(value.ev, from.kind, to.kind, pos);
		}

		return coerce(value, type, pos).ev;
	}

	private T incDec(Ast.IncDec incDec) {
		PlaceInfo info = place(incDec.target());

		if (info == null) {
			return bad();
		}

		Type type = info.type;

		if (!type.isNumeric()) {
			error(incDec.pos(), (incDec.increment() ? "++" : "--") + " needs a number, this is " + type);
			return bad();
		}

		Place place = info.place;
		PrimKind kind = ((Type.Prim) type).kind;
		int delta = incDec.increment() ? 1 : -1;
		boolean prefix = incDec.prefix();
		int line = incDec.pos().line();
		return new T(prim(kind), frame -> {
			Object target = place.target(frame);
			Object old = place.get(frame, target);

			if (old == null) {
				throw new ScriptException("Can't change a null number", line);
			}

			Object updated = switch (kind) {
				case INT -> (Integer) old + delta;
				case LONG -> (Long) old + delta;
				case DOUBLE -> (Double) old + delta;
				case CHAR -> (char) ((Character) old + delta);
				default -> old;
			};
			place.set(frame, target, updated);
			return prefix ? updated : old;
		});
	}


	private T lambda(Ast.Lambda lambda, Type expected) {
		ClassInfo functional = expected == null ? null : expected.classInfo();

		if (functional == null || functional.sam == null) {
			error(lambda.pos(), expected == null || expected.isLoose()
					? "A lambda needs a known type here, for example Runnable r = () -> ...;"
					: "A lambda can't be a " + expected);
			return bad();
		}

		MethodInfo sam = functional.sam;
		Map<String, Type> bindings = new HashMap<>();

		for (int i = 0; i < functional.typeParams.size(); i++) {
			bindings.put(functional.typeParams.get(i), ((Type.Ref) expected).arg(i));
		}

		if (sam.params.size() != lambda.params().size()) {
			error(lambda.pos(), "This lambda needs " + sam.params.size() + " parameter" + (sam.params.size() == 1 ? "" : "s") + " for a " + expected);
			return bad();
		}

		Type returnType = sam.returnType.subst(bindings);
		FuncCtx outer = ctx;
		ctx = new FuncCtx(outer, outer.owner, outer.isStatic, returnType, true, outer.name);

		for (int i = 0; i < lambda.params().size(); i++) {
			Ast.Param param = lambda.params().get(i);
			Type type = sam.params.get(i).subst(bindings);

			if (param.type() != null) {
				Type declared = resolveType(param.type());

				if (!type.isLoose() && !declared.equals(type) && declared != Type.ERROR) {
					error(param.pos(), "This parameter is a " + type + ", not " + declared);
				}

				type = declared;
			}

			if (type instanceof Type.Prim prim && prim.boxed) {
				type = prim;
			}

			for (FuncCtx enclosing = outer; enclosing != null; enclosing = enclosing.outer) {
				if (findLocalIn(enclosing, param.name())) {
					error(param.pos(), "Variable " + param.name() + " is already declared outside this lambda, pick another name");
					break;
				}
			}

			declareLocal(param.name(), type, param.isFinal(), true, param.pos());
		}

		Ex body;

		if (lambda.body() instanceof Ast.Block block) {
			body = block(block, false);

			if (returnType != Type.VOID && !returnType.isLoose() && completes(block)) {
				error(block.pos(), "This lambda must return " + article(returnType));
			}
		} else {
			Ast.Expr expression = (Ast.Expr) lambda.body();

			if (returnType == Type.VOID) {
				Ev ev = expr(expression, null).ev;
				body = frame -> {
					ev.ev(frame);
					return Frame.RETURN;
				};
			} else {
				Ev ev = coerce(expr(expression, returnType), returnType, expression.pos(), expression).ev;
				body = frame -> {
					frame.result = ev.ev(frame);
					return Frame.RETURN;
				};
			}
		}

		closeScope();
		FuncCtx inner = ctx;
		ctx = outer;
		Ev[] sources = inner.captureSources.stream().map(Local::load).toArray(Ev[]::new);
		int maxLocals = Math.max(1, inner.maxSlots);
		int paramCount = lambda.params().size();
		String name = inner.name + " (lambda)";
		String file = this.file;
		return new T(expected, frame -> {
			Object[] captured = new Object[sources.length];

			for (int i = 0; i < sources.length; i++) {
				captured[i] = sources[i].ev(frame);
			}

			return new LambdaValue(body, maxLocals, paramCount, captured, frame.self, name, file);
		});
	}

	private boolean findLocalIn(FuncCtx in, String name) {
		for (Scope scope = in.scope; scope != null; scope = scope.parent) {
			if (scope.vars.containsKey(name)) {
				return true;
			}
		}

		return false;
	}

	private static final class LambdaValue implements Fn {
		private final Ex body;
		private final int maxLocals;
		private final int paramCount;
		private final Object[] captured;
		private final Object self;
		private final String name;
		private final String file;

		LambdaValue(Ex body, int maxLocals, int paramCount, Object[] captured, Object self, String name, String file) {
			this.body = body;
			this.maxLocals = maxLocals;
			this.paramCount = paramCount;
			this.captured = captured;
			this.self = self;
			this.name = name;
			this.file = file;
		}

		@Override
		public Object call(Object... args) {
			Frame frame = new Frame(maxLocals, self);
			System.arraycopy(args, 0, frame.locals, 0, Math.min(args.length, paramCount));
			frame.captured = captured;

			try {
				body.ex(frame);
			} catch (ScriptException e) {
				e.leave(file, name, -1);
				throw e;
			} catch (StackOverflowError e) {
				throw new ScriptException("Stack overflow in " + name);
			}

			return frame.result;
		}

		@Override
		public String toString() {
			return "lambda";
		}
	}


	private void error(Pos pos, String message) {
		if (errors.size() < MAX_ERRORS) {
			errors.add(new CompileError(file, pos, message));
		}
	}
}
