package me.skaffy.client.shader.lang;

import java.util.List;
import java.util.Map;

import me.skaffy.client.shader.lang.Builtins.Blend;
import me.skaffy.client.shader.lang.Builtins.Filter;
import me.skaffy.client.shader.lang.Builtins.Format;
import me.skaffy.client.shader.lang.Builtins.Program;
import me.skaffy.client.shader.lang.Builtins.Stage;
import me.skaffy.client.shader.lang.Builtins.Wrap;

public record ShaderModule(String className, List<Uniform> uniforms, int uniformBlockSize, List<TextureSource> textures, List<Target> targets,
		List<Output> outputs, Map<Integer, List<String>> materials, List<Light> lights, List<Light> filters, List<Pass> passes, Map<Program, Hook> hooks,
		Map<Program, Hook> shadowHooks, Settings settings, boolean usesShadows, boolean usesSolid, boolean usesLight, java.util.Set<String> dependencies) {
	public record Setting(double value, String uniform) {
	}

	public record Settings(Setting sunPath, boolean hdr, boolean taa, Setting shadowResolution, Setting shadowDistance, int lightRange) {
	}

	public static final String PASS_VERTEX = """
			#version 330
			#extension GL_ARB_separate_shader_objects : require

			layout(location = 0) out vec2 sk_passUv;

			void main() {
				vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
				gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
				sk_passUv = uv;
			}
			""";

	public static final String OUTPUTS_MARKER = "//@sk_outputs";

	public record Uniform(String name, String typeName, ShType.Base base, int size, boolean matrix, int offset, double[] defaults) {
		public int components() {
			return matrix ? size * size : size;
		}
	}

	public record TextureSource(String name, String sampler, String source, Filter filter, Wrap wrap) {
	}

	public record Target(String name, String sampler, float scale, int width, int height, Format format, boolean persistent, Filter filter) {
	}

	public record Output(String name, String sampler, Format format, Filter filter) {
	}

	public record Light(String name, List<LightGroup> groups, double[] color, String uniform) {
	}

	public record LightGroup(List<String> blocks, int level, String levelFrom) {
	}

	public record Pass(String name, Stage stage, String output, Blend blend, String fragment, List<String> samplers) {
		public boolean reads(String sampler) {
			return samplers.contains(sampler);
		}
	}

	public record Hook(Program program, String vertexInclude, String fragmentInclude, boolean hasVertex, boolean hasFragment, boolean fog, List<String> samplers,
			List<String> outputs) {
		public String fragmentInclude(Map<String, Integer> locations, List<Output> all) {
			StringBuilder on = new StringBuilder("#ifdef SK_OUTPUTS\n");
			StringBuilder off = new StringBuilder("#else\n");
			StringBuilder init = new StringBuilder();

			for (Output output : all) {
				String name = output.sampler();
				Integer location = locations.get(name);

				if (location != null && outputs.contains(name)) {
					on.append("layout(location = ").append(location).append(") out vec4 SkOut_").append(name).append(";\n");
					on.append("#define SK_WRITE_").append(name).append("(v) SkOut_").append(name).append(" = (v)\n");
					init.append("SkOut_").append(name).append(" = vec4(0.0); ");
				} else {
					on.append("#define SK_WRITE_").append(name).append("(v)\n");
				}

				off.append("#define SK_WRITE_").append(name).append("(v)\n");
			}

			on.append("#define SK_OUTPUTS_INIT ").append(init).append('\n');
			off.append("#define SK_OUTPUTS_INIT\n#endif\n");
			return fragmentInclude.replace(OUTPUTS_MARKER, on.append(off).toString());
		}
	}

	public boolean usesMaterials() {
		return !materials.isEmpty();
	}
}
