package me.skaffy.client.shader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import me.skaffy.client.shader.lang.Builtins.Program;
import me.skaffy.client.shader.lang.ShaderModule;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

public final class WorldPrograms {
	public static final String NAMESPACE = "skaffy";
	public static final GpuFormat SHADOW_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
	private static final DepthStencilState SHADOW_DEPTH = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, 1.0F, 1.0F);

	public record Variant(RenderPipeline vanilla, Program program, String template, int part, boolean optional) {
		Variant(RenderPipeline vanilla, Program program, String template, int part) {
			this(vanilla, program, template, part, false);
		}

		public boolean mainPass() {
			List<ColorTargetState> targets = vanilla.getColorTargetStates();
			return targets.size() == 1 && targets.getFirst() != null && targets.getFirst().format() == GpuFormat.RGBA8_UNORM;
		}
	}

	private WorldPrograms() {
	}

	public static List<Variant> all() {
		List<Variant> variants = new ArrayList<>();
		Set<RenderPipeline> optional = Collections.newSetFromMap(new IdentityHashMap<>());
		optional.addAll(RenderPipelines.optionalPipelines());

		for (RenderPipeline pipeline : vanillaPipelines()) {
			Variant variant = classify(pipeline);

			if (variant != null) {
				variants.add(new Variant(variant.vanilla(), variant.program(), variant.template(), variant.part(), optional.contains(pipeline)));
			}
		}

		return variants;
	}

	public static Set<RenderPipeline> vanillaPipelines() {
		Set<RenderPipeline> pipelines = Collections.newSetFromMap(new IdentityHashMap<>());
		pipelines.addAll(RenderPipelines.requiredPipelines());
		pipelines.addAll(RenderPipelines.optionalPipelines());

		for (Field field : RenderPipelines.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers())) {
				continue;
			}

			try {
				field.setAccessible(true);
				Object value = field.get(null);

				if (value instanceof RenderPipeline pipeline) {
					pipelines.add(pipeline);
				} else if (value instanceof OitPipelineSet set) {
					pipelines.add(set.depthBoundsPipeline());
					pipelines.add(set.transmittancePipeline());
					pipelines.add(set.accumulatePipeline());
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
			}
		}

		return pipelines;
	}

	static @Nullable Variant classify(RenderPipeline pipeline) {
		if (pipeline == RenderPipelines.SKY) {
			return new Variant(pipeline, Program.SKY, "sky", 0);
		}

		if (pipeline == RenderPipelines.SUNRISE_SUNSET) {
			return new Variant(pipeline, Program.SKY, "sunrise", 1);
		}

		if (pipeline == RenderPipelines.STARS) {
			return new Variant(pipeline, Program.SKY, "stars", 2);
		}

		if (pipeline == RenderPipelines.CELESTIAL) {
			return new Variant(pipeline, Program.SKY, "celestial", -1);
		}

		if (pipeline == RenderPipelines.END_SKY) {
			return new Variant(pipeline, Program.SKY, "end_sky", 5);
		}

		Identifier vertex = pipeline.getShaders().get(ShaderType.VERTEX);
		Identifier fragment = pipeline.getShaders().get(ShaderType.FRAGMENT);

		if (me.skaffy.client.model.gpu.ModelPipelines.SHADER.equals(vertex) && me.skaffy.client.model.gpu.ModelPipelines.SHADER.equals(fragment)) {
			return new Variant(pipeline, Program.ENTITY, "model", 0);
		}

		if (vertex == null || fragment == null || !vertex.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) || !vertex.equals(fragment)) {
			return null;
		}

		return switch (vertex.getPath()) {
			case "core/terrain" -> new Variant(pipeline, Program.TERRAIN, "terrain", 0);
			case "core/block" -> new Variant(pipeline, Program.BLOCK, "block", 0);
			case "core/entity" -> new Variant(pipeline, Program.ENTITY, "entity", 0);
			case "core/item" -> new Variant(pipeline, Program.ITEM, "item", 0);
			case "core/particle" -> new Variant(pipeline, Program.PARTICLE, "particle", 0);
			case "core/clouds" -> new Variant(pipeline, Program.CLOUDS, "clouds", 0);
			default -> null;
		};
	}

	public static RenderPipeline replacement(Variant variant, Identifier location, String shaders, BindGroupLayout extra, FrameLayout layout, ShaderModule.Hook hook) {
		RenderPipeline vanilla = variant.vanilla();
		ColorTargetState[] colorTargets = new ColorTargetState[ColorTargetState.MAX_COLOR_TARGETS];
		List<ColorTargetState> targets = vanilla.getColorTargetStates();
		int count = targets.size();

		for (int i = 0; i < count; i++) {
			colorTargets[i] = targets.get(i);
		}

		boolean main = variant.mainPass();

		if (main) {
			ColorTargetState color = targets.getFirst();
			colorTargets[0] = new ColorTargetState(color.blendFunction(), layout.main(), color.writeMask());

			for (FrameLayout.Slot slot : layout.outputs()) {
				int mask = hook.outputs().contains(slot.sampler()) ? ColorTargetState.WRITE_ALL : ColorTargetState.WRITE_NONE;
				colorTargets[count++] = new ColorTargetState(Optional.empty(), slot.format(), mask);
			}
		}

		RenderPipeline.Builder builder = RenderPipeline.builder(snippet(vanilla, colorTargets, count, Optional.ofNullable(vanilla.getDepthStencilState()), vanilla.isCull()))
				.withLocation(location)
				.withVertexShader(template(shaders, variant.template()))
				.withFragmentShader(template(shaders, variant.template()))
				.withShaderDefine("SK_PART", variant.part())
				.withBindGroupLayout(extra);

		if (main && layout.hasOutputs()) {
			builder.withShaderDefine("SK_OUTPUTS");
		}

		return builder.build();
	}

	public static RenderPipeline shadowEntity(Variant variant, Identifier location, String shaders, BindGroupLayout extra) {
		ColorTargetState[] colorTargets = new ColorTargetState[ColorTargetState.MAX_COLOR_TARGETS];
		colorTargets[0] = new ColorTargetState(Optional.empty(), SHADOW_COLOR_FORMAT, ColorTargetState.WRITE_NONE);
		return RenderPipeline.builder(snippet(variant.vanilla(), colorTargets, 1, Optional.of(SHADOW_DEPTH), false))
				.withLocation(location)
				.withVertexShader(template(shaders, variant.template()))
				.withFragmentShader(template(shaders, variant.template()))
				.withShaderDefine("SK_PART", 0)
				.withShaderDefine("SK_SHADOW_PASS")
				.withBindGroupLayout(extra)
				.build();
	}

	public static RenderPipeline shadowTerrain(ChunkSectionLayer layer, Identifier location, String shaders, BindGroupLayout extra, boolean seeThrough) {
		RenderPipeline vanilla = layer.pipeline(false);
		boolean translucent = layer.translucent() || seeThrough;
		RenderPipeline.Snippet defines = new RenderPipeline.Snippet(Map.of(), Optional.of(vanilla.getShaderDefines()), Optional.empty(),
				new ColorTargetState[ColorTargetState.MAX_COLOR_TARGETS], 0, Optional.empty(), Optional.empty(), Optional.empty(), new VertexFormat[16],
				Optional.empty(), 0);
		RenderPipeline.Builder builder = RenderPipeline.builder(defines)
				.withLocation(location)
				.withVertexShader(template(shaders, "shadow_terrain"))
				.withFragmentShader(template(shaders, "shadow_terrain"))
				.withBindGroupLayout(extra)
				.withBindGroupLayout(BindGroupLayout.builder().withUniform("SkSection", UniformType.UNIFORM_BUFFER).withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER).build())
				.withVertexBinding(0, vanilla.getVertexFormatBinding(0))
				.withPrimitiveTopology(vanilla.getPrimitiveTopology())
				.withDepthStencilState(SHADOW_DEPTH)
				.withCull(false)
				.withColorTargetState(new ColorTargetState(Optional.empty(), SHADOW_COLOR_FORMAT, translucent ? ColorTargetState.WRITE_ALL : ColorTargetState.WRITE_NONE));

		if (seeThrough) {
			builder.withShaderDefine("SK_SHADOW_SEE_THROUGH");
		} else if (translucent) {
			builder.withShaderDefine("SK_SHADOW_TRANSLUCENT");
		}

		return builder.build();
	}

	private static RenderPipeline.Snippet snippet(RenderPipeline vanilla, ColorTargetState[] colorTargets, int count, Optional<DepthStencilState> depth, boolean cull) {
		VertexFormat[] formats = new VertexFormat[16];
		List<VertexFormat> bindings = vanilla.getVertexFormatBindings();

		for (int i = 0; i < bindings.size() && i < formats.length; i++) {
			formats[i] = bindings.get(i);
		}

		return new RenderPipeline.Snippet(
				Map.of(),
				Optional.of(vanilla.getShaderDefines()),
				Optional.of(vanilla.getBindGroupLayouts()),
				colorTargets,
				count,
				depth,
				Optional.of(vanilla.getPolygonMode()),
				Optional.of(cull),
				formats,
				Optional.of(vanilla.getPrimitiveTopology()),
				vanilla.pushConstantSize());
	}

	private static Identifier template(String shaders, String name) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, shaders + "/" + name);
	}
}
