package me.skaffy.client.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

import org.joml.Matrix4f;
import org.joml.Vector3f;

final class GeometryMesher {
	private static final Direction[] FACE_ORDER = {Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};

	private GeometryMesher() {
	}

	record Result(List<ModelData.Bone> bones, List<ModelData.Locator> locators, List<ModelData.Mesh> meshes) {
	}

	static Result mesh(BedrockGeometry geometry, int[][] textureSizes, java.util.function.IntUnaryOperator materialOf) {
		List<BedrockGeometry.Bone> ordered = order(geometry.bones());
		Map<String, Integer> indices = new HashMap<>();
		List<ModelData.Bone> bones = new ArrayList<>();
		List<ModelData.Locator> locators = new ArrayList<>();
		Map<Long, Builder> builders = new LinkedHashMap<>();

		for (BedrockGeometry.Bone bone : ordered) {
			BedrockGeometry.Bone parent = bone.parent() == null ? null : find(geometry.bones(), bone.parent(), bone);
			Integer parentIndex = parent == null ? null : indices.get(parent.name());
			float[] pivot = bone.pivot();
			float x = parentIndex == null ? pivot[0] : pivot[0] - parent.pivot()[0];
			float y = parentIndex == null ? 24 - pivot[1] : -(pivot[1] - parent.pivot()[1]);
			float z = parentIndex == null ? pivot[2] : pivot[2] - parent.pivot()[2];
			int index = bones.size();
			indices.put(bone.name(), index);
			bones.add(new ModelData.Bone(bone.name(), parentIndex == null ? -1 : parentIndex, x, y, z,
					rad(bone.rotation()[0]), rad(bone.rotation()[1]), rad(bone.rotation()[2]), 1, 1, 1));

			for (BedrockGeometry.Locator locator : bone.locators()) {
				float[] offset = locator.offset();
				locators.add(new ModelData.Locator(locator.name(), index, offset[0] - pivot[0], pivot[1] - offset[1], offset[2] - pivot[2],
						rad(locator.rotation()[0]), rad(locator.rotation()[1]), rad(locator.rotation()[2])));
			}

			for (BedrockGeometry.Cube cube : bone.cubes()) {
				Matrix4f transform = new Matrix4f();
				float[] cubePivot = pivot;

				if (cube.rotation() != null && !(cube.rotation()[0] == 0 && cube.rotation()[1] == 0 && cube.rotation()[2] == 0)) {
					cubePivot = cube.pivot();
					transform.translate((cubePivot[0] - pivot[0]) / 16, -(cubePivot[1] - pivot[1]) / 16, (cubePivot[2] - pivot[2]) / 16)
							.rotateZYX(rad(cube.rotation()[2]), rad(cube.rotation()[1]), rad(cube.rotation()[0]));
				}

				addCube(cube, cubePivot, transform, index, textureSizes, materialOf, builders);
			}

			for (BedrockGeometry.Polygon polygon : bone.polygons()) {
				int material = materialOf.applyAsInt(polygon.texture());

				if (material < 0) {
					continue;
				}

				int[] size = textureSizes[Math.clamp(polygon.texture(), 0, textureSizes.length - 1)];
				Builder builder = builders.computeIfAbsent(key(index, material), k -> new Builder(index, material));
				int corners = polygon.positions().length / 3;
				float[] facePositions = new float[corners * 3];
				float[] faceNormals = new float[corners * 3];
				float[] faceUvs = new float[corners * 2];

				for (int i = 0; i < corners; i++) {
					facePositions[i * 3] = (polygon.positions()[i * 3] - pivot[0]) / 16;
					facePositions[i * 3 + 1] = (pivot[1] - polygon.positions()[i * 3 + 1]) / 16;
					facePositions[i * 3 + 2] = (polygon.positions()[i * 3 + 2] - pivot[2]) / 16;
					float[] normal = polygon.normals() != null ? new float[] {polygon.normals()[i * 3], polygon.normals()[i * 3 + 1], polygon.normals()[i * 3 + 2]} : polygon.normal();
					faceNormals[i * 3] = normal[0];
					faceNormals[i * 3 + 1] = -normal[1];
					faceNormals[i * 3 + 2] = normal[2];
					faceUvs[i * 2] = polygon.uvs()[i * 2] / size[0];
					faceUvs[i * 2 + 1] = polygon.uvs()[i * 2 + 1] / size[1];
				}

				builder.polygon(facePositions, faceNormals, faceUvs, new float[] {polygon.normal()[0], -polygon.normal()[1], polygon.normal()[2]});
			}
		}

		List<ModelData.Mesh> meshes = new ArrayList<>();

		for (Builder builder : builders.values()) {
			if (!builder.indices.isEmpty()) {
				meshes.add(builder.build());
			}
		}

		return new Result(List.copyOf(bones), List.copyOf(locators), List.copyOf(meshes));
	}

	private static List<BedrockGeometry.Bone> order(List<BedrockGeometry.Bone> bones) {
		List<BedrockGeometry.Bone> ordered = new ArrayList<>();
		java.util.Set<String> placed = new java.util.HashSet<>();
		List<BedrockGeometry.Bone> left = new ArrayList<>();

		for (BedrockGeometry.Bone bone : bones) {
			if (placed.add(bone.name())) {
				left.add(bone);
			}
		}

		placed.clear();
		boolean progress = true;

		while (!left.isEmpty() && progress) {
			progress = false;

			for (int i = 0; i < left.size(); i++) {
				BedrockGeometry.Bone bone = left.get(i);
				BedrockGeometry.Bone parent = bone.parent() == null ? null : find(bones, bone.parent(), bone);

				if (parent == null || placed.contains(parent.name())) {
					ordered.add(bone);
					placed.add(bone.name());
					left.remove(i--);
					progress = true;
				}
			}
		}

		ordered.addAll(left);
		return ordered;
	}

	private static BedrockGeometry.Bone find(List<BedrockGeometry.Bone> bones, String name, BedrockGeometry.Bone self) {
		for (BedrockGeometry.Bone candidate : bones) {
			if (candidate.name().equals(name) && candidate != self) {
				return candidate;
			}
		}

		return null;
	}

	private static void addCube(BedrockGeometry.Cube cube, float[] pivot, Matrix4f transform, int bone, int[][] textureSizes, java.util.function.IntUnaryOperator materialOf,
			Map<Long, Builder> builders) {
		float[] origin = cube.origin();
		float[] size = cube.size();
		float minX = origin[0] - pivot[0];
		float minY = pivot[1] - origin[1] - size[1];
		float minZ = origin[2] - pivot[2];
		float inflate = cube.inflate();

		if (cube.faces() == null) {
			int material = materialOf.applyAsInt(cube.boxTexture());

			if (material < 0) {
				return;
			}

			int[] texture = textureSizes[Math.clamp(cube.boxTexture(), 0, textureSizes.length - 1)];
			ModelPart.Cube built = new ModelPart.Cube(cube.boxUv()[0], cube.boxUv()[1], minX, minY, minZ, size[0], size[1], size[2], inflate, inflate, inflate,
					cube.mirror(), texture[0], texture[1], EnumSet.allOf(Direction.class));
			Builder builder = builders.computeIfAbsent(key(bone, material), k -> new Builder(bone, material));

			for (ModelPart.Polygon polygon : built.polygons) {
				builder.vanilla(polygon, transform, 0);
			}

			return;
		}

		for (Direction direction : FACE_ORDER) {
			String name = faceName(direction);
			float[] uv = cube.faces().get(name);

			if (uv == null) {
				continue;
			}

			int textureIndex = uv.length > 5 ? (int) uv[5] : 0;
			int material = materialOf.applyAsInt(textureIndex);

			if (material < 0) {
				continue;
			}

			int[] texture = textureSizes[Math.clamp(textureIndex, 0, textureSizes.length - 1)];
			ModelPart.Cube built = new ModelPart.Cube(0, 0, minX, minY, minZ, size[0], size[1], size[2], inflate, inflate, inflate, false, texture[0], texture[1],
					EnumSet.of(direction));
			ModelPart.Vertex[] vertices = built.polygons[0].vertices().clone();
			float u0 = uv[0];
			float u1 = uv[0] + uv[2];
			float v0 = direction == Direction.UP ? uv[1] + uv[3] : uv[1];
			float v1 = direction == Direction.UP ? uv[1] : uv[1] + uv[3];
			ModelPart.Polygon polygon = new ModelPart.Polygon(vertices, u0, v0, u1, v1, texture[0], texture[1], false, direction);
			Builder builder = builders.computeIfAbsent(key(bone, material), k -> new Builder(bone, material));
			builder.vanilla(polygon, transform, uv.length > 4 ? Math.floorMod(Math.round(uv[4] / 90), 4) : 0);
		}
	}

	private static String faceName(Direction direction) {
		return switch (direction) {
			case NORTH -> "north";
			case SOUTH -> "south";
			case WEST -> "east";
			case EAST -> "west";
			case DOWN -> "up";
			case UP -> "down";
		};
	}

	private static long key(int bone, int material) {
		return (long) bone << 32 | material & 0xFFFFFFFFL;
	}

	private static float rad(float degrees) {
		return (float) Math.toRadians(degrees);
	}

	static final class Builder {
		private final int bone;
		private final int material;
		private final FloatList positions = new FloatList();
		private final FloatList normals = new FloatList();
		private final FloatList uvs = new FloatList();
		private final IntList indices = new IntList();

		Builder(int bone, int material) {
			this.bone = bone;
			this.material = material;
		}

		void vanilla(ModelPart.Polygon polygon, Matrix4f transform, int quarterTurns) {
			ModelPart.Vertex[] vertices = polygon.vertices();
			int count = vertices.length;
			float[] facePositions = new float[count * 3];
			float[] faceNormals = new float[count * 3];
			float[] faceUvs = new float[count * 2];
			Vector3f normal = transform.transformDirection(new Vector3f(polygon.normal()));

			for (int i = 0; i < count; i++) {
				Vector3f position = transform.transformPosition(vertices[i].x() / 16, vertices[i].y() / 16, vertices[i].z() / 16, new Vector3f());
				facePositions[i * 3] = position.x;
				facePositions[i * 3 + 1] = position.y;
				facePositions[i * 3 + 2] = position.z;
				faceNormals[i * 3] = normal.x;
				faceNormals[i * 3 + 1] = normal.y;
				faceNormals[i * 3 + 2] = normal.z;
				ModelPart.Vertex source = vertices[(i + quarterTurns) % count];
				faceUvs[i * 2] = source.u();
				faceUvs[i * 2 + 1] = source.v();
			}

			polygon(facePositions, faceNormals, faceUvs, new float[] {normal.x, normal.y, normal.z});
		}

		void polygon(float[] facePositions, float[] faceNormals, float[] faceUvs, float[] facing) {
			int corners = facePositions.length / 3;

			if (corners < 3) {
				return;
			}

			int base = positions.size() / 3;

			for (int i = 0; i < corners; i++) {
				positions.add(facePositions[i * 3], facePositions[i * 3 + 1], facePositions[i * 3 + 2]);
				normals.add(faceNormals[i * 3], faceNormals[i * 3 + 1], faceNormals[i * 3 + 2]);
				uvs.add(faceUvs[i * 2], faceUvs[i * 2 + 1]);
			}

			float nx = 0;
			float ny = 0;
			float nz = 0;

			for (int i = 0; i < corners; i++) {
				int j = (i + 1) % corners;
				float ax = facePositions[i * 3];
				float ay = facePositions[i * 3 + 1];
				float az = facePositions[i * 3 + 2];
				float bx = facePositions[j * 3];
				float by = facePositions[j * 3 + 1];
				float bz = facePositions[j * 3 + 2];
				nx += (ay - by) * (az + bz);
				ny += (az - bz) * (ax + bx);
				nz += (ax - bx) * (ay + by);
			}

			boolean flip = nx * facing[0] + ny * facing[1] + nz * facing[2] < 0;

			for (int i = 1; i + 1 < corners; i++) {
				if (flip) {
					indices.add(base, base + i + 1, base + i);
				} else {
					indices.add(base, base + i, base + i + 1);
				}
			}
		}

		ModelData.Mesh build() {
			return ModelData.Mesh.rigid(bone, material, positions.toArray(), normals.toArray(), uvs.toArray(), null, indices.toArray());
		}
	}

	static final class FloatList {
		private float[] data = new float[96];
		private int size;

		void add(float... values) {
			if (size + values.length > data.length) {
				data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + values.length));
			}

			System.arraycopy(values, 0, data, size, values.length);
			size += values.length;
		}

		int size() {
			return size;
		}

		float[] toArray() {
			return java.util.Arrays.copyOf(data, size);
		}
	}

	static final class IntList {
		private int[] data = new int[96];
		private int size;

		void add(int... values) {
			if (size + values.length > data.length) {
				data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + values.length));
			}

			System.arraycopy(values, 0, data, size, values.length);
			size += values.length;
		}

		boolean isEmpty() {
			return size == 0;
		}

		int size() {
			return size;
		}

		int[] toArray() {
			return java.util.Arrays.copyOf(data, size);
		}
	}
}
