package me.skaffy.client.model.gpu;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import me.skaffy.client.model.ModelData;

import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

final class ModelTextures {
	private static @Nullable Texture white;

	private ModelTextures() {
	}

	record Texture(GpuTexture texture, GpuTextureView view, GpuSampler sampler, boolean owned) {
		void close() {
			if (owned) {
				view.close();
				texture.close();
			}
		}
	}

	record MaterialTextures(Texture base, @Nullable Texture material, Texture emissive, boolean bakedEmissive) {
		void close() {
			base.close();
			emissive.close();

			if (material != null) {
				material.close();
			}
		}
	}

	record Prepared(@Nullable NativeImage material, @Nullable NativeImage emissive) {
		void close() {
			if (material != null) {
				material.close();
			}

			if (emissive != null) {
				emissive.close();
			}
		}
	}

	static Prepared prepare(ModelData.Material material) {
		NativeImage normal = image(material.normalTexture());
		NativeImage specular = image(material.specularTexture());
		NativeImage merged = null;
		NativeImage glow = null;

		if (normal != null || specular != null) {
			int width = Math.max(normal == null ? 1 : normal.getWidth(), specular == null ? 1 : specular.getWidth());
			int height = Math.max(normal == null ? 1 : normal.getHeight(), specular == null ? 1 : specular.getHeight());
			boolean smooth = material.sampling().smooth();
			merged = new NativeImage(width, height, false);

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int normalPixel = normal == null ? 0xFF8080FF : pixel(normal, x, y, width, height, smooth);
					float roughness = material.roughness();
					float alpha = material.specularKind() == ModelData.SpecularKind.SPECULAR_GLOSSINESS ? 1 : material.metallic();

					if (specular != null) {
						int pixel = pixel(specular, x, y, width, height, smooth);
						float r = (pixel >> 16 & 0xFF) / 255f;
						float g = (pixel >> 8 & 0xFF) / 255f;
						float b = (pixel & 0xFF) / 255f;
						float a = (pixel >>> 24) / 255f;

						switch (material.specularKind()) {
							case METALLIC_ROUGHNESS -> {
								roughness = g * material.roughness();
								alpha = b * material.metallic();
							}
							case SPECULAR_GLOSSINESS -> {
								roughness = 1 - a * (1 - material.roughness());
								alpha = Math.max(r, Math.max(g, b));
							}
							case MER -> {
								roughness = b;
								alpha = r;
							}
							case NONE -> {
							}
						}
					}

					merged.setPixel(x, y, channel(alpha) << 24 | (normalPixel & 0xFFFF00) | channel(roughness));
				}
			}

			if (material.specularKind() == ModelData.SpecularKind.MER && specular != null && material.emissiveTexture() == null) {
				glow = merGlow(image(material.texture()), specular, smooth);
			}
		}

		return new Prepared(merged, glow);
	}

	private static @Nullable NativeImage merGlow(@Nullable NativeImage base, NativeImage mer, boolean smooth) {
		int width = Math.max(base == null ? 1 : base.getWidth(), mer.getWidth());
		int height = Math.max(base == null ? 1 : base.getHeight(), mer.getHeight());
		NativeImage glow = new NativeImage(width, height, false);
		boolean any = false;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float strength = (pixel(mer, x, y, width, height, smooth) >> 8 & 0xFF) / 255f;
				int color = base == null ? -1 : pixel(base, x, y, width, height, smooth);
				any |= strength > 0;
				glow.setPixel(x, y, 0xFF000000 | channel((color >> 16 & 0xFF) / 255f * strength) << 16 | channel((color >> 8 & 0xFF) / 255f * strength) << 8
						| channel((color & 0xFF) / 255f * strength));
			}
		}

		if (!any) {
			glow.close();
			return null;
		}

		return glow;
	}

	private static @Nullable NativeImage image(ModelData.@Nullable TextureData data) {
		return data == null ? null : data.image();
	}

	private static int pixel(NativeImage image, int x, int y, int width, int height, boolean smooth) {
		if (image.getWidth() == width && image.getHeight() == height) {
			return image.getPixel(x, y);
		}

		return sample(image, (x + 0.5f) / width, (y + 0.5f) / height, smooth);
	}

	private static int sample(NativeImage image, float u, float v, boolean smooth) {
		int width = image.getWidth();
		int height = image.getHeight();

		if (!smooth) {
			return image.getPixel(Math.min(width - 1, (int) (u * width)), Math.min(height - 1, (int) (v * height)));
		}

		float x = u * width - 0.5f;
		float y = v * height - 0.5f;
		int x0 = Math.clamp((int) Math.floor(x), 0, width - 1);
		int y0 = Math.clamp((int) Math.floor(y), 0, height - 1);
		int x1 = Math.min(x0 + 1, width - 1);
		int y1 = Math.min(y0 + 1, height - 1);
		float fx = Math.clamp(x - (float) Math.floor(x), 0, 1);
		float fy = Math.clamp(y - (float) Math.floor(y), 0, 1);
		int a = image.getPixel(x0, y0);
		int b = image.getPixel(x1, y0);
		int c = image.getPixel(x0, y1);
		int d = image.getPixel(x1, y1);
		int result = 0;

		for (int shift = 0; shift < 32; shift += 8) {
			float top = (a >>> shift & 0xFF) * (1 - fx) + (b >>> shift & 0xFF) * fx;
			float bottom = (c >>> shift & 0xFF) * (1 - fx) + (d >>> shift & 0xFF) * fx;
			result |= Math.round(top * (1 - fy) + bottom * fy) << shift;
		}

		return result;
	}

	private static int channel(float value) {
		return Math.round(Math.clamp(value, 0, 1) * 255);
	}

	static MaterialTextures upload(String name, ModelData.Material material, Prepared prepared, long[] budget) {
		ModelData.Sampling sampling = material.sampling();
		Texture base = texture(name + "/base", image(material.texture()), sampling, budget, false);
		Texture merged = prepared.material() == null ? null : texture(name + "/material", prepared.material(), sampling, budget, true);
		boolean baked = prepared.emissive() != null;
		NativeImage glow = baked ? prepared.emissive() : image(material.emissiveTexture());
		Texture emissive = texture(name + "/emissive", glow, sampling, budget, false);
		prepared.close();
		return new MaterialTextures(base, merged, emissive, baked);
	}

	private static Texture texture(String name, @Nullable NativeImage image, ModelData.Sampling sampling, long[] budget, boolean data) {
		if (image == null) {
			return white();
		}

		GpuDevice device = RenderSystem.getDevice();
		int levels = 1;
		NativeImage[] mips = {image};

		if (sampling.smooth() && sampling.mipmaps()) {
			int lowestBit = Math.min(Integer.lowestOneBit(image.getWidth()), Integer.lowestOneBit(image.getHeight()));
			int maxLevel = Math.min(Mth.log2(Math.min(Math.min(image.getWidth(), image.getHeight()), lowestBit)), 10);

			if (maxLevel > 0) {
				if (data) {
					mips = dataMips(image, maxLevel);
				} else {
					Transparency transparency = image.computeTransparency();
					mips = MipmapGenerator.generateMipLevels(Identifier.fromNamespaceAndPath("skaffys-api", "model"), new NativeImage[] {image}, maxLevel, MipmapStrategy.AUTO, 0,
							transparency);
				}

				levels = mips.length;
			}
		}

		GpuTexture texture = device.createTexture(() -> "Skaffy model " + name, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM,
				image.getWidth(), image.getHeight(), 1, levels);

		for (int level = 0; level < mips.length; level++) {
			device.createCommandEncoder().writeToTexture(texture, mips[level], level, 0, 0, 0);
			budget[0] -= (long) mips[level].getWidth() * mips[level].getHeight() * 4;
		}

		for (int level = 1; level < mips.length; level++) {
			mips[level].close();
		}

		FilterMode magnify = sampling.smooth() ? FilterMode.LINEAR : FilterMode.NEAREST;
		AddressMode u = sampling.repeatU() ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
		AddressMode v = sampling.repeatV() ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
		GpuSampler sampler = RenderSystem.getSamplerCache().getSampler(u, v, magnify, magnify, levels > 1);
		return new Texture(texture, device.createTextureView(texture), sampler, true);
	}

	private static NativeImage[] dataMips(NativeImage image, int maxLevel) {
		NativeImage[] mips = new NativeImage[maxLevel + 1];
		mips[0] = image;

		for (int level = 1; level <= maxLevel; level++) {
			NativeImage last = mips[level - 1];
			NativeImage mip = new NativeImage(Math.max(1, last.getWidth() >> 1), Math.max(1, last.getHeight() >> 1), false);

			for (int y = 0; y < mip.getHeight(); y++) {
				for (int x = 0; x < mip.getWidth(); x++) {
					int a = last.getPixel(x * 2, y * 2);
					int b = last.getPixel(Math.min(x * 2 + 1, last.getWidth() - 1), y * 2);
					int c = last.getPixel(x * 2, Math.min(y * 2 + 1, last.getHeight() - 1));
					int d = last.getPixel(Math.min(x * 2 + 1, last.getWidth() - 1), Math.min(y * 2 + 1, last.getHeight() - 1));
					int result = 0;

					for (int shift = 0; shift < 32; shift += 8) {
						result |= ((a >>> shift & 0xFF) + (b >>> shift & 0xFF) + (c >>> shift & 0xFF) + (d >>> shift & 0xFF) + 2) / 4 << shift;
					}

					mip.setPixel(x, y, result);
				}
			}

			mips[level] = mip;
		}

		return mips;
	}

	static Texture white() {
		if (white == null) {
			white = solid("white", -1);
		}

		return white;
	}

	private static Texture solid(String name, int abgr) {
		GpuDevice device = RenderSystem.getDevice();
		GpuTexture texture = device.createTexture(() -> "Skaffy model " + name, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);

		try (NativeImage image = new NativeImage(1, 1, false)) {
			image.setPixelABGR(0, 0, abgr);
			device.createCommandEncoder().writeToTexture(texture, image);
		}

		GpuSampler sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.NEAREST, false);
		return new Texture(texture, device.createTextureView(texture), sampler, false);
	}
}
