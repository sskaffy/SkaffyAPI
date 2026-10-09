package me.skaffy.client.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import me.skaffy.client.SkaffySAPIClient;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import org.jspecify.annotations.Nullable;

public final class CloudShadows implements AutoCloseable {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/environment/clouds.png");
	private static volatile boolean stale = true;

	private @Nullable GpuTexture texture;
	private @Nullable GpuTextureView view;
	private int width = 1;

	public static void reloaded() {
		stale = true;
	}

	void update() {
		if (!stale && texture != null) {
			return;
		}

		stale = false;

		try (InputStream input = Minecraft.getInstance().getResourceManager().open(TEXTURE); NativeImage image = NativeImage.read(input)) {
			close();
			int w = image.getWidth();
			int h = image.getHeight();
			ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());

			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					byte cloud = ARGB.alpha(image.getPixel(x, y)) < 10 ? 0 : (byte) 255;
					pixels.put(cloud).put(cloud).put(cloud).put(cloud);
				}
			}

			pixels.flip();
			GpuDevice device = RenderSystem.getDevice();
			texture = device.createTexture(() -> "Skaffy clouds", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
			view = device.createTextureView(texture);
			device.createCommandEncoder().writeToTexture(texture, pixels, 0, 0, 0, 0, w, h);
			width = w;
		} catch (IOException | RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("Shaders: couldn't read the clouds for cloud shadows", e);
		}
	}

	@Nullable GpuTextureView view() {
		return view;
	}

	static GpuSampler sampler() {
		return RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);
	}

	static int mode(GameRenderState state) {
		CloudStatus status = state.optionsRenderState.cloudStatus;

		if (status == CloudStatus.OFF || ARGB.alpha(state.levelRenderState.cloudColor) == 0) {
			return 0;
		}

		return status == CloudStatus.FANCY ? 2 : 1;
	}

	float[] offset(GameRenderState state) {
		float moved = (float) (state.levelRenderState.gameTime % (width * 400L)) + state.levelRenderState.worldPartialTicks;
		return new float[] {moved * 0.030000001F, 3.96F};
	}

	@Override
	public void close() {
		if (view != null) {
			view.close();
			texture.close();
		}

		view = null;
		texture = null;
	}
}
