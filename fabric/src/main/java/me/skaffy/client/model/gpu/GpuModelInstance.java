package me.skaffy.client.model.gpu;

import java.nio.ByteBuffer;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import me.skaffy.client.model.ModelData;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public final class GpuModelInstance {
	private final GpuModel model;
	private final @Nullable GpuBuffer[] buffers;
	private final @Nullable ByteBuffer[] pending;
	private final boolean[] dirty;
	private final int[] cellLight;
	private long lightTick = Long.MIN_VALUE;
	private final Vector3f lightAt = new Vector3f(Float.NaN);
	private boolean closed;

	public GpuModelInstance(GpuModel model) {
		this.model = model;
		List<GpuModel.DynamicMesh> dynamic = model.dynamicMeshes();
		buffers = new GpuBuffer[dynamic.size()];
		pending = new ByteBuffer[dynamic.size()];
		dirty = new boolean[dynamic.size()];
		cellLight = new int[model.cells().size()];
	}

	public GpuModel model() {
		return model;
	}

	int[] cellLight() {
		return cellLight;
	}

	boolean lightDue(long tick, float x, float y, float z) {
		if (tick - lightTick < 5 && lightAt.distanceSquared(x, y, z) < 0.25f) {
			return false;
		}

		lightTick = tick;
		lightAt.set(x, y, z);
		return true;
	}

	void write(int dynamic, float[] positions, float[] normals, ModelData.Mesh mesh) {
		int count = mesh.vertexCount();
		ByteBuffer data = pending[dynamic];

		if (data == null) {
			data = MemoryUtil.memAlloc(count * GpuModel.VERTEX_SIZE);
			pending[dynamic] = data;
		}

		data.clear();
		Vector3f normal = new Vector3f();

		for (int v = 0; v < count; v++) {
			normal.set(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2]);
			GpuModel.putVertex(data, positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], mesh.uvs()[v * 2], mesh.uvs()[v * 2 + 1],
					mesh.colors() == null ? -1 : mesh.colors()[v], normal);
		}

		data.flip();
		dirty[dynamic] = true;
	}

	void uploadPending() {
		if (closed) {
			return;
		}

		for (int i = 0; i < pending.length; i++) {
			ByteBuffer data = pending[i];

			if (data == null || !dirty[i]) {
				continue;
			}

			dirty[i] = false;

			if (buffers[i] == null) {
				int index = i;
				buffers[i] = RenderSystem.getDevice().createBuffer(() -> "Skaffy model " + model.model().definition().name() + " skinned " + index,
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.remaining());
			}

			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffers[i].slice(), data);
		}
	}

	@Nullable GpuBuffer buffer(int dynamic) {
		return dynamic >= 0 && dynamic < buffers.length ? buffers[dynamic] : null;
	}

	public void close() {
		if (closed) {
			return;
		}

		closed = true;

		for (int i = 0; i < buffers.length; i++) {
			if (buffers[i] != null) {
				buffers[i].close();
			}

			if (pending[i] != null) {
				MemoryUtil.memFree(pending[i]);
				pending[i] = null;
			}
		}
	}
}
