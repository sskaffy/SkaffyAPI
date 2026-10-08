package me.skaffy.client.model.gpu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import me.skaffy.client.model.ClientModel;
import me.skaffy.client.model.ModelData;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public final class GpuModel {
	public static final int VERTEX_SIZE = 28;
	private static final float CELL_SIZE = 16;

	public record Range(int bone, int first, int count) {
	}

	public record Cell(int material, int first, int count, Range[] ranges, Vector3f min, Vector3f max, Vector3f center) {
	}

	public record DynamicMesh(int mesh, int material, int first, int count, int vertices) {
	}

	private final ClientModel model;
	private final ModelData data;
	private final Matrix4f[] rest;
	private final Matrix4f[] restInverse;
	private final List<Cell> cells;
	private final List<DynamicMesh> dynamic;
	private final Vector3f min = new Vector3f(Float.MAX_VALUE);
	private final Vector3f max = new Vector3f(-Float.MAX_VALUE);
	private @Nullable ByteBuffer vertexData;
	private @Nullable ByteBuffer indexData;
	private @Nullable GpuBuffer vertexBuffer;
	private @Nullable GpuBuffer indexBuffer;
	private final ModelTextures.MaterialTextures[] materials;
	private final ModelTextures.@Nullable Prepared[] prepared;
	private int uploadedMaterials;
	private boolean closed;

	GpuModel(ClientModel model, ModelData data) {
		this.model = model;
		this.data = data;
		int boneCount = data.bones().size();
		rest = new Matrix4f[boneCount];
		restInverse = new Matrix4f[boneCount];

		for (int i = 0; i < boneCount; i++) {
			ModelData.Bone bone = data.bones().get(i);
			Matrix4f matrix = bone.parent() >= 0 ? new Matrix4f(rest[bone.parent()]) : new Matrix4f();
			partTransform(matrix, bone.x(), bone.y(), bone.z(), bone.xRot(), bone.yRot(), bone.zRot(), bone.xScale(), bone.yScale(), bone.zScale());
			rest[i] = matrix;
			restInverse[i] = new Matrix4f(matrix).invert();
		}

		int vertexCount = 0;
		int indexCount = 0;

		for (ModelData.Mesh mesh : data.meshes()) {
			if (!dynamic(mesh)) {
				vertexCount += mesh.vertexCount();
			}

			indexCount += mesh.indices().length;
		}

		ByteBuffer vertices = MemoryUtil.memAlloc(Math.max(1, vertexCount) * VERTEX_SIZE);
		int[] indices = new int[indexCount];
		int indexCursor = 0;
		record Key(int material, int x, int y, int z) {
		}
		Map<Key, Map<Integer, IntList>> grouped = new LinkedHashMap<>();
		Map<Key, Vector3f[]> bounds = new HashMap<>();
		List<DynamicMesh> dynamicMeshes = new ArrayList<>();
		List<int[]> dynamicIndices = new ArrayList<>();
		int base = 0;
		Vector3f position = new Vector3f();
		Vector3f normal = new Vector3f();

		for (int m = 0; m < data.meshes().size(); m++) {
			ModelData.Mesh mesh = data.meshes().get(m);

			if (dynamic(mesh)) {
				dynamicIndices.add(mesh.indices());
				dynamicMeshes.add(new DynamicMesh(m, mesh.material(), -1, mesh.indices().length, mesh.vertexCount()));
				continue;
			}

			Matrix4f matrix = mesh.bone() >= 0 ? rest[mesh.bone()] : new Matrix4f();
			Matrix3f normals = matrix.normal(new Matrix3f());
			float[] p = mesh.positions();
			float[] world = new float[p.length];

			for (int v = 0; v < mesh.vertexCount(); v++) {
				matrix.transformPosition(p[v * 3], p[v * 3 + 1], p[v * 3 + 2], position);
				normals.transform(mesh.normals()[v * 3], mesh.normals()[v * 3 + 1], mesh.normals()[v * 3 + 2], normal).normalize();
				world[v * 3] = position.x;
				world[v * 3 + 1] = position.y;
				world[v * 3 + 2] = position.z;
				putVertex(vertices, position.x, position.y, position.z, mesh.uvs()[v * 2], mesh.uvs()[v * 2 + 1], mesh.colors() == null ? -1 : mesh.colors()[v], normal);
				min.min(position);
				max.max(position);
			}

			int[] meshIndices = mesh.indices();

			for (int i = 0; i + 2 < meshIndices.length; i += 3) {
				int a = meshIndices[i];
				int b = meshIndices[i + 1];
				int c = meshIndices[i + 2];
				float cx = (world[a * 3] + world[b * 3] + world[c * 3]) / 3;
				float cy = (world[a * 3 + 1] + world[b * 3 + 1] + world[c * 3 + 1]) / 3;
				float cz = (world[a * 3 + 2] + world[b * 3 + 2] + world[c * 3 + 2]) / 3;
				Key key = new Key(mesh.material(), (int) Math.floor(cx / CELL_SIZE), (int) Math.floor(cy / CELL_SIZE), (int) Math.floor(cz / CELL_SIZE));
				grouped.computeIfAbsent(key, k -> new LinkedHashMap<>()).computeIfAbsent(mesh.bone(), k -> new IntList()).add(base + a, base + b, base + c);
				Vector3f[] box = bounds.computeIfAbsent(key, k -> new Vector3f[] {new Vector3f(Float.MAX_VALUE), new Vector3f(-Float.MAX_VALUE)});

				for (int corner : new int[] {a, b, c}) {
					position.set(world[corner * 3], world[corner * 3 + 1], world[corner * 3 + 2]);
					box[0].min(position);
					box[1].max(position);
				}
			}

			base += mesh.vertexCount();
		}

		vertices.flip();

		if (vertices.limit() == 0) {
			vertices.limit(VERTEX_SIZE);
		}

		List<Cell> builtCells = new ArrayList<>();

		for (Map.Entry<Key, Map<Integer, IntList>> entry : grouped.entrySet()) {
			int first = indexCursor;
			List<Range> ranges = new ArrayList<>();

			for (Map.Entry<Integer, IntList> bone : entry.getValue().entrySet()) {
				IntList list = bone.getValue();
				System.arraycopy(list.data, 0, indices, indexCursor, list.size);
				ranges.add(new Range(bone.getKey(), indexCursor, list.size));
				indexCursor += list.size;
			}

			Vector3f[] box = bounds.get(entry.getKey());
			builtCells.add(new Cell(entry.getKey().material(), first, indexCursor - first, ranges.toArray(Range[]::new), box[0], box[1],
					new Vector3f(box[0]).add(box[1]).mul(0.5f)));
		}

		List<DynamicMesh> placedDynamic = new ArrayList<>();

		for (int i = 0; i < dynamicMeshes.size(); i++) {
			DynamicMesh mesh = dynamicMeshes.get(i);
			int[] meshIndices = dynamicIndices.get(i);
			System.arraycopy(meshIndices, 0, indices, indexCursor, meshIndices.length);
			placedDynamic.add(new DynamicMesh(mesh.mesh(), mesh.material(), indexCursor, meshIndices.length, mesh.vertices()));
			indexCursor += meshIndices.length;
			float[] p = data.meshes().get(mesh.mesh()).positions();

			for (int v = 0; v + 2 < p.length; v += 3) {
				position.set(p[v], p[v + 1], p[v + 2]);
				min.min(position);
				max.max(position);
			}
		}

		ByteBuffer indexBytes = MemoryUtil.memAlloc(Math.max(1, indexCursor) * 4);
		indexBytes.asIntBuffer().put(indices, 0, indexCursor);
		indexBytes.limit(Math.max(4, indexCursor * 4));
		this.vertexData = vertices;
		this.indexData = indexBytes;
		this.cells = List.copyOf(builtCells);
		this.dynamic = List.copyOf(placedDynamic);
		this.materials = new ModelTextures.MaterialTextures[data.materials().size()];
		this.prepared = new ModelTextures.Prepared[materials.length];

		for (int i = 0; i < materials.length; i++) {
			prepared[i] = ModelTextures.prepare(data.materials().get(i));
		}

		if (min.x > max.x) {
			min.set(0);
			max.set(0);
		}
	}

	static boolean dynamic(ModelData.Mesh mesh) {
		return mesh.skinned() || mesh.morphed();
	}

	static void partTransform(Matrix4f matrix, float x, float y, float z, float xRot, float yRot, float zRot, float xScale, float yScale, float zScale) {
		matrix.translate(x / 16, y / 16, z / 16);

		if (xRot != 0 || yRot != 0 || zRot != 0) {
			matrix.rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot));
		}

		if (xScale != 1 || yScale != 1 || zScale != 1) {
			matrix.scale(xScale, yScale, zScale);
		}
	}

	static void putVertex(ByteBuffer buffer, float x, float y, float z, float u, float v, int argb, Vector3f normal) {
		buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
		buffer.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
		buffer.put(normalByte(normal.x)).put(normalByte(normal.y)).put(normalByte(normal.z)).put((byte) 0);
	}

	private static byte normalByte(float value) {
		return (byte) Math.round(Math.clamp(value, -1, 1) * 127);
	}

	boolean upload(long[] budget) {
		if (closed) {
			return false;
		}

		if (vertexBuffer == null && vertexData != null && indexData != null) {
			vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Skaffy model " + model.definition().name() + " vertices", GpuBuffer.USAGE_VERTEX, vertexData);
			indexBuffer = RenderSystem.getDevice().createBuffer(() -> "Skaffy model " + model.definition().name() + " indices", GpuBuffer.USAGE_INDEX, indexData);
			budget[0] -= vertexData.remaining() + indexData.remaining();
			MemoryUtil.memFree(vertexData);
			MemoryUtil.memFree(indexData);
			vertexData = null;
			indexData = null;
		}

		while (uploadedMaterials < materials.length && budget[0] > 0) {
			ModelData.Material material = data.materials().get(uploadedMaterials);
			materials[uploadedMaterials] = ModelTextures.upload(model.definition().name() + "/" + material.name(), material, prepared[uploadedMaterials], budget);
			prepared[uploadedMaterials] = null;
			uploadedMaterials++;
		}

		return uploadedMaterials >= materials.length;
	}

	boolean uploaded() {
		return vertexBuffer != null && uploadedMaterials >= materials.length;
	}

	void close() {
		if (closed) {
			return;
		}

		closed = true;

		if (vertexBuffer != null) {
			vertexBuffer.close();
			indexBuffer.close();
		}

		if (vertexData != null) {
			MemoryUtil.memFree(vertexData);
			MemoryUtil.memFree(indexData);
			vertexData = null;
			indexData = null;
		}

		for (ModelTextures.MaterialTextures textures : materials) {
			if (textures != null) {
				textures.close();
			}
		}

		for (ModelTextures.Prepared waiting : prepared) {
			if (waiting != null) {
				waiting.close();
			}
		}
	}

	public ClientModel model() {
		return model;
	}

	public ModelData data() {
		return data;
	}

	public List<Cell> cells() {
		return cells;
	}

	public List<DynamicMesh> dynamicMeshes() {
		return dynamic;
	}

	public Matrix4f restMatrix(int bone) {
		return rest[bone];
	}

	public Matrix4f restInverse(int bone) {
		return restInverse[bone];
	}

	public Vector3f min() {
		return min;
	}

	public Vector3f max() {
		return max;
	}

	@Nullable GpuBuffer vertexBuffer() {
		return vertexBuffer;
	}

	@Nullable GpuBuffer indexBuffer() {
		return indexBuffer;
	}

	ModelTextures.MaterialTextures material(int index) {
		return materials[index];
	}

	static final class IntList {
		int[] data = new int[48];
		int size;

		void add(int a, int b, int c) {
			if (size + 3 > data.length) {
				data = java.util.Arrays.copyOf(data, data.length * 2);
			}

			data[size++] = a;
			data[size++] = b;
			data[size++] = c;
		}
	}
}
