package me.skaffy.client.nametag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;

final class NameTagTexture implements SpriteImage {
	private final Identifier id;
	private final GlyphRenderTypes renderTypes;
	private final boolean owned;
	private final int imageWidth;
	private final int imageHeight;
	private final int frameWidth;
	private final int frameHeight;
	private final int columns;
	private final int[] frames;
	private final int[] times;
	private final int totalTime;

	private NameTagTexture(Identifier id, boolean owned, int imageWidth, int imageHeight, int frameWidth, int frameHeight, int[] frames, int[] times) {
		this.id = id;
		this.renderTypes = GlyphRenderTypes.createForColorTexture(id);
		this.owned = owned;
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
		this.frameWidth = frameWidth;
		this.frameHeight = frameHeight;
		this.columns = Math.max(1, imageWidth / frameWidth);
		this.frames = frames;
		this.times = times;
		int total = 0;

		for (int time : times) {
			total += time;
		}

		this.totalTime = Math.max(1, total);
	}

	static NameTagTexture load(Identifier id, byte[] png, byte @Nullable [] metadata) throws IOException {
		NativeImage image = NativeImage.read(png);
		int width = image.getWidth();
		int height = image.getHeight();
		AnimationMetadataSection animation = metadata == null ? null : animation(metadata);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));

		if (animation == null) {
			return new NameTagTexture(id, true, width, height, width, height, new int[] {0}, new int[] {1});
		}

		FrameSize size = animation.calculateFrameSize(width, height);
		int frameWidth = Math.max(1, Math.min(size.width(), width));
		int frameHeight = Math.max(1, Math.min(size.height(), height));
		int count = (width / frameWidth) * (height / frameHeight);
		List<AnimationFrame> list = animation.frames().orElse(null);
		List<Integer> frames = new ArrayList<>();
		List<Integer> times = new ArrayList<>();

		if (list == null) {
			for (int i = 0; i < count; i++) {
				frames.add(i);
				times.add(animation.defaultFrameTime());
			}
		} else {
			for (AnimationFrame frame : list) {
				if (frame.index() < count) {
					frames.add(frame.index());
					times.add(frame.timeOr(animation.defaultFrameTime()));
				}
			}
		}

		if (frames.isEmpty()) {
			frames.add(0);
			times.add(1);
		}

		return new NameTagTexture(id, true, width, height, frameWidth, frameHeight,
				frames.stream().mapToInt(Integer::intValue).toArray(), times.stream().mapToInt(Integer::intValue).toArray());
	}

	static NameTagTexture missing() {
		return new NameTagTexture(MissingTextureAtlasSprite.getLocation(), false, 16, 16, 16, 16, new int[] {0}, new int[] {1});
	}

	private static @Nullable AnimationMetadataSection animation(byte[] metadata) {
		try {
			JsonObject json = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();

			if (!json.has("animation")) {
				return null;
			}

			return AnimationMetadataSection.CODEC.parse(JsonOps.INSTANCE, json.get("animation")).result().orElse(null);
		} catch (RuntimeException e) {
			return null;
		}
	}

	@Override
	public GlyphRenderTypes renderTypes() {
		return renderTypes;
	}

	@Override
	public Identifier texture() {
		return id;
	}

	@Override
	public float aspect() {
		return frameWidth / (float) frameHeight;
	}

	@Override
	public int uv(float[] out) {
		int frame = frame();
		int x = frame % columns * frameWidth;
		int y = frame / columns * frameHeight;
		out[0] = x / (float) imageWidth;
		out[1] = y / (float) imageHeight;
		out[2] = (x + frameWidth) / (float) imageWidth;
		out[3] = (y + frameHeight) / (float) imageHeight;
		return 1;
	}

	private int frame() {
		if (frames.length == 1) {
			return frames[0];
		}

		long tick = Util.getMillis() / 50 % totalTime;

		for (int i = 0; i < frames.length; i++) {
			if (tick < times[i]) {
				return frames[i];
			}

			tick -= times[i];
		}

		return frames[0];
	}

	void release() {
		if (owned) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
	}
}
