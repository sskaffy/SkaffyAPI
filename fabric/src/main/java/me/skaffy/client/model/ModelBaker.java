package me.skaffy.client.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.skaffy.client.mixin.ModelPartAccessor;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

final class ModelBaker {
	private static final Direction[] FACE_ORDER = {Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};

	private ModelBaker() {
	}

	static ModelPart bake(BedrockGeometry geometry, float scale, @Nullable ModelPart vanilla) {
		Node root = new Node(PartPose.ZERO);
		Map<String, Node> nodes = new HashMap<>();
		Map<String, BedrockGeometry.Bone> bones = new HashMap<>();

		for (BedrockGeometry.Bone bone : geometry.bones()) {
			if (nodes.containsKey(bone.name())) {
				continue;
			}

			bones.put(bone.name(), bone);
			BedrockGeometry.Bone parent = bone.parent() == null ? null : findParent(geometry, bone);
			Node node = new Node(pose(bone.pivot(), parent == null ? null : parent.pivot(), bone.rotation()));
			int rotatedCubes = 0;

			for (BedrockGeometry.Cube cube : bone.cubes()) {
				if (cube.rotation() == null || isZero(cube.rotation())) {
					node.cubes.add(cube(cube, bone.pivot(), geometry));
				} else {
					Node cubeNode = new Node(pose(cube.pivot(), bone.pivot(), cube.rotation()));
					cubeNode.cubes.add(cube(cube, cube.pivot(), geometry));
					node.children.put("skaffy_cube_" + rotatedCubes++, cubeNode);
				}
			}

			nodes.put(bone.name(), node);
		}

		for (BedrockGeometry.Bone bone : geometry.bones()) {
			Node node = nodes.get(bone.name());

			if (node == null || node.linked) {
				continue;
			}

			node.linked = true;
			Node parent = bone.parent() == null ? null : nodes.get(bone.parent());
			(parent != null && parent != node ? parent : root).children.put(bone.name(), node);
		}

		if (scale != 1) {
			float yOffset = 24.016f * (1 - scale);
			root.children.values().forEach(node -> node.pose = node.pose.scaled(scale).translated(0, yOffset, 0));
		}

		if (vanilla != null) {
			addMissing(root, vanilla);
		}

		return root.build();
	}

	private static BedrockGeometry.Bone findParent(BedrockGeometry geometry, BedrockGeometry.Bone bone) {
		for (BedrockGeometry.Bone candidate : geometry.bones()) {
			if (candidate.name().equals(bone.parent()) && candidate != bone) {
				return candidate;
			}
		}

		return null;
	}

	private static PartPose pose(float[] pivot, float @Nullable [] parentPivot, float[] rotation) {
		float x = parentPivot == null ? pivot[0] : pivot[0] - parentPivot[0];
		float y = parentPivot == null ? 24 - pivot[1] : -(pivot[1] - parentPivot[1]);
		float z = parentPivot == null ? pivot[2] : pivot[2] - parentPivot[2];
		return PartPose.offsetAndRotation(x, y, z, (float) Math.toRadians(rotation[0]), (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[2]));
	}

	private static ModelPart.Cube cube(BedrockGeometry.Cube cube, float[] pivot, BedrockGeometry geometry) {
		float[] origin = cube.origin();
		float[] size = cube.size();
		float minX = origin[0] - pivot[0];
		float minY = pivot[1] - origin[1] - size[1];
		float minZ = origin[2] - pivot[2];
		float inflate = cube.inflate();
		int textureWidth = geometry.textureWidth();
		int textureHeight = geometry.textureHeight();

		if (cube.faces() == null) {
			return new ModelPart.Cube(cube.boxUv()[0], cube.boxUv()[1], minX, minY, minZ, size[0], size[1], size[2], inflate, inflate, inflate,
					cube.mirror(), textureWidth, textureHeight, EnumSet.allOf(Direction.class));
		}

		Map<Direction, float[]> uvs = new HashMap<>();
		cube.faces().forEach((face, uv) -> {
			Direction direction = direction(face);

			if (direction != null) {
				uvs.put(direction, uv);
			}
		});

		Set<Direction> visible = uvs.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(uvs.keySet());
		ModelPart.Cube built = new ModelPart.Cube(0, 0, minX, minY, minZ, size[0], size[1], size[2], inflate, inflate, inflate, false, textureWidth, textureHeight, visible);
		int index = 0;

		for (Direction direction : FACE_ORDER) {
			if (!visible.contains(direction)) {
				continue;
			}

			float[] uv = uvs.get(direction);
			ModelPart.Vertex[] vertices = built.polygons[index].vertices().clone();
			float u0 = uv[0];
			float u1 = uv[0] + uv[2];
			float v0 = direction == Direction.UP ? uv[1] + uv[3] : uv[1];
			float v1 = direction == Direction.UP ? uv[1] : uv[1] + uv[3];
			built.polygons[index++] = new ModelPart.Polygon(vertices, u0, v0, u1, v1, textureWidth, textureHeight, false, direction);
		}

		return built;
	}

	private static @Nullable Direction direction(String face) {
		return switch (face) {
			case "north" -> Direction.NORTH;
			case "south" -> Direction.SOUTH;
			case "east" -> Direction.WEST;
			case "west" -> Direction.EAST;
			case "up" -> Direction.DOWN;
			case "down" -> Direction.UP;
			default -> null;
		};
	}

	private static boolean isZero(float[] vector) {
		return vector[0] == 0 && vector[1] == 0 && vector[2] == 0;
	}

	private static void addMissing(Node node, ModelPart vanilla) {
		((ModelPartAccessor) (Object) vanilla).skaffy$children().forEach((name, vanillaChild) -> {
			Node child = node.children.get(name);

			if (child == null) {
				child = new Node(vanillaChild.getInitialPose());
				node.children.put(name, child);
			}

			addMissing(child, vanillaChild);
		});
	}

	private static final class Node {
		private PartPose pose;
		private final List<ModelPart.Cube> cubes = new ArrayList<>();
		private final Map<String, Node> children = new LinkedHashMap<>();
		private boolean linked;

		Node(PartPose pose) {
			this.pose = pose;
		}

		ModelPart build() {
			Map<String, ModelPart> built = new LinkedHashMap<>();
			children.forEach((name, child) -> built.put(name, child.build()));
			ModelPart part = new ModelPart(List.copyOf(cubes), built);
			part.setInitialPose(pose);
			part.loadPose(pose);
			return part;
		}
	}
}
