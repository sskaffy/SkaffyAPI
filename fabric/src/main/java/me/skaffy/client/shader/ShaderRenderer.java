package me.skaffy.client.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.mixin.RenderTargetAccessor;
import me.skaffy.client.shader.ShaderEffect.PassRuntime;
import me.skaffy.client.shader.ShaderEffect.TargetRuntime;
import me.skaffy.client.shader.lang.Builtins;
import me.skaffy.client.shader.lang.Builtins.Program;
import me.skaffy.client.shader.lang.Builtins.Stage;
import me.skaffy.client.shader.lang.ShaderModule;
import me.skaffy.protocol.Easing;
import me.skaffy.protocol.shaders.ShadersPacket.ErrorKind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class ShaderRenderer {
	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
	private static List<Entry> sorted = List.of();
	private static final Map<RenderPipeline, CompiledRenderPipeline> SWAP = new IdentityHashMap<>();
	private static final Map<CompiledRenderPipeline, ShaderEffect> OURS = new IdentityHashMap<>();
	private static final Set<CompiledRenderPipeline> SHADOW_PIPELINES = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<String, Scratch> SCRATCH = new HashMap<>();
	private static final Set<String> OVERFLOW_REPORTED = new HashSet<>();
	private static @Nullable FrameUniforms frame;
	private static @Nullable DiskAssetStore assets;
	private static boolean dirty;
	private static boolean drawingHand;
	private static boolean anyPasses;
	private static @Nullable GpuTexture depthCopy;
	private static @Nullable GpuTextureView depthCopyView;
	private static @Nullable Map<Integer, List<String>> terrainMaterials;
	private static float sunPathRotation;

	private static FrameLayout wantedLayout = FrameLayout.VANILLA;
	private static FrameLayout activeLayout = FrameLayout.VANILLA;
	private static @Nullable ShaderEffect shadowOwner;
	private static @Nullable ShadowMap shadowMap;
	private static float shadowDistance = 128;
	private static int shadowResolution = 2048;
	private static @Nullable ShaderEffect drawingShadow;
	private static int shadowCascade = -1;
	private static boolean skipDraws;
	private static @Nullable ColoredLight coloredLight;
	private static @Nullable ShaderEffect lightOwner;
	private static @Nullable CloudShadows clouds;
	private static boolean taa;
	private static final Vector2f TAA_OFFSET = new Vector2f();
	private static int taaIndex;
	private static boolean splitSolid;
	private static boolean usesSolid;
	private static final List<Scratch> OUTPUTS = new ArrayList<>();
	private static @Nullable Scratch solid;
	private static @Nullable Scratch solidDepth;
	private static @Nullable Scratch hdr;
	private static @Nullable GpuTexture savedColor;
	private static @Nullable GpuTextureView savedColorView;
	private static boolean screenshotWaiting;
	private static @Nullable Blank blank;

	private static final class Entry {
		final String className;
		final boolean builtin;
		final ShaderEffect.@Nullable Assets builtinAssets;
		int order;
		@Nullable ShaderModule module;
		@Nullable UniformValues values;
		@Nullable ShaderEffect effect;
		boolean running;

		Entry(String className, int order, ShaderEffect.@Nullable Assets builtinAssets) {
			this.className = className;
			this.order = order;
			this.builtin = builtinAssets != null;
			this.builtinAssets = builtinAssets;
		}
	}

	private record Scratch(GpuTexture texture, GpuTextureView view) {
		void close() {
			view.close();
			texture.close();
		}
	}

	private record Blank(Scratch black, Scratch white) {
	}

	private ShaderRenderer() {
	}

	static void assets(@Nullable DiskAssetStore store) {
		assets = store;
	}


	static void enable(String className, int order, Map<String, Object> uniforms, @Nullable ShaderModule module) {
		Entry entry = ENTRIES.get(className);

		if (entry == null) {
			entry = new Entry(className, order, null);
			ENTRIES.put(className, entry);
			use(entry, module);
		}

		entry.order = order;
		setUniforms(entry, uniforms, 0, Easing.LINEAR);
		resort();
	}

	static void enableBuiltin(String className, ShaderModule module, ShaderEffect.Assets assets) {
		if (ENTRIES.containsKey(className)) {
			return;
		}

		Entry entry = new Entry(className, Integer.MIN_VALUE, assets);
		ENTRIES.put(className, entry);
		use(entry, module);
		resort();
	}

	static void resetUniforms(String className) {
		Entry entry = ENTRIES.get(className);

		if (entry != null && entry.values != null) {
			entry.values.reset();
		}
	}

	static void disable(String className) {
		Entry entry = ENTRIES.remove(className);

		if (entry != null) {
			stop(entry);
			resort();
		}
	}

	static void disableAll() {
		for (Entry entry : List.copyOf(ENTRIES.values())) {
			if (entry.builtin) {
				continue;
			}

			ENTRIES.remove(entry.className);
			stop(entry);
		}

		resort();
	}

	static boolean isEnabled(String className) {
		return ENTRIES.containsKey(className);
	}

	static boolean isRunning(String className) {
		Entry entry = ENTRIES.get(className);
		return entry != null && entry.running;
	}

	static void setUniforms(String className, Map<String, Object> values, int duration, Easing easing) {
		Entry entry = ENTRIES.get(className);

		if (entry == null) {
			ClientShaders.report(className, ErrorKind.REQUEST, "Can't set uniforms of " + className + ": it isn't enabled");
			return;
		}

		setUniforms(entry, values, duration, easing);
	}

	private static void setUniforms(Entry entry, Map<String, Object> values, int duration, Easing easing) {
		if (values.isEmpty()) {
			return;
		}

		if (entry.values == null) {
			ClientShaders.report(entry.className, ErrorKind.REQUEST, "Can't set uniforms of " + entry.className + " yet: the file isn't loaded (not sent, or it has compile errors)");
			return;
		}

		for (String problem : entry.values.set(values, duration, easing, Util.getMillis())) {
			ClientShaders.report(entry.className, ErrorKind.REQUEST, problem);
		}
	}

	static void fileCompiled(String className, @Nullable ShaderModule module) {
		Entry entry = ENTRIES.get(className);

		if (entry != null) {
			stop(entry);
			use(entry, module);
			resort();
		}
	}

	private static void use(Entry entry, @Nullable ShaderModule module) {
		entry.module = module;

		if (module == null) {
			return;
		}

		if (entry.values == null) {
			entry.values = new UniformValues(module);
		} else {
			entry.values.use(module);
		}
	}

	private static void stop(Entry entry) {
		if (entry.effect != null) {
			entry.effect.close();
			entry.effect = null;
		}

		if (entry.running) {
			entry.running = false;
			ClientShaders.sendState(entry.className, false);
		}

		dirty = true;
	}

	private static void resort() {
		List<Entry> list = new ArrayList<>(ENTRIES.values());
		list.sort(Comparator.<Entry>comparingInt(entry -> entry.order).thenComparing(entry -> entry.className));
		sorted = list;
		dirty = true;
		relayout();
	}

	private static void relayout() {
		boolean hdrWanted = false;
		List<FrameLayout.Slot> slots = new ArrayList<>();

		for (Entry entry : sorted) {
			if (entry.module == null) {
				continue;
			}

			hdrWanted |= entry.module.settings().hdr();

			for (ShaderModule.Output output : entry.module.outputs()) {
				if (slots.size() < FrameLayout.MAX_OUTPUTS) {
					slots.add(new FrameLayout.Slot(entry.className, output.sampler(), ShaderEffect.format(output.format())));
				} else if (OVERFLOW_REPORTED.add(entry.className + "." + output.name())) {
					ClientShaders.report(entry.className, ErrorKind.RUNTIME, "@Output " + output.name() + " is left out: enabled files can have "
							+ FrameLayout.MAX_OUTPUTS + " @Output fields together");
				}
			}
		}

		FrameLayout layout = new FrameLayout(hdrWanted ? GpuFormat.RGBA16_FLOAT : GpuFormat.RGBA8_UNORM, List.copyOf(slots));

		if (!layout.equals(wantedLayout)) {
			wantedLayout = layout;
			PipelineTwins.prepare(layout);
		}

		for (Entry entry : sorted) {
			if (entry.module == null || entry.effect != null && entry.effect.layout.equals(wantedLayout) && entry.effect.module == entry.module) {
				continue;
			}

			if (entry.effect != null) {
				entry.effect.close();
				entry.effect = null;
			}

			ShaderEffect effect = new ShaderEffect(entry.module, entry.values, wantedLayout);
			entry.effect = effect;

			try {
				effect.start(entry.builtin ? entry.builtinAssets : ShaderEffect.Assets.of(assets));
			} catch (RuntimeException e) {
				SkaffySAPIClient.LOGGER.error("Starting shader {} failed", entry.className, e);
				effect.close();
				entry.effect = null;
				ClientShaders.report(entry.className, ErrorKind.RUNTIME, "Couldn't start: " + e);
			}
		}

		dirty = true;
	}

	static void clear() {
		ENTRIES.values().forEach(entry -> entry.running &= entry.builtin);
		disableAll();
		OVERFLOW_REPORTED.clear();
		assets = null;

		if (!ENTRIES.isEmpty()) {
			return;
		}

		wantedLayout = FrameLayout.VANILLA;
		activeLayout = FrameLayout.VANILLA;
		rebuildSwaps();
		PipelineTwins.clear();
		releaseFrameResources();
	}


	public static void beginFrame() {
		Vista.update();

		if (ENTRIES.isEmpty() && !dirty) {
			if (frame != null) {
				releaseFrameResources();
			}

			return;
		}

		Profiler.get().push("skaffy_shaders");

		if (frame == null) {
			frame = new FrameUniforms();
		}

		if (clouds == null) {
			clouds = new CloudShadows();
		}

		frame.rotate();
		clouds.update();
		long now = Util.getMillis();
		boolean twinsReady = PipelineTwins.progress();

		for (Entry entry : sorted) {
			ShaderEffect effect = entry.effect;

			if (effect != null && effect.progress()) {
				dirty = true;

				if (effect.status == ShaderEffect.Status.FAILED) {
					ClientShaders.report(entry.className, ErrorKind.RUNTIME, effect.failure);
					effect.close();
					entry.effect = null;
				}
			}
		}

		if (!wantedLayout.equals(activeLayout) && twinsReady && sorted.stream().allMatch(entry -> entry.effect == null || entry.effect.status != ShaderEffect.Status.LOADING)) {
			activeLayout = wantedLayout;
			dirty = true;
		}

		for (Entry entry : sorted) {
			boolean running = runs(entry.effect);

			if (running != entry.running) {
				entry.running = running;
				ClientShaders.sendState(entry.className, running);
			}

			if (running) {
				entry.effect.writeUniforms(now);
				entry.effect.clearTargets();
			}
		}

		if (dirty) {
			dirty = false;
			rebuildSwaps();
		}

		sunPathRotation = 0;
		shadowDistance = 128;
		shadowResolution = 2048;

		for (Entry entry : sorted) {
			ShaderEffect effect = entry.effect;

			if (!runs(effect)) {
				continue;
			}

			ShaderModule.Settings settings = effect.module.settings();

			if (settings.sunPath() != null) {
				sunPathRotation = (float) effect.setting(settings.sunPath(), 0);
			}

			if (effect == shadowOwner) {
				shadowDistance = (float) Math.clamp(Math.min(effect.setting(settings.shadowDistance(), 128), effectDistance()), 16, 512);
				shadowResolution = Integer.highestOneBit(Math.clamp((int) effect.setting(settings.shadowResolution(), 2048), 512, 4096));
			}
		}

		updateTaa();
		blank();
		Minecraft minecraft = Minecraft.getInstance();

		if (coloredLight != null && lightOwner != null) {
			coloredLight.configure(lightOwner, lightRange(lightOwner));
		}

		if (coloredLight != null && minecraft.level != null) {
			coloredLight.update(minecraft.level, minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos);
		}

		Profiler.get().pop();
	}

	private static boolean runs(@Nullable ShaderEffect effect) {
		return effect != null && effect.status == ShaderEffect.Status.READY && effect.layout.equals(activeLayout);
	}

	public static float sunPathRotation() {
		return sunPathRotation;
	}

	private static void releaseFrameResources() {
		if (frame != null) {
			frame.close();
			frame = null;
		}

		SCRATCH.values().forEach(Scratch::close);
		SCRATCH.clear();
		closeDepthCopy();
		closeOutputs();
		closeScratch(solid);
		closeScratch(solidDepth);
		closeScratch(hdr);
		solid = null;
		solidDepth = null;
		hdr = null;

		if (shadowMap != null) {
			shadowMap.close();
			shadowMap = null;
		}

		if (coloredLight != null) {
			coloredLight.close();
			coloredLight = null;
		}

		lightOwner = null;

		if (clouds != null) {
			clouds.close();
			clouds = null;
		}

		if (blank != null) {
			blank.black.close();
			blank.white.close();
			blank = null;
		}

		sunPathRotation = 0;
	}

	private static void closeScratch(@Nullable Scratch scratch) {
		if (scratch != null) {
			scratch.close();
		}
	}

	private static void closeOutputs() {
		OUTPUTS.forEach(Scratch::close);
		OUTPUTS.clear();
	}

	private static void rebuildSwaps() {
		SWAP.clear();
		OURS.clear();
		SHADOW_PIPELINES.clear();
		Map<Program, ShaderEffect> owners = new EnumMap<>(Program.class);
		anyPasses = false;
		shadowOwner = null;
		lightOwner = null;
		boolean lightUsed = false;
		taa = false;
		splitSolid = false;
		usesSolid = false;

		for (Entry entry : sorted) {
			ShaderEffect effect = entry.effect;

			if (!runs(effect)) {
				continue;
			}

			anyPasses |= !effect.passes.isEmpty();

			for (Program program : effect.world.keySet()) {
				owners.put(program, effect);
			}

			for (Map<RenderPipeline, CompiledRenderPipeline> pipelines : effect.world.values()) {
				pipelines.values().forEach(pipeline -> OURS.put(pipeline, effect));
			}

			effect.passes.forEach(pass -> {
				if (pass.compiled != null) {
					OURS.put(pass.compiled, effect);
				}
			});

			if (effect.module.usesShadows() && effect.shadowTerrain.size() == ChunkSectionLayer.values().length && effect.shadowSeeThrough != null) {
				shadowOwner = effect;
			}

			if (!effect.module.lights().isEmpty()) {
				lightOwner = effect;
			}

			lightUsed |= effect.module.usesLight();
			taa |= effect.module.settings().taa();
			usesSolid |= effect.module.usesSolid();
			splitSolid |= effect.module.usesSolid() || effect.hasPasses(Stage.DEFERRED);
		}

		for (Map.Entry<Program, ShaderEffect> owner : owners.entrySet()) {
			for (Map.Entry<RenderPipeline, CompiledRenderPipeline> pipeline : owner.getValue().world.get(owner.getKey()).entrySet()) {
				SWAP.put(pipeline.getKey(), pipeline.getValue());
			}
		}

		if (shadowOwner != null) {
			ShaderEffect owner = shadowOwner;
			owner.shadowSwap.values().forEach(pipeline -> {
				SHADOW_PIPELINES.add(pipeline);
				OURS.put(pipeline, owner);
			});
			owner.shadowTerrain.values().forEach(pipeline -> {
				SHADOW_PIPELINES.add(pipeline);
				OURS.put(pipeline, owner);
			});
			SHADOW_PIPELINES.add(owner.shadowSeeThrough);
			OURS.put(owner.shadowSeeThrough, owner);

			if (shadowMap == null) {
				shadowMap = new ShadowMap();
			}
		} else if (shadowMap != null) {
			shadowMap.close();
			shadowMap = null;
		}

		if (lightOwner != null && lightUsed) {
			if (coloredLight == null) {
				coloredLight = new ColoredLight();
			}

			coloredLight.configure(lightOwner, lightRange(lightOwner));
		} else if (coloredLight != null) {
			coloredLight.close();
			coloredLight = null;
		}

		ShaderEffect terrain = owners.get(Program.TERRAIN);
		Map<Integer, List<String>> materials = terrain == null ? null : terrain.module.materials();

		if (materials != terrainMaterials || terrain == null && MaterialEncoding.active()) {
			terrainMaterials = materials;
			MaterialEncoding.set(materials);
		}
	}

	private static int lightRange(ShaderEffect owner) {
		int wanted = owner.module.settings().lightRange() > 0 ? owner.module.settings().lightRange() : 128;
		int seen = (int) effectDistance() * 2;
		return Math.clamp(Math.min(wanted, seen) / 32 * 32, 64, 256);
	}

	static float effectDistance() {
		return Math.min(Vista.distance(), Minecraft.getInstance().options.getEffectiveRenderDistance()) * 16f;
	}

	public static void levelProjection(Matrix4f projection) {
		if (frame == null || ENTRIES.isEmpty()) {
			return;
		}

		Matrix4f steady = new Matrix4f(projection);
		Matrix4f jitter = new Matrix4f();

		if (taa) {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			jitter.translation(TAA_OFFSET.x * 2 / main.width, TAA_OFFSET.y * 2 / main.height, 0);
			projection.set(new Matrix4f(jitter).mul(projection));
		}

		frame.update(projection, steady, jitter);
	}

	private static void updateTaa() {
		if (!taa) {
			TAA_OFFSET.set(0, 0);
			return;
		}

		taaIndex = taaIndex % 8 + 1;
		TAA_OFFSET.set(halton(taaIndex, 2) - 0.5F, halton(taaIndex, 3) - 0.5F);
	}

	private static float halton(int index, int base) {
		float result = 0;
		float fraction = 1;

		for (int i = index; i > 0; i /= base) {
			fraction /= base;
			result += fraction * (i % base);
		}

		return result;
	}

	static Vector2fc taaOffset() {
		return TAA_OFFSET;
	}

	static @Nullable ShadowMap shadowMap() {
		return shadowOwner != null ? shadowMap : null;
	}

	static float shadowDistance() {
		return shadowDistance;
	}

	static int shadowResolution() {
		return shadowResolution;
	}

	static @Nullable ColoredLight coloredLight() {
		return coloredLight;
	}

	static @Nullable CloudShadows clouds() {
		return clouds;
	}

	public static boolean hidesBlobShadows() {
		return shadowOwner != null;
	}

	public static boolean needsConsistentDepth() {
		return anyPasses;
	}

	public static void drawingHand(boolean hand) {
		drawingHand = hand;
	}

	private static void prepareFrameTextures() {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		int width = main.width;
		int height = main.height;
		GpuDevice device = RenderSystem.getDevice();
		CommandEncoder encoder = device.createCommandEncoder();

		if (OUTPUTS.size() != activeLayout.outputs().size() || !OUTPUTS.isEmpty() && OUTPUTS.getFirst().texture.getWidth(0) != width
				|| !OUTPUTS.isEmpty() && OUTPUTS.getFirst().texture.getHeight(0) != height) {
			closeOutputs();

			for (FrameLayout.Slot slot : activeLayout.outputs()) {
				GpuTexture texture = device.createTexture(() -> "Skaffy shader output " + slot.className() + "." + slot.sampler(), 15, slot.format(), width, height, 1, 1);
				OUTPUTS.add(new Scratch(texture, device.createTextureView(texture)));
			}
		}

		for (Scratch output : OUTPUTS) {
			encoder.clearColorTexture(output.texture, new Vector4f(0, 0, 0, 0));
		}

		solid = sized(solid, usesSolid, activeLayout.main(), width, height, "solid");
		solidDepth = sized(solidDepth, usesSolid, GpuFormat.D32_FLOAT, width, height, "solid depth");
		hdr = sized(hdr, activeLayout.hdr(), activeLayout.main(), width, height, "hdr color");
	}

	private static @Nullable Scratch sized(@Nullable Scratch current, boolean needed, GpuFormat format, int width, int height, String label) {
		if (current != null && (!needed || current.texture.getWidth(0) != width || current.texture.getHeight(0) != height || current.texture.getFormat() != format)) {
			current.close();
			current = null;
		}

		if (needed && current == null) {
			GpuDevice device = RenderSystem.getDevice();
			GpuTexture texture = device.createTexture(() -> "Skaffy shader " + label, 15, format, width, height, 1, 1);
			current = new Scratch(texture, device.createTextureView(texture));
		}

		return current;
	}


	public static void beginLevel() {
		if (frame == null) {
			return;
		}

		prepareFrameTextures();

		if (hdr == null || savedColor != null) {
			return;
		}

		RenderTargetAccessor main = (RenderTargetAccessor) Minecraft.getInstance().gameRenderer.mainRenderTarget();
		savedColor = main.skaffy$colorTexture();
		savedColorView = main.skaffy$colorTextureView();
		main.skaffy$setColorTexture(hdr.texture);
		main.skaffy$setColorTextureView(hdr.view);
	}

	public static void endLevel() {
		if (savedColor == null) {
			return;
		}

		RenderTargetAccessor main = (RenderTargetAccessor) Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTextureView hdrView = main.skaffy$colorTextureView();
		main.skaffy$setColorTexture(savedColor);
		main.skaffy$setColorTextureView(savedColorView);
		savedColor = null;
		savedColorView = null;
		Resolve.draw(hdrView, main.skaffy$colorTextureView());
	}

	public static boolean deferScreenshot() {
		if (savedColor != null) {
			screenshotWaiting = true;
			return true;
		}

		return false;
	}

	public static boolean takeScreenshotNow() {
		boolean waiting = screenshotWaiting;
		screenshotWaiting = false;
		return waiting;
	}


	public static @Nullable CompiledRenderPipeline swap(RenderPipeline pipeline) {
		if (drawingShadow != null) {
			CompiledRenderPipeline shadow = drawingShadow.shadowSwap.get(pipeline);
			return shadow == null || shadow.isClosed() ? null : shadow;
		}

		if (SWAP.isEmpty() || frame == null || !frame.written() || !(RenderSystem.isRenderingLevel || drawingHand)) {
			return null;
		}

		CompiledRenderPipeline ours = SWAP.get(pipeline);
		return ours == null || ours.isClosed() ? null : ours;
	}

	public static CompiledRenderPipeline adapt(List<RenderPassDescriptor.@Nullable Attachment<Optional<org.joml.Vector4fc>>> attachments, CompiledRenderPipeline pipeline) {
		if (drawingShadow != null) {
			if (SHADOW_PIPELINES.contains(pipeline)) {
				skipDraws = false;
				return pipeline;
			}

			skipDraws = true;
			CompiledRenderPipeline placeholder = drawingShadow.shadowTerrain.get(ChunkSectionLayer.SOLID);
			return placeholder != null ? placeholder : pipeline;
		}

		skipDraws = false;

		if (activeLayout.equals(FrameLayout.VANILLA) && wantedLayout.equals(FrameLayout.VANILLA)) {
			return pipeline;
		}

		CompiledRenderPipeline adapted = PipelineTwins.adapt(attachments, pipeline);

		if (adapted != pipeline && OURS.containsKey(pipeline)) {
			OURS.putIfAbsent(adapted, OURS.get(pipeline));
		}

		return adapted;
	}

	public static boolean skipsDraws() {
		return skipDraws;
	}

	static void shadowCascade(int cascade) {
		shadowCascade = cascade;
	}

	static void drawingShadow(@Nullable ShaderEffect owner) {
		drawingShadow = owner;
		skipDraws = false;
	}

	public static RenderPassDescriptor withOutputs(RenderPassDescriptor descriptor) {
		if (OUTPUTS.isEmpty() || drawingShadow != null || !(RenderSystem.isRenderingLevel || drawingHand) || descriptor.colorAttachments().size() != 1
				|| descriptor.depthAttachment() == null || OUTPUTS.size() != activeLayout.outputs().size()) {
			return descriptor;
		}

		RenderPassDescriptor.Attachment<Optional<org.joml.Vector4fc>> color = descriptor.colorAttachments().getFirst();

		if (color == null || color.textureView() != Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView()) {
			return descriptor;
		}

		RenderPassDescriptor.Builder builder = RenderPassDescriptor.builder(descriptor.label()).withColorAttachment(color.textureView(), color.clearValue());

		for (Scratch output : OUTPUTS) {
			builder.withColorAttachment(output.view);
		}

		return builder.withDepthAttachment(descriptor.depthAttachment().textureView(), descriptor.depthAttachment().clearValue())
				.withRenderArea(descriptor.renderArea())
				.build();
	}

	public static void bind(RenderPass pass, CompiledRenderPipeline pipeline) {
		if (OURS.isEmpty() || frame == null) {
			return;
		}

		ShaderEffect effect = OURS.get(pipeline);

		if (effect == null) {
			return;
		}

		pass.setUniform("SkFrame", shadowCascade >= 0 ? frame.shadow(shadowCascade) : drawingHand ? frame.hand() : frame.world());
		GpuBufferSlice uniforms = effect.uniforms();

		if (uniforms != null) {
			pass.setUniform("SkUniforms", uniforms);
		}

		for (Map.Entry<String, ShaderEffect.Sampled> texture : effect.textures.entrySet()) {
			pass.setUniform(texture.getKey(), texture.getValue().view(), texture.getValue().sampler());
		}

		if (drawingShadow == null) {
			for (String sampler : List.of("SkShadow", "SkShadowSolid", "SkShadowColor", "SkSolid", "SkSolidDepth")) {
				pass.setUniform(sampler, builtin(sampler), samplerFor(sampler));
			}
		}

		for (String sampler : List.of("SkLightA", "SkLightB", "SkLightTint", "SkClouds")) {
			pass.setUniform(sampler, builtin(sampler), samplerFor(sampler));
		}
	}

	private static GpuTextureView builtin(String sampler) {
		GpuTextureView view = switch (sampler) {
			case "SkShadow" -> shadowMap() != null ? shadowMap.depthView() : null;
			case "SkShadowSolid" -> shadowMap() != null ? shadowMap.solidView() : null;
			case "SkShadowColor" -> shadowMap() != null ? shadowMap.colorView() : null;
			case "SkSolid" -> solid != null ? solid.view : null;
			case "SkSolidDepth" -> solidDepth != null ? solidDepth.view : null;
			case "SkLightA" -> coloredLight != null ? coloredLight.view(0) : null;
			case "SkLightB" -> coloredLight != null ? coloredLight.view(1) : null;
			case "SkLightTint" -> coloredLight != null ? coloredLight.view(2) : null;
			case "SkClouds" -> clouds != null ? clouds.view() : null;
			default -> null;
		};

		if (view != null) {
			return view;
		}

		Blank blanks = blank();
		return sampler.equals("SkShadowColor") || sampler.equals("SkShadow") || sampler.equals("SkShadowSolid") || sampler.equals("SkLightTint")
				? blanks.white.view : blanks.black.view;
	}

	private static GpuSampler samplerFor(String sampler) {
		if (sampler.equals("SkClouds")) {
			return CloudShadows.sampler();
		}

		boolean linear = sampler.startsWith("SkLight") || sampler.equals("SkSolid");
		return RenderSystem.getSamplerCache().getClampToEdge(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
	}

	private static Blank blank() {
		if (blank == null) {
			blank = new Blank(onePixel(0x00000000, "black"), onePixel(0xFFFFFFFF, "white"));
		}

		return blank;
	}

	private static Scratch onePixel(int argb, String label) {
		GpuDevice device = RenderSystem.getDevice();
		GpuTexture texture = device.createTexture(() -> "Skaffy shader " + label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);

		try (NativeImage image = new NativeImage(1, 1, false)) {
			image.setPixel(0, 0, argb);
			device.createCommandEncoder().writeToTexture(texture, image);
		}

		return new Scratch(texture, device.createTextureView(texture));
	}


	public static void renderShadows(FeatureRenderDispatcher.PreparedFrame features) {
		if (shadowOwner == null || shadowMap == null || frame == null || !frame.written()) {
			return;
		}

		Profiler.get().push("skaffy_shadow_map");

		try {
			shadowMap.render(shadowOwner, features);
		} catch (RuntimeException e) {
			drawingShadow = null;
			SkaffySAPIClient.LOGGER.error("Drawing the shadow map of {} failed", shadowOwner.className, e);
			ClientShaders.report(shadowOwner.className, ErrorKind.RUNTIME, "Drawing the shadow map failed: " + e);
			Entry entry = ENTRIES.get(shadowOwner.className);

			if (entry != null) {
				stop(entry);
			}
		} finally {
			Profiler.get().pop();
		}
	}

	public static boolean splitsSolid() {
		return splitSolid && frame != null && frame.written();
	}

	public static void afterSolid() {
		runStage(Stage.DEFERRED);

		if (solid == null || solidDepth == null) {
			return;
		}

		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GpuTexture color = main.getColorTexture();
		GpuTexture depth = main.getDepthTexture();

		if (color != null && color.getFormat() == solid.texture.getFormat()) {
			encoder.copyTextureToTexture(color, solid.texture, 0, 0, 0, 0, 0, main.width, main.height);
		}

		if (depth != null && depth.getFormat() == solidDepth.texture.getFormat()) {
			encoder.copyTextureToTexture(depth, solidDepth.texture, 0, 0, 0, 0, 0, main.width, main.height);
		}
	}


	public static void runStage(Stage stage) {
		if (!anyPasses || frame == null || !frame.written()) {
			return;
		}

		Profiler.get().push("skaffy_shaders");

		try {
			runStageInner(stage);
		} finally {
			Profiler.get().pop();
		}
	}

	private static void runStageInner(Stage stage) {
		if (stage == Stage.SCREEN) {
			copyDepthForFinal();
		}

		for (Entry entry : sorted) {
			ShaderEffect effect = entry.effect;

			if (!runs(effect) || !effect.hasPasses(stage)) {
				continue;
			}

			try {
				for (PassRuntime pass : effect.passes) {
					if (pass.pass.stage() == stage) {
						runPass(effect, pass, stage);
					}
				}
			} catch (RuntimeException e) {
				SkaffySAPIClient.LOGGER.error("Shader {} failed while drawing", entry.className, e);
				ClientShaders.report(entry.className, ErrorKind.RUNTIME, "Failed while drawing: " + e);
				stop(entry);
			}
		}
	}

	private static void runPass(ShaderEffect effect, PassRuntime pass, Stage stage) {
		if (pass.compiled == null) {
			return;
		}

		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuDevice device = RenderSystem.getDevice();
		CommandEncoder encoder = device.createCommandEncoder();
		int width = main.width;
		int height = main.height;
		String outputSampler = pass.pass.output() == null ? "SkScreen" : samplerOf(effect, pass.pass.output());
		GpuTextureView output;

		if (pass.pass.output() == null) {
			output = main.getColorTextureView();
		} else {
			TargetRuntime target = effect.target(outputSampler, width, height);
			output = target.view;
		}

		Map<String, GpuTextureView> inputs = new LinkedHashMap<>();
		Map<String, GpuSampler> samplers = new HashMap<>();

		for (String sampler : pass.pass.samplers()) {
			GpuTexture source;
			GpuTextureView view;
			GpuSampler how;

			switch (sampler) {
				case "SkScreen" -> {
					source = main.getColorTexture();
					view = main.getColorTextureView();
					how = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				}
				case "SkDepth" -> {
					source = null;
					view = stage == Stage.FINAL ? depthCopyView : main.getDepthTextureView();
					how = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
				}
				case "SkShadow", "SkShadowSolid", "SkShadowColor", "SkSolid", "SkSolidDepth", "SkLightA", "SkLightB", "SkLightTint", "SkClouds" -> {
					source = null;
					view = builtin(sampler);
					how = samplerFor(sampler);
				}
				default -> {
					ShaderEffect.Sampled texture = effect.textures.get(sampler);
					int slot = outputSlot(effect, sampler);

					if (texture != null) {
						source = texture.texture();
						view = texture.view();
						how = texture.sampler();
					} else if (slot >= 0) {
						source = null;
						view = slot < OUTPUTS.size() ? OUTPUTS.get(slot).view : blank().black.view;
						how = RenderSystem.getSamplerCache().getClampToEdge(outputFilter(effect, sampler));
					} else {
						TargetRuntime target = effect.target(sampler, width, height);
						source = target.texture;
						view = target.view;
						how = RenderSystem.getSamplerCache().getClampToEdge(target.target.filter() == Builtins.Filter.LINEAR ? FilterMode.LINEAR : FilterMode.NEAREST);
					}
				}
			}

			if (view == null) {
				view = main.getDepthTextureView();
			}

			if (sampler.equals(outputSampler) && source != null) {
				Scratch copy = scratch(source.getFormat(), source.getWidth(0), source.getHeight(0));
				encoder.copyTextureToTexture(source, copy.texture, 0, 0, 0, 0, 0, source.getWidth(0), source.getHeight(0));
				view = copy.view;
			}

			inputs.put(sampler, view);
			samplers.put(sampler, how);
		}

		try (RenderPass renderPass = encoder.createRenderPass(() -> "Skaffy shader " + effect.className + "." + pass.pass.name(), output, Optional.empty())) {
			renderPass.setPipeline(pass.compiled);
			renderPass.setUniform("SkFrame", frame.world());
			GpuBufferSlice uniforms = effect.uniforms();

			if (uniforms != null) {
				renderPass.setUniform("SkUniforms", uniforms);
			}

			for (Map.Entry<String, GpuTextureView> input : inputs.entrySet()) {
				renderPass.setUniform(input.getKey(), input.getValue(), samplers.get(input.getKey()));
			}

			renderPass.draw(3, 1, 0, 0);
		}
	}

	private static int outputSlot(ShaderEffect effect, String sampler) {
		List<FrameLayout.Slot> slots = activeLayout.outputs();

		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).className().equals(effect.className) && slots.get(i).sampler().equals(sampler)) {
				return i;
			}
		}

		for (ShaderModule.Output output : effect.module.outputs()) {
			if (output.sampler().equals(sampler)) {
				return Integer.MAX_VALUE;
			}
		}

		return -1;
	}

	private static FilterMode outputFilter(ShaderEffect effect, String sampler) {
		for (ShaderModule.Output output : effect.module.outputs()) {
			if (output.sampler().equals(sampler)) {
				return output.filter() == Builtins.Filter.LINEAR ? FilterMode.LINEAR : FilterMode.NEAREST;
			}
		}

		return FilterMode.NEAREST;
	}

	private static String samplerOf(ShaderEffect effect, String targetName) {
		for (ShaderModule.Target target : effect.module.targets()) {
			if (target.name().equals(targetName)) {
				return target.sampler();
			}
		}

		throw new IllegalStateException("No target " + targetName);
	}

	private static Scratch scratch(GpuFormat format, int width, int height) {
		String key = format + "_" + width + "x" + height;
		Scratch scratch = SCRATCH.get(key);

		if (scratch == null) {
			if (SCRATCH.size() > 8) {
				SCRATCH.values().forEach(Scratch::close);
				SCRATCH.clear();
			}

			GpuDevice device = RenderSystem.getDevice();
			GpuTexture texture = device.createTexture(() -> "Skaffy shader copy " + key, 15, format, width, height, 1, 1);
			scratch = new Scratch(texture, device.createTextureView(texture));
			SCRATCH.put(key, scratch);
		}

		return scratch;
	}

	private static void copyDepthForFinal() {
		boolean needed = false;

		for (Entry entry : sorted) {
			ShaderEffect effect = entry.effect;

			if (runs(effect)) {
				for (PassRuntime pass : effect.passes) {
					needed |= pass.pass.stage() == Stage.FINAL && pass.pass.reads("SkDepth");
				}
			}
		}

		if (!needed) {
			return;
		}

		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTexture depth = main.getDepthTexture();

		if (depth == null) {
			return;
		}

		if (depthCopy == null || depthCopy.getWidth(0) != depth.getWidth(0) || depthCopy.getHeight(0) != depth.getHeight(0) || depthCopy.getFormat() != depth.getFormat()) {
			closeDepthCopy();
			GpuDevice device = RenderSystem.getDevice();
			depthCopy = device.createTexture(() -> "Skaffy shader depth copy", 15, depth.getFormat(), depth.getWidth(0), depth.getHeight(0), 1, 1);
			depthCopyView = device.createTextureView(depthCopy);
		}

		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(depth, depthCopy, 0, 0, 0, 0, 0, depth.getWidth(0), depth.getHeight(0));
	}

	private static void closeDepthCopy() {
		if (depthCopyView != null) {
			depthCopyView.close();
			depthCopyView = null;
		}

		if (depthCopy != null) {
			depthCopy.close();
			depthCopy = null;
		}
	}

	private static final class Resolve {
		private static @Nullable CompiledRenderPipeline pipeline;
		private static @Nullable EffectShaderSource source;

		static void draw(GpuTextureView from, GpuTextureView to) {
			if (pipeline == null) {
				source = new EffectShaderSource(Map.of(), false);
				net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(WorldPrograms.NAMESPACE, "hdr_resolve");
				source.shader(id, ShaderModule.PASS_VERTEX, """
						#version 330
						#extension GL_ARB_separate_shader_objects : require

						uniform sampler2D SkHdr;

						layout(location = 0) in vec2 sk_passUv;
						layout(location = 0) out vec4 fragColor;

						void main() {
							fragColor = vec4(clamp(texture(SkHdr, sk_passUv).rgb, 0.0, 1.0), 1.0);
						}
						""");
				RenderPipeline resolve = RenderPipeline.builder()
						.withLocation(id)
						.withVertexShader(id)
						.withFragmentShader(id)
						.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder()
								.withUniform("SkHdr", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build())
						.withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM,
								com.mojang.renderpearl.api.pipeline.ColorTargetState.WRITE_ALL))
						.withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
						.withCull(false)
						.build();
				pipeline = RenderSystem.getDevice().compilePipeline(resolve, source, Runnable::run).join().finishCompile();

				if (pipeline == null) {
					SkaffySAPIClient.LOGGER.error("Shaders: the hdr resolve pass didn't build");
					return;
				}
			}

			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Skaffy hdr resolve", to, Optional.empty(), null, OptionalDouble.empty())) {
				pass.setPipeline(pipeline);
				pass.setUniform("SkHdr", from, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.draw(3, 1, 0, 0);
			}
		}
	}
}
