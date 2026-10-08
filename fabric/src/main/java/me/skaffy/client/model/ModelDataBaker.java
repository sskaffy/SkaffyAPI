package me.skaffy.client.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import me.skaffy.client.mixin.ModelPartAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class ModelDataBaker {
	private static final int MAX_ATLAS = 4096;

	private ModelDataBaker() {
	}

	record Atlas(Identifier texture, float @Nullable [][] rects) {
	}

	static Atlas atlas(ModelData data, String name) {
		List<ModelData.Material> materials = data.materials();
		int count = materials.size();
		int[][] sizes = new int[count][];
		int width = 1;
		int height = 0;

		int rowWidth = 512;

		for (ModelData.Material material : materials) {
			if (material.texture() != null) {
				rowWidth = Math.max(rowWidth, material.texture().width());
			}
		}

		int x = 0;
		int y = 0;
		int rowHeight = 0;
		int[][] positions = new int[count][];

		for (int i = 0; i < count; i++) {
			ModelData.TextureData texture = materials.get(i).texture();
			int w = texture == null ? 1 : texture.width();
			int h = texture == null ? 1 : texture.height();
			sizes[i] = new int[] {w, h};

			if (x + w > rowWidth) {
				x = 0;
				y += rowHeight;
				rowHeight = 0;
			}

			positions[i] = new int[] {x, y};
			x += w;
			rowHeight = Math.max(rowHeight, h);
			width = Math.max(width, x);
			height = Math.max(height, y + rowHeight);
		}

		int atlasWidth = Math.max(1, width);
		int atlasHeight = Math.max(1, height);
		NativeImage atlas = new NativeImage(atlasWidth, atlasHeight, true);
		float[][] rects = new float[count][];

		for (int i = 0; i < count; i++) {
			ModelData.TextureData texture = materials.get(i).texture();
			NativeImage image = texture == null ? null : texture.image();

			if (image == null) {
				atlas.setPixel(positions[i][0], positions[i][1], -1);
			} else {
				for (int py = 0; py < image.getHeight(); py++) {
					for (int px = 0; px < image.getWidth(); px++) {
						atlas.setPixel(positions[i][0] + px, positions[i][1] + py, image.getPixel(px, py));
					}
				}
			}

			rects[i] = new float[] {positions[i][0] / (float) atlasWidth, positions[i][1] / (float) atlasHeight, (positions[i][0] + sizes[i][0]) / (float) atlasWidth,
					(positions[i][1] + sizes[i][1]) / (float) atlasHeight};
		}

		if (atlasWidth > MAX_ATLAS || atlasHeight > MAX_ATLAS) {
			float shrink = Math.min(MAX_ATLAS / (float) atlasWidth, MAX_ATLAS / (float) atlasHeight);
			NativeImage smaller = new NativeImage(Math.max(1, (int) (atlasWidth * shrink)), Math.max(1, (int) (atlasHeight * shrink)), true);
			atlas.resizeSubRectTo(0, 0, atlasWidth, atlasHeight, smaller);
			atlas.close();
			atlas = smaller;
		}

		Identifier id = Identifier.fromNamespaceAndPath("skaffys-api", "model_atlas/" + Util.sanitizeName(name.toLowerCase(java.util.Locale.ROOT), Identifier::validPathChar));
		NativeImage finalAtlas = atlas;
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Skaffy model atlas " + name, finalAtlas));
		return new Atlas(id, rects);
	}

	static ModelPart bake(ModelData data, Atlas atlas, float scale, @Nullable ModelPart vanilla) {
		int count = data.bones().size();
		List<List<ModelPart.Cube>> cubes = new ArrayList<>(count);
		List<Map<String, Integer>> children = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			cubes.add(new ArrayList<>());
			children.add(new LinkedHashMap<>());
		}

		List<ModelPart.Cube> rootCubes = new ArrayList<>();
		Map<String, Integer> rootChildren = new LinkedHashMap<>();
		List<SkinnedCube> skinned = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			ModelData.Bone bone = data.bones().get(i);
			(bone.parent() < 0 ? rootChildren : children.get(bone.parent())).putIfAbsent(bone.name(), i);
		}

		for (int m = 0; m < data.meshes().size(); m++) {
			ModelData.Mesh mesh = data.meshes().get(m);
			ModelData.Material material = data.materials().get(mesh.material());
			float[] rect = atlas.rects() == null ? null : atlas.rects()[mesh.material()];

			if (mesh.skinned()) {
				SkinnedCube cube = new SkinnedCube(mesh, material, rect);
				skinned.add(cube);
				rootCubes.add(cube);
			} else if (mesh.bone() >= 0) {
				cubes.get(mesh.bone()).add(new MeshCube(mesh, material, rect));
			}
		}

		ModelPart[] parts = new ModelPart[count];

		for (int i = count - 1; i >= 0; i--) {
			Map<String, ModelPart> built = new LinkedHashMap<>();
			children.get(i).forEach((name, child) -> built.put(name, parts[child]));
			ModelData.Bone bone = data.bones().get(i);
			ModelPart part = new ModelPart(List.copyOf(cubes.get(i)), built);
			PartPose pose = new PartPose(bone.x(), bone.y(), bone.z(), bone.xRot(), bone.yRot(), bone.zRot(), bone.xScale(), bone.yScale(), bone.zScale());

			if (bone.parent() < 0 && scale != 1) {
				pose = pose.scaled(scale).translated(0, 24.016f * (1 - scale), 0);
			}

			part.setInitialPose(pose);
			part.loadPose(pose);
			parts[i] = part;
		}

		Map<String, ModelPart> rootParts = new LinkedHashMap<>();
		rootChildren.forEach((name, index) -> rootParts.put(name, parts[index]));

		if (vanilla != null) {
			addMissing(rootParts, vanilla);
		}

		ModelPart root = new ModelPart(List.copyOf(rootCubes), rootParts);

		for (SkinnedCube cube : skinned) {
			cube.joints(data, parts, scale);
		}

		return root;
	}

	private static void addMissing(Map<String, ModelPart> parts, ModelPart vanilla) {
		((ModelPartAccessor) (Object) vanilla).skaffy$children().forEach((name, vanillaChild) -> {
			if (!parts.containsKey(name)) {
				ModelPart empty = new ModelPart(List.of(), emptyCopy(vanillaChild));
				empty.setInitialPose(vanillaChild.getInitialPose());
				empty.loadPose(vanillaChild.getInitialPose());
				parts.put(name, empty);
			}
		});
	}

	private static Map<String, ModelPart> emptyCopy(ModelPart vanilla) {
		Map<String, ModelPart> copy = new LinkedHashMap<>();
		((ModelPartAccessor) (Object) vanilla).skaffy$children().forEach((name, child) -> {
			ModelPart empty = new ModelPart(List.of(), emptyCopy(child));
			empty.setInitialPose(child.getInitialPose());
			empty.loadPose(child.getInitialPose());
			copy.put(name, empty);
		});
		return copy;
	}

	static void atlasUv(float u, float v, ModelData.Material material, float @Nullable [] rect, float[] out) {
		if (rect == null) {
			out[0] = u;
			out[1] = v;
			return;
		}

		float frameV = v;
		ModelData.TextureAnimation animation = material.animation();

		if (animation != null) {
			int step = (int) (Util.getMillis() / 50 / animation.frameTime() % animation.order().length);
			frameV = (animation.order()[step] + Math.clamp(v, 0, 1)) / animation.frames();
		}

		out[0] = rect[0] + (rect[2] - rect[0]) * Math.clamp(u - (float) Math.floor(u == 1 ? 0 : u), 0, 1);
		out[1] = rect[1] + (rect[3] - rect[1]) * Math.clamp(animation != null ? frameV : frameV - (float) Math.floor(frameV == 1 ? 0 : frameV), 0, 1);
	}

	private static int color(ModelData.Material material, int @Nullable [] colors, int vertex, int tint) {
		int color = material.baseColor();

		if (colors != null) {
			color = multiply(color, colors[vertex]);
		}

		return multiply(color, tint);
	}

	static int multiply(int a, int b) {
		if (a == -1) {
			return b;
		}

		if (b == -1) {
			return a;
		}

		int alpha = (a >>> 24) * (b >>> 24) / 255;
		int red = (a >> 16 & 0xFF) * (b >> 16 & 0xFF) / 255;
		int green = (a >> 8 & 0xFF) * (b >> 8 & 0xFF) / 255;
		int blue = (a & 0xFF) * (b & 0xFF) / 255;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	static final class MeshCube extends ModelPart.Cube {
		private final ModelData.Mesh mesh;
		private final ModelData.Material material;
		private final float @Nullable [] rect;

		MeshCube(ModelData.Mesh mesh, ModelData.Material material, float @Nullable [] rect) {
			super(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, EnumSet.noneOf(Direction.class));
			this.mesh = mesh;
			this.material = material;
			this.rect = rect;
		}

		@Override
		public void compile(PoseStack.Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color) {
			emit(pose, builder, lightCoords, overlayCoords, color, mesh.positions(), mesh.normals(), mesh, material, rect);
		}
	}

	private static void emit(PoseStack.Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color, float[] positions, float[] normals, ModelData.Mesh mesh,
			ModelData.Material material, float @Nullable [] rect) {
		Matrix4f matrix = pose.pose();
		Vector3f position = new Vector3f();
		Vector3f normal = new Vector3f();
		float[] uv = new float[2];
		int light = material.emissive() || material.unlit() ? LightCoordsUtil.FULL_BRIGHT : lightCoords;
		int[] indices = mesh.indices();
		boolean twoSided = material.doubleSided();

		for (int side = 0; side < (twoSided ? 2 : 1); side++) {
			for (int i = 0; i + 2 < indices.length; i += 3) {
				int[] corners = side == 0 ? new int[] {indices[i], indices[i + 1], indices[i + 2], indices[i + 2]} : new int[] {indices[i + 2], indices[i + 1], indices[i], indices[i]};

				for (int corner : corners) {
					matrix.transformPosition(positions[corner * 3], positions[corner * 3 + 1], positions[corner * 3 + 2], position);
					pose.transformNormal(normals[corner * 3], normals[corner * 3 + 1], normals[corner * 3 + 2], normal);

					if (side == 1) {
						normal.negate();
					}

					atlasUv(mesh.uvs()[corner * 2], mesh.uvs()[corner * 2 + 1], material, rect, uv);
					builder.addVertex(position.x, position.y, position.z, color(material, mesh.colors(), corner, color), uv[0], uv[1], overlayCoords, light, normal.x, normal.y,
							normal.z);
				}
			}
		}
	}

	static final class SkinnedCube extends ModelPart.Cube {
		private final ModelData.Mesh mesh;
		private final ModelData.Material material;
		private final float @Nullable [] rect;
		private ModelPart[][] chains = new ModelPart[0][];
		private float rootScale = 1;

		SkinnedCube(ModelData.Mesh mesh, ModelData.Material material, float @Nullable [] rect) {
			super(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, EnumSet.noneOf(Direction.class));
			this.mesh = mesh;
			this.material = material;
			this.rect = rect;
		}

		void joints(ModelData data, ModelPart[] parts, float scale) {
			chains = new ModelPart[parts.length][];
			rootScale = scale;

			for (int i = 0; i < parts.length; i++) {
				if (mesh.inverseBind()[i] == null) {
					continue;
				}

				List<ModelPart> chain = new ArrayList<>();

				for (int bone = i; bone >= 0; bone = data.bones().get(bone).parent()) {
					chain.addFirst(parts[bone]);
				}

				chains[i] = chain.toArray(ModelPart[]::new);
			}
		}

		@Override
		public void compile(PoseStack.Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color) {
			Matrix4f[] joints = new Matrix4f[chains.length];
			PoseStack stack = new PoseStack();

			for (int i = 0; i < chains.length; i++) {
				if (chains[i] == null) {
					continue;
				}

				stack.pushPose();

				for (ModelPart part : chains[i]) {
					part.translateAndRotate(stack);
				}

				joints[i] = new Matrix4f(stack.last().pose()).mul(mesh.inverseBind()[i]);
				stack.popPose();
			}

			int count = mesh.vertexCount();
			float[] positions = new float[count * 3];
			float[] normals = new float[count * 3];
			Skinning.skin(mesh, joints, null, positions, normals);
			emit(pose, builder, lightCoords, overlayCoords, color, positions, normals, mesh, material, rect);
		}
	}
}
