package me.skaffy.client.model;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public record ModelData(List<Bone> bones, List<Locator> locators, List<Material> materials, List<Mesh> meshes, Map<String, AnimationClip> animations) {
	public record Bone(String name, int parent, float x, float y, float z, float xRot, float yRot, float zRot, float xScale, float yScale, float zScale) {
	}

	public record Locator(String name, int bone, float x, float y, float z, float xRot, float yRot, float zRot) {
	}

	public enum AlphaMode {
		OPAQUE,
		MASK,
		BLEND
	}

	public record Sampling(boolean smooth, boolean mipmaps, boolean repeatU, boolean repeatV) {
		public static final Sampling PIXELS = new Sampling(false, false, false, false);
	}

	public record TextureAnimation(int frames, int frameTime, int[] order, boolean interpolate) {
	}

	public static final class TextureData {
		private final String key;
		private @Nullable NativeImage image;
		private final int width;
		private final int height;

		public TextureData(String key, NativeImage image) {
			this.key = key;
			this.image = image;
			this.width = image.getWidth();
			this.height = image.getHeight();
		}

		public String key() {
			return key;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}

		public @Nullable NativeImage image() {
			return image;
		}

		public void release() {
			if (image != null) {
				image.close();
				image = null;
			}
		}
	}

	public enum SpecularKind {
		NONE,
		METALLIC_ROUGHNESS,
		SPECULAR_GLOSSINESS,
		MER
	}

	public record Material(
			String name,
			@Nullable TextureData texture,
			int baseColor,
			AlphaMode alphaMode,
			float alphaCutoff,
			boolean doubleSided,
			boolean unlit,
			@Nullable TextureData emissiveTexture,
			float[] emissiveColor,
			boolean fullBright,
			@Nullable TextureData normalTexture,
			float normalScale,
			SpecularKind specularKind,
			@Nullable TextureData specularTexture,
			float metallic,
			float roughness,
			float[] specularColor,
			Sampling sampling,
			@Nullable TextureAnimation animation) {
		public boolean emissive() {
			return fullBright || emissiveColor[0] > 0 || emissiveColor[1] > 0 || emissiveColor[2] > 0;
		}

		public static Material plain(String name, @Nullable TextureData texture, AlphaMode alphaMode, boolean doubleSided, Sampling sampling, @Nullable TextureAnimation animation,
				boolean fullBright) {
			return new Material(name, texture, -1, alphaMode, 0.1f, doubleSided, false, null, new float[3], fullBright, null, 1, SpecularKind.NONE, null, 0, 1, new float[3],
					sampling, animation);
		}
	}

	public record Mesh(
			int bone,
			int material,
			float[] positions,
			float[] normals,
			float[] uvs,
			int @Nullable [] colors,
			int[] indices,
			int @Nullable [] joints,
			float @Nullable [] weights,
			Matrix4f @Nullable [] inverseBind,
			float @Nullable [][] morphPositions,
			float @Nullable [][] morphNormals,
			float @Nullable [] morphWeights) {
		public int vertexCount() {
			return positions.length / 3;
		}

		public boolean skinned() {
			return joints != null;
		}

		public boolean morphed() {
			return morphPositions != null && morphPositions.length > 0;
		}

		public static Mesh rigid(int bone, int material, float[] positions, float[] normals, float[] uvs, int @Nullable [] colors, int[] indices) {
			return new Mesh(bone, material, positions, normals, uvs, colors, indices, null, null, null, null, null, null);
		}
	}

	public @Nullable Bone bone(String name) {
		for (Bone bone : bones) {
			if (bone.name().equals(name)) {
				return bone;
			}
		}

		return null;
	}

	public int boneIndex(String name) {
		for (int i = 0; i < bones.size(); i++) {
			if (bones.get(i).name().equals(name)) {
				return i;
			}
		}

		return -1;
	}

	public @Nullable Locator locator(String name) {
		for (Locator locator : locators) {
			if (locator.name().equals(name)) {
				return locator;
			}
		}

		return null;
	}

	public void releaseImages() {
		for (Material material : materials) {
			for (TextureData texture : new TextureData[] {material.texture(), material.emissiveTexture(), material.normalTexture(), material.specularTexture()}) {
				if (texture != null) {
					texture.release();
				}
			}
		}
	}

	public int triangleCount() {
		int count = 0;

		for (Mesh mesh : meshes) {
			count += mesh.indices().length / 3;
		}

		return count;
	}
}
