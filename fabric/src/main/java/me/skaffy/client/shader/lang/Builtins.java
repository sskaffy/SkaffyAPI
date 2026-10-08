package me.skaffy.client.shader.lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.skaffy.client.shader.lang.ShType.StructDef;
import me.skaffy.client.shader.lang.ShType.StructField;

public final class Builtins {
	public static final String LIBRARY = "skaffy.shader";

	enum Context {
		PASS,
		VERTEX,
		FRAGMENT
	}

	static final int ANY = 0;
	static final int PIXELS = 1;
	static final int PASS_ONLY = 2;
	static final int NO_SHADOW = 4;
	static final int FRAGMENT_HOOKS = 8;

	record Value(String name, ShType type, String glsl, int restriction, boolean library) {
	}

	record Function(String name, List<ShType> params, ShType result, String glsl, int restriction, String helper, boolean library, List<String> samplers) {
	}

	static final List<String> LIGHT_SAMPLERS = List.of("SkLightA", "SkLightB", "SkLightTint");
	static final int MAX_LIGHT_TYPES = 8;
	static final int MAX_LIGHT_FILTERS = 32;

	public record FrameField(String glslType, String name) {
	}

	public static final List<FrameField> FRAME = List.of(
			new FrameField("mat4", "sk_viewMatrix"),
			new FrameField("mat4", "sk_projectionMatrix"),
			new FrameField("mat4", "sk_inverseViewMatrix"),
			new FrameField("mat4", "sk_inverseProjectionMatrix"),
			new FrameField("mat4", "sk_previousViewMatrix"),
			new FrameField("mat4", "sk_previousProjectionMatrix"),
			new FrameField("mat4", "sk_shadowMatrix"),
			new FrameField("vec4", "sk_fogColor"),
			new FrameField("vec4", "sk_shadeA"),
			new FrameField("vec4", "sk_shadeB"),
			new FrameField("vec4", "sk_shadowCascades"),
			new FrameField("vec4[8]", "sk_lightColors"),
			new FrameField("vec3", "sk_cameraPosition"),
			new FrameField("float", "sk_time"),
			new FrameField("vec3", "sk_cameraDirection"),
			new FrameField("float", "sk_frameTime"),
			new FrameField("vec3", "sk_sunDirection"),
			new FrameField("float", "sk_dayTime"),
			new FrameField("vec3", "sk_moonDirection"),
			new FrameField("float", "sk_rain"),
			new FrameField("vec3", "sk_lightDirection"),
			new FrameField("float", "sk_thunder"),
			new FrameField("vec3", "sk_previousCameraPosition"),
			new FrameField("float", "sk_partialTick"),
			new FrameField("vec3", "sk_heldLightColor"),
			new FrameField("float", "sk_heldLight"),
			new FrameField("vec3", "sk_lightOrigin"),
			new FrameField("float", "sk_wetness"),
			new FrameField("vec2", "sk_screenSize"),
			new FrameField("vec2", "sk_taaOffset"),
			new FrameField("vec2", "sk_lightAtlasSize"),
			new FrameField("vec2", "sk_cloudOffset"),
			new FrameField("float", "sk_fogStart"),
			new FrameField("float", "sk_fogEnd"),
			new FrameField("float", "sk_renderDistance"),
			new FrameField("float", "sk_fov"),
			new FrameField("float", "sk_near"),
			new FrameField("float", "sk_far"),
			new FrameField("float", "sk_eyeBlockLight"),
			new FrameField("float", "sk_eyeSkyLight"),
			new FrameField("float", "sk_biomeTemperature"),
			new FrameField("float", "sk_biomeDownfall"),
			new FrameField("float", "sk_nightVision"),
			new FrameField("float", "sk_darkness"),
			new FrameField("float", "sk_shadowRange"),
			new FrameField("float", "sk_cloudHeight"),
			new FrameField("float", "sk_effectDistance"),
			new FrameField("int", "sk_dimension"),
			new FrameField("int", "sk_cameraFluid"),
			new FrameField("int", "sk_isHand"),
			new FrameField("int", "sk_moonPhase"),
			new FrameField("int", "sk_frameCounter"),
			new FrameField("int", "sk_isSnowing"),
			new FrameField("int", "sk_lightRange"),
			new FrameField("int", "sk_lightColumns"),
			new FrameField("int", "sk_cloudMode"));

	public enum Program {
		TERRAIN,
		BLOCK,
		ENTITY,
		ITEM,
		PARTICLE,
		SKY,
		CLOUDS,
		SHADOW
	}

	public enum Stage {
		WORLD,
		DEFERRED,
		SCREEN,
		FINAL
	}

	public enum Blend {
		REPLACE,
		ADD,
		ALPHA,
		MULTIPLY
	}

	public enum Format {
		RGBA8,
		RGBA16F
	}

	public enum Filter {
		LINEAR,
		NEAREST
	}

	public enum Wrap {
		REPEAT,
		CLAMP
	}

	public static final List<String> SKY_PARTS = List.of("SKY", "SUNRISE", "STARS", "SUN", "MOON", "END_SKY", "END_FLASH");

	static final StructDef VERTEX = new StructDef("Vertex", "SkVertex", true);
	static final StructDef FRAGMENT = new StructDef("Fragment", "SkFragment", true);

	static {
		VERTEX.fields(List.of(
				new StructField("position", "sk_position", ShType.VEC3, true),
				new StructField("color", "sk_color", ShType.VEC4, true),
				new StructField("uv", "sk_uv", ShType.VEC2, true),
				new StructField("worldPos", "sk_worldPos", ShType.VEC3, false),
				new StructField("normal", "sk_normal", ShType.VEC3, false),
				new StructField("material", "sk_material", ShType.INT, false),
				new StructField("blockLight", "sk_blockLight", ShType.FLOAT, false),
				new StructField("skyLight", "sk_skyLight", ShType.FLOAT, false),
				new StructField("top", "sk_top", ShType.BOOL, false),
				new StructField("isHand", "sk_isHand", ShType.BOOL, false)));
		FRAGMENT.fields(List.of(
				new StructField("albedo", "sk_albedo", ShType.VEC4, false),
				new StructField("color", "sk_color", ShType.VEC4, false),
				new StructField("ao", "sk_ao", ShType.FLOAT, false),
				new StructField("tint", "sk_tint", ShType.VEC3, false),
				new StructField("texture", "sk_texture", ShType.VEC4, false),
				new StructField("lightmap", "sk_lightmap", ShType.VEC4, false),
				new StructField("blockLight", "sk_blockLight", ShType.FLOAT, false),
				new StructField("skyLight", "sk_skyLight", ShType.FLOAT, false),
				new StructField("normal", "sk_normal", ShType.VEC3, false),
				new StructField("position", "sk_position", ShType.VEC3, false),
				new StructField("worldPos", "sk_worldPos", ShType.VEC3, false),
				new StructField("uv", "sk_uv", ShType.VEC2, false),
				new StructField("material", "sk_material", ShType.INT, false),
				new StructField("vanilla", "sk_vanilla", ShType.VEC4, false),
				new StructField("isHand", "sk_isHand", ShType.BOOL, false),
				new StructField("direction", "sk_direction", ShType.VEC3, false),
				new StructField("part", "sk_part", ShType.INT, false),
				new StructField("mappedNormal", "sk_mappedNormal", ShType.VEC3, false),
				new StructField("roughness", "sk_roughness", ShType.FLOAT, false),
				new StructField("metallic", "sk_metallic", ShType.FLOAT, false),
				new StructField("specular", "sk_specular", ShType.VEC3, false),
				new StructField("emission", "sk_emission", ShType.VEC3, false),
				new StructField("hasMaterial", "sk_hasMaterial", ShType.BOOL, false),
				new StructField("unlit", "sk_unlit", ShType.BOOL, false)));
	}

	static final Map<String, ShType> LIBRARY_TYPES = Map.of(
			"Texture", ShType.TEXTURE,
			"Vertex", new ShType.StructType(VERTEX),
			"Fragment", new ShType.StructType(FRAGMENT));

	static final Set<String> ANNOTATIONS = Set.of("Uniform", "Varying", "Texture", "Target", "Output", "Blocks", "Light", "LightFilter", "Vertex", "Fragment", "Pass");

	static final Map<String, Value> VALUES = new LinkedHashMap<>();
	static final Map<String, List<Function>> FUNCTIONS = new HashMap<>();
	static final Map<String, String> HELPERS = new LinkedHashMap<>();
	static final Map<String, List<String>> HELPER_NEEDS = new HashMap<>();

	private Builtins() {
	}

	static Set<String> libraryNames() {
		Set<String> names = new java.util.TreeSet<>(ANNOTATIONS);
		names.addAll(LIBRARY_TYPES.keySet());
		VALUES.values().stream().filter(Value::library).forEach(value -> names.add(value.name()));
		FUNCTIONS.values().forEach(overloads -> overloads.stream().filter(Function::library).forEach(function -> names.add(function.name())));
		return names;
	}

	static {
		value("time", ShType.FLOAT, "sk_time");
		value("frameTime", ShType.FLOAT, "sk_frameTime");
		value("dayTime", ShType.FLOAT, "sk_dayTime");
		value("partialTick", ShType.FLOAT, "sk_partialTick");
		value("moonPhase", ShType.INT, "sk_moonPhase");
		value("screenSize", ShType.VEC2, "sk_screenSize");
		value("cameraPosition", ShType.VEC3, "sk_cameraPosition");
		value("previousCameraPosition", ShType.VEC3, "sk_previousCameraPosition");
		value("cameraDirection", ShType.VEC3, "sk_cameraDirection");
		value("viewMatrix", ShType.MAT4, "sk_viewMatrix");
		value("projectionMatrix", ShType.MAT4, "sk_projectionMatrix");
		value("inverseViewMatrix", ShType.MAT4, "sk_inverseViewMatrix");
		value("inverseProjectionMatrix", ShType.MAT4, "sk_inverseProjectionMatrix");
		value("previousViewMatrix", ShType.MAT4, "sk_previousViewMatrix");
		value("previousProjectionMatrix", ShType.MAT4, "sk_previousProjectionMatrix");
		value("sunDirection", ShType.VEC3, "sk_sunDirection");
		value("moonDirection", ShType.VEC3, "sk_moonDirection");
		value("lightDirection", ShType.VEC3, "sk_lightDirection");
		value("rain", ShType.FLOAT, "sk_rain");
		value("thunder", ShType.FLOAT, "sk_thunder");
		value("fogColor", ShType.VEC4, "sk_fogColor");
		value("fogStart", ShType.FLOAT, "sk_fogStart");
		value("fogEnd", ShType.FLOAT, "sk_fogEnd");
		value("renderDistance", ShType.FLOAT, "sk_renderDistance");
		value("effectDistance", ShType.FLOAT, "sk_effectDistance");
		value("fov", ShType.FLOAT, "sk_fov");
		value("near", ShType.FLOAT, "sk_near");
		value("far", ShType.FLOAT, "sk_far");
		value("eyeBlockLight", ShType.FLOAT, "sk_eyeBlockLight");
		value("eyeSkyLight", ShType.FLOAT, "sk_eyeSkyLight");
		value("dimension", ShType.INT, "sk_dimension");
		value("isInWater", ShType.BOOL, "(sk_cameraFluid == 1)");
		value("isInLava", ShType.BOOL, "(sk_cameraFluid == 2)");
		value("isInPowderSnow", ShType.BOOL, "(sk_cameraFluid == 3)");
		value("frameCounter", ShType.INT, "sk_frameCounter");
		value("wetness", ShType.FLOAT, "sk_wetness");
		value("cloudHeight", ShType.FLOAT, "sk_cloudHeight");
		value("heldLight", ShType.FLOAT, "sk_heldLight");
		value("heldLightColor", ShType.VEC3, "sk_heldLightColor");
		value("biomeTemperature", ShType.FLOAT, "sk_biomeTemperature");
		value("biomeDownfall", ShType.FLOAT, "sk_biomeDownfall");
		value("isSnowing", ShType.BOOL, "(sk_isSnowing != 0)");
		value("nightVision", ShType.FLOAT, "sk_nightVision");
		value("darkness", ShType.FLOAT, "sk_darkness");
		value("taaOffset", ShType.VEC2, "sk_taaOffset");
		value("shadowMatrix", ShType.MAT4, "sk_shadowMatrix");
		value("shadowRange", ShType.FLOAT, "sk_shadowRange");
		VALUES.put("SCREEN", new Value("SCREEN", ShType.TEXTURE, "SkScreen", PASS_ONLY, true));
		VALUES.put("DEPTH", new Value("DEPTH", ShType.TEXTURE, "SkDepth", PASS_ONLY, true));
		VALUES.put("SHADOW_MAP", new Value("SHADOW_MAP", ShType.TEXTURE, "SkShadow", NO_SHADOW, true));
		VALUES.put("SHADOW_MAP_SOLID", new Value("SHADOW_MAP_SOLID", ShType.TEXTURE, "SkShadowSolid", NO_SHADOW, true));
		VALUES.put("SHADOW_COLOR", new Value("SHADOW_COLOR", ShType.TEXTURE, "SkShadowColor", NO_SHADOW, true));
		VALUES.put("SOLID", new Value("SOLID", ShType.TEXTURE, "SkSolid", PIXELS | NO_SHADOW, true));
		VALUES.put("SOLID_DEPTH", new Value("SOLID_DEPTH", ShType.TEXTURE, "SkSolidDepth", PIXELS | NO_SHADOW, true));
		VALUES.put("SOURCE", new Value("SOURCE", ShType.TEXTURE, "Sampler0", FRAGMENT_HOOKS, true));

		constant("OVERWORLD", 0);
		constant("NETHER", 1);
		constant("END", 2);
		constant("OTHER_DIMENSION", 3);

		for (int i = 0; i < SKY_PARTS.size(); i++) {
			constant(SKY_PARTS.get(i), i);
		}

		VALUES.put("PI", new Value("PI", ShType.FLOAT, "3.14159265358979", ANY, true));
		VALUES.put("TAU", new Value("TAU", ShType.FLOAT, "6.28318530717959", ANY, true));

		for (String name : List.of("radians", "degrees", "sin", "cos", "tan", "asin", "acos", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
				"exp", "log", "exp2", "log2", "sqrt", "inversesqrt", "floor", "trunc", "round", "roundEven", "ceil", "fract", "normalize")) {
			glsl(name, "gf->gf");
		}

		glsl("atan", "gf->gf", "gf,gf->gf");
		glsl("pow", "gf,gf->gf");
		glsl("abs", "gf->gf", "gi->gi");
		glsl("sign", "gf->gf", "gi->gi");
		glsl("mod", "gf,gf->gf", "vf,float->vf");
		glsl("min", "gf,gf->gf", "vf,float->vf", "gi,gi->gi", "vi,int->vi");
		glsl("max", "gf,gf->gf", "vf,float->vf", "gi,gi->gi", "vi,int->vi");
		glsl("clamp", "gf,gf,gf->gf", "vf,float,float->vf", "gi,gi,gi->gi", "vi,int,int->vi");
		glsl("mix", "gf,gf,gf->gf", "vf,vf,float->vf", "gf,gf,gb->gf");
		glsl("step", "gf,gf->gf", "float,vf->vf");
		glsl("smoothstep", "gf,gf,gf->gf", "float,float,vf->vf");
		glsl("isnan", "gf->gb");
		glsl("isinf", "gf->gb");
		glsl("floatBitsToInt", "gf->gi");
		glsl("intBitsToFloat", "gi->gf");
		glsl("length", "gf->float");
		glsl("distance", "gf,gf->float");
		glsl("dot", "gf,gf->float");
		glsl("cross", "vec3,vec3->vec3");
		glsl("faceforward", "gf,gf,gf->gf");
		glsl("reflect", "gf,gf->gf");
		glsl("refract", "gf,gf,float->gf");
		glsl("matrixCompMult", "m,m->m");
		glsl("outerProduct", "vf,vf->m");
		glsl("transpose", "m->m");
		glsl("determinant", "m->float");
		glsl("inverse", "m->m");

		for (String name : List.of("lessThan", "lessThanEqual", "greaterThan", "greaterThanEqual")) {
			glsl(name, "vf,vf->vb", "vi,vi->vb");
		}

		glsl("equal", "vf,vf->vb", "vi,vi->vb", "vb,vb->vb");
		glsl("notEqual", "vf,vf->vb", "vi,vi->vb", "vb,vb->vb");
		glsl("any", "vb->bool");
		glsl("all", "vb->bool");
		glsl("not", "vb->vb");
		glsl("texture", "Texture,vec2->vec4");
		function("texture", List.of(ShType.TEXTURE, ShType.VEC2, ShType.FLOAT), ShType.VEC4, "texture", PIXELS, null, false, List.of());
		glsl("textureLod", "Texture,vec2,float->vec4");
		glsl("textureSize", "Texture,int->ivec2");
		glsl("texelFetch", "Texture,ivec2,int->vec4");

		for (String name : List.of("dFdx", "dFdy", "fwidth")) {
			for (ShType type : genFloat()) {
				function(name, List.of(type), type, name, PIXELS, null, false, List.of());
			}
		}

		helper("sk_ndcDepth", """
				float sk_ndcDepth(float depth) {
				#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
					return depth;
				#else
					return depth * 2.0 - 1.0;
				#endif
				}
				""");
		helper("sk_viewPos", """
				vec3 sk_viewPos(vec2 uv) {
					vec4 position = sk_inverseProjectionMatrix * vec4(uv * 2.0 - 1.0, sk_ndcDepth(texture(SkDepth, uv).r), 1.0);
					return position.xyz / position.w;
				}
				""", "sk_ndcDepth");
		helper("sk_relativePos", """
				vec3 sk_relativePos(vec2 uv) {
					return (sk_inverseViewMatrix * vec4(sk_viewPos(uv), 1.0)).xyz;
				}
				""", "sk_viewPos");
		helper("sk_worldPos", """
				vec3 sk_worldPos(vec2 uv) {
					return sk_relativePos(uv) + sk_cameraPosition;
				}
				""", "sk_relativePos");
		helper("sk_linearDepth", """
				float sk_linearDepth(vec2 uv) {
					return -sk_viewPos(uv).z;
				}
				""", "sk_viewPos");
		helper("sk_isSky", """
				bool sk_isSky(vec2 uv) {
					return texture(SkDepth, uv).r <= 0.0;
				}
				""");
		helper("sk_toScreen", """
				vec2 sk_toScreen(vec3 relative) {
					vec4 clip = sk_projectionMatrix * (sk_viewMatrix * vec4(relative, 1.0));
					return clip.xy / clip.w * 0.5 + 0.5;
				}
				""");
		helper("sk_inFront", """
				bool sk_inFront(vec3 relative) {
					return (sk_viewMatrix * vec4(relative, 1.0)).z < 0.0;
				}
				""");
		helper("sk_texelSize", """
				vec2 sk_texelSize(sampler2D texture0) {
					return 1.0 / vec2(textureSize(texture0, 0));
				}
				""");
		helper("sk_luminance", """
				float sk_luminance(vec3 color) {
					return dot(color, vec3(0.2126, 0.7152, 0.0722));
				}
				""");
		helper("sk_random", """
				float sk_random(vec2 p) {
					vec3 p3 = fract(vec3(p.xyx) * 0.1031);
					p3 += dot(p3, p3.yzx + 33.33);
					return fract((p3.x + p3.y) * p3.z);
				}
				float sk_random(vec3 p) {
					vec3 p3 = fract(p * 0.1031);
					p3 += dot(p3, p3.zyx + 31.32);
					return fract((p3.x + p3.y) * p3.z);
				}
				""");
		helper("sk_noise", """
				float sk_noise(vec2 p) {
					vec2 i = floor(p);
					vec2 f = fract(p);
					vec2 u = f * f * (3.0 - 2.0 * f);
					return mix(mix(sk_random(i), sk_random(i + vec2(1.0, 0.0)), u.x), mix(sk_random(i + vec2(0.0, 1.0)), sk_random(i + vec2(1.0, 1.0)), u.x), u.y);
				}
				float sk_noise(vec3 p) {
					vec3 i = floor(p);
					vec3 f = fract(p);
					vec3 u = f * f * (3.0 - 2.0 * f);
					float a = mix(mix(sk_random(i), sk_random(i + vec3(1.0, 0.0, 0.0)), u.x), mix(sk_random(i + vec3(0.0, 1.0, 0.0)), sk_random(i + vec3(1.0, 1.0, 0.0)), u.x), u.y);
					float b = mix(mix(sk_random(i + vec3(0.0, 0.0, 1.0)), sk_random(i + vec3(1.0, 0.0, 1.0)), u.x), mix(sk_random(i + vec3(0.0, 1.0, 1.0)), sk_random(i + vec3(1.0, 1.0, 1.0)), u.x), u.y);
					return mix(a, b, u.z);
				}
				""", "sk_random");

		helper("sk_depthToView", """
				vec3 sk_depthToView(vec2 uv, float depth) {
					vec4 position = sk_inverseProjectionMatrix * vec4(uv * 2.0 - 1.0, sk_ndcDepth(depth), 1.0);
					return position.xyz / position.w;
				}
				vec3 sk_depthToRelative(vec2 uv, float depth) {
					return (sk_inverseViewMatrix * vec4(sk_depthToView(uv, depth), 1.0)).xyz;
				}
				""", "sk_ndcDepth");
		helper("sk_toScreenDepth", """
				vec3 sk_toScreenDepth(vec3 relative) {
					vec4 clip = sk_projectionMatrix * (sk_viewMatrix * vec4(relative, 1.0));
					vec3 ndc = clip.xyz / clip.w;
				#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
					return vec3(ndc.xy * 0.5 + 0.5, ndc.z);
				#else
					return vec3(ndc.xy * 0.5 + 0.5, ndc.z * 0.5 + 0.5);
				#endif
				}
				""");
		helper("sk_shadowPos", """
				vec3 sk_shadowPos(vec3 relative) {
					vec4 clip = sk_shadowMatrix * vec4(relative, 1.0);
					int cascade = 2;
					vec2 xy = clip.xy;
					if (max(abs(clip.x), abs(clip.y)) * sk_shadowCascades.x < 0.94) {
						cascade = 0;
						xy = clip.xy * sk_shadowCascades.x;
					} else if (max(abs(clip.x), abs(clip.y)) * sk_shadowCascades.y < 0.94) {
						cascade = 1;
						xy = clip.xy * sk_shadowCascades.y;
					} else if (max(abs(clip.x), abs(clip.y)) > 1.0) {
						return vec3(-1.0, -1.0, clip.z);
					}
					vec2 cell = vec2(float(cascade % 2), float(cascade / 2));
					return vec3((xy * 0.5 + 0.5) * 0.5 + cell * 0.5, clip.z);
				}
				""");
		helper("sk_inLightVolume", """
				bool sk_inLightVolume(vec3 world) {
					if (sk_lightRange <= 0) {
						return false;
					}
					vec3 local = world - sk_lightOrigin;
					vec3 size = vec3(float(sk_lightRange), float(sk_lightRange / 2), float(sk_lightRange));
					return all(greaterThanEqual(local, vec3(0.5))) && all(lessThanEqual(local, size - 0.5));
				}
				""");
		helper("sk_lightSample", """
				vec2 sk_lightUv(int slice, vec2 xz) {
					int tile = sk_lightRange + 2;
					vec2 local = mod(xz - 0.5, float(sk_lightRange));
					vec2 corner = vec2(float(slice % sk_lightColumns), float(slice / sk_lightColumns)) * float(tile);
					return (corner + 1.5 + local) / sk_lightAtlasSize;
				}
				float sk_lightFade(vec3 world) {
					vec3 local = world - sk_lightOrigin;
					vec3 size = vec3(float(sk_lightRange), float(sk_lightRange / 2), float(sk_lightRange));
					vec3 edge = min(local, size - local);
					return clamp(min(edge.x, min(edge.y, edge.z)) / 6.0 - 0.1, 0.0, 1.0);
				}
				void sk_lightSample(vec3 world, out vec4 a, out vec4 b, out vec3 tint) {
					int height = sk_lightRange / 2;
					float y = mod(world.y - 0.5, float(height));
					int y0 = int(floor(y));
					int y1 = (y0 + 1) % height;
					float fy = y - floor(y);
					vec2 uv0 = sk_lightUv(y0, world.xz);
					vec2 uv1 = sk_lightUv(y1, world.xz);
					vec4 t = mix(textureLod(SkLightTint, uv0, 0.0), textureLod(SkLightTint, uv1, 0.0), fy);
					if (t.a < 0.001) {
						a = vec4(0.0);
						b = vec4(0.0);
						tint = vec3(1.0);
						return;
					}
					float scale = sk_lightFade(world) / t.a;
					a = mix(textureLod(SkLightA, uv0, 0.0), textureLod(SkLightA, uv1, 0.0), fy) * scale;
					b = mix(textureLod(SkLightB, uv0, 0.0), textureLod(SkLightB, uv1, 0.0), fy) * scale;
					tint = t.rgb / t.a;
				}
				""");
		helper("sk_coloredLight", """
				vec3 sk_coloredLight(vec3 world) {
					if (!sk_inLightVolume(world)) {
						return vec3(0.0);
					}
					vec4 a;
					vec4 b;
					vec3 tint;
					sk_lightSample(world, a, b, tint);
					vec3 light = a.x * sk_lightColors[0].rgb + a.y * sk_lightColors[1].rgb + a.z * sk_lightColors[2].rgb + a.w * sk_lightColors[3].rgb
							+ b.x * sk_lightColors[4].rgb + b.y * sk_lightColors[5].rgb + b.z * sk_lightColors[6].rgb + b.w * sk_lightColors[7].rgb;
					return light * tint;
				}
				""", "sk_inLightVolume", "sk_lightSample");
		helper("sk_lightLevel", """
				float sk_lightLevel(int type, vec3 world) {
					if (type < 0 || type > 7 || !sk_inLightVolume(world)) {
						return 0.0;
					}
					vec4 a;
					vec4 b;
					vec3 tint;
					sk_lightSample(world, a, b, tint);
					return type < 4 ? a[type] : b[type - 4];
				}
				""", "sk_inLightVolume", "sk_lightSample");
		helper("sk_cloudCover", """
				float sk_cloudCover(vec3 world) {
					if (sk_cloudMode == 0) {
						return 0.0;
					}
					vec2 at = world.xz + sk_cloudOffset;
					return textureLod(SkClouds, at / (12.0 * vec2(textureSize(SkClouds, 0))), 0.0).r;
				}
				""");
		helper("sk_cloudShadow", """
				float sk_cloudShadow(vec3 world) {
					if (sk_cloudMode == 0 || sk_lightDirection.y < 0.02) {
						return 0.0;
					}
					float up = sk_cloudHeight + (sk_cloudMode == 2 ? 2.0 : 0.0) - world.y;
					if (up <= 0.0) {
						return 0.0;
					}
					vec2 at = world.xz + sk_lightDirection.xz / sk_lightDirection.y * up + sk_cloudOffset;
					float cover = textureLod(SkClouds, at / (12.0 * vec2(textureSize(SkClouds, 0))), 0.0).r;
					return cover * smoothstep(0.02, 0.15, sk_lightDirection.y);
				}
				""");
		helper("sk_lightLevelAll", """
				float sk_lightLevelAll(vec3 world) {
					if (!sk_inLightVolume(world)) {
						return 0.0;
					}
					vec4 a;
					vec4 b;
					vec3 tint;
					sk_lightSample(world, a, b, tint);
					vec4 m = max(a, b);
					return max(max(m.x, m.y), max(m.z, m.w));
				}
				""", "sk_inLightVolume", "sk_lightSample");

		function("linearDepth", List.of(ShType.VEC2), ShType.FLOAT, "sk_linearDepth", PASS_ONLY, "sk_linearDepth", true);
		function("viewPos", List.of(ShType.VEC2), ShType.VEC3, "sk_viewPos", PASS_ONLY, "sk_viewPos", true);
		function("relativePos", List.of(ShType.VEC2), ShType.VEC3, "sk_relativePos", PASS_ONLY, "sk_relativePos", true);
		function("worldPos", List.of(ShType.VEC2), ShType.VEC3, "sk_worldPos", PASS_ONLY, "sk_worldPos", true);
		function("isSky", List.of(ShType.VEC2), ShType.BOOL, "sk_isSky", PASS_ONLY, "sk_isSky", true);
		function("toScreen", List.of(ShType.VEC3), ShType.VEC2, "sk_toScreen", ANY, "sk_toScreen", true);
		function("inFront", List.of(ShType.VEC3), ShType.BOOL, "sk_inFront", ANY, "sk_inFront", true);
		function("texelSize", List.of(ShType.TEXTURE), ShType.VEC2, "sk_texelSize", ANY, "sk_texelSize", true);
		function("luminance", List.of(ShType.VEC3), ShType.FLOAT, "sk_luminance", ANY, "sk_luminance", true);
		function("random", List.of(ShType.VEC2), ShType.FLOAT, "sk_random", ANY, "sk_random", true);
		function("random", List.of(ShType.VEC3), ShType.FLOAT, "sk_random", ANY, "sk_random", true);
		function("noise", List.of(ShType.VEC2), ShType.FLOAT, "sk_noise", ANY, "sk_noise", true);
		function("noise", List.of(ShType.VEC3), ShType.FLOAT, "sk_noise", ANY, "sk_noise", true);
		function("depthToView", List.of(ShType.VEC2, ShType.FLOAT), ShType.VEC3, "sk_depthToView", ANY, "sk_depthToView", true);
		function("depthToRelative", List.of(ShType.VEC2, ShType.FLOAT), ShType.VEC3, "sk_depthToRelative", ANY, "sk_depthToView", true);
		function("toScreenDepth", List.of(ShType.VEC3), ShType.VEC3, "sk_toScreenDepth", ANY, "sk_toScreenDepth", true);
		function("shadowPos", List.of(ShType.VEC3), ShType.VEC3, "sk_shadowPos", ANY, "sk_shadowPos", true);
		function("coloredLight", List.of(ShType.VEC3), ShType.VEC3, "sk_coloredLight", ANY, "sk_coloredLight", true, LIGHT_SAMPLERS);
		function("lightLevel", List.of(ShType.INT, ShType.VEC3), ShType.FLOAT, "sk_lightLevel", ANY, "sk_lightLevel", true, LIGHT_SAMPLERS);
		function("lightLevel", List.of(ShType.VEC3), ShType.FLOAT, "sk_lightLevelAll", ANY, "sk_lightLevelAll", true, LIGHT_SAMPLERS);
		function("cloudShadow", List.of(ShType.VEC3), ShType.FLOAT, "sk_cloudShadow", ANY, "sk_cloudShadow", true, List.of("SkClouds"));
		function("cloudCover", List.of(ShType.VEC3), ShType.FLOAT, "sk_cloudCover", ANY, "sk_cloudCover", true, List.of("SkClouds"));
		function("inLightVolume", List.of(ShType.VEC3), ShType.BOOL, "sk_inLightVolume", ANY, "sk_inLightVolume", true);
		function("write", List.of(ShType.TEXTURE, ShType.VEC4), ShType.VOID, "SK_WRITE", ANY, null, true);
	}


	private static void value(String name, ShType type, String glsl) {
		VALUES.put(name, new Value(name, type, glsl, ANY, true));
	}

	private static void constant(String name, int value) {
		VALUES.put(name, new Value(name, ShType.INT, Integer.toString(value), ANY, true));
	}

	private static void helper(String name, String code, String... needs) {
		HELPERS.put(name, code);
		HELPER_NEEDS.put(name, List.of(needs));
	}

	private static void function(String name, List<ShType> params, ShType result, String glsl, int restriction, String helper, boolean library) {
		function(name, params, result, glsl, restriction, helper, library, List.of());
	}

	private static void function(String name, List<ShType> params, ShType result, String glsl, int restriction, String helper, boolean library, List<String> samplers) {
		FUNCTIONS.computeIfAbsent(name, key -> new ArrayList<>()).add(new Function(name, params, result, glsl, restriction, helper, library, samplers));
	}

	private static List<ShType> genFloat() {
		return List.of(ShType.FLOAT, ShType.VEC2, ShType.VEC3, ShType.VEC4);
	}

	private static void glsl(String name, String... patterns) {
		for (String pattern : patterns) {
			String[] sides = pattern.split("->");
			String[] params = sides[0].split(",");
			boolean generic = false;
			boolean sized = false;

			for (String token : pattern.split("->|,")) {
				generic |= Set.of("gf", "gi", "gb").contains(token.trim());
				sized |= Set.of("vf", "vi", "vb", "m").contains(token.trim());
			}

			int from = generic ? 1 : sized ? 2 : 0;
			int to = generic || sized ? 4 : 0;

			for (int size = from; size <= to; size++) {
				List<ShType> types = new ArrayList<>();

				for (String param : params) {
					types.add(resolve(param.trim(), size));
				}

				function(name, types, resolve(sides[1].trim(), size), name, ANY, null, false, List.of());
			}
		}
	}

	private static ShType resolve(String token, int size) {
		return switch (token) {
			case "gf", "vf" -> ShType.vector(ShType.Base.FLOAT, size);
			case "gi", "vi" -> ShType.vector(ShType.Base.INT, size);
			case "gb", "vb" -> ShType.vector(ShType.Base.BOOL, size);
			case "m" -> new ShType.Matrix(size);
			default -> {
				ShType type = ShType.builtin(token);

				if (type == null) {
					throw new IllegalArgumentException("Unknown type " + token);
				}

				yield type;
			}
		};
	}
}
