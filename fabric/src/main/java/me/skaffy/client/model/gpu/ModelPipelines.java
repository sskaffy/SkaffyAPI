package me.skaffy.client.model.gpu;

import java.util.List;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.resources.Identifier;

public final class ModelPipelines {
	public static final String NAMESPACE = "skaffys-api";
	public static final Identifier SHADER = Identifier.fromNamespaceAndPath(NAMESPACE, "core/model");
	public static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL;
	public static final BindGroupLayout MODEL = BindGroupLayout.builder()
			.withUniform("SkaffyModel", UniformType.UNIFORM_BUFFER)
			.withUniform("Sampler3", UniformType.COMBINED_IMAGE_SAMPLER)
			.withUniform("Sampler5", UniformType.COMBINED_IMAGE_SAMPLER)
			.build();

	private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
			.withVertexShader(SHADER)
			.withFragmentShader(SHADER)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER2)
			.withBindGroupLayout(MODEL)
			.withVertexBinding(0, FORMAT)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withDepthStencilState(DepthStencilState.DEFAULT)
			.buildSnippet();
	private static final RenderPipeline.Snippet OIT_SNIPPET = RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withBindGroupLayout(BindGroupLayouts.FOG)
			.withBindGroupLayout(BindGroupLayouts.LIGHTING)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER2)
			.withBindGroupLayout(MODEL)
			.withVertexShader(SHADER)
			.withFragmentShader(SHADER)
			.withVertexBinding(0, FORMAT)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.buildSnippet();

	public static final RenderPipeline SOLID = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
			.withLocation(id("pipeline/model_solid"))
			.withColorTargetState(ColorTargetState.DEFAULT)
			.build());
	public static final RenderPipeline SOLID_NO_CULL = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
			.withLocation(id("pipeline/model_solid_no_cull"))
			.withColorTargetState(ColorTargetState.DEFAULT)
			.withCull(false)
			.build());
	public static final RenderPipeline TRANSLUCENT = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
			.withLocation(id("pipeline/model_translucent"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build());
	public static final RenderPipeline TRANSLUCENT_NO_CULL = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
			.withLocation(id("pipeline/model_translucent_no_cull"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withCull(false)
			.build());
	public static final OitPipelineSet OIT_TRANSLUCENT = RenderPipelines.register(OitPipelineSet.builder("skaffy_model", RenderPipeline.builder(OIT_SNIPPET)).build());
	public static final OitPipelineSet OIT_TRANSLUCENT_NO_CULL = RenderPipelines.register(
			OitPipelineSet.builder("skaffy_model_no_cull", RenderPipeline.builder(OIT_SNIPPET).withCull(false)).build());
	public static final RenderPipeline OUTLINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(id("pipeline/model_outline"))
			.withVertexShader(id("core/model_outline"))
			.withFragmentShader(id("core/model_outline"))
			.withBindGroupLayout(MODEL)
			.withVertexBinding(0, FORMAT)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withCull(false)
			.build());

	private ModelPipelines() {
	}

	public static void init() {
	}

	public static RenderPipeline pipeline(boolean blended, boolean cull) {
		return blended ? cull ? TRANSLUCENT : TRANSLUCENT_NO_CULL : cull ? SOLID : SOLID_NO_CULL;
	}

	public static OitPipelineSet oit(boolean cull) {
		return cull ? OIT_TRANSLUCENT : OIT_TRANSLUCENT_NO_CULL;
	}

	public static List<RenderPipeline> all() {
		return List.of(SOLID, SOLID_NO_CULL, TRANSLUCENT, TRANSLUCENT_NO_CULL,
				OIT_TRANSLUCENT.depthBoundsPipeline(), OIT_TRANSLUCENT.transmittancePipeline(), OIT_TRANSLUCENT.accumulatePipeline(),
				OIT_TRANSLUCENT_NO_CULL.depthBoundsPipeline(), OIT_TRANSLUCENT_NO_CULL.transmittancePipeline(), OIT_TRANSLUCENT_NO_CULL.accumulatePipeline());
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}
}
