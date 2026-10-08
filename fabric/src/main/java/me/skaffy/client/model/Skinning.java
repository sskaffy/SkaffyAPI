package me.skaffy.client.model;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class Skinning {
	private Skinning() {
	}

	public static void skin(ModelData.Mesh mesh, Matrix4f @Nullable [] joints, float @Nullable [] morphWeights, float[] positionsOut, float[] normalsOut) {
		int count = mesh.vertexCount();
		float[] positions = mesh.positions();
		float[] normals = mesh.normals();

		if (morphWeights != null && mesh.morphed()) {
			positions = positions.clone();
			normals = normals.clone();

			for (int t = 0; t < mesh.morphPositions().length && t < morphWeights.length; t++) {
				float weight = morphWeights[t];

				if (weight == 0) {
					continue;
				}

				float[] delta = mesh.morphPositions()[t];

				for (int i = 0; i < Math.min(delta.length, positions.length); i++) {
					positions[i] += delta[i] * weight;
				}

				float[] normalDelta = mesh.morphNormals() == null ? null : mesh.morphNormals()[t];

				if (normalDelta != null) {
					for (int i = 0; i < Math.min(normalDelta.length, normals.length); i++) {
						normals[i] += normalDelta[i] * weight;
					}
				}
			}
		}

		if (joints == null || !mesh.skinned()) {
			System.arraycopy(positions, 0, positionsOut, 0, positions.length);
			System.arraycopy(normals, 0, normalsOut, 0, normals.length);
			return;
		}

		int[] jointIndices = mesh.joints();
		float[] weights = mesh.weights();
		Vector3f position = new Vector3f();
		Vector3f normal = new Vector3f();
		Matrix3f rotation = new Matrix3f();

		for (int v = 0; v < count; v++) {
			float px = 0;
			float py = 0;
			float pz = 0;
			float nx = 0;
			float ny = 0;
			float nz = 0;

			for (int k = 0; k < 4; k++) {
				float weight = weights[v * 4 + k];

				if (weight <= 0) {
					continue;
				}

				int joint = jointIndices[v * 4 + k];
				Matrix4f matrix = joint < joints.length ? joints[joint] : null;

				if (matrix == null) {
					px += positions[v * 3] * weight;
					py += positions[v * 3 + 1] * weight;
					pz += positions[v * 3 + 2] * weight;
					nx += normals[v * 3] * weight;
					ny += normals[v * 3 + 1] * weight;
					nz += normals[v * 3 + 2] * weight;
					continue;
				}

				matrix.transformPosition(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], position);
				matrix.get3x3(rotation).transform(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2], normal);
				px += position.x * weight;
				py += position.y * weight;
				pz += position.z * weight;
				nx += normal.x * weight;
				ny += normal.y * weight;
				nz += normal.z * weight;
			}

			float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			positionsOut[v * 3] = px;
			positionsOut[v * 3 + 1] = py;
			positionsOut[v * 3 + 2] = pz;
			normalsOut[v * 3] = length > 0 ? nx / length : 0;
			normalsOut[v * 3 + 1] = length > 0 ? ny / length : 1;
			normalsOut[v * 3 + 2] = length > 0 ? nz / length : 0;
		}
	}
}
