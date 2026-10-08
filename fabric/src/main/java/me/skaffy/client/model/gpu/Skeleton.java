package me.skaffy.client.model.gpu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import me.skaffy.client.model.ModelData;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class Skeleton {
	private final ModelData data;
	private final ModelPart[] parts;
	private final Map<String, ModelPart> byName = new HashMap<>();
	private final Matrix4f[] matrices;

	public Skeleton(ModelData data) {
		this.data = data;
		List<ModelData.Bone> bones = data.bones();
		parts = new ModelPart[bones.size()];
		matrices = new Matrix4f[bones.size()];

		for (int i = 0; i < bones.size(); i++) {
			ModelData.Bone bone = bones.get(i);
			ModelPart part = new ModelPart(List.of(), Map.of());
			PartPose pose = new PartPose(bone.x(), bone.y(), bone.z(), bone.xRot(), bone.yRot(), bone.zRot(), bone.xScale(), bone.yScale(), bone.zScale());
			part.setInitialPose(pose);
			part.loadPose(pose);
			parts[i] = part;
			byName.putIfAbsent(bone.name(), part);
			matrices[i] = new Matrix4f();
		}
	}

	public Function<String, @Nullable ModelPart> lookup() {
		return byName::get;
	}

	public @Nullable ModelPart part(int bone) {
		return bone >= 0 && bone < parts.length ? parts[bone] : null;
	}

	public int size() {
		return parts.length;
	}

	public void reset() {
		for (ModelPart part : parts) {
			part.resetPose();
			part.visible = true;
		}
	}

	public Matrix4f[] compute() {
		for (int i = 0; i < parts.length; i++) {
			ModelPart part = parts[i];
			int parent = data.bones().get(i).parent();
			Matrix4f matrix = parent >= 0 ? matrices[i].set(matrices[parent]) : matrices[i].identity();
			GpuModel.partTransform(matrix, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale);
		}

		return matrices;
	}

	public boolean hidden(int bone) {
		for (int b = bone; b >= 0; b = data.bones().get(b).parent()) {
			if (!parts[b].visible) {
				return true;
			}
		}

		return false;
	}
}
