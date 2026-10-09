package me.skaffy.client.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import me.skaffy.client.shader.lang.Builtins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class FrameUniforms implements AutoCloseable {
	static final int SIZE = layoutSize();
	private static final long START = Util.getNanos();

	private final MappableRingBuffer world = new MappableRingBuffer(() -> "Skaffy shader frame", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, SIZE);
	private final MappableRingBuffer hand = new MappableRingBuffer(() -> "Skaffy shader frame (hand)", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, SIZE);
	private final MappableRingBuffer[] shadow = new MappableRingBuffer[ShadowMap.CASCADES];
	private final Matrix4f previousView = new Matrix4f();
	private final Matrix4f previousProjection = new Matrix4f();
	private final Vector3f previousCamera = new Vector3f();
	private long lastFrame = Util.getNanos();
	private boolean hasPrevious;
	private boolean written;
	private int frameCounter;
	private float wetness;
	private static java.lang.reflect.Field climateField;
	private static java.lang.reflect.Method downfallMethod;

	GpuBufferSlice world() {
		return world.currentBuffer().slice();
	}

	GpuBufferSlice hand() {
		return hand.currentBuffer().slice();
	}

	GpuBufferSlice shadow(int cascade) {
		return shadow[cascade].currentBuffer().slice();
	}

	FrameUniforms() {
		for (int i = 0; i < shadow.length; i++) {
			int cascade = i;
			shadow[i] = new MappableRingBuffer(() -> "Skaffy shader frame (shadow " + cascade + ")", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, SIZE);
		}
	}

	boolean written() {
		return written;
	}

	void rotate() {
		world.rotate();
		hand.rotate();

		for (MappableRingBuffer cascade : shadow) {
			cascade.rotate();
		}
		written = false;
	}

	void update(Matrix4f projection, Matrix4f steady, Matrix4f jitter) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		GameRenderState state = minecraft.gameRenderer.gameRenderState();
		CameraRenderState camera = state.levelRenderState.cameraRenderState;
		SkyRenderState sky = state.levelRenderState.skyRenderState;
		float partialTick = state.levelRenderState.worldPartialTicks;

		Matrix4f view = new Matrix4f(camera.viewRotationMatrix);
		Matrix4f inverseView = new Matrix4f(view).invert();
		Matrix4f inverseProjection = new Matrix4f(projection).invert();
		Vector3f cameraPos = new Vector3f((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z);
		Vector3f forward = inverseView.transformDirection(new Vector3f(0, 0, -1)).normalize();
		float tilt = (float) Math.toRadians(ShaderRenderer.sunPathRotation());
		Vector3f sun = celestial(sky.sunAngle, tilt);
		Vector3f moon = celestial(sky.moonAngle, tilt);
		Vector3f light = sun.y >= 0 ? sun : moon;
		long now = Util.getNanos();
		float frameTime = Math.min(1, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		float time = (float) (((now - START) / 1.0e9) % 3600.0);
		float fov = (float) Math.toDegrees(2 * Math.atan(1 / projection.m11()));
		float dayTime = 0;
		float rain = 0;
		float thunder = 0;
		int dimension = 3;
		float eyeBlock = 0;
		float eyeSky = 1;

		if (level != null) {
			dayTime = (float) (Math.floorMod(level.getDefaultClockTime(), 24000L) + partialTick);
			rain = level.getRainLevel(partialTick);
			thunder = level.getThunderLevel(partialTick);
			dimension = level.dimension() == Level.OVERWORLD ? 0 : level.dimension() == Level.NETHER ? 1 : level.dimension() == Level.END ? 2 : 3;
			BlockPos eye = camera.blockPos;
			eyeBlock = level.getBrightness(LightLayer.BLOCK, eye) / 15f;
			eyeSky = level.getBrightness(LightLayer.SKY, eye) / 15f;
		}

		if (!hasPrevious) {
			previousView.set(view);
			previousProjection.set(steady);
			previousCamera.set(cameraPos);
			hasPrevious = true;
		}

		int fluid = camera.fogType == FogType.WATER ? 1 : camera.fogType == FogType.LAVA ? 2 : camera.fogType == FogType.POWDER_SNOW ? 3 : 0;
		Extras extras = extras(minecraft, level, camera.blockPos, light, camera.pos, partialTick, rain, frameTime, state);
		Vector4f fogColor = camera.fogData.color;
		float renderDistance = minecraft.options.getEffectiveRenderDistance() * 16f;
		Values values = new Values(view, projection, inverseView, inverseProjection, new Matrix4f(previousView), new Matrix4f(jitter).mul(previousProjection),
				fogColor, cameraPos, time, forward, frameTime, sun, dayTime, moon, rain, light, thunder, new Vector3f(previousCamera), partialTick,
				state.windowRenderState.width, state.windowRenderState.height, camera.fogData.environmentalStart, camera.fogData.environmentalEnd,
				renderDistance, ShaderRenderer.effectDistance(), fov, 0.05f, camera.depthFar, eyeBlock, eyeSky, dimension, fluid, sky.moonPhase.index(), extras);

		try (GpuBufferSlice.MappedView mapped = world.currentBuffer().map(false, true)) {
			write(mapped.data(), values, 0, values.extras.shadow);
		}

		try (GpuBufferSlice.MappedView mapped = hand.currentBuffer().map(false, true)) {
			write(mapped.data(), values, 1, values.extras.shadow);
		}

		ShadowMap shadows = ShaderRenderer.shadowMap();

		for (int i = 0; i < shadow.length; i++) {
			try (GpuBufferSlice.MappedView mapped = shadow[i].currentBuffer().map(false, true)) {
				write(mapped.data(), values, 0, shadows != null ? shadows.cellMatrix(i) : values.extras.shadow);
			}
		}

		previousView.set(view);
		previousProjection.set(steady);
		previousCamera.set(cameraPos);
		written = true;
	}

	private static Vector3f celestial(float angle, float tilt) {
		return new Matrix3f().rotateY((float) Math.toRadians(-90)).rotateZ(tilt).rotateX(angle).transform(new Vector3f(0, 1, 0)).normalize();
	}

	private record Values(Matrix4f view, Matrix4f projection, Matrix4f inverseView, Matrix4f inverseProjection, Matrix4f previousView,
			Matrix4f previousProjection, Vector4f fogColor, Vector3f camera, float time, Vector3f forward, float frameTime, Vector3f sun,
			float dayTime, Vector3f moon, float rain, Vector3f light, float thunder, Vector3f previousCamera, float partialTick, int width,
			int height, float fogStart, float fogEnd, float renderDistance, float effectDistance, float fov, float near, float far, float eyeBlock, float eyeSky,
			int dimension, int fluid, int moonPhase, Extras extras) {
	}

	private record Extras(Matrix4f shadow, float shadowRange, float[] cascades, float[] shade, float[] lightColors, Vector3f lightOrigin, int lightRange, int lightColumns,
			float atlasWidth, float atlasHeight, Vector3f heldColor, float held, float wetness, float temperature, float downfall, boolean snowing,
			float nightVision, float darkness, float taaX, float taaY, int frameCounter, float cloudHeight, float[] cloudOffset, int cloudMode) {
	}

	private Extras extras(Minecraft minecraft, ClientLevel level, BlockPos eye, Vector3f light, net.minecraft.world.phys.Vec3 cameraPos, float partialTick,
			float rain, float frameTime, GameRenderState state) {
		ShadowMap shadows = ShaderRenderer.shadowMap();
		Matrix4f shadow = new Matrix4f();
		float shadowRange = 0;
		float[] cascades = {1, 1, 1};

		if (shadows != null) {
			shadows.prepare(light, cameraPos, ShaderRenderer.shadowDistance(), ShaderRenderer.shadowResolution());
			shadow.set(shadows.matrix());
			cascades = shadows.scales();
			shadowRange = shadows.distance();
		}

		CardinalLighting cardinal = level != null ? level.cardinalLighting() : CardinalLighting.DEFAULT;
		float[] shade = new float[6];

		for (Direction direction : Direction.values()) {
			shade[direction.ordinal()] = cardinal.byFace(direction);
		}

		ColoredLight colored = ShaderRenderer.coloredLight();
		float[] lightColors = new float[32];
		Vector3f lightOrigin = new Vector3f();
		int lightRange = 0;
		int lightColumns = 1;
		float atlasWidth = 1;
		float atlasHeight = 1;

		if (colored != null && colored.active()) {
			List<me.skaffy.client.shader.lang.ShaderModule.Light> lights = colored.owner().module.lights();

			for (int i = 0; i < lights.size() && i < 8; i++) {
				float[] color = colored.owner().color(lights.get(i));
				lightColors[i * 4] = color[0];
				lightColors[i * 4 + 1] = color[1];
				lightColors[i * 4 + 2] = color[2];
				lightColors[i * 4 + 3] = 1;
			}

			net.minecraft.world.phys.Vec3 origin = colored.origin();
			lightOrigin.set((float) origin.x, (float) origin.y, (float) origin.z);
			lightRange = colored.range();
			lightColumns = colored.columns();
			atlasWidth = colored.atlasWidth();
			atlasHeight = colored.atlasHeight();
		}

		Vector3f heldColor = new Vector3f();
		float held = 0;
		float nightVision = 0;
		float darkness = 0;
		LocalPlayer player = minecraft.player;

		if (player != null) {
			for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
				if (stack.getItem() instanceof BlockItem item) {
					BlockState block = item.getBlock().defaultBlockState();
					int typed = colored != null ? colored.levelOf(block) : -1;
					int emission = typed >= 0 ? typed : block.getLightEmission();

					if (emission > 0 && emission / 15f > held) {
						held = emission / 15f;
						float[] color = colored != null ? colored.colorOf(block) : null;
						heldColor.set(color != null ? color[0] : 1, color != null ? color[1] : 1, color != null ? color[2] : 1);
					}
				}
			}

			nightVision = player.hasEffect(MobEffects.NIGHT_VISION) ? GameRenderer.nightVisionScale(player, partialTick) : 0;
			darkness = player.getEffectBlendFactor(MobEffects.DARKNESS, partialTick);
		}

		float temperature = 0.8f;
		float downfall = 0.4f;
		boolean snowing = false;

		if (level != null) {
			Biome biome = level.getBiome(eye).value();
			temperature = biome.getBaseTemperature();
			downfall = downfall(biome, downfall);
			snowing = rain > 0 && level.getPrecipitationAt(eye) == Biome.Precipitation.SNOW;
		}

		float target = level != null && level.isRainingAt(eye.above()) ? 1 : rain * 0.3f;
		float speed = target > wetness ? 1 / 12f : 1 / 45f;
		wetness += (target - wetness) * (1 - (float) Math.exp(-frameTime * speed * 3));
		Vector2fc jitter = ShaderRenderer.taaOffset();
		CloudShadows clouds = ShaderRenderer.clouds();
		int cloudMode = clouds != null && clouds.view() != null && level != null ? CloudShadows.mode(state) : 0;
		float[] cloudOffset = clouds != null ? clouds.offset(state) : new float[2];
		return new Extras(shadow, shadowRange, cascades, shade, lightColors, lightOrigin, lightRange, lightColumns, atlasWidth, atlasHeight, heldColor, held,
				wetness, temperature, downfall, snowing, nightVision, darkness, jitter.x(), jitter.y(), frameCounter++, state.levelRenderState.cloudHeight,
				cloudOffset, cloudMode);
	}

	private static float downfall(Biome biome, float fallback) {
		try {
			if (climateField == null) {
				climateField = Biome.class.getDeclaredField("climateSettings");
				climateField.setAccessible(true);
				downfallMethod = climateField.getType().getDeclaredMethod("downfall");
				downfallMethod.setAccessible(true);
			}

			return (float) downfallMethod.invoke(climateField.get(biome));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return fallback;
		}
	}

	private static void write(ByteBuffer buffer, Values v, int isHand, Matrix4f shadowMatrix) {
		buffer.order(ByteOrder.nativeOrder());
		int offset = 0;

		for (Builtins.FrameField field : Builtins.FRAME) {
			int align = align(field.glslType());
			offset = (offset + align - 1) / align * align;

			switch (field.name()) {
				case "sk_viewMatrix" -> v.view.get(offset, buffer);
				case "sk_projectionMatrix" -> v.projection.get(offset, buffer);
				case "sk_inverseViewMatrix" -> v.inverseView.get(offset, buffer);
				case "sk_inverseProjectionMatrix" -> v.inverseProjection.get(offset, buffer);
				case "sk_previousViewMatrix" -> v.previousView.get(offset, buffer);
				case "sk_previousProjectionMatrix" -> v.previousProjection.get(offset, buffer);
				case "sk_fogColor" -> v.fogColor.get(offset, buffer);
				case "sk_cameraPosition" -> v.camera.get(offset, buffer);
				case "sk_time" -> buffer.putFloat(offset, v.time);
				case "sk_cameraDirection" -> v.forward.get(offset, buffer);
				case "sk_frameTime" -> buffer.putFloat(offset, v.frameTime);
				case "sk_sunDirection" -> v.sun.get(offset, buffer);
				case "sk_dayTime" -> buffer.putFloat(offset, v.dayTime);
				case "sk_moonDirection" -> v.moon.get(offset, buffer);
				case "sk_rain" -> buffer.putFloat(offset, v.rain);
				case "sk_lightDirection" -> v.light.get(offset, buffer);
				case "sk_thunder" -> buffer.putFloat(offset, v.thunder);
				case "sk_previousCameraPosition" -> v.previousCamera.get(offset, buffer);
				case "sk_partialTick" -> buffer.putFloat(offset, v.partialTick);
				case "sk_screenSize" -> buffer.putFloat(offset, v.width).putFloat(offset + 4, v.height);
				case "sk_fogStart" -> buffer.putFloat(offset, v.fogStart);
				case "sk_fogEnd" -> buffer.putFloat(offset, v.fogEnd);
				case "sk_renderDistance" -> buffer.putFloat(offset, v.renderDistance);
				case "sk_effectDistance" -> buffer.putFloat(offset, v.effectDistance);
				case "sk_fov" -> buffer.putFloat(offset, v.fov);
				case "sk_near" -> buffer.putFloat(offset, v.near);
				case "sk_far" -> buffer.putFloat(offset, v.far);
				case "sk_eyeBlockLight" -> buffer.putFloat(offset, v.eyeBlock);
				case "sk_eyeSkyLight" -> buffer.putFloat(offset, v.eyeSky);
				case "sk_dimension" -> buffer.putInt(offset, v.dimension);
				case "sk_cameraFluid" -> buffer.putInt(offset, v.fluid);
				case "sk_isHand" -> buffer.putInt(offset, isHand);
				case "sk_moonPhase" -> buffer.putInt(offset, v.moonPhase);
				case "sk_shadowMatrix" -> shadowMatrix.get(offset, buffer);
				case "sk_shadowCascades" -> buffer.putFloat(offset, v.extras.cascades[0]).putFloat(offset + 4, v.extras.cascades[1])
						.putFloat(offset + 8, v.extras.cascades[2]).putFloat(offset + 12, 0);
				case "sk_shadeA" -> buffer.putFloat(offset, v.extras.shade[0]).putFloat(offset + 4, v.extras.shade[1]).putFloat(offset + 8, v.extras.shade[2])
						.putFloat(offset + 12, v.extras.shade[3]);
				case "sk_shadeB" -> buffer.putFloat(offset, v.extras.shade[4]).putFloat(offset + 4, v.extras.shade[5]).putFloat(offset + 8, 0).putFloat(offset + 12, 0);
				case "sk_lightColors" -> {
					for (int i = 0; i < 32; i++) {
						buffer.putFloat(offset + i * 4, v.extras.lightColors[i]);
					}
				}
				case "sk_heldLightColor" -> v.extras.heldColor.get(offset, buffer);
				case "sk_heldLight" -> buffer.putFloat(offset, v.extras.held);
				case "sk_lightOrigin" -> v.extras.lightOrigin.get(offset, buffer);
				case "sk_wetness" -> buffer.putFloat(offset, v.extras.wetness);
				case "sk_taaOffset" -> buffer.putFloat(offset, v.extras.taaX).putFloat(offset + 4, v.extras.taaY);
				case "sk_lightAtlasSize" -> buffer.putFloat(offset, v.extras.atlasWidth).putFloat(offset + 4, v.extras.atlasHeight);
				case "sk_biomeTemperature" -> buffer.putFloat(offset, v.extras.temperature);
				case "sk_biomeDownfall" -> buffer.putFloat(offset, v.extras.downfall);
				case "sk_nightVision" -> buffer.putFloat(offset, v.extras.nightVision);
				case "sk_darkness" -> buffer.putFloat(offset, v.extras.darkness);
				case "sk_shadowRange" -> buffer.putFloat(offset, v.extras.shadowRange);
				case "sk_frameCounter" -> buffer.putInt(offset, v.extras.frameCounter);
				case "sk_isSnowing" -> buffer.putInt(offset, v.extras.snowing ? 1 : 0);
				case "sk_lightRange" -> buffer.putInt(offset, v.extras.lightRange);
				case "sk_lightColumns" -> buffer.putInt(offset, v.extras.lightColumns);
				case "sk_cloudHeight" -> buffer.putFloat(offset, v.extras.cloudHeight);
				case "sk_cloudOffset" -> buffer.putFloat(offset, v.extras.cloudOffset[0]).putFloat(offset + 4, v.extras.cloudOffset[1]);
				case "sk_cloudMode" -> buffer.putInt(offset, v.extras.cloudMode);
				default -> buffer.putInt(offset, 0);
			}

			offset += size(field.glslType());
		}
	}

	private static int align(String type) {
		return switch (type) {
			case "mat4", "vec4", "vec3", "vec4[8]" -> 16;
			case "vec2" -> 8;
			default -> 4;
		};
	}

	private static int size(String type) {
		return switch (type) {
			case "vec4[8]" -> 128;
			case "mat4" -> 64;
			case "vec4" -> 16;
			case "vec3" -> 12;
			case "vec2" -> 8;
			default -> 4;
		};
	}

	private static int layoutSize() {
		int offset = 0;

		for (Builtins.FrameField field : Builtins.FRAME) {
			int align = align(field.glslType());
			offset = (offset + align - 1) / align * align + size(field.glslType());
		}

		return (offset + 15) / 16 * 16;
	}

	@Override
	public void close() {
		world.close();
		hand.close();

		for (MappableRingBuffer cascade : shadow) {
			cascade.close();
		}
	}
}
