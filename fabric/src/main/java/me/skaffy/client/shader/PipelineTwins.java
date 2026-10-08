package me.skaffy.client.shader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.mixin.PipelineCacheAccessor;
import me.skaffy.client.mixin.RenderSystemAccessor;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;

public final class PipelineTwins {
	private static final Map<CompiledRenderPipeline, Origin> ORIGINS = new IdentityHashMap<>();
	private static final Map<RenderPipeline, Map<List<GpuFormat>, CompiledRenderPipeline>> TWINS = new IdentityHashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Set<CompiledRenderPipeline> FAILED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
	private static int nextId;

	private record Origin(RenderPipeline pipeline, @Nullable ShaderSource source) {
	}

	private record Pending(RenderPipeline original, List<GpuFormat> formats, CompletableFuture<CompiledRenderPipeline.Pending> future) {
	}

	private PipelineTwins() {
	}

	public static void rememberVanilla(RenderPipeline pipeline, CompiledRenderPipeline compiled) {
		Origin origin = ORIGINS.get(compiled);

		if (origin == null) {
			ORIGINS.put(compiled, new Origin(pipeline, null));
		}
	}

	static void remember(RenderPipeline pipeline, CompiledRenderPipeline compiled, ShaderSource source) {
		ORIGINS.put(compiled, new Origin(pipeline, source));
	}

	public static CompiledRenderPipeline adapt(List<RenderPassDescriptor.@Nullable Attachment<Optional<org.joml.Vector4fc>>> attachments, CompiledRenderPipeline pipeline) {
		if (!(pipeline instanceof FrontendRenderPipeline frontend) || fits(attachments, frontend.colorTargetStates())) {
			return pipeline;
		}

		Origin origin = ORIGINS.get(pipeline);

		if (origin == null || FAILED.contains(pipeline)) {
			return pipeline;
		}

		List<GpuFormat> formats = new ArrayList<>();

		for (RenderPassDescriptor.Attachment<Optional<org.joml.Vector4fc>> attachment : attachments) {
			formats.add(attachment == null ? null : attachment.textureView().texture().getFormat());
		}

		CompiledRenderPipeline twin = TWINS.computeIfAbsent(origin.pipeline, key -> new HashMap<>()).get(formats);

		if (twin != null && !twin.isClosed()) {
			return twin;
		}

		twin = compileNow(origin, formats);

		if (twin == null) {
			FAILED.add(pipeline);
			SkaffySAPIClient.LOGGER.warn("Shaders: couldn't adapt {} to {}", origin.pipeline.getLocation(), formats);
			return pipeline;
		}

		TWINS.get(origin.pipeline).put(formats, twin);
		return twin;
	}

	private static boolean fits(List<RenderPassDescriptor.@Nullable Attachment<Optional<org.joml.Vector4fc>>> attachments, List<@Nullable ColorTargetState> targets) {
		if (attachments.size() != targets.size()) {
			return false;
		}

		for (int i = 0; i < attachments.size(); i++) {
			RenderPassDescriptor.Attachment<Optional<org.joml.Vector4fc>> attachment = attachments.get(i);

			if (attachment != null && (targets.get(i) == null || targets.get(i).format() != attachment.textureView().texture().getFormat())) {
				return false;
			}
		}

		return true;
	}

	private static @Nullable CompiledRenderPipeline compileNow(Origin origin, List<GpuFormat> formats) {
		ShaderSource source = origin.source != null ? origin.source : vanillaSource();

		if (source == null) {
			return null;
		}

		try {
			return RenderSystem.getDevice().compilePipeline(twin(origin.pipeline, formats), source, Runnable::run).join().finishCompile();
		} catch (RuntimeException e) {
			SkaffySAPIClient.LOGGER.error("Shaders: building a copy of {} failed", origin.pipeline.getLocation(), e);
			return null;
		}
	}

	private static @Nullable ShaderSource vanillaSource() {
		PipelineCache cache = RenderSystemAccessor.skaffy$currentPipelineCache();

		if (cache == null) {
			cache = RenderSystemAccessor.skaffy$fallbackPipelineCache();
		}

		return cache == null ? null : ((PipelineCacheAccessor) cache).skaffy$shaderSource();
	}

	static RenderPipeline twin(RenderPipeline original, List<GpuFormat> formats) {
		ColorTargetState[] targets = new ColorTargetState[ColorTargetState.MAX_COLOR_TARGETS];
		List<ColorTargetState> own = original.getColorTargetStates();

		for (int i = 0; i < formats.size(); i++) {
			if (formats.get(i) == null) {
				continue;
			}

			ColorTargetState mine = i < own.size() ? own.get(i) : null;
			targets[i] = mine != null ? new ColorTargetState(mine.blendFunction(), formats.get(i), mine.writeMask())
					: new ColorTargetState(Optional.empty(), formats.get(i), ColorTargetState.WRITE_NONE);
		}

		VertexFormat[] vertexFormats = new VertexFormat[16];
		List<VertexFormat> bindings = original.getVertexFormatBindings();

		for (int i = 0; i < bindings.size() && i < vertexFormats.length; i++) {
			vertexFormats[i] = bindings.get(i);
		}

		RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(original.getShaders(), Optional.of(original.getShaderDefines()), Optional.of(original.getBindGroupLayouts()),
				targets, formats.size(), Optional.ofNullable(original.getDepthStencilState()), Optional.of(original.getPolygonMode()), Optional.of(original.isCull()),
				vertexFormats, Optional.of(original.getPrimitiveTopology()), original.pushConstantSize());
		return RenderPipeline.builder(snippet)
				.withLocation(Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "twin/" + nextId++ + "/" + original.getLocation().getNamespace() + "/" + original.getLocation().getPath()))
				.build();
	}

	static void prepare(FrameLayout layout) {
		if (layout.equals(FrameLayout.VANILLA)) {
			return;
		}

		ShaderSource source = vanillaSource();

		if (source == null) {
			return;
		}

		List<List<GpuFormat>> signatures = new ArrayList<>();
		signatures.add(List.of(layout.main()));

		if (layout.hasOutputs()) {
			signatures.add(layout.formats());
		}

		for (RenderPipeline pipeline : WorldPrograms.vanillaPipelines()) {
			List<ColorTargetState> targets = pipeline.getColorTargetStates();

			if (targets.size() != 1 || targets.getFirst() == null || targets.getFirst().format() != GpuFormat.RGBA8_UNORM || menuOnly(pipeline)) {
				continue;
			}

			for (List<GpuFormat> formats : signatures) {
				Map<List<GpuFormat>, CompiledRenderPipeline> twins = TWINS.computeIfAbsent(pipeline, key -> new HashMap<>());

				if (twins.containsKey(formats) || PENDING.stream().anyMatch(pending -> pending.original == pipeline && pending.formats.equals(formats))) {
					continue;
				}

				PENDING.add(new Pending(pipeline, formats, RenderSystem.getDevice().compilePipeline(twin(pipeline, formats), source, Util.backgroundExecutor())));
			}
		}
	}

	private static boolean menuOnly(RenderPipeline pipeline) {
		String path = pipeline.getLocation().getPath();
		return path.startsWith("pipeline/gui") || path.equals("pipeline/panorama") || path.equals("pipeline/mojang_logo") || path.equals("pipeline/lightmap")
				|| path.startsWith("pipeline/animate_sprite") || path.equals("pipeline/tracy_blit");
	}

	static boolean progress() {
		for (int i = 0; i < PENDING.size(); i++) {
			Pending pending = PENDING.get(i);

			if (!pending.future.isDone()) {
				continue;
			}

			PENDING.remove(i--);

			try {
				CompiledRenderPipeline compiled = pending.future.join().finishCompile();

				if (compiled != null) {
					TWINS.computeIfAbsent(pending.original, key -> new HashMap<>()).put(pending.formats, compiled);
				}
			} catch (RuntimeException e) {
				SkaffySAPIClient.LOGGER.warn("Shaders: a copy of {} didn't build: {}", pending.original.getLocation(), e.toString());
			}
		}

		return PENDING.isEmpty();
	}

	static void clear() {
		for (Pending pending : PENDING) {
			pending.future.thenAccept(result -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
				CompiledRenderPipeline leftover = result.finishCompile();

				if (leftover != null) {
					leftover.close();
				}
			}));
		}

		PENDING.clear();
		TWINS.values().forEach(map -> map.values().forEach(CompiledRenderPipeline::close));
		TWINS.clear();
		FAILED.clear();
		ORIGINS.keySet().removeIf(CompiledRenderPipeline::isClosed);
	}
}
