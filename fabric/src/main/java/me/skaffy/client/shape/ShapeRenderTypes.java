package me.skaffy.client.shape;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;

import me.skaffy.protocol.shapes.ShapesPacket.Render;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

final class ShapeRenderTypes {
	private static final RenderPipeline FILL_SEE_THROUGH = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skaffys-api", "pipeline/shape_fill_see_through"))
			.withDepthStencilState(Optional.empty())
			.build());
	private static final RenderPipeline LINES_SEE_THROUGH = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skaffys-api", "pipeline/shape_lines_see_through"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(Optional.empty())
			.build());
	static final RenderType FILL = RenderTypes.debugQuads();
	static final RenderType FILL_THROUGH = RenderType.create("skaffy_shape_fill_see_through", RenderSetup.builder(FILL_SEE_THROUGH).sortOnUpload().createRenderSetup());
	static final RenderType LINES = RenderTypes.linesTranslucentNoDepthWrite();
	static final RenderType LINES_THROUGH = RenderType.create("skaffy_shape_lines_see_through",
			RenderSetup.builder(LINES_SEE_THROUGH).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());
	private static final Map<String, RenderType> WORLD = new HashMap<>();

	private ShapeRenderTypes() {
	}

	private static GpuSampler repeat() {
		return RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.NEAREST, false);
	}

	static RenderType world(Identifier texture, Render render) {
		return WORLD.computeIfAbsent(render + "|" + texture, key -> {
			RenderSetup.RenderSetupBuilder builder = switch (render) {
				case SOLID -> RenderSetup.builder(RenderPipelines.ENTITY_SOLID);
				case CUTOUT -> RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT_CULL);
				case TRANSLUCENT -> RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL).setOitPipelines(RenderPipelines.OIT_ENTITY_CULL).sortOnUpload();
			};

			return RenderType.create("skaffy_shape_" + render.name().toLowerCase(java.util.Locale.ROOT),
					builder.withTexture("Sampler0", texture, ShapeRenderTypes::repeat).useLightmap().useOverlay().createRenderSetup());
		});
	}

	static void clear() {
		WORLD.clear();
	}
}
