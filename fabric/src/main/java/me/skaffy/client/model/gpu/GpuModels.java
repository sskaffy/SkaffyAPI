package me.skaffy.client.model.gpu;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.model.ClientModel;
import me.skaffy.client.model.ModelData;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;

public final class GpuModels {
	private static final long FRAME_BUDGET = 48L * 1024 * 1024;
	private static final Map<ClientModel, Entry> MODELS = new IdentityHashMap<>();
	private static final long[] BUDGET = {FRAME_BUDGET};
	private static long budgetRefilled;

	private static final class Entry {
		@Nullable CompletableFuture<GpuModel> building;
		@Nullable GpuModel model;
		@Nullable Skeleton skeleton;
		boolean failed;
	}

	static {
		ClientModel.ModelReleaseListeners.add(model -> Minecraft.getInstance().execute(() -> drop(model)));
	}

	private GpuModels() {
	}

	public static @Nullable GpuModel get(ClientModel model) {
		ModelData data = model.data();

		if (data == null) {
			return null;
		}

		Entry entry = MODELS.computeIfAbsent(model, m -> new Entry());

		if (entry.failed) {
			return null;
		}

		if (entry.model == null) {
			if (entry.building == null) {
				entry.building = CompletableFuture.supplyAsync(() -> new GpuModel(model, data), Util.backgroundExecutor());
				entry.building.whenCompleteAsync((built, error) -> {
					if (MODELS.get(model) != entry) {
						if (built != null) {
							built.close();
						}

						return;
					}

					if (error != null) {
						entry.failed = true;
						SkaffySAPIClient.LOGGER.warn("Model {} couldn't be prepared for drawing", model.definition().name(), error);
					} else {
						entry.model = built;
						entry.skeleton = new Skeleton(data);
					}
				}, Minecraft.getInstance());
			}

			return null;
		}

		if (!entry.model.uploaded()) {
			long now = Util.getMillis();

			if (now - budgetRefilled > 8) {
				BUDGET[0] = FRAME_BUDGET;
				budgetRefilled = now;
			}

			if (BUDGET[0] <= 0 || !entry.model.upload(BUDGET)) {
				return null;
			}

			releaseBigImages(model, data);
		}

		return entry.model;
	}

	private static void releaseBigImages(ClientModel model, ModelData data) {
		long bytes = 0;

		for (ModelData.Material material : data.materials()) {
			if (material.texture() != null) {
				bytes += (long) material.texture().width() * material.texture().height() * 4;
			}
		}

		if (model.format() == ClientModel.Format.GLTF && bytes > 32L * 1024 * 1024) {
			data.releaseImages();
		}
	}

	public static @Nullable Skeleton skeleton(ClientModel model) {
		Entry entry = MODELS.get(model);
		return entry == null ? null : entry.skeleton;
	}

	private static void drop(ClientModel model) {
		Entry entry = MODELS.remove(model);

		if (entry != null && entry.model != null) {
			entry.model.close();
		}
	}

	public static void clear() {
		MODELS.values().forEach(entry -> {
			if (entry.model != null) {
				entry.model.close();
			}
		});
		MODELS.clear();
	}
}
