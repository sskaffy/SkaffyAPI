package me.skaffy.client.nametag;

import java.util.IdentityHashMap;
import java.util.Map;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import me.skaffy.client.mixin.RenderSetupAccessor;
import me.skaffy.client.mixin.RenderTypeAccessor;
import me.skaffy.client.mixin.TextureBindingAccessor;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

final class NameTagRenderTypes {
	private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
	private static final RenderPipeline TEXT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.WORLD_TEXT_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skaffys-api", "pipeline/name_tag_text"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(TEST_ONLY)
			.build());
	private static final RenderPipeline TEXT_GRAYSCALE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.WORLD_TEXT_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skaffys-api", "pipeline/name_tag_text_grayscale"))
			.withShaderDefine("IS_GRAYSCALE")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(TEST_ONLY)
			.build());
	private static final Map<RenderType, RenderType> TRANSLUCENT = new IdentityHashMap<>();

	private NameTagRenderTypes() {
	}

	static RenderType translucent(RenderType type) {
		return TRANSLUCENT.computeIfAbsent(type, NameTagRenderTypes::convert);
	}

	static void clear() {
		TRANSLUCENT.clear();
	}

	private static RenderType convert(RenderType type) {
		RenderSetupAccessor setup = (RenderSetupAccessor) (Object) ((RenderTypeAccessor) type).skaffy$state();
		RenderPipeline pipeline;
		OitPipelineSet oit;

		if (setup.skaffy$pipeline() == RenderPipelines.TEXT) {
			pipeline = TEXT;
			oit = RenderPipelines.OIT_TEXT;
		} else if (setup.skaffy$pipeline() == RenderPipelines.TEXT_GRAYSCALE) {
			pipeline = TEXT_GRAYSCALE;
			oit = RenderPipelines.OIT_TEXT_GRAYSCALE;
		} else {
			return type;
		}

		Object texture = setup.skaffy$textures().get("Sampler0");

		if (texture == null) {
			return type;
		}

		Identifier location = ((TextureBindingAccessor) texture).skaffy$location();
		RenderSetup translucent = RenderSetup.builder(pipeline).setOitPipelines(oit).withTexture("Sampler0", location).useLightmap().createRenderSetup();
		return RenderType.create("skaffy_name_tag_text", translucent);
	}
}
