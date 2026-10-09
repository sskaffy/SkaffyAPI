package me.skaffy.client.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.model.ModelData.AlphaMode;
import me.skaffy.client.model.ModelData.Material;
import me.skaffy.client.model.ModelData.Sampling;
import me.skaffy.client.model.ModelData.TextureData;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.entitymodels.ModelDefinition;

import org.jspecify.annotations.Nullable;

final class ModelLoader {
	private final DiskAssetStore assets;
	private final Map<String, Map<String, AnimationClip>> animationFiles = new ConcurrentHashMap<>();

	ModelLoader(DiskAssetStore assets) {
		this.assets = assets;
	}

	ClientModel.Format format(String asset) {
		byte[] data = read(asset);

		if (data == null) {
			return ClientModel.Format.BEDROCK;
		}

		if (GltfReader.matches(data)) {
			return ClientModel.Format.GLTF;
		}

		try {
			JsonObject json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
			return BbModelReader.matches(json) ? ClientModel.Format.BLOCKBENCH : ClientModel.Format.BEDROCK;
		} catch (RuntimeException e) {
			return ClientModel.Format.BEDROCK;
		}
	}

	ModelData load(ClientModel model) {
		ModelDefinition definition = model.definition();
		List<String> warnings = new ArrayList<>();
		ModelData data;

		try {
			data = switch (model.format()) {
				case BEDROCK -> bedrock(model);
				case BLOCKBENCH -> BbModelReader.read(json(definition.geometry()), definition.translucent(), warnings);
				case GLTF -> GltfReader.read(require(definition.geometry()), this::fileOfModel, warnings);
			};
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException("Model " + definition.name() + " (" + definition.geometry() + "): " + e.getMessage(), e);
		}

		for (String warning : warnings) {
			SkaffySAPIClient.LOGGER.warn("Model {}: {}", definition.name(), warning);
		}

		SkaffySAPIClient.LOGGER.info("Read model {}: {} bones, {} triangles, {} materials, {} animations", definition.name(), data.bones().size(), data.triangleCount(),
				data.materials().size(), data.animations().size());

		if (data.bones().isEmpty() && data.triangleCount() == 0) {
			SkaffySAPIClient.LOGGER.warn("Model {} ({}, read as {}) has nothing in it, so it draws nothing", definition.name(), definition.geometry(), model.format());
		}
		return data;
	}

	private ModelData bedrock(ClientModel model) throws IOException {
		BedrockGeometry geometry = model.geometry();

		if (geometry == null) {
			throw new IOException("its geometry couldn't be read");
		}

		ModelDefinition definition = model.definition();
		TextureData texture = image(definition.texture(), "base");
		TextureData emissive = image(definition.emissiveTexture(), "emissive");
		AlphaMode alpha = definition.translucent() ? AlphaMode.BLEND : AlphaMode.MASK;
		Material material = new Material("texture", texture, -1, alpha, 0.1f, false, false, emissive, emissive == null ? new float[3] : new float[] {1, 1, 1}, false,
				null, 1, ModelData.SpecularKind.NONE, null, 0, 1, new float[3], Sampling.PIXELS, null);
		GeometryMesher.Result meshed = GeometryMesher.mesh(geometry, new int[][] {{geometry.textureWidth(), geometry.textureHeight()}}, index -> 0);
		return new ModelData(meshed.bones(), meshed.locators(), List.of(material), meshed.meshes(), Map.of());
	}

	private @Nullable TextureData image(@Nullable String asset, String key) {
		if (asset == null) {
			return null;
		}

		byte[] data = read(asset);

		if (data == null) {
			SkaffySAPIClient.LOGGER.warn("Model texture {} not found", asset);
			return null;
		}

		try {
			return new TextureData(key, ImageDecoding.decode(data));
		} catch (IOException e) {
			SkaffySAPIClient.LOGGER.warn("Model texture {} can't be read: {}", asset, e.getMessage());
			return null;
		}
	}

	AnimationClip clip(ClientModel model, @Nullable ModelData data, ModelDefinition.AnimationRef ref) {
		Map<String, AnimationClip> file = data != null && ref.asset().equals(model.definition().geometry()) ? data.animations() : animations(ref.asset());
		AnimationClip clip = file.get(ref.name());

		if (clip == null) {
			SkaffySAPIClient.LOGGER.warn("Model {} uses animation {} from {}, which isn't in that file", model.definition().name(), ref.name(), ref.asset());
			return AnimationClip.empty(ref.name());
		}

		return clip;
	}

	Map<String, AnimationClip> animations(String asset) {
		return animationFiles.computeIfAbsent(asset, id -> {
			byte[] data = read(id);

			if (data == null) {
				SkaffySAPIClient.LOGGER.warn("Animation file {} wasn't sent by the server", id);
				return Map.of();
			}

			List<String> warnings = new ArrayList<>();

			try {
				Map<String, AnimationClip> result;

				if (GltfReader.matches(data)) {
					ModelData model = GltfReader.read(data, this::fileOfModel, warnings);
					model.releaseImages();
					result = model.animations();
				} else {
					JsonObject json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();

					if (BbModelReader.matches(json)) {
						ModelData model = BbModelReader.read(json, false, warnings);
						model.releaseImages();
						result = model.animations();
					} else {
						result = BedrockAnimation.parseFile(json, warnings);
					}
				}

				warnings.forEach(warning -> SkaffySAPIClient.LOGGER.warn("Animation file {}: {}", id, warning));
				return result;
			} catch (IOException | RuntimeException e) {
				SkaffySAPIClient.LOGGER.warn("Animation file {} can't be read: {}", id, e.getMessage());
				return Map.of();
			}
		});
	}

	private byte @Nullable [] fileOfModel(String uri) {
		String id = uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1) : uri;
		return Protocol.isValidAssetId(id) ? read(id) : null;
	}

	byte @Nullable [] read(String asset) {
		try {
			return Protocol.isValidAssetId(asset) && assets.size(asset) >= 0 ? assets.read(asset) : null;
		} catch (IOException e) {
			return null;
		}
	}

	private byte[] require(String asset) throws IOException {
		byte[] data = read(asset);

		if (data == null) {
			throw new IOException("the server didn't send " + asset);
		}

		return data;
	}

	private JsonObject json(String asset) throws IOException {
		return JsonParser.parseString(new String(require(asset), StandardCharsets.UTF_8)).getAsJsonObject();
	}
}
