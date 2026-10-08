package me.skaffy.client.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.util.ShaderCompileException;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.shader.lang.Builtins;
import me.skaffy.client.shader.lang.Builtins.Program;
import me.skaffy.client.shader.lang.Builtins.Stage;
import me.skaffy.client.shader.lang.ShaderModule;
import me.skaffy.protocol.Protocol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;

final class ShaderEffect implements AutoCloseable {
	private static int nextId;
	private static final int FINISH_PER_FRAME = 12;

	enum Status {
		LOADING,
		READY,
		FAILED,
		CLOSED
	}

	final ShaderModule module;
	final String className;
	final int id;
	final FrameLayout layout;
	Status status = Status.LOADING;
	@Nullable String failure;

	final List<PassRuntime> passes = new ArrayList<>();
	final Map<Program, Map<RenderPipeline, CompiledRenderPipeline>> world = new EnumMap<>(Program.class);
	final Map<RenderPipeline, CompiledRenderPipeline> shadowSwap = new IdentityHashMap<>();
	final Map<ChunkSectionLayer, CompiledRenderPipeline> shadowTerrain = new EnumMap<>(ChunkSectionLayer.class);
	@Nullable CompiledRenderPipeline shadowSeeThrough;
	final Map<String, Sampled> textures = new HashMap<>();
	final Map<String, TargetRuntime> targets = new HashMap<>();
	final @Nullable MappableRingBuffer uniformBuffer;
	final UniformValues values;

	private final List<Compile> pending = new ArrayList<>();
	private final List<EffectShaderSource> sources = new ArrayList<>();
	private Map<Identifier, ShaderSource.CachedIncludeSource> includes = Map.of();

	@FunctionalInterface
	interface Assets {
		byte @Nullable [] read(String id) throws IOException;

		static Assets of(@Nullable DiskAssetStore store) {
			return id -> store != null && store.size(id) >= 0 ? store.read(id) : null;
		}
	}

	record Sampled(GpuTexture texture, GpuTextureView view, GpuSampler sampler) {
	}

	static final class PassRuntime {
		final ShaderModule.Pass pass;
		final RenderPipeline pipeline;
		@Nullable CompiledRenderPipeline compiled;

		PassRuntime(ShaderModule.Pass pass, RenderPipeline pipeline) {
			this.pass = pass;
			this.pipeline = pipeline;
		}
	}

	static final class TargetRuntime {
		final ShaderModule.Target target;
		@Nullable GpuTexture texture;
		@Nullable GpuTextureView view;
		int width;
		int height;

		TargetRuntime(ShaderModule.Target target) {
			this.target = target;
		}

		void close() {
			if (view != null) {
				view.close();
			}

			if (texture != null) {
				texture.close();
			}

			view = null;
			texture = null;
		}
	}

	private record Compile(RenderPipeline pipeline, ShaderSource source, CompletableFuture<CompiledRenderPipeline.Pending> future, Program program,
			RenderPipeline vanilla, PassRuntime pass, boolean optional, boolean shadow, ChunkSectionLayer layer, boolean seeThrough) {
	}

	ShaderEffect(ShaderModule module, UniformValues values, FrameLayout layout) {
		this.module = module;
		this.className = module.className();
		this.id = nextId++;
		this.values = values;
		this.layout = layout;
		this.uniformBuffer = module.uniformBlockSize() > 0
				? new MappableRingBuffer(() -> "Skaffy shader uniforms " + className, GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, module.uniformBlockSize())
				: null;
	}


	void start(Assets assets) {
		GpuDevice device = RenderSystem.getDevice();
		Minecraft minecraft = Minecraft.getInstance();
		includes = ShaderManager.listAllIncludes(minecraft.getResourceManager());

		for (ShaderModule.TextureSource texture : module.textures()) {
			textures.put(texture.sampler(), loadTexture(texture, assets));
		}

		for (ShaderModule.Target target : module.targets()) {
			targets.put(target.sampler(), new TargetRuntime(target));
		}

		EffectShaderSource passSource = new EffectShaderSource(includes, false);
		sources.add(passSource);
		List<Compile> toStart = new ArrayList<>();

		for (int i = 0; i < module.passes().size(); i++) {
			passSource.shader(Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "e" + id + "/pass_" + i), ShaderModule.PASS_VERTEX, module.passes().get(i).fragment());
		}

		for (int i = 0; i < module.passes().size(); i++) {
			ShaderModule.Pass pass = module.passes().get(i);
			Identifier shader = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "e" + id + "/pass_" + i);
			BindGroupLayout.Builder bindings = BindGroupLayout.builder().withUniform("SkFrame", UniformType.UNIFORM_BUFFER);

			if (uniformBuffer != null) {
				bindings.withUniform("SkUniforms", UniformType.UNIFORM_BUFFER);
			}

			for (String sampler : pass.samplers()) {
				bindings.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER);
			}

			GpuFormat screen = pass.stage() == Stage.FINAL ? GpuFormat.RGBA8_UNORM : layout.main();
			GpuFormat format = pass.output() == null ? screen : format(targetOf(pass.output()).target.format());
			int mask = pass.output() == null ? ColorTargetState.WRITE_COLOR : ColorTargetState.WRITE_ALL;
			RenderPipeline pipeline = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "e" + id + "/pass_" + i + "_" + pass.name().toLowerCase(Locale.ROOT)))
					.withVertexShader(shader)
					.withFragmentShader(shader)
					.withBindGroupLayout(bindings.build())
					.withColorTargetState(new ColorTargetState(blend(pass.blend()), format, mask))
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.withCull(false)
					.build();
			PassRuntime runtime = new PassRuntime(pass, pipeline);
			passes.add(runtime);
			toStart.add(new Compile(pipeline, passSource, null, null, null, runtime, false, false, null, false));
		}

		Map<Program, List<WorldPrograms.Variant>> variants = new EnumMap<>(Program.class);
		List<WorldPrograms.Variant> all = WorldPrograms.all();

		for (WorldPrograms.Variant variant : all) {
			if (module.hooks().containsKey(variant.program())) {
				variants.computeIfAbsent(variant.program(), key -> new ArrayList<>()).add(variant);
			}
		}

		for (Map.Entry<Program, List<WorldPrograms.Variant>> entry : variants.entrySet()) {
			Program program = entry.getKey();
			ShaderModule.Hook hook = module.hooks().get(program);
			EffectShaderSource source = hookSource(hook, layout.locations(className));
			String base = "e" + id + "/" + program.name().toLowerCase(Locale.ROOT);
			BindGroupLayout extra = bindings(hook);
			world.put(program, new IdentityHashMap<>());

			for (WorldPrograms.Variant variant : entry.getValue()) {
				Identifier template = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, base + "/" + variant.template());
				source.shader(template, EffectShaderSource.template(variant.template() + ".vsh"), EffectShaderSource.template(variant.template() + ".fsh"));
			}

			for (WorldPrograms.Variant variant : entry.getValue()) {
				if (variant.vanilla().getPolygonMode() == PolygonMode.WIREFRAME && !device.getDeviceInfo().features().wireframeFillMode()) {
					continue;
				}

				Identifier location = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, base + "/" + variant.vanilla().getLocation().getPath());
				RenderPipeline pipeline = WorldPrograms.replacement(variant, location, base, extra, layout, hook);
				toStart.add(new Compile(pipeline, source, null, program, variant.vanilla(), null, variant.optional(), false, null, false));
			}
		}

		for (Map.Entry<Program, ShaderModule.Hook> entry : module.shadowHooks().entrySet()) {
			Program kind = entry.getKey();
			ShaderModule.Hook hook = entry.getValue();
			EffectShaderSource source = hookSource(hook, Map.of());
			String base = "e" + id + "/shadow_" + kind.name().toLowerCase(Locale.ROOT);
			BindGroupLayout extra = bindings(hook);

			if (kind == Program.TERRAIN) {
				Identifier template = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, base + "/shadow_terrain");
				source.shader(template, EffectShaderSource.template("shadow_terrain.vsh"), EffectShaderSource.template("shadow_terrain.fsh"));

				for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
					Identifier location = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, base + "/" + layer.label());
					toStart.add(new Compile(WorldPrograms.shadowTerrain(layer, location, base, extra, false), source, null, kind, null, null, false, true, layer, false));
				}

				Identifier seeThrough = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, base + "/cutout_see_through");
				toStart.add(new Compile(WorldPrograms.shadowTerrain(ChunkSectionLayer.CUTOUT, seeThrough, base, extra, true), source, null, kind, null, null, false, true,
						ChunkSectionLayer.CUTOUT, true));

				continue;
			}

			List<WorldPrograms.Variant> drawn = all.stream().filter(variant -> variant.program() == kind && variant.mainPass()
					&& variant.vanilla().getPolygonMode() != PolygonMode.WIREFRAME).toList();

			for (WorldPrograms.Variant variant : drawn) {
				Identifier template = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, base + "/" + variant.template());
				source.shader(template, EffectShaderSource.template(variant.template() + ".vsh"), EffectShaderSource.template(variant.template() + ".fsh"));
			}

			for (WorldPrograms.Variant variant : drawn) {
				Identifier location = Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, base + "/" + variant.vanilla().getLocation().getPath());
				toStart.add(new Compile(WorldPrograms.shadowEntity(variant, location, base, extra), source, null, kind, variant.vanilla(), null, true, true, null, false));
			}
		}

		for (Compile compile : toStart) {
			pending.add(new Compile(compile.pipeline, compile.source, device.compilePipeline(compile.pipeline, compile.source, Util.backgroundExecutor()),
					compile.program, compile.vanilla, compile.pass, compile.optional, compile.shadow, compile.layer, compile.seeThrough));
		}

		SkaffySAPIClient.LOGGER.info("Building shader {}: {} passes, {} world pipelines", className, passes.size(), pending.size() - passes.size());
	}

	private EffectShaderSource hookSource(ShaderModule.Hook hook, Map<String, Integer> locations) {
		EffectShaderSource source = new EffectShaderSource(includes, false);
		sources.add(source);
		source.include(Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "hooks_vertex.glsl"), hook.vertexInclude());
		source.include(Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "hooks_fragment.glsl"), hook.fragmentInclude(locations, module.outputs()));
		source.include(Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "template.glsl"), EffectShaderSource.template("template.glsl"));
		source.include(Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "sky_common.glsl"), EffectShaderSource.template("sky_common.glsl"));
		return source;
	}

	private BindGroupLayout bindings(ShaderModule.Hook hook) {
		BindGroupLayout.Builder layout = BindGroupLayout.builder().withUniform("SkFrame", UniformType.UNIFORM_BUFFER);

		if (uniformBuffer != null) {
			layout.withUniform("SkUniforms", UniformType.UNIFORM_BUFFER);
		}

		for (String sampler : hook.samplers()) {
			layout.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER);
		}

		return layout.build();
	}

	private TargetRuntime targetOf(String name) {
		for (TargetRuntime target : targets.values()) {
			if (target.target.name().equals(name)) {
				return target;
			}
		}

		throw new IllegalStateException("No target " + name);
	}

	static GpuFormat format(Builtins.Format format) {
		return format == Builtins.Format.RGBA8 ? GpuFormat.RGBA8_UNORM : GpuFormat.RGBA16_FLOAT;
	}

	private static Optional<BlendFunction> blend(Builtins.Blend blend) {
		return switch (blend) {
			case REPLACE -> Optional.empty();
			case ADD -> Optional.of(BlendFunction.ADDITIVE);
			case ALPHA -> Optional.of(BlendFunction.TRANSLUCENT);
			case MULTIPLY -> Optional.of(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO, BlendFactor.ZERO, BlendFactor.ONE));
		};
	}

	boolean progress() {
		if (status != Status.LOADING) {
			return false;
		}

		int finished = 0;

		for (int i = 0; i < pending.size() && finished < FINISH_PER_FRAME; i++) {
			Compile compile = pending.get(i);

			if (!compile.future.isDone()) {
				continue;
			}

			pending.remove(i--);
			finished++;
			CompiledRenderPipeline compiled;

			try {
				compiled = compile.future.join().finishCompile();
			} catch (RuntimeException e) {
				SkaffySAPIClient.LOGGER.error("Building {} for shader {} crashed", compile.pipeline.getLocation(), className, e);
				compiled = null;
			}

			if (compiled == null && compile.optional) {
				SkaffySAPIClient.LOGGER.warn("Shader {}: skipped {} (vanilla can do without it)", className, compile.pipeline.getLocation());
				continue;
			}

			if (compiled == null) {
				String what = compile.pass != null ? "pass " + compile.pass.pass.name()
						: compile.shadow ? "the shadow map's " + (compile.layer != null ? compile.layer.label() + " blocks" : compile.vanilla.getLocation())
						: compile.program + " for " + compile.vanilla.getLocation();
				fail("Couldn't build " + what + ": " + diagnose(compile));
				return true;
			}

			PipelineTwins.remember(compile.pipeline, compiled, compile.source);

			if (compile.pass != null) {
				compile.pass.compiled = compiled;
			} else if (compile.seeThrough) {
				shadowSeeThrough = compiled;
			} else if (compile.layer != null) {
				shadowTerrain.put(compile.layer, compiled);
			} else if (compile.shadow) {
				shadowSwap.put(compile.vanilla, compiled);
			} else {
				world.get(compile.program).put(compile.vanilla, compiled);
			}
		}

		if (pending.isEmpty()) {
			status = Status.READY;
			SkaffySAPIClient.LOGGER.info("Shader {} is ready", className);
			return true;
		}

		return false;
	}

	private String diagnose(Compile compile) {
		GpuDevice device = RenderSystem.getDevice();

		try (GlslCompiler compiler = new GlslCompiler(device.getDeviceInfo().isZZeroToOne(), device.getDeviceInfo().features().shaderDrawParameters())) {
			for (ShaderType type : ShaderType.values()) {
				Identifier id = compile.pipeline.getShaders().get(type);
				String source = compile.source.getShader(id, type);

				if (source == null) {
					return "the " + type.getName() + " shader is missing";
				}

				try {
					compiler.compileToSpv(id.toString(), source, type, compile.pipeline.getShaderDefines(), compile.source).close();
				} catch (ShaderCompileException e) {
					return e.getMessage();
				}
			}
		} catch (RuntimeException e) {
			return e.toString();
		}

		return "the graphics driver rejected it (see the client log)";
	}

	private void fail(String reason) {
		status = Status.FAILED;
		failure = reason;
		SkaffySAPIClient.LOGGER.error("Shader {} failed: {}", className, reason);
		dropPending();
	}

	private void dropPending() {
		List<CompletableFuture<?>> running = new ArrayList<>();

		for (Compile compile : pending) {
			running.add(compile.future.thenAccept(result -> Minecraft.getInstance().execute(() -> {
				CompiledRenderPipeline leftover = result.finishCompile();

				if (leftover != null) {
					leftover.close();
				}
			})));
		}

		pending.clear();
		List<EffectShaderSource> oldSources = List.copyOf(sources);
		Map<Identifier, ShaderSource.CachedIncludeSource> oldIncludes = includes;
		sources.clear();
		includes = Map.of();
		CompletableFuture.allOf(running.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
			oldSources.forEach(EffectShaderSource::close);
			oldIncludes.values().forEach(ShaderSource.CachedIncludeSource::close);
		}));
	}

	private void closeSources() {
		sources.forEach(EffectShaderSource::close);
		sources.clear();
		includes.values().forEach(ShaderSource.CachedIncludeSource::close);
		includes = Map.of();
	}


	private Sampled loadTexture(ShaderModule.TextureSource source, Assets assets) {
		byte[] png = null;

		try {
			if (source.source().contains(":")) {
				Identifier id = Identifier.tryParse(source.source().toLowerCase(Locale.ROOT));

				if (id != null) {
					PackResources vanilla = Minecraft.getInstance().getVanillaPackResources().fullResources();
					IoSupplier<java.io.InputStream> resource = vanilla.getResource(PackType.CLIENT_RESOURCES, id.withPath("textures/" + id.getPath() + ".png"));

					if (resource != null) {
						try (java.io.InputStream stream = resource.get()) {
							png = stream.readAllBytes();
						}
					}
				}
			} else if (Protocol.isValidAssetId(source.source())) {
				png = assets.read(source.source());
			}
		} catch (IOException e) {
			png = null;
		}

		GpuDevice device = RenderSystem.getDevice();
		AddressMode wrap = source.wrap() == Builtins.Wrap.REPEAT ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
		FilterMode filter = source.filter() == Builtins.Filter.LINEAR ? FilterMode.LINEAR : FilterMode.NEAREST;
		GpuSampler sampler = RenderSystem.getSamplerCache().getSampler(wrap, wrap, filter, filter, false);

		if (png != null) {
			try (NativeImage image = NativeImage.read(png)) {
				GpuTexture texture = device.createTexture(() -> "Skaffy shader texture " + source.source(), GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
						GpuFormat.RGBA8_UNORM, image.getWidth(), image.getHeight(), 1, 1);
				device.createCommandEncoder().writeToTexture(texture, image);
				return new Sampled(texture, device.createTextureView(texture), sampler);
			} catch (IOException | RuntimeException e) {
				ClientShaders.report(className, me.skaffy.protocol.shaders.ShadersPacket.ErrorKind.REQUEST, "Texture " + source.name() + " (" + source.source() + ") isn't a readable PNG: " + e.getMessage());
			}
		} else {
			ClientShaders.report(className, me.skaffy.protocol.shaders.ShadersPacket.ErrorKind.REQUEST, "Texture " + source.name() + ": " + source.source()
					+ (source.source().contains(":") ? " isn't a vanilla texture" : " isn't an asset the client has"));
		}

		GpuTexture white = device.createTexture(() -> "Skaffy shader texture (missing)", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);

		try (NativeImage image = new NativeImage(1, 1, false)) {
			image.setPixel(0, 0, 0xFFFFFFFF);
			device.createCommandEncoder().writeToTexture(white, image);
		}

		return new Sampled(white, device.createTextureView(white), sampler);
	}


	void writeUniforms(long now) {
		values.tick(now);

		if (uniformBuffer == null || !values.consumeDirty()) {
			return;
		}

		uniformBuffer.rotate();

		try (GpuBufferSlice.MappedView mapped = uniformBuffer.currentBuffer().map(false, true)) {
			values.write(mapped.data());
		}
	}

	@Nullable GpuBufferSlice uniforms() {
		return uniformBuffer == null ? null : uniformBuffer.currentBuffer().slice();
	}

	double setting(ShaderModule.@Nullable Setting setting, double fallback) {
		if (setting == null) {
			return fallback;
		}

		return setting.uniform() != null ? values.value(setting.uniform(), setting.value()) : setting.value();
	}

	float[] color(ShaderModule.Light light) {
		double[] value = light.uniform() != null ? values.values(light.uniform()) : light.color();
		return value == null || value.length < 3 ? new float[] {1, 1, 1} : new float[] {(float) value[0], (float) value[1], (float) value[2]};
	}

	TargetRuntime target(String sampler, int screenWidth, int screenHeight) {
		TargetRuntime target = targets.get(sampler);
		int width = target.target.width() > 0 ? target.target.width() : Math.max(1, Math.round(screenWidth * target.target.scale()));
		int height = target.target.height() > 0 ? target.target.height() : Math.max(1, Math.round(screenHeight * target.target.scale()));

		if (target.texture == null || target.width != width || target.height != height) {
			target.close();
			GpuDevice device = RenderSystem.getDevice();
			target.texture = device.createTexture(() -> "Skaffy shader target " + className + "." + target.target.name(), 15, format(target.target.format()), width, height, 1, 1);
			target.view = device.createTextureView(target.texture);
			target.width = width;
			target.height = height;
			device.createCommandEncoder().clearColorTexture(target.texture, new org.joml.Vector4f(0, 0, 0, 0));
		}

		return target;
	}

	void clearTargets() {
		for (TargetRuntime target : targets.values()) {
			if (!target.target.persistent() && target.texture != null) {
				RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.texture, new org.joml.Vector4f(0, 0, 0, 0));
			}
		}
	}

	boolean hasPasses(Stage stage) {
		for (PassRuntime pass : passes) {
			if (pass.pass.stage() == stage) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void close() {
		if (status == Status.LOADING) {
			dropPending();
		}

		status = Status.CLOSED;
		closeSources();
		passes.forEach(pass -> {
			if (pass.compiled != null) {
				pass.compiled.close();
			}
		});
		world.values().forEach(map -> map.values().forEach(CompiledRenderPipeline::close));
		world.clear();
		shadowSwap.values().forEach(CompiledRenderPipeline::close);
		shadowSwap.clear();
		shadowTerrain.values().forEach(CompiledRenderPipeline::close);
		shadowTerrain.clear();

		if (shadowSeeThrough != null) {
			shadowSeeThrough.close();
			shadowSeeThrough = null;
		}
		textures.values().forEach(sampled -> {
			sampled.view.close();
			sampled.texture.close();
		});
		textures.clear();
		targets.values().forEach(TargetRuntime::close);

		if (uniformBuffer != null) {
			uniformBuffer.close();
		}
	}
}
