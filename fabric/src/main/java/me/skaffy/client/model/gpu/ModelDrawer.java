package me.skaffy.client.model.gpu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import me.skaffy.client.model.AnimationPlayer;
import me.skaffy.client.model.ClientModel;
import me.skaffy.client.model.ModelAnimator;
import me.skaffy.client.model.ModelData;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class ModelDrawer {
	private ModelDrawer() {
	}

	public interface BoneLooks {
		int tint(int bone);
	}

	public record Part(int dynamic, int first, int count, int material, Matrix4f local, int light, int tint, int frame, Vector3f center, boolean blended) {
	}

	public record Frame(GpuModel model, GpuModelInstance instance, List<Part> parts, Matrix4f[] bones, Matrix4f[] modelBones, Matrix4f entityLocal) {
	}

	public static final class Holder {
		private @Nullable GpuModelInstance instance;

		@Nullable GpuModelInstance get(GpuModel model) {
			if (instance == null || instance.model() != model) {
				if (instance != null) {
					instance.close();
				}

				instance = new GpuModelInstance(model);
			}

			return instance;
		}

		public void close() {
			if (instance != null) {
				instance.close();
				instance = null;
			}
		}
	}

	public static @Nullable Frame extract(ClientModel model, Holder holder, List<AnimationPlayer.Active> animations, ModelAnimator.Queries queries, @Nullable Consumer<Skeleton> pose,
			@Nullable BoneLooks looks, Matrix4f entityLocal, double x, double y, double z, @Nullable Frustum frustum, int light, int tint, @Nullable Level level, long tick) {
		GpuModel gpu = GpuModels.get(model);
		Skeleton skeleton = GpuModels.skeleton(model);

		if (gpu == null || skeleton == null) {
			return null;
		}

		GpuModelInstance instance = holder.get(gpu);
		ModelData data = gpu.data();
		skeleton.reset();

		if (!animations.isEmpty()) {
			ModelAnimator.apply(skeleton.lookup(), animations, queries);
		}

		if (pose != null) {
			pose.accept(skeleton);
		}

		Matrix4f[] current = skeleton.compute();
		int boneCount = current.length;
		Matrix4f[] delta = new Matrix4f[boneCount];
		boolean[] moved = new boolean[boneCount];
		boolean[] hidden = new boolean[boneCount];
		int[] boneTint = new int[boneCount];

		for (int b = 0; b < boneCount; b++) {
			delta[b] = new Matrix4f(current[b]).mul(gpu.restInverse(b));
			moved[b] = !identity(delta[b]);
			hidden[b] = skeleton.hidden(b);
			int own = looks == null ? -1 : looks.tint(b);
			int parent = data.bones().get(b).parent();
			boneTint[b] = parent >= 0 && boneTint[parent] != -1 ? multiply(boneTint[parent], own) : own;
		}

		long millis = Util.getMillis();
		int[] frames = new int[data.materials().size()];

		for (int m = 0; m < frames.length; m++) {
			ModelData.TextureAnimation animation = data.materials().get(m).animation();

			if (animation != null) {
				frames[m] = animation.order()[(int) (millis / 50 / animation.frameTime() % animation.order().length)];
			}
		}

		int[] cellLight = instance.cellLight();
		boolean sampleLight = light < 0 && level != null && instance.lightDue(tick, (float) x, (float) y, (float) z);
		List<Part> parts = new ArrayList<>();
		Vector3f corner = new Vector3f();
		List<GpuModel.Cell> cells = gpu.cells();

		for (int c = 0; c < cells.size(); c++) {
			GpuModel.Cell cell = cells.get(c);
			boolean whole = true;

			for (GpuModel.Range range : cell.ranges()) {
				if (moved[range.bone()] || hidden[range.bone()] || boneTint[range.bone()] != -1) {
					whole = false;
					break;
				}
			}

			if (whole && frustum != null && !visible(frustum, entityLocal, cell.min(), cell.max(), x, y, z)) {
				continue;
			}

			if (sampleLight) {
				entityLocal.transformPosition(cell.center(), corner);
				BlockPos position = BlockPos.containing(x + corner.x, y + corner.y, z + corner.z);
				cellLight[c] = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, position), level.getBrightness(LightLayer.SKY, position));
			}

			int cellLightValue = light >= 0 ? light : cellLight[c];
			ModelData.Material material = data.materials().get(cell.material());
			boolean blended = material.alphaMode() == ModelData.AlphaMode.BLEND;
			Vector3f center = entityLocal.transformPosition(cell.center(), new Vector3f());

			if (whole) {
				parts.add(new Part(-1, cell.first(), cell.count(), cell.material(), entityLocal, cellLightValue, tint, frames[cell.material()], center, blended || (tint >>> 24) < 255));
				continue;
			}

			for (GpuModel.Range range : cell.ranges()) {
				int bone = range.bone();

				if (hidden[bone]) {
					continue;
				}

				int partTint = multiply(tint, boneTint[bone]);
				Matrix4f local = moved[bone] ? new Matrix4f(entityLocal).mul(delta[bone]) : entityLocal;
				parts.add(new Part(-1, range.first(), range.count(), cell.material(), local, cellLightValue, partTint, frames[cell.material()], center,
						blended || (partTint >>> 24) < 255));
			}
		}

		List<GpuModel.DynamicMesh> dynamic = gpu.dynamicMeshes();

		if (!dynamic.isEmpty()) {
			float[][] morphWeights = ModelAnimator.morphWeights(data, animations);
			Matrix4f[] joints = new Matrix4f[boneCount];

			for (int i = 0; i < dynamic.size(); i++) {
				GpuModel.DynamicMesh entry = dynamic.get(i);
				ModelData.Mesh mesh = data.meshes().get(entry.mesh());

				if (mesh.bone() >= 0 && hidden[mesh.bone()]) {
					continue;
				}

				float[] positions = new float[mesh.vertexCount() * 3];
				float[] normals = new float[mesh.vertexCount() * 3];

				if (mesh.skinned()) {
					for (int b = 0; b < boneCount; b++) {
						joints[b] = mesh.inverseBind()[b] == null ? null : new Matrix4f(current[b]).mul(mesh.inverseBind()[b]);
					}

					skin(mesh, joints, morphWeights == null ? null : morphWeights[entry.mesh()], positions, normals);
				} else {
					skin(mesh, null, morphWeights == null ? null : morphWeights[entry.mesh()], positions, normals);
					Matrix4f bone = current[mesh.bone()];
					org.joml.Matrix3f normalMatrix = bone.normal(new org.joml.Matrix3f());
					Vector3f point = new Vector3f();

					for (int v = 0; v < mesh.vertexCount(); v++) {
						bone.transformPosition(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], point);
						positions[v * 3] = point.x;
						positions[v * 3 + 1] = point.y;
						positions[v * 3 + 2] = point.z;
						normalMatrix.transform(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2], point).normalize();
						normals[v * 3] = point.x;
						normals[v * 3 + 1] = point.y;
						normals[v * 3 + 2] = point.z;
					}
				}

				instance.write(i, positions, normals, mesh);
				ModelData.Material material = data.materials().get(entry.material());
				int meshTint = mesh.bone() >= 0 ? multiply(tint, boneTint[mesh.bone()]) : tint;
				Vector3f center = entityLocal.transformPosition(new Vector3f(gpu.min()).add(gpu.max()).mul(0.5f), new Vector3f());
				int meshLight = light >= 0 ? light : level == null ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.pack(
						level.getBrightness(LightLayer.BLOCK, BlockPos.containing(x, y + 0.5, z)), level.getBrightness(LightLayer.SKY, BlockPos.containing(x, y + 0.5, z)));
				parts.add(new Part(i, entry.first(), entry.count(), entry.material(), entityLocal, meshLight, meshTint, frames[entry.material()], center,
						material.alphaMode() == ModelData.AlphaMode.BLEND || (meshTint >>> 24) < 255));
			}
		}

		Matrix4f[] bones = new Matrix4f[boneCount];
		Matrix4f[] modelBones = new Matrix4f[boneCount];

		for (int b = 0; b < boneCount; b++) {
			bones[b] = new Matrix4f(entityLocal).mul(current[b]);
			modelBones[b] = new Matrix4f(current[b]);
		}

		return new Frame(gpu, instance, parts, bones, modelBones, entityLocal);
	}

	public static void submit(Frame frame, PoseStack poseStack, SubmitNodeCollector collector, int overlay, int outlineColor) {
		SubmitNodeCollection collection = collection(collector);

		if (collection == null) {
			return;
		}

		Matrix4f base = poseStack.last().pose();

		for (Part part : frame.parts()) {
			Matrix4f pose = new Matrix4f(base).mul(part.local());
			Vector3f center = base.transformPosition(part.center(), new Vector3f());
			GpuModelFeature.Draw draw = new GpuModelFeature.Draw(frame.model(), frame.instance(), part.dynamic(), part.first(), part.count(), part.material(), pose, part.light(),
					overlay, part.tint(), part.frame(), false);
			GpuModelFeature.Submit submit = new GpuModelFeature.Submit(draw, center.lengthSquared());

			if (part.blended()) {
				collection.translucentModels.submit(submit);
			} else {
				collection.solid.submit(submit);
			}

			if (outlineColor != 0) {
				collection.outline.submit(new GpuModelFeature.Submit(new GpuModelFeature.Draw(frame.model(), frame.instance(), part.dynamic(), part.first(), part.count(),
						part.material(), pose, part.light(), overlay, outlineColor | 0xFF000000, part.frame(), true), center.lengthSquared()));
			}
		}
	}

	private static @Nullable SubmitNodeCollection collection(SubmitNodeCollector collector) {
		if (collector instanceof SubmitNodeStorage storage) {
			return storage.order(0);
		}

		return collector instanceof SubmitNodeCollection collection ? collection : null;
	}

	private static boolean visible(Frustum frustum, Matrix4f local, Vector3f min, Vector3f max, double x, double y, double z) {
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;
		Vector3f corner = new Vector3f();

		for (int i = 0; i < 8; i++) {
			local.transformPosition((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z, corner);
			minX = Math.min(minX, corner.x);
			minY = Math.min(minY, corner.y);
			minZ = Math.min(minZ, corner.z);
			maxX = Math.max(maxX, corner.x);
			maxY = Math.max(maxY, corner.y);
			maxZ = Math.max(maxZ, corner.z);
		}

		return frustum.isVisible(new AABB(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ).inflate(0.5));
	}

	private static boolean identity(Matrix4f m) {
		final float e = 1.0E-5f;
		return Math.abs(m.m00() - 1) < e && Math.abs(m.m11() - 1) < e && Math.abs(m.m22() - 1) < e && Math.abs(m.m33() - 1) < e
				&& Math.abs(m.m01()) < e && Math.abs(m.m02()) < e && Math.abs(m.m03()) < e && Math.abs(m.m10()) < e && Math.abs(m.m12()) < e && Math.abs(m.m13()) < e
				&& Math.abs(m.m20()) < e && Math.abs(m.m21()) < e && Math.abs(m.m23()) < e && Math.abs(m.m30()) < e && Math.abs(m.m31()) < e && Math.abs(m.m32()) < e;
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

	private static void skin(ModelData.Mesh mesh, Matrix4f @Nullable [] joints, float @Nullable [] morphWeights, float[] positions, float[] normals) {
		me.skaffy.client.model.Skinning.skin(mesh, joints, morphWeights, positions, normals);
	}
}
