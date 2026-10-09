package me.skaffy.client.shader.lang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import me.skaffy.client.gui.sfy.CompileError;
import me.skaffy.client.gui.sfy.CompileException;
import me.skaffy.client.gui.sfy.Pos;
import me.skaffy.client.shader.lang.Builtins.Blend;
import me.skaffy.client.shader.lang.Builtins.Context;
import me.skaffy.client.shader.lang.Builtins.Filter;
import me.skaffy.client.shader.lang.Builtins.Format;
import me.skaffy.client.shader.lang.Builtins.Program;
import me.skaffy.client.shader.lang.Builtins.Stage;
import me.skaffy.client.shader.lang.Builtins.Wrap;
import me.skaffy.client.shader.lang.ShType.ArrayType;
import me.skaffy.client.shader.lang.ShType.Base;
import me.skaffy.client.shader.lang.ShType.Matrix;
import me.skaffy.client.shader.lang.ShType.Scalar;
import me.skaffy.client.shader.lang.ShType.StructDef;
import me.skaffy.client.shader.lang.ShType.StructField;
import me.skaffy.client.shader.lang.ShType.StructType;
import me.skaffy.client.shader.lang.ShType.Vector;
import me.skaffy.client.shader.lang.ShaderAst.Annotation;
import me.skaffy.client.shader.lang.ShaderAst.AnnotationArg;
import me.skaffy.client.shader.lang.ShaderAst.ArrayInit;
import me.skaffy.client.shader.lang.ShaderAst.Assign;
import me.skaffy.client.shader.lang.ShaderAst.Binary;
import me.skaffy.client.shader.lang.ShaderAst.Block;
import me.skaffy.client.shader.lang.ShaderAst.Break;
import me.skaffy.client.shader.lang.ShaderAst.Call;
import me.skaffy.client.shader.lang.ShaderAst.Case;
import me.skaffy.client.shader.lang.ShaderAst.Cast;
import me.skaffy.client.shader.lang.ShaderAst.Conditional;
import me.skaffy.client.shader.lang.ShaderAst.Continue;
import me.skaffy.client.shader.lang.ShaderAst.Declarator;
import me.skaffy.client.shader.lang.ShaderAst.Discard;
import me.skaffy.client.shader.lang.ShaderAst.DoWhile;
import me.skaffy.client.shader.lang.ShaderAst.Empty;
import me.skaffy.client.shader.lang.ShaderAst.Expr;
import me.skaffy.client.shader.lang.ShaderAst.ExprStmt;
import me.skaffy.client.shader.lang.ShaderAst.Field;
import me.skaffy.client.shader.lang.ShaderAst.For;
import me.skaffy.client.shader.lang.ShaderAst.ForEach;
import me.skaffy.client.shader.lang.ShaderAst.If;
import me.skaffy.client.shader.lang.ShaderAst.Import;
import me.skaffy.client.shader.lang.ShaderAst.IncDec;
import me.skaffy.client.shader.lang.ShaderAst.Index;
import me.skaffy.client.shader.lang.ShaderAst.Literal;
import me.skaffy.client.shader.lang.ShaderAst.LocalVar;
import me.skaffy.client.shader.lang.ShaderAst.Member;
import me.skaffy.client.shader.lang.ShaderAst.Method;
import me.skaffy.client.shader.lang.ShaderAst.Name;
import me.skaffy.client.shader.lang.ShaderAst.NewArray;
import me.skaffy.client.shader.lang.ShaderAst.NewRecord;
import me.skaffy.client.shader.lang.ShaderAst.Param;
import me.skaffy.client.shader.lang.ShaderAst.RecordDecl;
import me.skaffy.client.shader.lang.ShaderAst.Return;
import me.skaffy.client.shader.lang.ShaderAst.Select;
import me.skaffy.client.shader.lang.ShaderAst.Stmt;
import me.skaffy.client.shader.lang.ShaderAst.Switch;
import me.skaffy.client.shader.lang.ShaderAst.TypeRef;
import me.skaffy.client.shader.lang.ShaderAst.Unary;
import me.skaffy.client.shader.lang.ShaderAst.Unit;
import me.skaffy.client.shader.lang.ShaderAst.While;

final class ShaderChecker {
	static final int MAX_ARRAY_LENGTH = 4096;
	static final int FIRST_VARYING_LOCATION = 8;
	static final int LAST_VARYING_LOCATION = 14;
	static final int MAX_PASSES = 32;
	static final int MAX_TARGETS = 32;
	static final int MAX_TEXTURES = 8;
	static final int MAX_OUTPUTS = 7;
	static final String SUN_PATH = "sunPathRotation";
	static final String HDR = "hdr";
	static final String TAA = "taa";
	static final String SHADOW_RESOLUTION = "shadowResolution";
	static final String SHADOW_DISTANCE = "shadowDistance";
	static final String LIGHT_RANGE = "coloredLightRange";
	static final Set<String> SETTINGS = Set.of(SUN_PATH, HDR, TAA, SHADOW_RESOLUTION, SHADOW_DISTANCE, LIGHT_RANGE);
	static final Set<String> SHADOW_SAMPLERS = Set.of("SkShadow", "SkShadowSolid", "SkShadowColor");

	private final String file;
	private final String className;
	private final String simpleName;
	private final String packageName;
	private final Classes classes;
	private final boolean helper;
	private final String prefix;
	private final List<CompileError> errors = new ArrayList<>();
	private final Map<String, StructDef> records = new LinkedHashMap<>();
	private final Map<String, FieldSym> fields = new LinkedHashMap<>();
	private final Map<String, List<MethodSym>> methods = new LinkedHashMap<>();
	private final List<MethodSym> methodOrder = new ArrayList<>();
	private final Set<String> imported = new HashSet<>();
	private final Set<String> reportedImports = new HashSet<>();
	private final Map<String, String> classImports = new HashMap<>();
	private final List<String> packageImports = new ArrayList<>();
	private final Set<String> reportedClasses = new HashSet<>();
	private boolean importAll;

	private MethodSym current;
	private final Deque<Map<String, Local>> scopes = new ArrayDeque<>();
	private int loops;
	private int switches;
	private int temps;

	private ShaderChecker(String file, String className, Classes classes, boolean helper, String prefix) {
		this.file = file;
		this.className = className;
		int dot = className.lastIndexOf('.');
		this.simpleName = className.substring(dot + 1);
		this.packageName = dot < 0 ? "" : className.substring(0, dot);
		this.classes = classes;
		this.helper = helper;
		this.prefix = prefix;
	}

	static final class Classes {
		private final java.util.function.Function<String, String> sources;
		private final Map<String, ShaderChecker> loaded = new HashMap<>();
		private final Set<String> failed = new HashSet<>();
		private final List<String> loading = new ArrayList<>();
		private final List<ShaderChecker> order = new ArrayList<>();
		private final List<CompileError> parseErrors = new ArrayList<>();
		final Set<String> dependencies = new java.util.TreeSet<>();
		final Set<String> missing = new java.util.TreeSet<>();
		private int next = 1;

		Classes(java.util.function.Function<String, String> sources) {
			this.sources = sources;
		}

		boolean exists(String fullName) {
			dependencies.add(fullName);
			return loaded.containsKey(fullName) || failed.contains(fullName) || sources.apply(fullName) != null;
		}

		ShaderChecker load(String fullName, ShaderChecker from, Pos pos) {
			dependencies.add(fullName);
			ShaderChecker done = loaded.get(fullName);

			if (done != null) {
				return done;
			}

			if (failed.contains(fullName)) {
				return null;
			}

			if (loading.contains(fullName)) {
				List<String> cycle = new ArrayList<>(loading.subList(loading.indexOf(fullName), loading.size()));
				cycle.add(fullName);
				from.error(pos, "Classes can't import each other in a circle: " + String.join(" -> ", cycle));
				return null;
			}

			String source = sources.apply(fullName);

			if (source == null) {
				from.error(pos, fullName + " isn't loaded (the server didn't send it)");
				failed.add(fullName);
				missing.add(fullName);
				return null;
			}

			String simple = fullName.substring(fullName.lastIndexOf('.') + 1);
			Unit unit;

			try {
				unit = ShaderParser.parseFile(simple + ".sfy", source);
			} catch (CompileException e) {
				parseErrors.addAll(e.errors());
				failed.add(fullName);
				return null;
			}

			ShaderChecker checker = new ShaderChecker(simple + ".sfy", fullName, this, true, "k" + next++ + "_");
			loading.add(fullName);
			checker.run(unit);
			loading.remove(fullName);
			loaded.put(fullName, checker);
			order.add(checker);
			return checker;
		}
	}


	static final class FieldSym {
		enum Kind {
			CONST,
			UNIFORM,
			VARYING,
			TEXTURE,
			TARGET,
			OUTPUT
		}

		final Kind kind;
		final String name;
		final ShType type;
		final Pos pos;
		String serverName;
		String glsl;
		List<ShaderModule.LightGroup> lightGroups;
		List<ShaderModule.LightGroup> filterGroups;
		Const value;
		String initCode;
		boolean flat;
		int location = -1;
		String source;
		Filter filter = Filter.LINEAR;
		Wrap wrap = Wrap.REPEAT;
		float scale = 1;
		int width;
		int height;
		Format format = Format.RGBA16F;
		boolean persistent;
		List<String> blocks;

		FieldSym(Kind kind, String name, ShType type, Pos pos) {
			this.kind = kind;
			this.name = name;
			this.type = type;
			this.pos = pos;
		}
	}

	static final class MethodSym {
		enum Kind {
			HELPER,
			VERTEX,
			FRAGMENT,
			PASS
		}

		final Method decl;
		Kind kind = Kind.HELPER;
		final String name;
		final List<ShType> params = new ArrayList<>();
		ShType result;
		String glsl;
		Set<Program> programs = EnumSet.noneOf(Program.class);
		boolean fog = true;
		Stage stage = Stage.SCREEN;
		FieldSym output;
		Blend blend = Blend.REPLACE;
		boolean takesUv;
		String code;
		String prototype;
		final Map<MethodSym, Pos> calls = new LinkedHashMap<>();
		final Set<String> helpers = new LinkedHashSet<>();
		final Set<String> samplers = new LinkedHashSet<>();
		Pos pixelsUse;
		String pixelsWhat;
		Pos passUse;
		String passWhat;
		Pos varyingWrite;
		String varyingWriteName;
		Pos varyingRead;
		String varyingReadName;
		Pos shadowUse;
		String shadowWhat;
		Pos sourceUse;
		Pos writeUse;
		final Set<FieldSym> outputsWritten = new LinkedHashSet<>();
		boolean usesShadowMap;
		boolean usesSolid;
		boolean usesLight;

		MethodSym(Method decl) {
			this.decl = decl;
			this.name = decl.name();
		}

		String describe() {
			return switch (kind) {
				case VERTEX -> "@Vertex " + name;
				case FRAGMENT -> "@Fragment " + name;
				case PASS -> "@Pass " + name;
				case HELPER -> name;
			};
		}
	}

	private record Local(String name, ShType type, String glsl, boolean isFinal, Const value) {
	}

	private record Ex(ShType type, String code, Const value, String readOnly, FieldSym varying) {
		Ex(ShType type, String code) {
			this(type, code, null, "this isn't a variable", null);
		}

		Ex(ShType type, String code, Const value) {
			this(type, code, value, "this isn't a variable", null);
		}
	}

	record Checked(String className, List<StructDef> records, List<FieldSym> fields, List<MethodSym> methods) {
	}


	static Checked check(String file, String className, Unit unit, Classes classes) throws CompileException {
		ShaderChecker checker = new ShaderChecker(file, className, classes, false, "");
		classes.loading.add(className);
		checker.run(unit);
		classes.loading.remove(className);
		List<CompileError> all = new ArrayList<>(checker.errors);
		all.addAll(classes.parseErrors);

		for (ShaderChecker helper : classes.order) {
			all.addAll(helper.errors);
		}

		if (!all.isEmpty()) {
			throw new CompileException(all.size() > 100 ? all.subList(0, 100) : all);
		}

		List<StructDef> records = new ArrayList<>(checker.records.values());
		List<FieldSym> fields = new ArrayList<>(checker.fields.values());
		List<MethodSym> methods = new ArrayList<>(checker.methodOrder);

		for (ShaderChecker helper : classes.order) {
			records.addAll(helper.records.values());
			fields.addAll(helper.fields.values());
			methods.addAll(helper.methodOrder);
		}

		checker.checkProgramWide(fields);

		if (!checker.errors.isEmpty()) {
			throw new CompileException(checker.errors);
		}

		return new Checked(className, List.copyOf(records), List.copyOf(fields), List.copyOf(methods));
	}

	private void checkProgramWide(List<FieldSym> all) {
		Map<Integer, FieldSym> materials = new HashMap<>();
		int lights = 0;
		int filters = 0;

		for (FieldSym field : all) {
			if (field.blocks != null && field.value != null) {
				FieldSym other = materials.putIfAbsent(field.value.intValue(), field);

				if (other != null) {
					error(Pos.NONE, "Block material " + field.value.intValue() + " is used twice: " + other.serverName + " and " + field.serverName);
				}
			}

			lights += field.lightGroups != null ? 1 : 0;
			filters += field.filterGroups != null ? 1 : 0;
		}

		if (lights > Builtins.MAX_LIGHT_TYPES) {
			error(Pos.NONE, "There can be at most " + Builtins.MAX_LIGHT_TYPES + " @Light types, these files have " + lights);
		}

		if (filters > Builtins.MAX_LIGHT_FILTERS) {
			error(Pos.NONE, "There can be at most " + Builtins.MAX_LIGHT_FILTERS + " @LightFilter colors, these files have " + filters);
		}
	}

	private void error(Pos pos, String message) {
		if (errors.size() < 100) {
			errors.add(new CompileError(file, pos, message));
		}
	}

	private void run(Unit unit) {
		checkHeader(unit);

		for (Member member : unit.type().members()) {
			if (member instanceof RecordDecl record) {
				declareRecord(record);
			}
		}

		for (Member member : unit.type().members()) {
			if (member instanceof RecordDecl record) {
				defineRecord(record);
			}
		}

		if (!unit.type().annotations().isEmpty()) {
			error(unit.type().annotations().getFirst().pos(), "The class of a shader takes no annotations");
		}

		for (Member member : unit.type().members()) {
			if (member instanceof Field field) {
				declareField(field);
			}
		}

		for (Member member : unit.type().members()) {
			if (member instanceof Method method) {
				declareMethod(method);
			}
		}

		for (MethodSym method : methodOrder) {
			checkBody(method);
		}

		checkReachability();
		checkLimits(unit);
		checkSettings();
	}

	private void checkSettings() {
		for (String name : SETTINGS) {
			FieldSym field = fields.get(name);

			if (field == null) {
				continue;
			}

			if (helper) {
				error(field.pos, name + " sets up the frame, so it only works in the file that's enabled, not in a class it imports");
				continue;
			}

			boolean constant = field.kind == FieldSym.Kind.CONST && field.value != null;
			boolean uniform = field.kind == FieldSym.Kind.UNIFORM;

			switch (name) {
				case SUN_PATH -> {
					if (!field.type.equals(ShType.FLOAT) || !constant && !uniform) {
						error(field.pos, "sunPathRotation tilts the sun's path in degrees: const float sunPathRotation = 30.0; or @Uniform float sunPathRotation = 30.0;");
					}
				}
				case HDR, TAA -> {
					if (!field.type.equals(ShType.BOOL) || !constant) {
						error(field.pos, name + " is a constant: const bool " + name + " = true;");
					}
				}
				case SHADOW_RESOLUTION -> {
					if (!field.type.equals(ShType.INT) || !constant && !uniform) {
						error(field.pos, "shadowResolution is the shadow map's size in pixels: const int shadowResolution = 2048; or a @Uniform int");
					} else if (constant && (field.value.intValue() < 512 || field.value.intValue() > 8192)) {
						error(field.pos, "shadowResolution is 512 to 8192 pixels");
					}
				}
				case SHADOW_DISTANCE -> {
					if (!field.type.equals(ShType.FLOAT) || !constant && !uniform) {
						error(field.pos, "shadowDistance is how far shadows reach in blocks: const float shadowDistance = 128.0; or a @Uniform float");
					} else if (constant && (field.value.scalar() < 16 || field.value.scalar() > 512)) {
						error(field.pos, "shadowDistance is 16 to 512 blocks");
					}
				}
				case LIGHT_RANGE -> {
					if (!field.type.equals(ShType.INT) || !constant) {
						error(field.pos, "coloredLightRange is a constant: const int coloredLightRange = 128;");
					} else if (field.value.intValue() < 64 || field.value.intValue() > 256 || field.value.intValue() % 16 != 0) {
						error(field.pos, "coloredLightRange is 64 to 256 blocks, a multiple of 16");
					}
				}
				default -> {
				}
			}
		}
	}

	private void checkLimits(Unit unit) {
		long passes = methodOrder.stream().filter(method -> method.kind == MethodSym.Kind.PASS).count();
		long targets = fields.values().stream().filter(field -> field.kind == FieldSym.Kind.TARGET).count();
		long textures = fields.values().stream().filter(field -> field.kind == FieldSym.Kind.TEXTURE).count();
		long outputs = fields.values().stream().filter(field -> field.kind == FieldSym.Kind.OUTPUT).count();

		if (outputs > MAX_OUTPUTS) {
			error(unit.type().pos(), "A shader file can have at most " + MAX_OUTPUTS + " @Output fields, this one has " + outputs);
		}

		if (passes > MAX_PASSES) {
			error(unit.type().pos(), "A shader file can have at most " + MAX_PASSES + " passes, this one has " + passes);
		}

		if (targets > MAX_TARGETS) {
			error(unit.type().pos(), "A shader file can have at most " + MAX_TARGETS + " @Target fields, this one has " + targets);
		}

		if (textures > MAX_TEXTURES) {
			error(unit.type().pos(), "A shader file can have at most " + MAX_TEXTURES + " @Texture fields, this one has " + textures);
		}
	}


	private void checkHeader(Unit unit) {
		int dot = className.lastIndexOf('.');
		String expectedPackage = dot < 0 ? "" : className.substring(0, dot);
		String expectedClass = className.substring(dot + 1);

		if (!unit.packageName().equals(expectedPackage)) {
			error(unit.packagePos(), "The package must be " + expectedPackage + " (the file is " + className + ")");
		}

		if (!unit.type().name().equals(expectedClass)) {
			error(unit.type().pos(), "The class must be called " + expectedClass + ", like the file");
		}

		boolean any = false;

		for (Import imp : unit.imports()) {
			if (imp.name().equals("skaffy.gui") || imp.name().startsWith("skaffy.gui.")) {
				error(imp.pos(), "This is a shader file, it can't import from skaffy.gui (GUI files import skaffy.gui.*; shader files import skaffy.shader.*)");
				continue;
			}

			if (imp.wildcard()) {
				if (imp.name().equals(Builtins.LIBRARY)) {
					importAll = true;
					any = true;
				} else if (imp.name().equals("skaffy") || imp.name().startsWith("skaffy.")) {
					error(imp.pos(), "Unknown import " + imp.name() + ".* (shader files import skaffy.shader.*)");
				} else if (!packageImports.contains(imp.name())) {
					packageImports.add(imp.name());
				}

				continue;
			}

			int last = imp.name().lastIndexOf('.');
			String pkg = last < 0 ? "" : imp.name().substring(0, last);
			String name = imp.name().substring(last + 1);

			if (pkg.equals(Builtins.LIBRARY)) {
				if (!Builtins.libraryNames().contains(name)) {
					error(imp.pos(), "skaffy.shader has no " + name + suggestion(name, Builtins.libraryNames()));
					continue;
				}

				imported.add(name);
				any = true;
				continue;
			}

			if (imp.name().startsWith("skaffy.")) {
				error(imp.pos(), "Unknown import " + imp.name() + " (shader files import skaffy.shader.* or single names from it)");
				continue;
			}

			if (imp.name().equals(className)) {
				error(imp.pos(), "A class doesn't need to import itself");
				continue;
			}

			String other = classImports.get(name);

			if (other != null && !other.equals(imp.name())) {
				error(imp.pos(), "Two imported classes are called " + name + ": " + other + " and " + imp.name());
				continue;
			}

			classImports.put(name, imp.name());

			if (!classes.exists(imp.name())) {
				error(imp.pos(), imp.name() + " isn't loaded (the server didn't send it)");
				reportedClasses.add(name);
				classes.missing.add(imp.name());
			}
		}

		if (!any) {
			error(unit.packagePos(), "A shader file needs import skaffy.shader.*; (or single names like import skaffy.shader.time;) after its package");
		}

		for (Import imp : unit.imports()) {
			String simple = imp.name().substring(imp.name().lastIndexOf('.') + 1);

			if (!imp.wildcard() && imp.name().equals(classImports.get(simple)) && classes.exists(imp.name()) && classes.load(imp.name(), this, imp.pos()) == null) {
				reportedClasses.add(simple);
			}
		}
	}

	private ShaderChecker classNamed(String name, Pos pos) {
		if (fields.containsKey(name) || records.containsKey(name) || name.equals(simpleName)) {
			return null;
		}

		String full = classImports.get(name);

		if (full == null) {
			String samePackage = packageName.isEmpty() ? name : packageName + "." + name;

			if (classes.exists(samePackage)) {
				full = samePackage;
			} else {
				for (String pkg : packageImports) {
					if (classes.exists(pkg + "." + name)) {
						full = pkg + "." + name;
						break;
					}
				}
			}
		}

		if (full == null || reportedClasses.contains(name) && !classes.loaded.containsKey(full)) {
			return null;
		}

		ShaderChecker other = classes.load(full, this, pos);

		if (other == null) {
			reportedClasses.add(name);
		}

		return other;
	}

	private boolean requireImport(String name, Pos pos) {
		if (importAll || imported.contains(name)) {
			return true;
		}

		if (reportedImports.add(name)) {
			error(pos, name + " comes from skaffy.shader: add import skaffy.shader." + name + "; (or import skaffy.shader.*;)");
		}

		return false;
	}


	private ShType resolveType(TypeRef ref, boolean allowVoid) {
		ShType base = resolveTypeName(ref.name(), ref.pos());

		if (base == null) {
			return null;
		}

		if (ref.array()) {
			error(ref.pos(), "Array types need their length from a value: float[] a = {1, 2}; or new float[4]");
			return null;
		}

		return base;
	}

	private ShType resolveTypeName(String name, Pos pos) {
		int dot = name.lastIndexOf('.');

		if (dot > 0) {
			String owner = name.substring(0, dot);
			String type = name.substring(dot + 1);
			ShaderChecker other = owner.contains(".") ? null : classNamed(owner, pos);

			if (other == null) {
				String full = owner.contains(".") ? owner : classImports.getOrDefault(owner, packageName.isEmpty() ? owner : packageName + "." + owner);
				classes.missing.add(full);

				if (reportedClasses.add(owner)) {
					error(pos, "Unknown class " + owner + " (no class " + full + " was sent; classes of other packages need an import)");
				}

				return null;
			}

			StructDef record = other.records.get(type);

			if (record == null) {
				error(pos, owner + " has no record " + type + suggestion(type, other.records.keySet()));
				return null;
			}

			return new StructType(record);
		}

		ShType builtin = ShType.builtin(name);

		if (builtin != null) {
			if (builtin == ShType.TEXTURE && !requireImport("Texture", pos)) {
				return null;
			}

			return builtin;
		}

		if (Builtins.LIBRARY_TYPES.containsKey(name)) {
			return requireImport(name, pos) ? Builtins.LIBRARY_TYPES.get(name) : null;
		}

		StructDef record = records.get(name);

		if (record != null) {
			return new StructType(record);
		}

		String hint = switch (name) {
			case "double" -> " (use float)";
			case "boolean" -> " (use bool)";
			case "Vector3f", "Vec3" -> " (use vec3)";
			case "String" -> " (shaders have no text)";
			default -> suggestion(name, typeNames());
		};
		error(pos, "Unknown type " + name + hint);
		return null;
	}

	private Set<String> typeNames() {
		Set<String> names = new HashSet<>(List.of("float", "int", "bool", "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4",
				"bvec2", "bvec3", "bvec4", "mat2", "mat3", "mat4"));
		names.addAll(Builtins.LIBRARY_TYPES.keySet());
		names.addAll(records.keySet());
		return names;
	}

	private void declareRecord(RecordDecl record) {
		if (records.containsKey(record.name()) || ShType.builtin(record.name()) != null || Builtins.LIBRARY_TYPES.containsKey(record.name())) {
			error(record.pos(), "There already is a type called " + record.name());
			return;
		}

		records.put(record.name(), new StructDef(record.name(), "S_" + prefix + record.name(), false));
	}

	private void defineRecord(RecordDecl record) {
		StructDef def = records.get(record.name());

		if (def == null || !def.fields().isEmpty()) {
			return;
		}

		List<StructField> structFields = new ArrayList<>();
		Set<String> names = new HashSet<>();

		for (Param component : record.components()) {
			if (!names.add(component.name())) {
				error(component.pos(), record.name() + " already has a " + component.name());
				continue;
			}

			ShType type = component.type().array() ? null : resolveType(component.type(), false);

			if (component.type().array()) {
				error(component.pos(), "Record components can't be arrays yet");
			}

			if (type == null) {
				continue;
			}

			if (type == ShType.TEXTURE || type instanceof StructType struct && struct.def().builtin()) {
				error(component.pos(), "A record can't hold a " + type.name());
				continue;
			}

			if (type instanceof StructType struct && dependsOn(struct.def(), def, new HashSet<>())) {
				error(component.pos(), "A record can't contain itself");
				continue;
			}

			structFields.add(new StructField(component.name(), "m_" + component.name(), type, true));
		}

		if (structFields.isEmpty()) {
			error(record.pos(), "A record needs at least one component");
			structFields.add(new StructField("sk_unused", "sk_unused", ShType.FLOAT, false));
		}

		def.fields(structFields);
	}

	private static boolean dependsOn(StructDef from, StructDef target, Set<StructDef> seen) {
		if (from == target) {
			return true;
		}

		if (!seen.add(from)) {
			return false;
		}

		for (StructField field : from.fields()) {
			if (field.type() instanceof StructType struct && dependsOn(struct.def(), target, seen)) {
				return true;
			}
		}

		return false;
	}


	private void declareField(Field field) {
		if (fields.containsKey(field.name())) {
			error(field.pos(), "There already is a field called " + field.name());
			return;
		}

		Annotation kindAnnotation = null;
		Annotation blocks = null;
		List<Annotation> light = new ArrayList<>();

		for (Annotation annotation : field.annotations()) {
			if (!Builtins.ANNOTATIONS.contains(annotation.name())) {
				error(annotation.pos(), "Unknown annotation @" + annotation.name() + suggestion(annotation.name(), Builtins.ANNOTATIONS));
				continue;
			}

			if (!requireImport(annotation.name(), annotation.pos())) {
				continue;
			}

			switch (annotation.name()) {
				case "Uniform", "Varying", "Texture", "Target", "Output" -> {
					if (kindAnnotation != null) {
						error(annotation.pos(), "A field can only be one of @Uniform, @Varying, @Texture, @Target and @Output");
					}

					if (helper && Set.of("Varying", "Target", "Output").contains(annotation.name())) {
						error(annotation.pos(), "@" + annotation.name() + " fields belong to the file that's enabled, not to a class it imports");
					}

					kindAnnotation = annotation;
				}
				case "Blocks" -> blocks = annotation;
				case "Light", "LightFilter" -> {
					if (!light.isEmpty() && !light.getFirst().name().equals(annotation.name())) {
						error(annotation.pos(), "A field can only be one of @Light and @LightFilter");
						continue;
					}

					light.add(annotation);
				}
				default -> error(annotation.pos(), "@" + annotation.name() + " goes on methods, not fields");
			}
		}

		boolean constant = field.modifiers().contains("const") || field.modifiers().contains("final");

		if (field.type().array() && kindAnnotation != null) {
			error(field.pos(), "@" + kindAnnotation.name() + " fields can't be arrays");
			return;
		}

		if (kindAnnotation == null) {
			if (!constant) {
				error(field.pos(), "Fields of a shader are constants (const float " + field.name() + " = ...;) or marked @Uniform, @Varying, @Texture, @Target or @Output");
				return;
			}

			declareConstant(field, blocks, light);
			return;
		}

		if (constant) {
			error(field.pos(), "@" + kindAnnotation.name() + " fields can't be const or final");
		}

		if (blocks != null) {
			error(blocks.pos(), "@Blocks goes on a const int, like @Blocks(\"minecraft:water\") const int WATER = 1;");
		}

		if (!light.isEmpty() && !kindAnnotation.name().equals("Uniform")) {
			error(light.getFirst().pos(), "@" + light.getFirst().name() + " goes on a vec3 color: a constant or a @Uniform");
			light.clear();
		}

		ShType type = resolveType(field.type(), false);

		if (type == null) {
			return;
		}

		switch (kindAnnotation.name()) {
			case "Uniform" -> declareUniform(field, kindAnnotation, type, light);
			case "Varying" -> declareVarying(field, kindAnnotation, type);
			case "Texture" -> declareTexture(field, kindAnnotation, type);
			case "Target" -> declareTarget(field, kindAnnotation, type);
			case "Output" -> declareOutput(field, kindAnnotation, type);
			default -> {
			}
		}
	}

	private void lightOf(FieldSym sym, List<Annotation> annotations) {
		Annotation first = annotations.getFirst();

		if (!sym.type.equals(ShType.VEC3)) {
			error(first.pos(), "@" + first.name() + " goes on a vec3 color, like @" + first.name() + "(\"minecraft:torch\") const vec3 FIRE = #FFA040;");
			return;
		}

		List<ShaderModule.LightGroup> groups = new ArrayList<>();

		for (Annotation light : annotations) {
			ShaderModule.LightGroup group = lightGroup(light);

			if (group != null) {
				groups.add(group);
			}
		}

		if (groups.size() != annotations.size()) {
			return;
		}

		if (first.name().equals("Light")) {
			sym.lightGroups = groups;
		} else {
			sym.filterGroups = groups;
		}
	}

	private ShaderModule.LightGroup lightGroup(Annotation light) {
		boolean filter = light.name().equals("LightFilter");
		List<String> blockIds = null;
		int level = 0;
		String levelFrom = null;
		boolean ok = true;

		for (AnnotationArg arg : light.args()) {
			String key = arg.key() == null ? "value" : arg.key();

			switch (key) {
				case "value" -> blockIds = blockList(light.name(), arg.value(), arg.pos());
				case "level", "levelFrom" -> {
					if (filter) {
						error(arg.pos(), "@LightFilter only takes blocks; it tints light, it doesn't give any");
						ok = false;
						continue;
					}

					if (key.equals("levelFrom")) {
						if (!(arg.value() instanceof Literal literal) || literal.kind() != ShaderAst.LiteralKind.STRING
								|| !((String) literal.value()).matches("[a-z0-9_]+")) {
							error(arg.pos(), "levelFrom names a number property of the blocks, like levelFrom = \"power\"");
							ok = false;
						} else {
							levelFrom = (String) literal.value();
						}

						continue;
					}

					Const value = constArg(arg, ShType.INT);

					if (value != null && (value.intValue() < 1 || value.intValue() > 15)) {
						error(arg.pos(), "level is 1 to 15, like block light");
						ok = false;
					} else if (value != null) {
						level = value.intValue();
					}
				}
				default -> {
					error(arg.pos(), "@" + light.name() + " takes the blocks" + (filter ? "" : ", level = 1 to 15 and levelFrom = \"property\""));
					ok = false;
				}
			}
		}

		if (blockIds == null) {
			error(light.pos(), "Name the blocks: @" + light.name() + "(\"minecraft:torch\") or @" + light.name() + "({\"minecraft:torch\", \"#minecraft:candles\"})");
			return null;
		}

		if (levelFrom != null && level == 0) {
			level = 15;
		}

		return ok ? new ShaderModule.LightGroup(blockIds, level, levelFrom) : null;
	}

	private void declareOutput(Field field, Annotation annotation, ShType type) {
		if (type != ShType.TEXTURE) {
			error(field.pos(), "@Output fields are of type Texture");
			return;
		}

		if (field.init() != null) {
			error(field.init().pos(), "An @Output starts empty every frame; @Fragment hooks write(" + field.name() + ", value) into it");
		}

		FieldSym sym = new FieldSym(FieldSym.Kind.OUTPUT, field.name(), type, field.pos());
		sym.glsl = "SkT_" + prefix + field.name();
		sym.serverName = field.name();
		sym.filter = Filter.NEAREST;

		for (AnnotationArg arg : annotation.args()) {
			switch (arg.key() == null ? "" : arg.key()) {
				case "format" -> sym.format = enumArg(arg, Format.class, sym.format);
				case "filter" -> sym.filter = enumArg(arg, Filter.class, sym.filter);
				default -> error(arg.pos(), "@Output takes format = RGBA16F/RGBA8 and filter = NEAREST/LINEAR");
			}
		}

		fields.put(field.name(), sym);
	}

	private void declareConstant(Field field, Annotation blocks, List<Annotation> light) {
		if (field.init() == null) {
			error(field.pos(), "A constant needs a value: const float " + field.name() + " = ...;");
			return;
		}

		ShType declared = field.type().array() ? null : resolveType(field.type(), false);

		if (!field.type().array() && declared == null) {
			return;
		}

		if (declared == ShType.TEXTURE) {
			error(field.pos(), "Textures are fields marked @Texture or @Target");
			return;
		}

		scopes.push(new HashMap<>());
		Ex init = field.type().array() ? checkArrayInit(field.init(), field.type(), field.pos()) : convertOrError(checkExpr(field.init(), declared), declared, field.init().pos());
		scopes.pop();

		if (init == null) {
			return;
		}

		FieldSym sym = new FieldSym(FieldSym.Kind.CONST, field.name(), init.type(), field.pos());
		sym.glsl = "u_" + prefix + field.name();
		sym.serverName = helper ? simpleName + "." + field.name() : field.name();
		sym.value = init.value();
		sym.initCode = init.code();

		if (blocks != null) {
			if (!init.type().equals(ShType.INT) || sym.value == null) {
				error(blocks.pos(), "@Blocks goes on a const int with a number, like @Blocks(\"minecraft:water\") const int WATER = 1;");
			} else if (sym.value.intValue() < 1 || sym.value.intValue() > 4095) {
				error(field.pos(), "Block materials are numbers from 1 to 4095 (0 means not listed)");
			} else if (blocks.args().size() != 1 || blocks.args().getFirst().key() != null) {
				error(blocks.pos(), "Write @Blocks(\"minecraft:water\") or @Blocks({\"#minecraft:leaves\", \"minecraft:short_grass\"})");
			} else {
				sym.blocks = blockList("Blocks", blocks.args().getFirst().value(), blocks.pos());
			}
		}

		if (!light.isEmpty()) {
			if (sym.value == null) {
				error(light.getFirst().pos(), "The color of a @" + light.getFirst().name() + " constant must be worked out without running the shader, like #FFA040");
			} else {
				lightOf(sym, light);
			}
		}

		fields.put(field.name(), sym);
	}

	private List<String> blockList(String annotation, Expr value, Pos pos) {
		List<String> result = new ArrayList<>();
		List<Expr> values = value instanceof ArrayInit init ? init.values() : List.of(value);

		if (values.isEmpty()) {
			error(pos, "@" + annotation + " needs at least one block");
		}

		for (Expr entry : values) {
			if (!(entry instanceof Literal literal) || literal.kind() != ShaderAst.LiteralKind.STRING) {
				error(entry.pos(), "Blocks are written as text, like \"minecraft:stone\" or \"#minecraft:leaves\"");
				continue;
			}

			String id = (String) literal.value();
			String plain = id.startsWith("#") ? id.substring(1) : id;

			if (!plain.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+(\\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*])?")) {
				error(entry.pos(), "\"" + id + "\" isn't a block id (like minecraft:stone), tag (like #minecraft:leaves) or either with states (like minecraft:repeater[powered=true])");
				continue;
			}

			result.add(id.startsWith("#") ? "#" + withNamespace(plain) : withNamespace(plain));
		}

		return result;
	}

	private static String withNamespace(String id) {
		int states = id.indexOf('[');
		String name = states < 0 ? id : id.substring(0, states);
		return name.contains(":") ? id : "minecraft:" + id;
	}

	private void declareUniform(Field field, Annotation annotation, ShType type, List<Annotation> light) {
		noArgs(annotation);

		if (!type.isBasic()) {
			error(field.pos(), "@Uniform fields are float, int, bool, vectors or matrices, not " + type.name());
			return;
		}

		FieldSym sym = new FieldSym(FieldSym.Kind.UNIFORM, field.name(), type, field.pos());
		sym.glsl = "u_" + prefix + field.name();
		sym.serverName = helper ? simpleName + "." + field.name() : field.name();
		sym.value = Const.of(type, new double[type.components()]);

		if (!light.isEmpty()) {
			lightOf(sym, light);
		}

		if (field.init() != null) {
			scopes.push(new HashMap<>());
			Ex init = convertOrError(checkExpr(field.init(), type), type, field.init().pos());
			scopes.pop();

			if (init != null) {
				if (init.value() == null) {
					error(field.init().pos(), "The starting value of a @Uniform must be worked out without running the shader (numbers, constants, vec3(...))");
				} else {
					sym.value = init.value();
				}
			}
		}

		fields.put(field.name(), sym);
	}

	private void declareVarying(Field field, Annotation annotation, ShType type) {
		Boolean flat = boolArg(annotation, "flat", false);

		for (AnnotationArg arg : annotation.args()) {
			if (!"flat".equals(arg.key())) {
				error(arg.pos(), "@Varying only takes flat = true or false");
			}
		}

		if (field.init() != null) {
			error(field.init().pos(), "A @Varying gets its value in a @Vertex hook, not here");
		}

		if (type.base() == Base.BOOL || !(type instanceof Scalar || type instanceof Vector || type instanceof Matrix)) {
			error(field.pos(), "@Varying fields are float, int, vectors or matrices" + (type.base() == Base.BOOL ? " (use an int for true/false)" : ""));
			return;
		}

		FieldSym sym = new FieldSym(FieldSym.Kind.VARYING, field.name(), type, field.pos());
		sym.glsl = "u_" + prefix + field.name();
		sym.serverName = field.name();
		sym.flat = Boolean.TRUE.equals(flat) || type.base() == Base.INT;
		int next = FIRST_VARYING_LOCATION;

		for (FieldSym other : fields.values()) {
			if (other.kind == FieldSym.Kind.VARYING) {
				next = Math.max(next, other.location + slots(other.type));
			}
		}

		if (next + slots(type) - 1 > LAST_VARYING_LOCATION) {
			error(field.pos(), "Too many @Varying fields: there is room for " + (LAST_VARYING_LOCATION - FIRST_VARYING_LOCATION + 1)
					+ " (vectors count one each, a matN counts N); pack values into vec4s");
			return;
		}

		sym.location = next;
		fields.put(field.name(), sym);
	}

	private static int slots(ShType type) {
		return type instanceof Matrix matrix ? matrix.size() : 1;
	}

	private void declareTexture(Field field, Annotation annotation, ShType type) {
		if (type != ShType.TEXTURE) {
			error(field.pos(), "@Texture fields are of type Texture");
			return;
		}

		if (field.init() != null) {
			error(field.init().pos(), "The texture is named in @Texture(\"...\"), not with =");
		}

		FieldSym sym = new FieldSym(FieldSym.Kind.TEXTURE, field.name(), type, field.pos());
		sym.glsl = "SkT_" + prefix + field.name();
		sym.serverName = helper ? simpleName + "." + field.name() : field.name();
		String source = null;

		for (AnnotationArg arg : annotation.args()) {
			String key = arg.key() == null ? "value" : arg.key();

			switch (key) {
				case "value" -> source = stringArg(arg);
				case "filter" -> sym.filter = enumArg(arg, Filter.class, sym.filter);
				case "wrap" -> sym.wrap = enumArg(arg, Wrap.class, sym.wrap);
				default -> error(arg.pos(), "@Texture takes a texture and filter = LINEAR/NEAREST, wrap = REPEAT/CLAMP");
			}
		}

		if (source == null || source.isEmpty()) {
			error(annotation.pos(), "Name the texture: @Texture(\"noise.png\") for an asset or @Texture(\"minecraft:block/stone\") for a vanilla texture");
			return;
		}

		sym.source = source;
		fields.put(field.name(), sym);
	}

	private void declareTarget(Field field, Annotation annotation, ShType type) {
		if (type != ShType.TEXTURE) {
			error(field.pos(), "@Target fields are of type Texture");
			return;
		}

		if (field.init() != null) {
			error(field.init().pos(), "A @Target starts empty; passes draw into it");
		}

		FieldSym sym = new FieldSym(FieldSym.Kind.TARGET, field.name(), type, field.pos());
		sym.glsl = "SkT_" + prefix + field.name();
		sym.serverName = field.name();

		for (AnnotationArg arg : annotation.args()) {
			if (arg.key() == null) {
				error(arg.pos(), "@Target takes named values: scale, width, height, format, persistent, filter");
				continue;
			}

			switch (arg.key()) {
				case "scale" -> {
					Const value = constArg(arg, ShType.FLOAT);

					if (value != null) {
						sym.scale = (float) value.scalar();

						if (!(sym.scale > 0 && sym.scale <= 4)) {
							error(arg.pos(), "scale is above 0 and at most 4");
						}
					}
				}
				case "width", "height" -> {
					Const value = constArg(arg, ShType.INT);

					if (value != null) {
						if (value.intValue() < 1 || value.intValue() > 8192) {
							error(arg.pos(), arg.key() + " is 1 to 8192 pixels");
						} else if (arg.key().equals("width")) {
							sym.width = value.intValue();
						} else {
							sym.height = value.intValue();
						}
					}
				}
				case "format" -> sym.format = enumArg(arg, Format.class, sym.format);
				case "persistent" -> {
					Boolean value = boolValue(arg);

					if (value != null) {
						sym.persistent = value;
					}
				}
				case "filter" -> sym.filter = enumArg(arg, Filter.class, sym.filter);
				default -> error(arg.pos(), "@Target takes scale, width, height, format, persistent and filter");
			}
		}

		if ((sym.width == 0) != (sym.height == 0)) {
			error(annotation.pos(), "Give @Target both width and height (a fixed size) or neither (a size like the screen's times scale)");
		}

		fields.put(field.name(), sym);
	}


	private void noArgs(Annotation annotation) {
		if (!annotation.args().isEmpty()) {
			error(annotation.pos(), "@" + annotation.name() + " takes no values");
		}
	}

	private String stringArg(AnnotationArg arg) {
		if (arg.value() instanceof Literal literal && literal.kind() == ShaderAst.LiteralKind.STRING) {
			return (String) literal.value();
		}

		error(arg.pos(), "Expected text in quotes");
		return null;
	}

	private <E extends Enum<E>> E enumArg(AnnotationArg arg, Class<E> type, E fallback) {
		if (arg.value() instanceof Name name) {
			for (E constant : type.getEnumConstants()) {
				if (constant.name().equals(name.name())) {
					return constant;
				}
			}
		}

		List<String> names = new ArrayList<>();

		for (E constant : type.getEnumConstants()) {
			names.add(constant.name());
		}

		error(arg.pos(), (arg.key() == null ? "Expected" : arg.key() + " is") + " one of " + String.join(", ", names));
		return fallback;
	}

	private Boolean boolValue(AnnotationArg arg) {
		if (arg.value() instanceof Literal literal && literal.kind() == ShaderAst.LiteralKind.BOOL) {
			return (Boolean) literal.value();
		}

		error(arg.pos(), arg.key() + " is true or false");
		return null;
	}

	private Boolean boolArg(Annotation annotation, String key, boolean fallback) {
		AnnotationArg arg = annotation.arg(key);
		return arg == null ? fallback : boolValue(arg);
	}

	private Const constArg(AnnotationArg arg, ShType type) {
		scopes.push(new HashMap<>());
		Ex value = convertOrError(checkExpr(arg.value(), type), type, arg.pos());
		scopes.pop();

		if (value == null) {
			return null;
		}

		if (value.value() == null) {
			error(arg.pos(), arg.key() + " must be a fixed number");
		}

		return value.value();
	}


	private void declareMethod(Method method) {
		MethodSym sym = new MethodSym(method);
		boolean ok = true;

		for (Param param : method.params()) {
			if (param.type().array()) {
				error(param.pos(), "Array parameters aren't supported yet; pass a record or the values");
				ok = false;
				continue;
			}

			ShType type = resolveType(param.type(), false);

			if (type == null) {
				ok = false;
				continue;
			}

			sym.params.add(type);
		}

		if (method.returnType() == null) {
			sym.result = ShType.VOID;
		} else if (method.returnType().array()) {
			error(method.returnType().pos(), "Methods can't return arrays yet; return a record");
			ok = false;
		} else {
			sym.result = resolveType(method.returnType(), false);
			ok &= sym.result != null;

			if (sym.result == ShType.TEXTURE) {
				error(method.returnType().pos(), "Methods can't return a Texture");
				ok = false;
			}
		}

		if (!ok) {
			return;
		}

		Annotation role = null;

		for (Annotation annotation : method.annotations()) {
			if (!Builtins.ANNOTATIONS.contains(annotation.name())) {
				error(annotation.pos(), "Unknown annotation @" + annotation.name() + suggestion(annotation.name(), Builtins.ANNOTATIONS));
				continue;
			}

			if (!requireImport(annotation.name(), annotation.pos())) {
				continue;
			}

			if (!Set.of("Vertex", "Fragment", "Pass").contains(annotation.name())) {
				error(annotation.pos(), "@" + annotation.name() + " goes on fields, not methods");
				continue;
			}

			if (role != null) {
				error(annotation.pos(), "A method can only be one of @Vertex, @Fragment and @Pass");
				continue;
			}

			if (helper) {
				error(annotation.pos(), "@" + annotation.name() + " methods belong to the file that's enabled, not to a class it imports (write a helper here and call it from there)");
				continue;
			}

			role = annotation;
		}

		sym.glsl = "f_" + prefix + method.name();

		if (role != null) {
			switch (role.name()) {
				case "Vertex" -> declareVertexHook(sym, role);
				case "Fragment" -> declareFragmentHook(sym, role);
				case "Pass" -> declarePass(sym, role);
				default -> {
				}
			}
		}

		List<MethodSym> overloads = methods.computeIfAbsent(method.name(), key -> new ArrayList<>());

		for (MethodSym other : overloads) {
			if (other.params.equals(sym.params)) {
				error(method.pos(), "There already is a method " + method.name() + " with these parameters");
				return;
			}
		}

		if (sym.kind != MethodSym.Kind.HELPER && !overloads.isEmpty() || !overloads.isEmpty() && overloads.getFirst().kind != MethodSym.Kind.HELPER) {
			error(method.pos(), "Hooks and passes can't share their name with another method");
			return;
		}

		if (overloads.size() > 0) {
			sym.glsl = "f_" + prefix + method.name() + "_" + overloads.size();
		}

		overloads.add(sym);
		methodOrder.add(sym);
	}

	private Set<Program> programs(Annotation annotation) {
		Set<Program> programs = EnumSet.noneOf(Program.class);
		AnnotationArg first = annotation.args().isEmpty() ? null : annotation.args().getFirst();

		if (first == null || first.key() != null && !first.key().equals("value")) {
			error(annotation.pos(), "Name the programs: @" + annotation.name() + "(TERRAIN) or @" + annotation.name() + "({TERRAIN, ENTITY})");
			return programs;
		}

		List<Expr> values = first.value() instanceof ArrayInit init ? init.values() : List.of(first.value());

		for (Expr value : values) {
			Program program = value instanceof Name name ? programByName(name.name()) : null;

			if (program == null) {
				error(value.pos(), "Programs are " + String.join(", ", java.util.Arrays.stream(Program.values()).map(Enum::name).toList()));
			} else if (!programs.add(program)) {
				error(value.pos(), program + " is listed twice");
			}
		}

		return programs;
	}

	private static Program programByName(String name) {
		for (Program program : Program.values()) {
			if (program.name().equals(name)) {
				return program;
			}
		}

		return null;
	}

	private void declareVertexHook(MethodSym sym, Annotation annotation) {
		sym.kind = MethodSym.Kind.VERTEX;
		sym.programs = programs(annotation);

		for (AnnotationArg arg : annotation.args().subList(Math.min(1, annotation.args().size()), annotation.args().size())) {
			error(arg.pos(), "@Vertex only takes the programs");
		}

		if (sym.result != ShType.VOID || sym.params.size() != 1 || !sym.params.getFirst().equals(Builtins.LIBRARY_TYPES.get("Vertex"))) {
			error(sym.decl.pos(), "A @Vertex hook looks like: void " + sym.name + "(Vertex v) { v.position.y += 0.1; }");
		}

		for (MethodSym other : methodOrder) {
			if (other.kind == MethodSym.Kind.VERTEX) {
				for (Program program : sym.programs) {
					if (other.programs.contains(program)) {
						error(annotation.pos(), program + " already has a @Vertex hook: " + other.name);
					}
				}
			}
		}
	}

	private void declareFragmentHook(MethodSym sym, Annotation annotation) {
		sym.kind = MethodSym.Kind.FRAGMENT;
		sym.programs = programs(annotation);

		for (AnnotationArg arg : annotation.args().subList(Math.min(1, annotation.args().size()), annotation.args().size())) {
			if ("fog".equals(arg.key())) {
				Boolean fog = boolValue(arg);
				sym.fog = fog == null || fog;
			} else {
				error(arg.pos(), "@Fragment takes the programs and fog = true or false");
			}
		}

		if (!sym.result.equals(ShType.VEC4) || sym.params.size() != 1 || !sym.params.getFirst().equals(Builtins.LIBRARY_TYPES.get("Fragment"))) {
			error(sym.decl.pos(), "A @Fragment hook looks like: vec4 " + sym.name + "(Fragment f) { return f.vanilla; }");
		}

		for (MethodSym other : methodOrder) {
			if (other.kind == MethodSym.Kind.FRAGMENT) {
				for (Program program : sym.programs) {
					if (other.programs.contains(program)) {
						error(annotation.pos(), program + " already has a @Fragment hook: " + other.name);
					}
				}
			}
		}
	}

	private void declarePass(MethodSym sym, Annotation annotation) {
		sym.kind = MethodSym.Kind.PASS;

		for (AnnotationArg arg : annotation.args()) {
			if (arg.key() == null) {
				error(arg.pos(), "@Pass takes named values: stage, output, blend");
				continue;
			}

			switch (arg.key()) {
				case "stage" -> sym.stage = enumArg(arg, Stage.class, sym.stage);
				case "blend" -> sym.blend = enumArg(arg, Blend.class, sym.blend);
				case "output" -> {
					if (arg.value() instanceof Name name && name.name().equals("SCREEN")) {
						sym.output = null;
					} else if (arg.value() instanceof Name name && fields.get(name.name()) instanceof FieldSym target && target.kind == FieldSym.Kind.TARGET) {
						sym.output = target;
					} else {
						error(arg.pos(), "output is SCREEN or a @Target field");
					}
				}
				default -> error(arg.pos(), "@Pass takes stage, output and blend");
			}
		}

		sym.takesUv = sym.params.size() == 1;

		if (!sym.result.equals(ShType.VEC4) || sym.params.size() > 1 || sym.takesUv && !sym.params.getFirst().equals(ShType.VEC2)) {
			error(sym.decl.pos(), "A @Pass looks like: vec4 " + sym.name + "(vec2 uv) { return texture(SCREEN, uv); }");
		}
	}


	private void checkBody(MethodSym method) {
		current = method;
		scopes.clear();
		scopes.push(new HashMap<>());
		loops = 0;
		switches = 0;
		temps = 0;
		StringBuilder signature = new StringBuilder();
		signature.append(method.result.glsl()).append(' ').append(method.glsl).append('(');
		List<Param> params = method.decl.params();

		for (int i = 0; i < params.size() && i < method.params.size(); i++) {
			Param param = params.get(i);
			ShType type = method.params.get(i);

			if (i > 0) {
				signature.append(", ");
			}

			String glsl = "l_" + param.name();
			boolean inout = method.kind == MethodSym.Kind.VERTEX;
			signature.append(inout ? "inout " : "").append(type.glsl()).append(' ').append(glsl);

			if (!declareLocal(param.name(), new Local(param.name(), type, glsl, type == ShType.TEXTURE, null), param.pos())) {
				continue;
			}
		}

		signature.append(')');
		method.prototype = signature + ";";
		StringBuilder body = new StringBuilder();
		block(method.decl.body(), body, 1, false);

		if (method.result != ShType.VOID && completesNormally(method.decl.body())) {
			error(method.decl.body().pos(), method.name + " can end without returning a " + method.result.name());
		}

		method.code = signature + " " + body.toString().stripLeading();
		current = null;
	}

	private boolean declareLocal(String name, Local local, Pos pos) {
		for (Map<String, Local> scope : scopes) {
			if (scope.containsKey(name)) {
				error(pos, name + " is already defined here");
				return false;
			}
		}

		scopes.peek().put(name, local);
		return true;
	}

	private Local findLocal(String name) {
		for (Map<String, Local> scope : scopes) {
			Local local = scope.get(name);

			if (local != null) {
				return local;
			}
		}

		return null;
	}

	private static void indent(StringBuilder out, int depth) {
		out.append("\t".repeat(depth));
	}

	private void block(Block block, StringBuilder out, int depth, boolean newScope) {
		scopes.push(new HashMap<>());
		out.append("{\n");

		for (Stmt statement : block.statements()) {
			statement(statement, out, depth);
		}

		indent(out, depth - 1);
		out.append("}\n");
		scopes.pop();
	}

	private void body(Stmt statement, StringBuilder out, int depth) {
		if (statement instanceof Block inner) {
			block(inner, out, depth + 1, true);
		} else {
			scopes.push(new HashMap<>());
			out.append("{\n");
			statement(statement, out, depth + 1);
			indent(out, depth);
			out.append("}\n");
			scopes.pop();
		}
	}

	private void statement(Stmt statement, StringBuilder out, int depth) {
		switch (statement) {
			case Block inner -> {
				indent(out, depth);
				block(inner, out, depth + 1, true);
			}
			case Empty ignored -> {
			}
			case LocalVar local -> localVar(local, out, depth, true);
			case ExprStmt expr -> {
				Ex value = checkExpr(expr.expr(), null);

				if (value != null) {
					if (!(expr.expr() instanceof Assign || expr.expr() instanceof IncDec || expr.expr() instanceof Call)) {
						error(expr.pos(), "This value isn't used (assign it or call something)");
					}

					indent(out, depth);
					out.append(stripParens(value.code())).append(";\n");
				}
			}
			case If ifStmt -> {
				Ex condition = condition(ifStmt.condition());
				indent(out, depth);
				out.append("if (").append(condition == null ? "true" : stripParens(condition.code())).append(") ");
				body(ifStmt.then(), out, depth);

				if (ifStmt.otherwise() != null) {
					indent(out, depth);
					out.append("else ");
					body(ifStmt.otherwise(), out, depth);
				}
			}
			case While loop -> {
				Ex condition = condition(loop.condition());
				indent(out, depth);
				out.append("while (").append(condition == null ? "false" : stripParens(condition.code())).append(") ");
				loops++;
				body(loop.body(), out, depth);
				loops--;
			}
			case DoWhile loop -> {
				indent(out, depth);
				out.append("do ");
				loops++;
				body(loop.body(), out, depth);
				loops--;
				Ex condition = condition(loop.condition());
				out.setLength(out.length() - 1);
				out.append(" while (").append(condition == null ? "false" : stripParens(condition.code())).append(");\n");
			}
			case For loop -> forLoop(loop, out, depth);
			case ForEach loop -> forEach(loop, out, depth);
			case Return ret -> returnStatement(ret, out, depth);
			case Break brk -> {
				if (loops == 0 && switches == 0) {
					error(brk.pos(), "break only works in a loop or switch");
				}

				indent(out, depth);
				out.append("break;\n");
			}
			case Continue cont -> {
				if (loops == 0) {
					error(cont.pos(), "continue only works in a loop");
				}

				indent(out, depth);
				out.append("continue;\n");
			}
			case Discard discard -> {
				usePixels(discard.pos(), "discard");
				indent(out, depth);
				out.append("discard;\n");
			}
			case Switch sw -> switchStatement(sw, out, depth);
		}
	}

	private static String stripParens(String code) {
		if (code.length() >= 2 && code.charAt(0) == '(' && code.charAt(code.length() - 1) == ')') {
			int depth = 0;

			for (int i = 0; i < code.length(); i++) {
				char c = code.charAt(i);

				if (c == '(') {
					depth++;
				} else if (c == ')') {
					depth--;

					if (depth == 0 && i < code.length() - 1) {
						return code;
					}
				}
			}

			return code.substring(1, code.length() - 1);
		}

		return code;
	}

	private Ex condition(Expr expr) {
		Ex value = checkExpr(expr, ShType.BOOL);

		if (value != null && !value.type().equals(ShType.BOOL)) {
			error(expr.pos(), "Expected a bool (true or false) but this is " + value.type().name()
					+ (value.type() instanceof Vector vector && vector.kind() == Base.BOOL ? " (use any(...) or all(...))" : ""));
			return null;
		}

		return value;
	}

	private void localVar(LocalVar local, StringBuilder out, int depth, boolean statement) {
		ShType declared = null;

		if (local.type() != null && !local.type().array()) {
			declared = resolveType(local.type(), false);

			if (declared == null) {
				for (Declarator declarator : local.declarators()) {
					declareLocal(declarator.name(), new Local(declarator.name(), ShType.FLOAT, "l_" + declarator.name(), false, null), declarator.pos());
				}

				return;
			}

			if (declared == ShType.TEXTURE) {
				error(local.type().pos(), "Textures can't be stored in variables; use the field directly");
				return;
			}
		}

		for (Declarator declarator : local.declarators()) {
			Ex init = null;

			if (declarator.init() != null) {
				if (local.type() != null && local.type().array()) {
					init = checkArrayInit(declarator.init(), local.type(), declarator.pos());
				} else if (declared == null) {
					init = checkExpr(declarator.init(), null);

					if (init != null && (init.type() == ShType.VOID || init.type() == ShType.TEXTURE)) {
						error(declarator.init().pos(), "var can't hold a " + init.type().name());
						init = null;
					}
				} else {
					init = convertOrError(checkExpr(declarator.init(), declared), declared, declarator.init().pos());
				}
			} else if (local.type() == null) {
				error(declarator.pos(), "var needs a value to know its type");
			} else if (local.type().array()) {
				error(declarator.pos(), "An array needs its values or length: float[] a = new float[4];");
			} else if (local.isConst()) {
				error(declarator.pos(), "A const needs a value");
			}

			ShType type = init != null ? init.type() : declared;

			if (type == null) {
				declareLocal(declarator.name(), new Local(declarator.name(), ShType.FLOAT, "l_" + declarator.name(), false, null), declarator.pos());
				continue;
			}

			Const value = local.isConst() && init != null ? init.value() : null;

			if (local.isConst() && init != null && value == null && type.isBasic()) {
				error(declarator.init().pos(), "A const value must be worked out without running the shader; use final instead");
			}

			String glsl = "l_" + declarator.name();
			declareLocal(declarator.name(), new Local(declarator.name(), type, glsl, local.isConst() || local.isFinal(), value), declarator.pos());
			indent(out, depth);

			if (value != null) {
				out.append("const ");
			}

			out.append(type.glsl()).append(' ').append(glsl);

			if (init != null) {
				out.append(" = ").append(stripParens(init.code()));
			} else {
				out.append(" = ").append(zero(type));
			}

			out.append(statement ? ";\n" : "");
		}
	}

	private static String zero(ShType type) {
		return switch (type) {
			case Scalar scalar -> Const.scalarGlsl(scalar.kind(), 0);
			case Vector vector -> vector.glsl() + "(" + Const.scalarGlsl(vector.kind(), 0) + ")";
			case Matrix matrix -> matrix.glsl() + "(0.0)";
			case StructType struct -> {
				StringBuilder code = new StringBuilder(struct.glsl()).append('(');

				for (int i = 0; i < struct.def().fields().size(); i++) {
					if (i > 0) {
						code.append(", ");
					}

					code.append(zero(struct.def().fields().get(i).type()));
				}

				yield code.append(')').toString();
			}
			case ArrayType array -> {
				StringBuilder code = new StringBuilder(array.glsl()).append('(');
				String element = zero(array.element());

				for (int i = 0; i < array.length(); i++) {
					if (i > 0) {
						code.append(", ");
					}

					code.append(element);
				}

				yield code.append(')').toString();
			}
			default -> "0";
		};
	}

	private void forLoop(For loop, StringBuilder out, int depth) {
		scopes.push(new HashMap<>());
		StringBuilder init = new StringBuilder();

		for (Stmt stmt : loop.init()) {
			if (stmt instanceof LocalVar local) {
				if (local.declarators().size() > 1) {
					error(local.pos(), "Declare one variable in a for loop's start");
				}

				StringBuilder declaration = new StringBuilder();
				localVar(local, declaration, 0, false);
				init.append(declaration);
			} else if (stmt instanceof ExprStmt expr) {
				Ex value = checkExpr(expr.expr(), null);

				if (value != null) {
					if (!init.isEmpty()) {
						init.append(", ");
					}

					init.append(stripParens(value.code()));
				}
			}
		}

		Ex condition = loop.condition() == null ? null : condition(loop.condition());
		StringBuilder updates = new StringBuilder();

		for (Expr update : loop.updates()) {
			Ex value = checkExpr(update, null);

			if (value != null) {
				if (!updates.isEmpty()) {
					updates.append(", ");
				}

				updates.append(stripParens(value.code()));
			}
		}

		indent(out, depth);
		out.append("for (").append(init).append("; ").append(condition == null ? "" : stripParens(condition.code())).append("; ").append(updates).append(") ");
		loops++;
		body(loop.body(), out, depth);
		loops--;
		scopes.pop();
	}

	private void forEach(ForEach loop, StringBuilder out, int depth) {
		Ex iterable = checkExpr(loop.iterable(), null);

		if (iterable == null) {
			return;
		}

		if (!(iterable.type() instanceof ArrayType array)) {
			error(loop.iterable().pos(), "for (x : values) goes over an array, not a " + iterable.type().name());
			return;
		}

		ShType element = array.element();

		if (loop.type() != null) {
			if (loop.type().array()) {
				error(loop.type().pos(), "Expected " + element.name());
				return;
			}

			ShType declared = resolveType(loop.type(), false);

			if (declared != null && !declared.equals(element) && !(declared.equals(ShType.FLOAT) && element.equals(ShType.INT))) {
				error(loop.type().pos(), "The array holds " + element.name() + ", not " + declared.name());
				return;
			}

			if (declared != null) {
				element = declared;
			}
		}

		int id = temps++;
		String arrayName = "sk_array" + id;
		String indexName = "sk_i" + id;
		indent(out, depth);
		out.append(array.glsl()).append(' ').append(arrayName).append(" = ").append(stripParens(iterable.code())).append(";\n");
		indent(out, depth);
		out.append("for (int ").append(indexName).append(" = 0; ").append(indexName).append(" < ").append(array.length()).append("; ").append(indexName).append("++) {\n");
		scopes.push(new HashMap<>());
		String glsl = "l_" + loop.name();
		declareLocal(loop.name(), new Local(loop.name(), element, glsl, false, null), loop.pos());
		indent(out, depth + 1);
		String elementCode = arrayName + "[" + indexName + "]";
		out.append(element.glsl()).append(' ').append(glsl).append(" = ")
				.append(element.equals(array.element()) ? elementCode : "float(" + elementCode + ")").append(";\n");
		loops++;
		indent(out, depth + 1);
		body(loop.body(), out, depth + 1);
		loops--;
		scopes.pop();
		indent(out, depth);
		out.append("}\n");
	}

	private void returnStatement(Return ret, StringBuilder out, int depth) {
		ShType expected = current.result;
		indent(out, depth);

		if (ret.value() == null) {
			if (expected != ShType.VOID) {
				error(ret.pos(), current.name + " must return a " + expected.name());
			}

			out.append("return;\n");
			return;
		}

		if (expected == ShType.VOID) {
			error(ret.pos(), current.name + " is void and returns nothing");
			out.append("return;\n");
			return;
		}

		Ex value = convertOrError(checkExpr(ret.value(), expected), expected, ret.value().pos());
		out.append("return ").append(value == null ? zero(expected) : stripParens(value.code())).append(";\n");
	}

	private void switchStatement(Switch sw, StringBuilder out, int depth) {
		Ex selector = checkExpr(sw.selector(), ShType.INT);

		if (selector != null && !selector.type().equals(ShType.INT)) {
			error(sw.selector().pos(), "switch works on an int, not a " + selector.type().name());
		}

		indent(out, depth);
		out.append("switch (").append(selector == null ? "0" : stripParens(selector.code())).append(") {\n");
		Set<Integer> seen = new HashSet<>();
		boolean hasDefault = false;
		switches++;

		for (Case c : sw.cases()) {
			if (c.isDefault()) {
				if (hasDefault) {
					error(c.pos(), "Only one default");
				}

				hasDefault = true;
				indent(out, depth);
				out.append("default:");
			}

			for (Expr label : c.labels()) {
				Ex value = checkExpr(label, ShType.INT);

				if (value == null) {
					continue;
				}

				if (!value.type().equals(ShType.INT) || value.value() == null) {
					error(label.pos(), "case labels are fixed int numbers or constants");
					continue;
				}

				if (!seen.add(value.value().intValue())) {
					error(label.pos(), "case " + value.value().intValue() + " is listed twice");
				}

				indent(out, depth);
				out.append("case ").append(value.value().intValue()).append(':');
			}

			out.append(" {\n");
			scopes.push(new HashMap<>());

			for (Stmt stmt : c.body()) {
				statement(stmt, out, depth + 1);
			}

			if (c.arrow()) {
				indent(out, depth + 1);
				out.append("break;\n");
			}

			scopes.pop();
			indent(out, depth);
			out.append("}\n");
		}

		switches--;
		indent(out, depth);
		out.append("}\n");
	}

	private static boolean completesNormally(Stmt statement) {
		return switch (statement) {
			case Return ignored -> false;
			case Discard ignored -> false;
			case Block block -> block.statements().isEmpty() || completesNormally(block.statements().getLast());
			case If ifStmt -> ifStmt.otherwise() == null || completesNormally(ifStmt.then()) || completesNormally(ifStmt.otherwise());
			case While loop -> !(loop.condition() instanceof Literal literal && Boolean.TRUE.equals(literal.value())) || containsBreak(loop.body());
			case For loop -> loop.condition() != null || containsBreak(loop.body());
			case Switch sw -> {
				boolean hasDefault = sw.cases().stream().anyMatch(Case::isDefault);

				if (!hasDefault) {
					yield true;
				}

				for (Case c : sw.cases()) {
					if (c.body().isEmpty() && !c.arrow()) {
						continue;
					}

					for (Stmt stmt : c.body()) {
						if (containsBreak(stmt)) {
							yield true;
						}
					}

					if (c.body().isEmpty() || completesNormally(c.body().getLast()) && c.arrow()) {
						yield true;
					}
				}

				Case last = sw.cases().getLast();
				yield last.body().isEmpty() || completesNormally(last.body().getLast());
			}
			default -> true;
		};
	}

	private static boolean containsBreak(Stmt statement) {
		return switch (statement) {
			case Break ignored -> true;
			case Block block -> block.statements().stream().anyMatch(ShaderChecker::containsBreak);
			case If ifStmt -> containsBreak(ifStmt.then()) || ifStmt.otherwise() != null && containsBreak(ifStmt.otherwise());
			default -> false;
		};
	}


	private Ex checkArrayInit(Expr init, TypeRef ref, Pos pos) {
		ShType element = resolveTypeName(ref.name(), ref.pos());

		if (element == null) {
			return null;
		}

		if (element == ShType.TEXTURE) {
			error(ref.pos(), "There are no arrays of textures");
			return null;
		}

		if (init instanceof ArrayInit values) {
			return arrayOf(element, values.values(), values.pos());
		}

		Ex value = checkExpr(init, null);

		if (value == null) {
			return null;
		}

		if (!(value.type() instanceof ArrayType array) || !array.element().equals(element)) {
			error(init.pos(), "Expected an array of " + element.name() + " but this is " + value.type().name());
			return null;
		}

		return value;
	}

	private Ex arrayOf(ShType element, List<Expr> values, Pos pos) {
		if (values.isEmpty()) {
			error(pos, "An array needs at least one value");
			return null;
		}

		if (values.size() > MAX_ARRAY_LENGTH) {
			error(pos, "Arrays can have at most " + MAX_ARRAY_LENGTH + " values");
			return null;
		}

		ArrayType type = new ArrayType(element, values.size());
		StringBuilder code = new StringBuilder(type.glsl()).append('(');

		for (int i = 0; i < values.size(); i++) {
			Expr value = values.get(i);

			if (value instanceof ArrayInit) {
				error(value.pos(), "Arrays of arrays don't exist in shaders");
				return null;
			}

			Ex checked = convertOrError(checkExpr(value, element), element, value.pos());

			if (i > 0) {
				code.append(", ");
			}

			code.append(checked == null ? zero(element) : stripParens(checked.code()));
		}

		return new Ex(type, code.append(')').toString());
	}


	private Ex convertOrError(Ex value, ShType target, Pos pos) {
		if (value == null || target == null) {
			return value;
		}

		Ex converted = convert(value, target);

		if (converted == null) {
			error(pos, "Expected " + target.name() + " but this is " + value.type().name() + hint(value.type(), target));
		}

		return converted;
	}

	private static String hint(ShType from, ShType to) {
		if (from.equals(ShType.FLOAT) && to.equals(ShType.INT)) {
			return " (use int(...) to cut off the decimals)";
		}

		if (from instanceof Vector && to instanceof Vector && from.components() > to.components()) {
			return " (use " + "xyzw".substring(0, to.components()) + " to take the first values, like v." + "xyzw".substring(0, to.components()) + ")";
		}

		if (from instanceof Vector && to instanceof Vector && from.components() < to.components()) {
			return " (use " + to.name() + "(value, ...) to add values)";
		}

		return "";
	}

	private static Ex convert(Ex value, ShType target) {
		if (value.type().equals(target)) {
			return value;
		}

		if (value.type().base() == Base.INT && target.base() == Base.FLOAT
				&& (value.type() instanceof Scalar && target instanceof Scalar || value.type() instanceof Vector from && target instanceof Vector to && from.size() == to.size())) {
			Const converted = value.value() == null ? null : Const.of(target, value.value().values());
			String code = converted != null ? converted.glsl() : target.glsl() + "(" + stripParens(value.code()) + ")";
			return new Ex(target, code, converted);
		}

		return null;
	}


	private Ex checkExpr(Expr expr, ShType expected) {
		return switch (expr) {
			case Literal literal -> literal(literal);
			case Name name -> name(name);
			case Select select -> select(select);
			case Index index -> index(index);
			case Call call -> call(call);
			case NewRecord create -> newRecord(create);
			case NewArray create -> newArray(create);
			case ArrayInit init -> {
				if (expected instanceof ArrayType array) {
					yield arrayOf(array.element(), init.values(), init.pos());
				}

				error(init.pos(), "{...} only works right after an array declaration: float[] a = {1, 2};");
				yield null;
			}
			case Unary unary -> unary(unary);
			case Binary binary -> binary(binary);
			case Assign assign -> assign(assign);
			case IncDec incDec -> incDec(incDec);
			case Conditional conditional -> conditional(conditional);
			case Cast cast -> construct(cast.type(), List.of(cast.operand()), cast.pos());
		};
	}

	private Ex literal(Literal literal) {
		return switch (literal.kind()) {
			case INT -> {
				int value = literal.value() instanceof Integer number ? number : Integer.MIN_VALUE;

				if (literal.value() instanceof Long) {
					error(literal.pos(), "2147483648 is too big for an int");
				}

				Const constant = Const.ofInt(value);
				yield new Ex(ShType.INT, constant.glsl(), constant);
			}
			case FLOAT -> {
				Const constant = Const.of(ShType.FLOAT, (Double) literal.value());
				yield new Ex(ShType.FLOAT, constant.glsl(), constant);
			}
			case BOOL -> {
				Const constant = Const.of(ShType.BOOL, (Boolean) literal.value() ? 1 : 0);
				yield new Ex(ShType.BOOL, constant.glsl(), constant);
			}
			case COLOR3, COLOR4 -> {
				int argb = (Integer) literal.value();
				double r = (argb >> 16 & 0xFF) / 255.0;
				double g = (argb >> 8 & 0xFF) / 255.0;
				double b = (argb & 0xFF) / 255.0;
				double a = (argb >>> 24) / 255.0;
				Const constant = literal.kind() == ShaderAst.LiteralKind.COLOR3 ? Const.of(ShType.VEC3, r, g, b) : Const.of(ShType.VEC4, r, g, b, a);
				yield new Ex(constant.type(), constant.glsl(), constant);
			}
			case STRING -> {
				error(literal.pos(), "Text only works in annotations, like @Texture(\"noise.png\")");
				yield null;
			}
		};
	}

	private Ex name(Name name) {
		Local local = findLocal(name.name());

		if (local != null) {
			if (local.type() == ShType.TEXTURE) {
				return new Ex(local.type(), local.glsl(), null, "textures can't be changed", null);
			}

			return new Ex(local.type(), local.glsl(), local.value(), local.isFinal() ? name.name() + " is final" : null, null);
		}

		FieldSym field = fields.get(name.name());

		if (field != null) {
			return field(field, name.pos());
		}

		Builtins.Value value = Builtins.VALUES.get(name.name());

		if (value != null) {
			if (value.library() && !requireImport(value.name(), name.pos())) {
				return null;
			}

			if (current != null) {
				useValue(value, name.pos());
			} else if (value.restriction() != Builtins.ANY || value.type() == ShType.TEXTURE) {
				error(name.pos(), value.name() + " can't be used in a constant");
				return null;
			}

			Const constant = null;

			if (value.type().equals(ShType.INT) && value.glsl().matches("-?\\d+")) {
				constant = Const.ofInt(Integer.parseInt(value.glsl()));
			}

			return new Ex(value.type(), value.glsl(), constant, value.name() + " is a built-in value and can't be changed", null);
		}

		if (programByName(name.name()) != null && Builtins.VALUES.get(name.name()) == null) {
			error(name.pos(), name.name() + " is a program and only works in @Vertex(...) and @Fragment(...)");
			return null;
		}

		if (records.containsKey(name.name())) {
			error(name.pos(), name.name() + " is a record; make one with new " + name.name() + "(...)");
			return null;
		}

		if (reportedClasses.contains(name.name())) {
			return null;
		}

		if (Character.isUpperCase(name.name().charAt(0)) && classNamed(name.name(), name.pos()) != null) {
			error(name.pos(), name.name() + " is a class; use what's in it, like " + name.name() + ".VALUE or " + name.name() + ".method(...)");
			return null;
		}

		Set<String> candidates = new HashSet<>(fields.keySet());
		candidates.addAll(Builtins.VALUES.keySet());

		for (Map<String, Local> scope : scopes) {
			candidates.addAll(scope.keySet());
		}

		error(name.pos(), "Unknown name " + name.name() + suggestion(name.name(), candidates));
		return null;
	}

	private Ex field(FieldSym field, Pos pos) {
		return switch (field.kind) {
			case CONST -> new Ex(field.type, field.glsl, field.value, field.name + " is a constant", null);
			case UNIFORM -> {
				String code = field.type.base() == Base.BOOL ? field.type.glsl() + "(" + field.glsl + "_i)" : field.glsl;

				if (field.type instanceof Scalar && field.type.base() == Base.BOOL) {
					code = "(" + field.glsl + "_i != 0)";
				}

				yield new Ex(field.type, code, null, field.name + " is a @Uniform; the server changes it", null);
			}
			case VARYING -> {
				if (current == null) {
					error(pos, "A @Varying can't be used in a constant");
					yield null;
				}

				if (current.varyingRead == null) {
					current.varyingRead = pos;
					current.varyingReadName = field.name;
				}

				yield new Ex(field.type, field.glsl, null, null, field);
			}
			case TEXTURE, TARGET, OUTPUT -> {
				if (current == null) {
					error(pos, "Textures can't be used in a constant");
					yield null;
				}

				if (field.kind == FieldSym.Kind.TARGET) {
					usePass(pos, field.name + " (a @Target)");
				} else if (field.kind == FieldSym.Kind.OUTPUT) {
					usePass(pos, field.name + " (an @Output; hooks write(" + field.name + ", value), passes read it)");
				}

				current.samplers.add(field.glsl);
				yield new Ex(field.type, field.glsl, null, "textures can't be changed", null);
			}
		};
	}

	private boolean unsentClass(Expr target) {
		if (!(target instanceof Name name) || findLocal(name.name()) != null || fields.containsKey(name.name()) || Builtins.VALUES.containsKey(name.name())
				|| records.containsKey(name.name()) || !Character.isUpperCase(name.name().charAt(0))) {
			return false;
		}

		String full = classImports.getOrDefault(name.name(), packageName.isEmpty() ? name.name() : packageName + "." + name.name());

		if (classes.exists(full)) {
			return reportedClasses.contains(name.name());
		}

		classes.missing.add(full);

		if (reportedClasses.add(name.name())) {
			error(name.pos(), "Unknown class " + name.name() + " (no class " + full + " was sent; classes of other packages need an import)");
		}

		return true;
	}

	private void useValue(Builtins.Value value, Pos pos) {
		int restriction = value.restriction();

		if ((restriction & Builtins.PASS_ONLY) != 0) {
			usePass(pos, value.name());
		}

		if ((restriction & Builtins.PIXELS) != 0) {
			usePixels(pos, value.name());
		}

		if ((restriction & Builtins.NO_SHADOW) != 0 && current.shadowUse == null) {
			current.shadowUse = pos;
			current.shadowWhat = value.name();
		}

		if ((restriction & Builtins.FRAGMENT_HOOKS) != 0 && current.sourceUse == null) {
			current.sourceUse = pos;
		}

		if (value.type() == ShType.TEXTURE && !value.glsl().equals("Sampler0")) {
			current.samplers.add(value.glsl());
		}

		current.usesShadowMap |= SHADOW_SAMPLERS.contains(value.glsl());
		current.usesSolid |= value.glsl().startsWith("SkSolid");
	}

	private ShaderChecker classTarget(Expr target) {
		if (!(target instanceof Name name) || findLocal(name.name()) != null || fields.containsKey(name.name())
				|| Builtins.VALUES.containsKey(name.name()) || !Character.isUpperCase(name.name().charAt(0))) {
			return null;
		}

		return classNamed(name.name(), name.pos());
	}

	private void usePixels(Pos pos, String what) {
		if (current != null && current.pixelsUse == null) {
			current.pixelsUse = pos;
			current.pixelsWhat = what;
		}
	}

	private void usePass(Pos pos, String what) {
		if (current != null && current.passUse == null) {
			current.passUse = pos;
			current.passWhat = what;
		}
	}

	private Ex select(Select select) {
		ShaderChecker owner = classTarget(select.target());

		if (owner != null) {
			FieldSym field = owner.fields.get(select.name());

			if (field == null) {
				error(select.pos(), owner.simpleName + " has no " + select.name() + suggestion(select.name(), owner.fields.keySet()));
				return null;
			}

			if (field.kind == FieldSym.Kind.CONST && field.value == null && current == null) {
				error(select.pos(), owner.simpleName + "." + select.name() + " isn't worked out while compiling, so it can't be used in a constant here");
				return null;
			}

			return field(field, select.pos());
		}

		if (unsentClass(select.target())) {
			return null;
		}

		Ex target = checkExpr(select.target(), null);

		if (target == null) {
			return null;
		}

		String member = select.name();

		switch (target.type()) {
			case Vector vector -> {
				return swizzle(target, vector, member, select.pos());
			}
			case StructType struct -> {
				StructField field = struct.def().field(member);

				if (field == null) {
					Set<String> names = new HashSet<>();
					struct.def().fields().forEach(candidate -> names.add(candidate.name()));
					error(select.pos(), struct.name() + " has no " + member + suggestion(member, names));
					return null;
				}

				String readOnly = target.readOnly();

				if (readOnly == null && !field.writable()) {
					readOnly = struct.name() + "." + member + " can only be read";
				}

				return new Ex(field.type(), target.code() + "." + field.glslName(), null, readOnly, target.varying());
			}
			case ArrayType array -> {
				if (member.equals("length")) {
					Const length = Const.ofInt(array.length());
					return new Ex(ShType.INT, length.glsl(), length);
				}

				error(select.pos(), "Arrays only have length");
				return null;
			}
			case Scalar scalar -> {
				error(select.pos(), "A " + scalar.name() + " has no " + member);
				return null;
			}
			case Matrix matrix -> {
				error(select.pos(), "Matrices have no fields; m[0] is the first column");
				return null;
			}
			default -> {
				error(select.pos(), "A " + target.type().name() + " has no " + member);
				return null;
			}
		}
	}

	private Ex swizzle(Ex target, Vector vector, String member, Pos pos) {
		String[] sets = {"xyzw", "rgba", "stpq"};
		String set = null;

		for (String candidate : sets) {
			if (candidate.indexOf(member.charAt(0)) >= 0) {
				set = candidate;
			}
		}

		if (set == null || member.length() > 4) {
			error(pos, vector.name() + " has no " + member + " (use x, y, z, w or r, g, b, a, like v.xy)");
			return null;
		}

		int[] indices = new int[member.length()];
		boolean repeats = false;
		Set<Character> seen = new HashSet<>();

		for (int i = 0; i < member.length(); i++) {
			int at = set.indexOf(member.charAt(i));

			if (at < 0) {
				error(pos, "Don't mix " + set + " letters with others in " + member);
				return null;
			}

			if (at >= vector.size()) {
				error(pos, vector.name() + " has only " + vector.size() + " values, so no " + member.charAt(i));
				return null;
			}

			indices[i] = at;
			repeats |= !seen.add(member.charAt(i));
		}

		ShType type = ShType.vector(vector.kind(), member.length());
		Const value = null;

		if (target.value() != null) {
			double[] values = new double[indices.length];

			for (int i = 0; i < indices.length; i++) {
				values[i] = target.value().values()[indices[i]];
			}

			value = Const.of(type, values);
		}

		String readOnly = target.readOnly() != null ? target.readOnly() : repeats ? member + " repeats a letter, so it can't be assigned" : null;
		return new Ex(type, target.code() + "." + member, value, readOnly, target.varying());
	}

	private Ex index(Index index) {
		Ex target = checkExpr(index.target(), null);
		Ex at = checkExpr(index.index(), ShType.INT);

		if (target == null || at == null) {
			return null;
		}

		if (!at.type().equals(ShType.INT)) {
			error(index.index().pos(), "An index is an int, not a " + at.type().name());
			return null;
		}

		int length;
		ShType element;

		switch (target.type()) {
			case ArrayType array -> {
				length = array.length();
				element = array.element();
			}
			case Vector vector -> {
				length = vector.size();
				element = ShType.scalar(vector.kind());
			}
			case Matrix matrix -> {
				length = matrix.size();
				element = matrix.column();
			}
			default -> {
				error(index.pos(), "Only arrays, vectors and matrices have [index], not " + target.type().name());
				return null;
			}
		}

		Const value = null;

		if (at.value() != null) {
			int i = at.value().intValue();

			if (i < 0 || i >= length) {
				error(index.index().pos(), "Index " + i + " is out of bounds for length " + length);
				return null;
			}

			if (target.value() != null && element.isBasic()) {
				int size = element.components();
				double[] values = new double[size];
				System.arraycopy(target.value().values(), i * size, values, 0, size);
				value = Const.of(element, values);
			}
		}

		return new Ex(element, target.code() + "[" + stripParens(at.code()) + "]", value, target.readOnly(), target.varying());
	}


	private Ex call(Call call) {
		if (call.target() != null) {
			ShaderChecker owner = classTarget(call.target());

			if (owner != null) {
				List<MethodSym> overloads = owner.methods.get(call.name());

				if (overloads != null) {
					return userCall(call, overloads);
				}

				if (owner.records.containsKey(call.name())) {
					return recordCall(owner.records.get(call.name()), call.args(), call.pos());
				}

				error(call.pos(), owner.simpleName + " has no method " + call.name() + suggestion(call.name(), owner.methods.keySet()));
				return null;
			}

			if (unsentClass(call.target())) {
				return null;
			}

			Ex target = checkExpr(call.target(), null);

			if (target == null) {
				return null;
			}

			if (call.name().equals("length") && call.args().isEmpty() && (target.type() instanceof ArrayType || target.type() instanceof Vector)) {
				int length = target.type() instanceof ArrayType array ? array.length() : ((Vector) target.type()).size();
				Const value = Const.ofInt(length);
				return new Ex(ShType.INT, value.glsl(), value);
			}

			String hint = Builtins.FUNCTIONS.containsKey(call.name()) ? " (functions are called like " + call.name() + "(value, ...))" : "";
			error(call.pos(), target.type().name() + " has no method " + call.name() + hint);
			return null;
		}

		List<MethodSym> userMethods = methods.get(call.name());

		if (userMethods != null) {
			return userCall(call, userMethods);
		}

		if (ShType.builtin(call.name()) != null && !call.name().equals("Texture")) {
			return construct(call.name(), call.args(), call.pos());
		}

		if (records.containsKey(call.name())) {
			return recordCall(records.get(call.name()), call.args(), call.pos());
		}

		if (call.name().equals("write")) {
			return write(call);
		}

		if (call.name().equals("lightLevel") && call.args().size() == 2 && lightField(call.args().getFirst()) != null) {
			return lightLevel(call, lightField(call.args().getFirst()));
		}

		if (call.name().equals("lightLevel") && call.args().size() == 2 && methods.get("lightLevel") == null) {
			Ex type = checkExpr(call.args().getFirst(), ShType.INT);

			if (type != null && !type.type().equals(ShType.INT)) {
				error(call.args().getFirst().pos(), "lightLevel's first value is a @Light field (or a type number 0 to 7), like lightLevel(FIRE, f.worldPos)");
				return null;
			}
		}

		List<Builtins.Function> overloads = Builtins.FUNCTIONS.get(call.name());

		if (overloads != null) {
			return builtinCall(call, overloads);
		}

		Set<String> names = new HashSet<>(methods.keySet());
		names.addAll(Builtins.FUNCTIONS.keySet());
		error(call.pos(), "Unknown method " + call.name() + suggestion(call.name(), names));
		return null;
	}

	private Ex write(Call call) {
		if (!requireImport("write", call.pos())) {
			return null;
		}

		FieldSym output = call.args().size() == 2 && call.args().getFirst() instanceof Name name && fields.get(name.name()) instanceof FieldSym field
				&& field.kind == FieldSym.Kind.OUTPUT ? field : null;

		if (output == null) {
			error(call.pos(), "write(output, value) takes an @Output field of this file and a vec4");
			return null;
		}

		Ex value = convertOrError(checkExpr(call.args().get(1), ShType.VEC4), ShType.VEC4, call.args().get(1).pos());

		if (value == null) {
			return null;
		}

		if (current == null) {
			error(call.pos(), "write(...) can't be used in a constant");
			return null;
		}

		if (current.writeUse == null) {
			current.writeUse = call.pos();
		}

		current.outputsWritten.add(output);
		return new Ex(ShType.VOID, "SK_WRITE_" + output.glsl + "(" + stripParens(value.code()) + ")");
	}

	private FieldSym lightField(Expr expr) {
		FieldSym field = null;

		if (expr instanceof Name name && findLocal(name.name()) == null) {
			field = fields.get(name.name());
		} else if (expr instanceof Select select) {
			ShaderChecker owner = classTarget(select.target());
			field = owner == null ? null : owner.fields.get(select.name());
		}

		return field != null && field.lightGroups != null ? field : null;
	}

	private Ex lightLevel(Call call, FieldSym light) {
		if (!requireImport("lightLevel", call.pos())) {
			return null;
		}

		Ex position = convertOrError(checkExpr(call.args().get(1), ShType.VEC3), ShType.VEC3, call.args().get(1).pos());

		if (position == null) {
			return null;
		}

		if (current == null) {
			error(call.pos(), "lightLevel can't be used in a constant");
			return null;
		}

		current.helpers.add("sk_lightLevel");
		current.samplers.addAll(Builtins.LIGHT_SAMPLERS);
		current.usesLight = true;
		return new Ex(ShType.FLOAT, "sk_lightLevel(SK_LIGHT_" + light.glsl + ", " + stripParens(position.code()) + ")");
	}

	private List<Ex> args(List<Expr> exprs) {
		List<Ex> args = new ArrayList<>();
		boolean ok = true;

		for (Expr expr : exprs) {
			Ex arg = checkExpr(expr, null);
			args.add(arg);
			ok &= arg != null;
		}

		return ok ? args : null;
	}

	private static int fit(List<Ex> args, List<ShType> params) {
		if (args.size() != params.size()) {
			return -1;
		}

		int conversions = 0;

		for (int i = 0; i < args.size(); i++) {
			ShType from = args.get(i).type();
			ShType to = params.get(i);

			if (from.equals(to)) {
				continue;
			}

			if (convert(args.get(i), to) == null) {
				return -1;
			}

			conversions++;
		}

		return conversions;
	}

	private String argList(List<Ex> args, List<ShType> params) {
		StringBuilder code = new StringBuilder();

		for (int i = 0; i < args.size(); i++) {
			if (i > 0) {
				code.append(", ");
			}

			code.append(stripParens(convert(args.get(i), params.get(i)).code()));
		}

		return code.toString();
	}

	private static String typesOf(List<Ex> args) {
		List<String> names = new ArrayList<>();

		for (Ex arg : args) {
			names.add(arg.type().name());
		}

		return "(" + String.join(", ", names) + ")";
	}

	private Ex userCall(Call call, List<MethodSym> overloads) {
		List<Ex> args = args(call.args());

		if (args == null) {
			return null;
		}

		MethodSym best = null;
		int bestConversions = Integer.MAX_VALUE;

		for (MethodSym method : overloads) {
			int conversions = fit(args, method.params);

			if (conversions >= 0 && conversions < bestConversions) {
				best = method;
				bestConversions = conversions;
			}
		}

		if (best == null) {
			MethodSym first = overloads.getFirst();
			error(call.pos(), call.name() + " takes " + typesOf(first.params) + ", not " + typesOf(args));
			return null;
		}

		if (best.kind != MethodSym.Kind.HELPER) {
			error(call.pos(), best.describe() + " is run by the game and can't be called from code");
			return null;
		}

		if (current != null) {
			current.calls.putIfAbsent(best, call.pos());
		} else {
			error(call.pos(), "Constants can't call methods");
			return null;
		}

		return new Ex(best.result, best.glsl + "(" + argList(args, best.params) + ")");
	}

	private static String typesOf(Iterable<ShType> types) {
		List<String> names = new ArrayList<>();

		for (ShType type : types) {
			names.add(type.name());
		}

		return "(" + String.join(", ", names) + ")";
	}

	private Ex builtinCall(Call call, List<Builtins.Function> overloads) {
		List<Ex> args = args(call.args());

		if (args == null) {
			return null;
		}

		Builtins.Function best = null;
		int bestConversions = Integer.MAX_VALUE;

		for (Builtins.Function function : overloads) {
			int conversions = fit(args, function.params());

			if (conversions >= 0 && conversions < bestConversions) {
				best = function;
				bestConversions = conversions;
			}
		}

		if (best == null) {
			Set<String> options = new LinkedHashSet<>();

			for (Builtins.Function function : overloads) {
				options.add(typesOf(function.params()));
			}

			error(call.pos(), call.name() + " takes " + String.join(" or ", options.stream().limit(6).toList()) + ", not " + typesOf(args));
			return null;
		}

		if (best.library() && !requireImport(best.name(), call.pos())) {
			return null;
		}

		if (current == null) {
			Const folded = fold(best.name(), args, best.result());

			if (folded != null) {
				return new Ex(best.result(), folded.glsl(), folded);
			}

			if (best.restriction() != Builtins.ANY || best.helper() != null) {
				error(call.pos(), call.name() + " can't be used in a constant");
				return null;
			}

			return new Ex(best.result(), best.glsl() + "(" + argList(args, best.params()) + ")");
		}

		if (best.restriction() == Builtins.PIXELS) {
			usePixels(call.pos(), call.name() + (best.params().size() == 3 && best.name().equals("texture") ? " with a bias" : ""));
		} else if (best.restriction() == Builtins.PASS_ONLY) {
			usePass(call.pos(), call.name());
			current.samplers.add("SkDepth");
		}

		if (best.helper() != null) {
			current.helpers.add(best.helper());
		}

		current.samplers.addAll(best.samplers());
		current.usesLight |= Set.of("coloredLight", "lightLevel", "inLightVolume").contains(best.name());
		current.usesShadowMap |= best.name().equals("shadowPos");

		String name = best.glsl();

		if (name.equals("texture") && best.params().size() == 2) {
			name = "SK_TEXTURE";
		}

		Const folded = fold(best.name(), args, best.result());
		return new Ex(best.result(), name + "(" + argList(args, best.params()) + ")", folded);
	}

	private static Const fold(String name, List<Ex> args, ShType result) {
		for (Ex arg : args) {
			if (arg.value() == null || arg.type().base() != Base.FLOAT && arg.type().base() != Base.INT) {
				return null;
			}
		}

		if (!result.isBasic() || result instanceof Matrix) {
			return null;
		}

		DoubleUnaryOperator unary = switch (name) {
			case "radians" -> Math::toRadians;
			case "degrees" -> Math::toDegrees;
			case "sin" -> Math::sin;
			case "cos" -> Math::cos;
			case "tan" -> Math::tan;
			case "sqrt" -> Math::sqrt;
			case "abs" -> Math::abs;
			case "floor" -> Math::floor;
			case "ceil" -> Math::ceil;
			case "fract" -> x -> x - Math.floor(x);
			case "exp" -> Math::exp;
			case "log" -> Math::log;
			case "exp2" -> x -> Math.pow(2, x);
			case "log2" -> x -> Math.log(x) / Math.log(2);
			default -> null;
		};

		if (unary != null && args.size() == 1) {
			double[] values = args.getFirst().value().values().clone();

			for (int i = 0; i < values.length; i++) {
				values[i] = unary.applyAsDouble(values[i]);
			}

			return Const.of(result, values);
		}

		DoubleBinaryOperator binary = switch (name) {
			case "pow" -> Math::pow;
			case "min" -> Math::min;
			case "max" -> Math::max;
			case "mod" -> (a, b) -> a - b * Math.floor(a / b);
			default -> null;
		};

		if (binary != null && args.size() == 2) {
			double[] a = args.get(0).value().values();
			double[] b = args.get(1).value().values();
			double[] values = new double[result.components()];

			for (int i = 0; i < values.length; i++) {
				values[i] = binary.applyAsDouble(a[a.length == 1 ? 0 : i], b[b.length == 1 ? 0 : i]);
			}

			return Const.of(result, values);
		}

		if (name.equals("clamp") && args.size() == 3) {
			double[] x = args.get(0).value().values();
			double[] lo = args.get(1).value().values();
			double[] hi = args.get(2).value().values();
			double[] values = new double[result.components()];

			for (int i = 0; i < values.length; i++) {
				values[i] = Math.min(Math.max(x[i], lo[lo.length == 1 ? 0 : i]), hi[hi.length == 1 ? 0 : i]);
			}

			return Const.of(result, values);
		}

		if (name.equals("mix") && args.size() == 3 && args.get(2).type().base() == Base.FLOAT) {
			double[] a = args.get(0).value().values();
			double[] b = args.get(1).value().values();
			double[] t = args.get(2).value().values();
			double[] values = new double[result.components()];

			for (int i = 0; i < values.length; i++) {
				double f = t[t.length == 1 ? 0 : i];
				values[i] = a[i] + (b[i] - a[i]) * f;
			}

			return Const.of(result, values);
		}

		if (name.equals("normalize") && args.size() == 1) {
			double[] v = args.getFirst().value().values().clone();
			double length = 0;

			for (double c : v) {
				length += c * c;
			}

			length = Math.sqrt(length);

			for (int i = 0; i < v.length; i++) {
				v[i] /= length;
			}

			return Const.of(result, v);
		}

		return null;
	}

	private Ex construct(String typeName, List<Expr> exprs, Pos pos) {
		ShType type = ShType.builtin(typeName);
		List<Ex> args = args(exprs);

		if (args == null || type == null) {
			return null;
		}

		if (args.isEmpty()) {
			error(pos, typeName + "(...) needs values");
			return null;
		}

		for (Ex arg : args) {
			if (!arg.type().isBasic()) {
				error(pos, typeName + "(...) is made from numbers, vectors and matrices, not " + arg.type().name());
				return null;
			}
		}

		int total = 0;

		for (Ex arg : args) {
			total += arg.type().components();
		}

		int needed = type.components();

		switch (type) {
			case Scalar ignored -> {
				if (args.size() != 1) {
					error(pos, typeName + "(...) takes one value");
					return null;
				}
			}
			case Vector ignored -> {
				boolean single = args.size() == 1 && (args.getFirst().type() instanceof Scalar || total >= needed && !(args.getFirst().type() instanceof Matrix));

				if (!single && total != needed) {
					error(pos, typeName + " needs " + needed + " values, these are " + total + " " + typesOf(args));
					return null;
				}
			}
			case Matrix ignored -> {
				boolean single = args.size() == 1 && (args.getFirst().type() instanceof Scalar || args.getFirst().type() instanceof Matrix);

				if (!single && total != needed) {
					error(pos, typeName + " needs " + needed + " values (column by column), these are " + total);
					return null;
				}
			}
			default -> {
			}
		}

		StringBuilder code = new StringBuilder(type.glsl()).append('(');
		boolean known = true;

		for (int i = 0; i < args.size(); i++) {
			if (i > 0) {
				code.append(", ");
			}

			code.append(stripParens(args.get(i).code()));
			known &= args.get(i).value() != null;
		}

		code.append(')');
		Const value = known ? foldConstruct(type, args) : null;
		return new Ex(type, value != null && type instanceof Scalar ? value.glsl() : code.toString(), value);
	}

	private static Const foldConstruct(ShType type, List<Ex> args) {
		double[] values = new double[type.components()];
		Base base = type.base();

		if (args.size() == 1 && args.getFirst().type() instanceof Scalar) {
			double v = convertScalar(args.getFirst().value().scalar(), base);

			if (type instanceof Matrix matrix) {
				for (int i = 0; i < matrix.size(); i++) {
					values[i * matrix.size() + i] = v;
				}
			} else {
				java.util.Arrays.fill(values, v);
			}

			return Const.of(type, values);
		}

		if (args.size() == 1 && args.getFirst().type() instanceof Matrix from && type instanceof Matrix to) {
			for (int column = 0; column < to.size(); column++) {
				for (int row = 0; row < to.size(); row++) {
					values[column * to.size() + row] = column < from.size() && row < from.size()
							? args.getFirst().value().values()[column * from.size() + row] : column == row ? 1 : 0;
				}
			}

			return Const.of(type, values);
		}

		int at = 0;

		for (Ex arg : args) {
			for (double v : arg.value().values()) {
				if (at < values.length) {
					values[at++] = convertScalar(v, base);
				}
			}
		}

		return Const.of(type, values);
	}

	private static double convertScalar(double value, Base base) {
		return switch (base) {
			case FLOAT -> value;
			case INT -> (int) value;
			case BOOL -> value != 0 ? 1 : 0;
		};
	}

	private Ex recordCall(StructDef record, List<Expr> exprs, Pos pos) {
		List<Ex> args = args(exprs);

		if (args == null) {
			return null;
		}

		List<ShType> params = new ArrayList<>();

		for (StructField field : record.fields()) {
			params.add(field.type());
		}

		if (fit(args, params) < 0) {
			error(pos, record.name() + " takes " + typesOf(params) + ", not " + typesOf(args));
			return null;
		}

		return new Ex(new StructType(record), record.glslName() + "(" + argList(args, params) + ")");
	}

	private Ex newRecord(NewRecord create) {
		if (create.name().contains(".")) {
			ShType type = resolveTypeName(create.name(), create.pos());
			return type instanceof StructType struct ? recordCall(struct.def(), create.args(), create.pos()) : null;
		}

		StructDef record = records.get(create.name());

		if (record == null) {
			if (ShType.builtin(create.name()) != null) {
				error(create.pos(), "Write " + create.name() + "(...) without new");
			} else {
				error(create.pos(), "Unknown record " + create.name() + suggestion(create.name(), records.keySet()));
			}

			return null;
		}

		return recordCall(record, create.args(), create.pos());
	}

	private Ex newArray(NewArray create) {
		ShType element = resolveTypeName(create.element(), create.pos());

		if (element == null) {
			return null;
		}

		if (element == ShType.TEXTURE) {
			error(create.pos(), "There are no arrays of textures");
			return null;
		}

		if (create.values() != null) {
			return arrayOf(element, create.values(), create.pos());
		}

		Ex size = checkExpr(create.size(), ShType.INT);

		if (size == null) {
			return null;
		}

		if (!size.type().equals(ShType.INT) || size.value() == null) {
			error(create.size().pos(), "The length of an array is a fixed int (a number or a constant)");
			return null;
		}

		int length = size.value().intValue();

		if (length < 1 || length > MAX_ARRAY_LENGTH) {
			error(create.size().pos(), "Arrays have 1 to " + MAX_ARRAY_LENGTH + " values");
			return null;
		}

		ArrayType type = new ArrayType(element, length);
		return new Ex(type, zero(type));
	}


	private Ex unary(Unary unary) {
		Ex operand = checkExpr(unary.operand(), null);

		if (operand == null) {
			return null;
		}

		ShType type = operand.type();

		switch (unary.op()) {
			case "-", "+" -> {
				if (!type.isNumeric()) {
					error(unary.pos(), unary.op() + " needs a number, not " + type.name());
					return null;
				}

				Const value = null;

				if (operand.value() != null) {
					double[] values = operand.value().values().clone();

					if (unary.op().equals("-")) {
						for (int i = 0; i < values.length; i++) {
							values[i] = type.base() == Base.INT ? (int) -(int) values[i] : -values[i];
						}
					}

					value = Const.of(type, values);
				}

				String code = unary.op().equals("+") ? operand.code() : "(-" + operand.code() + ")";
				return new Ex(type, value != null && type instanceof Scalar ? value.glsl().startsWith("-") ? "(" + value.glsl() + ")" : value.glsl() : code, value);
			}
			case "!" -> {
				if (!type.equals(ShType.BOOL)) {
					error(unary.pos(), "! needs a bool, not " + type.name() + (type instanceof Vector ? " (use not(...) for vectors)" : ""));
					return null;
				}

				Const value = operand.value() == null ? null : Const.of(ShType.BOOL, operand.value().boolValue() ? 0 : 1);
				return new Ex(type, "(!" + operand.code() + ")", value);
			}
			case "~" -> {
				if (type.base() != Base.INT || type instanceof Matrix) {
					error(unary.pos(), "~ needs an int, not " + type.name());
					return null;
				}

				Const value = null;

				if (operand.value() != null) {
					double[] values = operand.value().values().clone();

					for (int i = 0; i < values.length; i++) {
						values[i] = ~(int) values[i];
					}

					value = Const.of(type, values);
				}

				return new Ex(type, "(~" + operand.code() + ")", value);
			}
			default -> {
				error(unary.pos(), "Unknown operator " + unary.op());
				return null;
			}
		}
	}

	private Ex binary(Binary binary) {
		Ex left = checkExpr(binary.left(), null);
		Ex right = checkExpr(binary.right(), null);

		if (left == null || right == null) {
			return null;
		}

		String op = binary.op();
		ShType type = binaryType(op, left.type(), right.type(), binary.pos());

		if (type == null) {
			return null;
		}

		boolean logical = op.equals("&&") || op.equals("||");
		boolean comparison = op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
		Ex l = left;
		Ex r = right;

		if (!logical && left.type().isNumeric() && right.type().isNumeric()) {
			if (left.type().base() == Base.INT && right.type().base() == Base.FLOAT) {
				l = convert(left, ShType.withBase(left.type(), Base.FLOAT));
			} else if (left.type().base() == Base.FLOAT && right.type().base() == Base.INT) {
				r = convert(right, ShType.withBase(right.type(), Base.FLOAT));
			}
		}

		Const value = (l.value() != null && r.value() != null) ? foldBinary(op, l, r, type, comparison) : null;

		if (value != null && op.equals("/") && type.base() == Base.INT && containsZero(r.value())) {
			error(binary.pos(), "Division by zero");
			return null;
		}

		String code = value != null && type instanceof Scalar ? scalarCode(value) : "(" + l.code() + " " + op + " " + r.code() + ")";
		return new Ex(type, code, value);
	}

	private static String scalarCode(Const value) {
		String glsl = value.glsl();
		return glsl.startsWith("-") ? "(" + glsl + ")" : glsl;
	}

	private static boolean containsZero(Const value) {
		for (double v : value.values()) {
			if (v == 0) {
				return true;
			}
		}

		return false;
	}

	private ShType binaryType(String op, ShType left, ShType right, Pos pos) {
		switch (op) {
			case "&&", "||" -> {
				if (!left.equals(ShType.BOOL) || !right.equals(ShType.BOOL)) {
					error(pos, op + " needs two bools, not " + left.name() + " and " + right.name());
					return null;
				}

				return ShType.BOOL;
			}
			case "==", "!=" -> {
				if (left == ShType.TEXTURE || right == ShType.TEXTURE || left == ShType.VOID || right == ShType.VOID) {
					error(pos, "Can't compare " + left.name() + " and " + right.name());
					return null;
				}

				if (!left.equals(right) && !(left.isNumeric() && right.isNumeric() && sameShape(left, right))) {
					error(pos, "Can't compare " + left.name() + " and " + right.name());
					return null;
				}

				return ShType.BOOL;
			}
			case "<", ">", "<=", ">=" -> {
				if (!(left instanceof Scalar && right instanceof Scalar && left.isNumeric() && right.isNumeric())) {
					error(pos, op + " compares two numbers, not " + left.name() + " and " + right.name()
							+ (left instanceof Vector || right instanceof Vector ? " (use lessThan(...) and friends for vectors)" : ""));
					return null;
				}

				return ShType.BOOL;
			}
			case "%", "&", "|", "^", "<<", ">>" -> {
				if (left.base() != Base.INT || right.base() != Base.INT || left instanceof Matrix || right instanceof Matrix) {
					String hint = op.equals("%") && (left.base() == Base.FLOAT || right.base() == Base.FLOAT) ? " (use mod(a, b) for decimals)" : "";
					error(pos, op + " needs ints, not " + left.name() + " and " + right.name() + hint);
					return null;
				}

				if (op.equals("<<") || op.equals(">>")) {
					if (right instanceof Vector && !(left instanceof Vector)) {
						error(pos, "Can't shift an int by a vector");
						return null;
					}

					return left;
				}

				return arithmeticShape(left, right, op, pos);
			}
			case "+", "-", "*", "/" -> {
				if (!left.isNumeric() || !right.isNumeric()) {
					error(pos, op + " needs numbers, not " + left.name() + " and " + right.name());
					return null;
				}

				Base base = left.base() == Base.FLOAT || right.base() == Base.FLOAT ? Base.FLOAT : Base.INT;

				if (op.equals("*")) {
					if (left instanceof Matrix matrix && right instanceof Vector vector) {
						return vector.size() == matrix.size() && vector.kind() != Base.BOOL ? matrix.column() : mismatch(op, left, right, pos);
					}

					if (left instanceof Vector vector && right instanceof Matrix matrix) {
						return vector.size() == matrix.size() ? matrix.column() : mismatch(op, left, right, pos);
					}
				}

				ShType l = ShType.withBase(left, base);
				ShType r = ShType.withBase(right, base);
				return arithmeticShape(l, r, op, pos);
			}
			default -> {
				error(pos, "Unknown operator " + op);
				return null;
			}
		}
	}

	private ShType arithmeticShape(ShType left, ShType right, String op, Pos pos) {
		if (left.equals(right)) {
			return left;
		}

		if (left instanceof Scalar && left.base() == right.base()) {
			return right;
		}

		if (right instanceof Scalar && left.base() == right.base()) {
			return left;
		}

		return mismatch(op, left, right, pos);
	}

	private ShType mismatch(String op, ShType left, ShType right, Pos pos) {
		error(pos, "Can't use " + op + " on " + left.name() + " and " + right.name());
		return null;
	}

	private static boolean sameShape(ShType a, ShType b) {
		return a instanceof Scalar && b instanceof Scalar || a instanceof Vector va && b instanceof Vector vb && va.size() == vb.size();
	}

	private static Const foldBinary(String op, Ex left, Ex right, ShType type, boolean comparison) {
		double[] a = left.value().values();
		double[] b = right.value().values();
		ShType.Base base = left.type().base();

		if (comparison) {
			boolean result = switch (op) {
				case "==" -> java.util.Arrays.equals(a, b);
				case "!=" -> !java.util.Arrays.equals(a, b);
				case "<" -> a[0] < b[0];
				case ">" -> a[0] > b[0];
				case "<=" -> a[0] <= b[0];
				default -> a[0] >= b[0];
			};
			return Const.of(ShType.BOOL, result ? 1 : 0);
		}

		if (left.type() instanceof Matrix || right.type() instanceof Matrix) {
			if (op.equals("*") && !(left.type() instanceof Scalar) && !(right.type() instanceof Scalar)) {
				return null;
			}
		}

		double[] values = new double[type.components()];

		for (int i = 0; i < values.length; i++) {
			double x = a[a.length == 1 ? 0 : i];
			double y = b[b.length == 1 ? 0 : i];
			values[i] = switch (op) {
				case "+" -> x + y;
				case "-" -> x - y;
				case "*" -> x * y;
				case "/" -> base == Base.INT ? (y == 0 ? 0 : (int) x / (int) y) : x / y;
				case "%" -> y == 0 ? 0 : (int) x % (int) y;
				case "&" -> (int) x & (int) y;
				case "|" -> (int) x | (int) y;
				case "^" -> (int) x ^ (int) y;
				case "<<" -> (int) x << (int) y;
				case ">>" -> (int) x >> (int) y;
				case "&&" -> x != 0 && y != 0 ? 1 : 0;
				case "||" -> x != 0 || y != 0 ? 1 : 0;
				default -> 0;
			};

			if (type.base() == Base.INT) {
				values[i] = (int) (long) values[i];
			} else if (type.base() == Base.FLOAT) {
				values[i] = (float) values[i];
			}
		}

		return Const.of(type, values);
	}

	private Ex assign(Assign assign) {
		Ex target = checkExpr(assign.target(), null);

		if (target == null) {
			checkExpr(assign.value(), null);
			return null;
		}

		if (!writable(target, assign.target().pos())) {
			checkExpr(assign.value(), null);
			return null;
		}

		Ex value = checkExpr(assign.value(), target.type());

		if (value == null) {
			return null;
		}

		if (assign.op().equals("=")) {
			Ex converted = convertOrError(value, target.type(), assign.value().pos());
			return converted == null ? null : new Ex(target.type(), "(" + target.code() + " = " + stripParens(converted.code()) + ")");
		}

		String op = assign.op().substring(0, assign.op().length() - 1);
		ShType result = binaryType(op, target.type(), value.type(), assign.pos());

		if (result == null) {
			return null;
		}

		if (!result.equals(target.type())) {
			error(assign.pos(), target.type().name() + " " + assign.op() + " " + value.type().name() + " gives a " + result.name() + ", which doesn't fit");
			return null;
		}

		Ex converted = value.type().base() == Base.INT && target.type().base() == Base.FLOAT ? convert(value, ShType.withBase(value.type(), Base.FLOAT)) : value;
		return new Ex(target.type(), "(" + target.code() + " " + assign.op() + " " + stripParens(converted.code()) + ")");
	}

	private boolean writable(Ex target, Pos pos) {
		if (target.varying() != null) {
			if (current != null && current.varyingWrite == null) {
				current.varyingWrite = pos;
				current.varyingWriteName = target.varying().name;
			}

			return true;
		}

		if (target.readOnly() != null) {
			error(pos, "Can't change this: " + target.readOnly());
			return false;
		}

		return true;
	}

	private Ex incDec(IncDec incDec) {
		Ex target = checkExpr(incDec.target(), null);

		if (target == null) {
			return null;
		}

		if (!target.type().isNumeric()) {
			error(incDec.pos(), (incDec.increment() ? "++" : "--") + " needs a number, not " + target.type().name());
			return null;
		}

		if (!writable(target, incDec.target().pos())) {
			return null;
		}

		String op = incDec.increment() ? "++" : "--";
		return new Ex(target.type(), incDec.prefix() ? "(" + op + target.code() + ")" : "(" + target.code() + op + ")");
	}

	private Ex conditional(Conditional conditional) {
		Ex condition = condition(conditional.condition());
		Ex then = checkExpr(conditional.then(), null);
		Ex otherwise = checkExpr(conditional.otherwise(), null);

		if (condition == null || then == null || otherwise == null) {
			return null;
		}

		ShType type = then.type();

		if (!then.type().equals(otherwise.type())) {
			Ex a = convert(then, otherwise.type());
			Ex b = convert(otherwise, then.type());

			if (a != null) {
				then = a;
				type = otherwise.type();
			} else if (b != null) {
				otherwise = b;
			} else {
				error(conditional.pos(), "Both sides of ? : must be the same type, not " + then.type().name() + " and " + otherwise.type().name());
				return null;
			}
		}

		if (type == ShType.TEXTURE || type == ShType.VOID) {
			error(conditional.pos(), "? : can't choose between " + type.name() + " values");
			return null;
		}

		Const value = condition.value() == null ? null : condition.value().boolValue() ? then.value() : otherwise.value();
		return new Ex(type, "(" + condition.code() + " ? " + then.code() + " : " + otherwise.code() + ")", value);
	}


	private void checkReachability() {
		for (MethodSym method : methodOrder) {
			if (method.code == null) {
				continue;
			}

			List<MethodSym> path = new ArrayList<>();

			if (recursion(method, path, new HashSet<>())) {
				error(method.decl.pos(), "Shaders can't call themselves in a loop: " + String.join(" -> ", path.stream().map(m -> m.name).toList()));
				return;
			}
		}

		for (MethodSym root : methodOrder) {
			if (root.kind == MethodSym.Kind.HELPER || root.code == null) {
				continue;
			}

			for (MethodSym method : reachable(root)) {
				String via = method == root ? "" : " (" + method.name + " is used by " + root.describe() + ")";

				boolean shadow = root.programs.contains(Program.SHADOW);

				if (shadow && method.shadowUse != null) {
					error(method.shadowUse, method.shadowWhat + " can't be read by SHADOW hooks: they draw the shadow map" + via);
				}

				switch (root.kind) {
					case VERTEX -> {
						if (method.pixelsUse != null) {
							error(method.pixelsUse, method.pixelsWhat + " only works in @Fragment hooks and passes" + via);
						}

						if (method.passUse != null) {
							error(method.passUse, method.passWhat + " only works in passes" + via);
						}

						if (method.sourceUse != null) {
							error(method.sourceUse, "SOURCE only works in @Fragment hooks" + via);
						}

						if (method.writeUse != null) {
							error(method.writeUse, "write(...) only works in @Fragment hooks" + via);
						}
					}
					case FRAGMENT -> {
						if (method.passUse != null) {
							error(method.passUse, method.passWhat + " only works in passes" + via);
						}

						if (method.varyingWrite != null) {
							error(method.varyingWrite, method.varyingWriteName + " is a @Varying: set it in a @Vertex hook, read it in @Fragment hooks" + via);
						}

						if (method.sourceUse != null && (root.programs.contains(Program.SKY) || root.programs.contains(Program.CLOUDS))) {
							error(method.sourceUse, "SOURCE is the texture being drawn, and SKY and CLOUDS hooks have none" + via);
						}

						if (method.writeUse != null && shadow) {
							error(method.writeUse, "SHADOW hooks can't write(...) outputs; they only draw the shadow map" + via);
						}
					}
					case PASS -> {
						if (method.varyingWrite != null || method.varyingRead != null) {
							Pos pos = method.varyingWrite != null ? method.varyingWrite : method.varyingRead;
							String name = method.varyingWrite != null ? method.varyingWriteName : method.varyingReadName;
							error(pos, name + " is a @Varying, which only exists in @Vertex and @Fragment hooks" + via);
						}

						if (method.sourceUse != null) {
							error(method.sourceUse, "SOURCE only works in @Fragment hooks" + via);
						}

						if (method.writeUse != null) {
							error(method.writeUse, "write(...) only works in @Fragment hooks; a pass returns its color" + via);
						}
					}
					default -> {
					}
				}
			}
		}

		checkShadowFallback();
	}

	private void checkShadowFallback() {
		boolean shadowVertex = false;
		boolean usesShadows = false;

		for (MethodSym method : methodOrder) {
			shadowVertex |= method.kind == MethodSym.Kind.VERTEX && method.programs.contains(Program.SHADOW);
			usesShadows |= method.programs.contains(Program.SHADOW);

			if (method.code != null && method.kind != MethodSym.Kind.HELPER) {
				for (MethodSym reached : reachable(method)) {
					usesShadows |= reached.usesShadowMap;
				}
			}
		}

		if (shadowVertex || !usesShadows) {
			return;
		}

		for (MethodSym root : methodOrder) {
			if (root.kind != MethodSym.Kind.VERTEX || root.code == null
					|| java.util.Collections.disjoint(root.programs, EnumSet.of(Program.TERRAIN, Program.BLOCK, Program.ENTITY, Program.ITEM))) {
				continue;
			}

			for (MethodSym method : reachable(root)) {
				if (method.shadowUse != null) {
					error(method.shadowUse, method.shadowWhat + " can't be read here: without a @Vertex(SHADOW) hook, " + root.describe()
							+ " also moves things in the shadow map. Add a @Vertex(SHADOW) hook");
				}
			}
		}
	}

	private boolean recursion(MethodSym method, List<MethodSym> path, Set<MethodSym> done) {
		if (path.contains(method)) {
			path.add(method);
			return true;
		}

		if (!done.add(method)) {
			return false;
		}

		path.add(method);

		for (MethodSym callee : method.calls.keySet()) {
			if (recursion(callee, path, done)) {
				return true;
			}
		}

		path.removeLast();
		return false;
	}

	static List<MethodSym> reachable(MethodSym root) {
		List<MethodSym> order = new ArrayList<>();
		collect(root, order, new HashSet<>());
		return order;
	}

	private static void collect(MethodSym method, List<MethodSym> order, Set<MethodSym> seen) {
		if (!seen.add(method)) {
			return;
		}

		for (MethodSym callee : method.calls.keySet()) {
			collect(callee, order, seen);
		}

		order.add(method);
	}


	static String suggestion(String name, Iterable<String> candidates) {
		String best = null;
		int bestDistance = Integer.MAX_VALUE;

		for (String candidate : candidates) {
			int distance = distance(name.toLowerCase(Locale.ROOT), candidate.toLowerCase(Locale.ROOT));

			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}

		return best != null && bestDistance <= Math.max(1, name.length() / 3) ? " (did you mean " + best + "?)" : "";
	}

	private static int distance(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] row = new int[b.length() + 1];

		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}

		for (int i = 1; i <= a.length(); i++) {
			row[0] = i;

			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				row[j] = Math.min(Math.min(row[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}

			int[] swap = previous;
			previous = row;
			row = swap;
		}

		return previous[b.length()];
	}
}
