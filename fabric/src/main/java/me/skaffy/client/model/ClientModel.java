package me.skaffy.client.model;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.protocol.entitymodels.ModelDefinition;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;

public final class ClientModel {
	private static final long KEEP_MILLIS = 60_000;

	public enum Format {
		BEDROCK,
		BLOCKBENCH,
		GLTF
	}

	private final int number;
	private final ModelDefinition definition;
	private final Format format;
	private final @Nullable BedrockGeometry geometry;
	private final @Nullable BedrockGeometry babyGeometry;
	private final @Nullable Identifier texture;
	private final @Nullable Identifier emissiveTexture;
	private final AnimationClip @Nullable [] eagerClips;
	private final ModelLoader loader;
	private volatile @Nullable ModelData data;
	private volatile @Nullable AnimationClip[] clips;
	private @Nullable CompletableFuture<ModelData> loading;
	private volatile boolean failed;
	private volatile long lastUsed;
	private int generation;

	ClientModel(int number, ModelDefinition definition, Format format, @Nullable BedrockGeometry geometry, @Nullable BedrockGeometry babyGeometry, @Nullable Identifier texture,
			@Nullable Identifier emissiveTexture, AnimationClip @Nullable [] eagerClips, ModelLoader loader) {
		this.number = number;
		this.definition = definition;
		this.format = format;
		this.geometry = geometry;
		this.babyGeometry = babyGeometry;
		this.texture = texture;
		this.emissiveTexture = emissiveTexture;
		this.eagerClips = eagerClips;
		this.clips = eagerClips;
		this.loader = loader;
	}

	public int number() {
		return number;
	}

	public ModelDefinition definition() {
		return definition;
	}

	public Format format() {
		return format;
	}

	public @Nullable BedrockGeometry geometry() {
		return geometry;
	}

	public @Nullable BedrockGeometry babyGeometry() {
		return babyGeometry;
	}

	public @Nullable Identifier texture() {
		return texture;
	}

	public @Nullable Identifier emissiveTexture() {
		return emissiveTexture;
	}

	public int animationCount() {
		return definition.animations().size();
	}

	public @Nullable AnimationClip clip(int number) {
		AnimationClip[] known = clips;

		if (number < 1 || number > definition.animations().size()) {
			return null;
		}

		if (known == null) {
			data();
			return null;
		}

		return known[number - 1];
	}

	public boolean replacesVanilla(int number) {
		return number >= 1 && number <= definition.animations().size() && definition.animations().get(number - 1).replacesVanilla();
	}

	public boolean hasAutomaticAnimations() {
		ModelDefinition d = definition;
		return d.idle() > 0 || d.walk() > 0 || d.attack() > 0 || d.hurt() > 0 || d.death() > 0;
	}

	public @Nullable ModelData data() {
		lastUsed = Util.getMillis();
		ModelData loaded = data;

		if (loaded != null || failed) {
			return loaded;
		}

		if (loading == null) {
			int started = generation;
			loading = CompletableFuture.supplyAsync(() -> loader.load(this), Util.backgroundExecutor());
			loading.whenCompleteAsync((result, error) -> {
				if (started != generation) {
					if (result != null) {
						result.releaseImages();
					}

					return;
				}

				loading = null;

				if (error != null || result == null) {
					failed = true;
					SkaffySAPIClient.LOGGER.warn("Model {} could not be read", definition.name(), error);
					return;
				}

				data = result;

				if (eagerClips == null) {
					AnimationClip[] resolved = new AnimationClip[definition.animations().size()];

					for (int i = 0; i < resolved.length; i++) {
						resolved[i] = loader.clip(this, result, definition.animations().get(i));
					}

					clips = resolved;
				}
			}, net.minecraft.client.Minecraft.getInstance());
		}

		return null;
	}

	public boolean ready() {
		return data != null || failed;
	}

	boolean releaseIfUnused(long now) {
		if (data == null || now - lastUsed < KEEP_MILLIS) {
			return false;
		}

		release();
		return true;
	}

	void release() {
		generation++;
		ModelData loaded = data;
		data = null;
		loading = null;

		if (eagerClips == null) {
			clips = null;
		}

		if (loaded != null) {
			loaded.releaseImages();
			ModelReleaseListeners.released(this);
		}
	}

	@Override
	public String toString() {
		return "ClientModel[" + number + " " + definition.name() + "]";
	}

	public static final class ModelReleaseListeners {
		private static final List<java.util.function.Consumer<ClientModel>> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

		private ModelReleaseListeners() {
		}

		public static void add(java.util.function.Consumer<ClientModel> listener) {
			LISTENERS.add(listener);
		}

		static void released(ClientModel model) {
			LISTENERS.forEach(listener -> listener.accept(model));
		}
	}
}
