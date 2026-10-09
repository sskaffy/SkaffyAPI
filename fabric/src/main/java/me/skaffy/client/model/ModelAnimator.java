package me.skaffy.client.model;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class ModelAnimator {
	private static final Map<Model<?>, Function<String, ModelPart>> LOOKUPS = new WeakHashMap<>();

	private ModelAnimator() {
	}

	public interface Queries {
		float lifeTime();

		float groundSpeed();

		float headXRotation();

		float headYRotation();

		float distanceMoved();

		Map<String, Float> variables();
	}

	public static void afterSetupAnim(Model<?> model, Object state) {
		if (!(state instanceof SkaffyRenderState skaffyState)) {
			return;
		}

		ModelRenderData data = skaffyState.skaffy$modelData();

		if (data == null || data.animations().isEmpty()) {
			return;
		}

		apply(LOOKUPS.computeIfAbsent(model, m -> m.root().createPartLookup()), data.animations(), data);
	}

	public static void apply(Function<String, @Nullable ModelPart> parts, List<AnimationPlayer.Active> animations, Queries queries) {
		Map<ModelPart, Absolute> absolute = null;
		float[] value = new float[4];

		for (AnimationPlayer.Active active : animations) {
			Molang.Scope scope = scope(queries, active.time());

			for (AnimationClip.BoneTrack track : active.clip().bones()) {
				if (!absolute(track)) {
					continue;
				}

				ModelPart part = parts.apply(track.bone());

				if (part == null) {
					continue;
				}

				if (absolute == null) {
					absolute = new IdentityHashMap<>();
				}

				Absolute sum = absolute.computeIfAbsent(part, p -> new Absolute());
				sum.add(track, active.time(), active.weight(), scope, value);
			}
		}

		if (absolute != null) {
			absolute.forEach(ModelAnimator::finish);
		}

		for (AnimationPlayer.Active active : animations) {
			Molang.Scope scope = scope(queries, active.time());
			float weight = active.weight();

			for (AnimationClip.BoneTrack track : active.clip().bones()) {
				ModelPart part = parts.apply(track.bone());

				if (part == null) {
					continue;
				}

				if (active.replacesVanilla() && !absolute(track)) {
					if (weight >= 1) {
						part.resetPose();
					} else {
						PartPose rest = part.getInitialPose();
						part.x += (rest.x() - part.x) * weight;
						part.y += (rest.y() - part.y) * weight;
						part.z += (rest.z() - part.z) * weight;
						part.xRot += (rest.xRot() - part.xRot) * weight;
						part.yRot += (rest.yRot() - part.yRot) * weight;
						part.zRot += (rest.zRot() - part.zRot) * weight;
					}
				}

				if (track.rotation() != null && track.rotation().kind() == AnimationClip.Kind.ROTATION_ADD) {
					track.rotation().sample(active.time(), scope, value);
					part.xRot += (float) Math.toRadians(value[0]) * weight;
					part.yRot += (float) Math.toRadians(value[1]) * weight;
					part.zRot += (float) Math.toRadians(value[2]) * weight;
				}

				if (track.position() != null && track.position().kind() == AnimationClip.Kind.POSITION_ADD) {
					track.position().sample(active.time(), scope, value);
					part.x += value[0] * weight;
					part.y -= value[1] * weight;
					part.z += value[2] * weight;
				}

				if (track.scale() != null && track.scale().kind() == AnimationClip.Kind.SCALE_MULTIPLY) {
					track.scale().sample(active.time(), scope, value);
					part.xScale *= 1 + (value[0] - 1) * weight;
					part.yScale *= 1 + (value[1] - 1) * weight;
					part.zScale *= 1 + (value[2] - 1) * weight;
				}
			}
		}
	}

	private static boolean absolute(AnimationClip.BoneTrack track) {
		return track.rotation() != null && track.rotation().kind() == AnimationClip.Kind.ROTATION_SET
				|| track.position() != null && track.position().kind() == AnimationClip.Kind.POSITION_SET
				|| track.scale() != null && track.scale().kind() == AnimationClip.Kind.SCALE_SET;
	}

	private static void finish(ModelPart part, Absolute sum) {
		if (sum.rotationWeight > 0) {
			Quaternionf target = new Quaternionf(sum.rotation.x, sum.rotation.y, sum.rotation.z, sum.rotation.w).normalize();

			if (sum.rotationWeight < 1) {
				Quaternionf current = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
				target = current.slerp(target, sum.rotationWeight);
			}

			Vector3f euler = new Matrix3f().rotation(target).getEulerAnglesZYX(new Vector3f());
			part.xRot = euler.x;
			part.yRot = euler.y;
			part.zRot = euler.z;
		}

		if (sum.positionWeight > 0) {
			float keep = Math.max(0, 1 - sum.positionWeight);
			float scale = 1 / Math.max(1, sum.positionWeight);
			part.x = part.x * keep + sum.position.x * scale;
			part.y = part.y * keep + sum.position.y * scale;
			part.z = part.z * keep + sum.position.z * scale;
		}

		if (sum.scaleWeight > 0) {
			float keep = Math.max(0, 1 - sum.scaleWeight);
			float scale = 1 / Math.max(1, sum.scaleWeight);
			part.xScale = part.xScale * keep + sum.scale.x * scale;
			part.yScale = part.yScale * keep + sum.scale.y * scale;
			part.zScale = part.zScale * keep + sum.scale.z * scale;
		}
	}

	private static final class Absolute {
		final org.joml.Vector4f rotation = new org.joml.Vector4f(0, 0, 0, 0);
		final Vector3f position = new Vector3f();
		final Vector3f scale = new Vector3f();
		float rotationWeight;
		float positionWeight;
		float scaleWeight;

		void add(AnimationClip.BoneTrack track, float time, float weight, Molang.Scope scope, float[] value) {
			if (track.rotation() != null && track.rotation().kind() == AnimationClip.Kind.ROTATION_SET) {
				track.rotation().sample(time, scope, value);

				float sign = rotation.x * value[0] + rotation.y * value[1] + rotation.z * value[2] + rotation.w * value[3] < 0 ? -1 : 1;
				rotation.add(value[0] * weight * sign, value[1] * weight * sign, value[2] * weight * sign, value[3] * weight * sign);
				rotationWeight += weight;
			}

			if (track.position() != null && track.position().kind() == AnimationClip.Kind.POSITION_SET) {
				track.position().sample(time, scope, value);
				position.add(value[0] * weight, value[1] * weight, value[2] * weight);
				positionWeight += weight;
			}

			if (track.scale() != null && track.scale().kind() == AnimationClip.Kind.SCALE_SET) {
				track.scale().sample(time, scope, value);
				scale.add(value[0] * weight, value[1] * weight, value[2] * weight);
				scaleWeight += weight;
			}
		}
	}

	public static float @Nullable [][] morphWeights(ModelData data, List<AnimationPlayer.Active> animations) {
		float[][] weights = null;

		for (int i = 0; i < data.meshes().size(); i++) {
			ModelData.Mesh mesh = data.meshes().get(i);

			if (mesh.morphed()) {
				if (weights == null) {
					weights = new float[data.meshes().size()][];
				}

				weights[i] = mesh.morphWeights().clone();
			}
		}

		if (weights == null) {
			return null;
		}

		float[][] sums = new float[weights.length][];
		float[] totals = new float[weights.length];

		for (AnimationPlayer.Active active : animations) {
			for (AnimationClip.MorphTrack track : active.clip().morphs()) {
				if (track.mesh() < 0 || track.mesh() >= weights.length || weights[track.mesh()] == null) {
					continue;
				}

				float[] value = new float[track.weights().components()];
				track.weights().sample(active.time(), null, value);
				float[] sum = sums[track.mesh()] == null ? sums[track.mesh()] = new float[weights[track.mesh()].length] : sums[track.mesh()];

				for (int t = 0; t < Math.min(sum.length, value.length); t++) {
					sum[t] += value[t] * active.weight();
				}

				totals[track.mesh()] += active.weight();
			}
		}

		for (int i = 0; i < weights.length; i++) {
			if (sums[i] == null || totals[i] <= 0) {
				continue;
			}

			float keep = Math.max(0, 1 - totals[i]);
			float scale = 1 / Math.max(1, totals[i]);

			for (int t = 0; t < weights[i].length; t++) {
				weights[i][t] = weights[i][t] * keep + sums[i][t] * scale;
			}
		}

		return weights;
	}

	static Molang.Scope scope(Queries queries, float animTime) {
		return new Molang.Scope() {
			@Override
			public float animTime() {
				return animTime;
			}

			@Override
			public float lifeTime() {
				return queries.lifeTime();
			}

			@Override
			public float groundSpeed() {
				return queries.groundSpeed();
			}

			@Override
			public float headXRotation() {
				return queries.headXRotation();
			}

			@Override
			public float headYRotation() {
				return queries.headYRotation();
			}

			@Override
			public float distanceMoved() {
				return queries.distanceMoved();
			}

			@Override
			public float variable(String name) {
				Float value = queries.variables().get(name);
				return value == null ? 0 : value;
			}
		};
	}
}
