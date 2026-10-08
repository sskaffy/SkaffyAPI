package me.skaffy.client.shader.lang;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import me.skaffy.client.shader.lang.Builtins.Program;
import me.skaffy.client.shader.lang.ShType.Matrix;
import me.skaffy.client.shader.lang.ShType.StructDef;
import me.skaffy.client.shader.lang.ShType.StructField;
import me.skaffy.client.shader.lang.ShType.StructType;
import me.skaffy.client.shader.lang.ShaderChecker.Checked;
import me.skaffy.client.shader.lang.ShaderChecker.FieldSym;
import me.skaffy.client.shader.lang.ShaderChecker.MethodSym;

final class ShaderAssembler {
	private static final String HEADER = """
			#version 330
			#extension GL_ARB_separate_shader_objects : require

			""";
	static final List<Program> SHADOW_KINDS = List.of(Program.TERRAIN, Program.BLOCK, Program.ENTITY, Program.ITEM);

	private final Checked checked;
	private final String uniformBlock;
	private final String lightDefines;

	private ShaderAssembler(Checked checked, String uniformBlock, String lightDefines) {
		this.checked = checked;
		this.uniformBlock = uniformBlock;
		this.lightDefines = lightDefines;
	}

	static ShaderModule assemble(Checked checked, java.util.Set<String> dependencies) {
		List<ShaderModule.Uniform> uniforms = new ArrayList<>();
		StringBuilder block = new StringBuilder();
		int offset = 0;

		for (FieldSym field : checked.fields()) {
			if (field.kind != FieldSym.Kind.UNIFORM) {
				continue;
			}

			ShType type = field.type;
			boolean matrix = type instanceof Matrix;
			int size = matrix ? ((Matrix) type).size() : type.components();
			int align = matrix || size >= 3 ? 16 : size == 2 ? 8 : 4;
			int bytes = matrix ? 16 * size : 4 * size;
			offset = (offset + align - 1) / align * align;
			uniforms.add(new ShaderModule.Uniform(field.serverName, type.name(), type.base(), size, matrix, offset, field.value.values().clone()));
			offset += bytes;
			boolean bool = type.base() == ShType.Base.BOOL;
			String glslType = bool ? ShType.withBase(type, ShType.Base.INT).glsl() : type.glsl();
			block.append('\t').append(glslType).append(' ').append(field.glsl).append(bool ? "_i" : "").append(";\n");
		}

		int blockSize = uniforms.isEmpty() ? 0 : (offset + 15) / 16 * 16;
		String uniformBlock = uniforms.isEmpty() ? "" : "layout(std140) uniform SkUniforms {\n" + block + "};\n\n";

		List<ShaderModule.TextureSource> textures = new ArrayList<>();
		List<ShaderModule.Target> targets = new ArrayList<>();
		List<ShaderModule.Output> outputs = new ArrayList<>();
		List<ShaderModule.Light> lights = new ArrayList<>();
		List<ShaderModule.Light> filters = new ArrayList<>();
		Map<Integer, List<String>> materials = new TreeMap<>();
		StringBuilder lightDefines = new StringBuilder();

		for (FieldSym field : checked.fields()) {
			switch (field.kind) {
				case TEXTURE -> textures.add(new ShaderModule.TextureSource(field.serverName, field.glsl, field.source, field.filter, field.wrap));
				case TARGET -> targets.add(new ShaderModule.Target(field.name, field.glsl, field.scale, field.width, field.height, field.format, field.persistent, field.filter));
				case OUTPUT -> outputs.add(new ShaderModule.Output(field.name, field.glsl, field.format, field.filter));
				case CONST -> {
					if (field.blocks != null) {
						materials.computeIfAbsent(field.value.intValue(), key -> new ArrayList<>()).addAll(field.blocks);
					}
				}
				default -> {
				}
			}

			boolean uniform = field.kind == FieldSym.Kind.UNIFORM;
			double[] color = uniform || field.value == null ? null : field.value.values().clone();

			if (field.lightGroups != null) {
				lightDefines.append("#define SK_LIGHT_").append(field.glsl).append(' ').append(lights.size()).append('\n');
				lights.add(new ShaderModule.Light(field.serverName, List.copyOf(field.lightGroups), color, uniform ? field.serverName : null));
			}

			if (field.filterGroups != null) {
				filters.add(new ShaderModule.Light(field.serverName, List.copyOf(field.filterGroups), color, uniform ? field.serverName : null));
			}
		}

		ShaderAssembler assembler = new ShaderAssembler(checked, uniformBlock, lightDefines.toString());
		List<ShaderModule.Pass> passes = new ArrayList<>();
		Map<Program, MethodSym> vertexHooks = new EnumMap<>(Program.class);
		Map<Program, MethodSym> fragmentHooks = new EnumMap<>(Program.class);
		boolean usesShadows = false;
		boolean usesSolid = false;
		boolean usesLight = !lights.isEmpty();

		for (MethodSym method : checked.methods()) {
			switch (method.kind) {
				case PASS -> passes.add(assembler.pass(method));
				case VERTEX -> method.programs.forEach(program -> vertexHooks.put(program, method));
				case FRAGMENT -> method.programs.forEach(program -> fragmentHooks.put(program, method));
				default -> {
				}
			}

			if (method.kind != MethodSym.Kind.HELPER && method.code != null) {
				usesShadows |= method.programs.contains(Program.SHADOW);

				for (MethodSym reached : ShaderChecker.reachable(method)) {
					usesShadows |= reached.usesShadowMap;
					usesSolid |= reached.usesSolid;
					usesLight |= reached.usesLight;
				}
			}
		}

		Map<Program, ShaderModule.Hook> hooks = new EnumMap<>(Program.class);

		for (Program program : Program.values()) {
			MethodSym vertex = vertexHooks.get(program);
			MethodSym fragment = fragmentHooks.get(program);

			if (program != Program.SHADOW && (vertex != null || fragment != null)) {
				hooks.put(program, assembler.hook(program, vertex, fragment));
			}
		}

		ShaderModule.Settings settings = settings(checked);
		usesShadows |= settings.shadowResolution() != null || settings.shadowDistance() != null;
		Map<Program, ShaderModule.Hook> shadowHooks = new EnumMap<>(Program.class);

		if (usesShadows) {
			MethodSym shadowVertex = vertexHooks.get(Program.SHADOW);
			MethodSym shadowFragment = fragmentHooks.get(Program.SHADOW);

			for (Program kind : SHADOW_KINDS) {
				shadowHooks.put(kind, assembler.hook(Program.SHADOW, shadowVertex != null ? shadowVertex : vertexHooks.get(kind), shadowFragment));
			}
		}

		return new ShaderModule(checked.className(), List.copyOf(uniforms), blockSize, List.copyOf(textures), List.copyOf(targets), List.copyOf(outputs),
				materials, List.copyOf(lights), List.copyOf(filters), List.copyOf(passes), hooks, shadowHooks, settings, usesShadows, usesSolid, usesLight,
				java.util.Set.copyOf(dependencies));
	}

	private static ShaderModule.Settings settings(Checked checked) {
		ShaderModule.Setting sunPath = null;
		boolean hdr = false;
		boolean taa = false;
		ShaderModule.Setting shadowResolution = null;
		ShaderModule.Setting shadowDistance = null;
		int lightRange = 0;

		for (FieldSym field : checked.fields()) {
			if (!field.serverName.equals(field.name) || !ShaderChecker.SETTINGS.contains(field.name)) {
				continue;
			}

			boolean uniform = field.kind == FieldSym.Kind.UNIFORM;
			double value = field.value == null ? 0 : field.value.scalar();
			ShaderModule.Setting setting = new ShaderModule.Setting(value, uniform ? field.name : null);

			switch (field.name) {
				case ShaderChecker.SUN_PATH -> sunPath = setting;
				case ShaderChecker.HDR -> hdr = value != 0;
				case ShaderChecker.TAA -> taa = value != 0;
				case ShaderChecker.SHADOW_RESOLUTION -> shadowResolution = setting;
				case ShaderChecker.SHADOW_DISTANCE -> shadowDistance = setting;
				case ShaderChecker.LIGHT_RANGE -> lightRange = (int) value;
				default -> {
				}
			}
		}

		return new ShaderModule.Settings(sunPath, hdr, taa, shadowResolution, shadowDistance, lightRange);
	}

	static String frameBlock() {
		StringBuilder code = new StringBuilder("layout(std140) uniform SkFrame {\n");

		for (Builtins.FrameField field : Builtins.FRAME) {
			code.append('\t').append(field.glslType()).append(' ').append(field.name()).append(";\n");
		}

		return code.append("};\n\n").toString();
	}

	private static String zeroOf(ShType type) {
		return switch (type) {
			case ShType.Scalar scalar -> Const.scalarGlsl(scalar.kind(), 0);
			case ShType.Vector vector -> vector.glsl() + "(" + Const.scalarGlsl(vector.kind(), 0) + ")";
			case Matrix matrix -> matrix.glsl() + "(0.0)";
			default -> throw new IllegalArgumentException("Varying of type " + type.name());
		};
	}

	private static String struct(StructDef def) {
		StringBuilder code = new StringBuilder("struct ").append(def.glslName()).append(" {\n");

		for (StructField field : def.fields()) {
			code.append('\t').append(field.type().glsl()).append(' ').append(field.glslName()).append(";\n");
		}

		return code.append("};\n\n").toString();
	}

	private List<StructDef> orderedRecords() {
		List<StructDef> order = new ArrayList<>();

		for (StructDef def : checked.records()) {
			addRecord(def, order);
		}

		return order;
	}

	private static void addRecord(StructDef def, List<StructDef> order) {
		if (order.contains(def)) {
			return;
		}

		for (StructField field : def.fields()) {
			if (field.type() instanceof StructType struct && !struct.def().builtin()) {
				addRecord(struct.def(), order);
			}
		}

		order.add(def);
	}

	private void common(StringBuilder code, List<MethodSym> methods, Set<String> samplers) {
		code.append(frameBlock());
		code.append(uniformBlock);
		code.append(lightDefines);

		for (String sampler : samplers) {
			code.append("uniform sampler2D ").append(sampler).append(";\n");
		}

		if (!samplers.isEmpty()) {
			code.append('\n');
		}

		for (StructDef def : orderedRecords()) {
			code.append(struct(def));
		}

		for (FieldSym field : checked.fields()) {
			if (field.kind == FieldSym.Kind.CONST) {
				String declaration = field.type instanceof ShType.ArrayType array
						? "const " + array.glsl() + " " + field.glsl
						: "const " + field.type.glsl() + " " + field.glsl;
				code.append(declaration).append(" = ").append(field.value != null && field.type.isBasic() ? field.value.glsl() : field.initCode).append(";\n");
			}
		}

		code.append('\n');
		Set<String> helpers = new LinkedHashSet<>();

		for (MethodSym method : methods) {
			for (String helper : method.helpers) {
				addHelper(helper, helpers);
			}
		}

		for (String helper : Builtins.HELPERS.keySet()) {
			if (helpers.contains(helper)) {
				code.append(Builtins.HELPERS.get(helper)).append('\n');
			}
		}
	}

	private static void addHelper(String helper, Set<String> helpers) {
		if (helpers.add(helper)) {
			for (String need : Builtins.HELPER_NEEDS.getOrDefault(helper, List.of())) {
				addHelper(need, helpers);
			}
		}
	}

	private static void methods(StringBuilder code, List<MethodSym> methods) {
		for (MethodSym method : methods) {
			code.append(method.prototype).append('\n');
		}

		code.append('\n');

		for (MethodSym method : methods) {
			code.append(method.code).append('\n');
		}
	}

	private static Set<String> samplers(List<MethodSym> methods) {
		Set<String> samplers = new LinkedHashSet<>();

		for (MethodSym method : methods) {
			samplers.addAll(method.samplers);
		}

		return samplers;
	}

	private ShaderModule.Pass pass(MethodSym pass) {
		List<MethodSym> methods = ShaderChecker.reachable(pass);
		Set<String> samplers = samplers(methods);
		StringBuilder code = new StringBuilder(HEADER);
		code.append("#define SK_TEXTURE(t, uv) texture(t, uv)\n\n");
		common(code, methods, samplers);
		methods(code, methods);
		code.append("layout(location = 0) in vec2 sk_passUv;\n");
		code.append("layout(location = 0) out vec4 sk_fragColor;\n\n");
		code.append("void main() {\n\tsk_fragColor = ").append(pass.glsl).append(pass.takesUv ? "(sk_passUv)" : "()").append(";\n}\n");
		return new ShaderModule.Pass(pass.name, pass.stage, pass.output == null ? null : pass.output.name, pass.blend, code.toString(), List.copyOf(samplers));
	}

	private void varyings(StringBuilder code, String direction) {
		for (FieldSym field : checked.fields()) {
			if (field.kind == FieldSym.Kind.VARYING) {
				code.append("layout(location = ").append(field.location).append(") ").append(field.flat ? "flat " : "")
						.append(direction).append(' ').append(field.type.glsl()).append(' ').append(field.glsl).append(";\n");
			}
		}

		code.append('\n');
	}

	private ShaderModule.Hook hook(Program program, MethodSym vertex, MethodSym fragment) {
		List<MethodSym> vertexMethods = vertex == null ? List.of() : ShaderChecker.reachable(vertex);
		List<MethodSym> fragmentMethods = fragment == null ? List.of() : ShaderChecker.reachable(fragment);
		Set<String> samplers = new LinkedHashSet<>(samplers(vertexMethods));
		samplers.addAll(samplers(fragmentMethods));
		Set<String> outputs = new LinkedHashSet<>();

		for (MethodSym method : fragmentMethods) {
			method.outputsWritten.forEach(output -> outputs.add(output.glsl));
		}

		StringBuilder vertexCode = new StringBuilder("#define SK_TEXTURE(t, uv) textureLod(t, uv, 0.0)\n\n");
		vertexCode.append(struct(Builtins.VERTEX));
		common(vertexCode, vertexMethods, samplers(vertexMethods));
		varyings(vertexCode, "out");
		methods(vertexCode, vertexMethods);
		vertexCode.append("const bool sk_hasVertexHook = ").append(vertex != null).append(";\n\n");
		vertexCode.append("void sk_vertexHook(inout SkVertex v) {\n");

		for (FieldSym field : checked.fields()) {
			if (field.kind == FieldSym.Kind.VARYING) {
				vertexCode.append('\t').append(field.glsl).append(" = ").append(zeroOf(field.type)).append(";\n");
			}
		}

		if (vertex != null) {
			vertexCode.append('\t').append(vertex.glsl).append("(v);\n");
		}

		vertexCode.append("}\n");

		StringBuilder fragmentCode = new StringBuilder("#define SK_TEXTURE(t, uv) texture(t, uv)\n\n");
		fragmentCode.append(ShaderModule.OUTPUTS_MARKER).append("\n\n");
		fragmentCode.append(struct(Builtins.FRAGMENT));
		common(fragmentCode, fragmentMethods, samplers(fragmentMethods));
		varyings(fragmentCode, "in");
		methods(fragmentCode, fragmentMethods);
		fragmentCode.append("const bool sk_hasFragmentHook = ").append(fragment != null).append(";\n\n");
		fragmentCode.append("vec4 sk_fragmentHook(SkFragment f) {\n\tSK_OUTPUTS_INIT\n");
		fragmentCode.append(fragment != null ? "\treturn " + fragment.glsl + "(f);\n" : "\treturn f.sk_vanilla;\n");
		fragmentCode.append("}\n\n#define SK_FOG ").append(fragment == null || fragment.fog ? 1 : 0).append('\n');

		return new ShaderModule.Hook(program, vertexCode.toString(), fragmentCode.toString(), vertex != null, fragment != null,
				fragment == null || fragment.fog, List.copyOf(samplers), List.copyOf(outputs));
	}
}
