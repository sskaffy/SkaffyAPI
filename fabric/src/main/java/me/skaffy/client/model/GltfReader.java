package me.skaffy.client.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import me.skaffy.client.model.AnimationClip.BoneTrack;
import me.skaffy.client.model.AnimationClip.Interpolation;
import me.skaffy.client.model.AnimationClip.Kind;
import me.skaffy.client.model.AnimationClip.Loop;
import me.skaffy.client.model.AnimationClip.MorphTrack;
import me.skaffy.client.model.AnimationClip.SampledChannel;
import me.skaffy.client.model.ModelData.AlphaMode;
import me.skaffy.client.model.ModelData.Material;
import me.skaffy.client.model.ModelData.Mesh;
import me.skaffy.client.model.ModelData.Sampling;
import me.skaffy.client.model.ModelData.SpecularKind;
import me.skaffy.client.model.ModelData.TextureData;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class GltfReader {
	private static final int GLB_MAGIC = 0x46546C67;
	private static final int CHUNK_JSON = 0x4E4F534A;
	private static final int CHUNK_BIN = 0x004E4942;
	private static final float GROUND = 24;

	public interface Files {
		byte @Nullable [] read(String uri);
	}

	private final JsonObject json;
	private final List<ByteBuffer> buffers = new ArrayList<>();
	private final Files files;
	private final List<String> warnings;
	private final Map<Integer, TextureData> images = new HashMap<>();
	private final Map<Integer, Integer> uvSets = new HashMap<>();
	private final Map<Integer, float[][]> uvTransforms = new HashMap<>();

	private GltfReader(JsonObject json, @Nullable ByteBuffer binary, Files files, List<String> warnings) throws IOException {
		this.json = json;
		this.files = files;
		this.warnings = warnings;

		for (JsonElement element : array(json, "buffers")) {
			JsonObject buffer = element.getAsJsonObject();

			if (!buffer.has("uri")) {
				buffers.add(binary != null ? binary : ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN));
			} else {
				buffers.add(ByteBuffer.wrap(load(buffer.get("uri").getAsString())).order(ByteOrder.LITTLE_ENDIAN));
			}
		}
	}

	public static boolean matches(byte[] data) {
		if (data.length >= 12 && ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC) {
			return true;
		}

		if (data.length == 0 || data[0] == (byte) 0x89) {
			return false;
		}

		try (JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
			if (reader.peek() != JsonToken.BEGIN_OBJECT) {
				return false;
			}

			reader.beginObject();

			while (reader.hasNext()) {
				if (reader.nextName().equals("asset") && reader.peek() == JsonToken.BEGIN_OBJECT) {
					return true;
				}

				reader.skipValue();
			}
		} catch (IOException | RuntimeException e) {
			return false;
		}

		return false;
	}

	public static ModelData read(byte[] data, Files files, List<String> warnings) throws IOException {
		ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		JsonObject json;
		ByteBuffer binary = null;

		if (data.length >= 12 && bytes.getInt(0) == GLB_MAGIC) {
			int offset = 12;
			json = null;

			while (offset + 8 <= data.length) {
				int length = bytes.getInt(offset);
				int type = bytes.getInt(offset + 4);

				if (offset + 8 + length > data.length) {
					throw new IOException("Broken .glb chunk");
				}

				if (type == CHUNK_JSON) {
					json = JsonParser.parseString(new String(data, offset + 8, length, StandardCharsets.UTF_8)).getAsJsonObject();
				} else if (type == CHUNK_BIN && binary == null) {
					binary = ByteBuffer.wrap(data, offset + 8, length).slice().order(ByteOrder.LITTLE_ENDIAN);
				}

				offset += 8 + length;
			}

			if (json == null) {
				throw new IOException(".glb without JSON");
			}
		} else {
			json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
		}

		return new GltfReader(json, binary, files, warnings).model();
	}

	private ModelData model() throws IOException {
		JsonArray nodes = array(json, "nodes");
		int[] parents = new int[nodes.size()];
		java.util.Arrays.fill(parents, -1);

		for (int i = 0; i < nodes.size(); i++) {
			for (JsonElement child : array(nodes.get(i).getAsJsonObject(), "children")) {
				int index = child.getAsInt();

				if (index >= 0 && index < parents.length) {
					parents[index] = i;
				}
			}
		}

		List<Integer> roots = new ArrayList<>();
		JsonArray scenes = array(json, "scenes");

		if (!scenes.isEmpty()) {
			int scene = json.has("scene") ? json.get("scene").getAsInt() : 0;

			for (JsonElement node : array(scenes.get(Math.clamp(scene, 0, scenes.size() - 1)).getAsJsonObject(), "nodes")) {
				roots.add(node.getAsInt());
			}
		} else {
			for (int i = 0; i < nodes.size(); i++) {
				if (parents[i] < 0) {
					roots.add(i);
				}
			}
		}

		List<Integer> order = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();

		for (int root : roots) {
			if (root >= 0 && root < nodes.size() && seen.add(root)) {
				queue.add(root);
				parents[root] = -1;
			}
		}

		while (!queue.isEmpty()) {
			int node = queue.poll();
			order.add(node);

			for (JsonElement child : array(nodes.get(node).getAsJsonObject(), "children")) {
				int index = child.getAsInt();

				if (index >= 0 && index < nodes.size() && seen.add(index)) {
					parents[index] = node;
					queue.add(index);
				}
			}
		}

		Map<Integer, Integer> boneOfNode = new HashMap<>();
		List<ModelData.Bone> bones = new ArrayList<>();
		List<ModelData.Locator> locators = new ArrayList<>();
		Set<String> names = new HashSet<>();
		String[] nodeNames = new String[nodes.size()];

		for (int node : order) {
			JsonObject nodeJson = nodes.get(node).getAsJsonObject();
			String name = nodeJson.has("name") && !nodeJson.get("name").getAsString().isEmpty() ? nodeJson.get("name").getAsString() : "node" + node;

			while (!names.add(name)) {
				name = name + "_" + node;
			}

			nodeNames[node] = name;
			float[] t = new float[3];
			Quaternionf q = new Quaternionf();
			float[] s = {1, 1, 1};
			transform(nodeJson, t, q, s);
			boolean root = parents[node] < 0;
			Vector3f euler = new Matrix3f().rotation(new Quaternionf(q.x, -q.y, -q.z, q.w)).getEulerAnglesZYX(new Vector3f());
			boneOfNode.put(node, bones.size());
			bones.add(new ModelData.Bone(name, root ? -1 : boneOfNode.get(parents[node]), t[0] * 16, -t[1] * 16 + (root ? GROUND : 0), -t[2] * 16,
					euler.x, euler.y, euler.z, s[0], s[1], s[2]));

			if (nodeJson.has("camera")) {
				locators.add(new ModelData.Locator(name, boneOfNode.get(node), 0, 0, 0, 0, (float) Math.PI, 0));
			}
		}

		List<Material> materials = new ArrayList<>();
		JsonArray materialsJson = array(json, "materials");

		for (int i = 0; i < materialsJson.size(); i++) {
			materials.add(material(materialsJson.get(i).getAsJsonObject(), i));
		}

		int defaultMaterial = -1;
		List<Mesh> meshes = new ArrayList<>();
		JsonArray meshesJson = array(json, "meshes");
		JsonArray skins = array(json, "skins");
		Map<Integer, List<Integer>> meshesOfNode = new HashMap<>();

		for (int node : order) {
			JsonObject nodeJson = nodes.get(node).getAsJsonObject();

			if (!nodeJson.has("mesh")) {
				continue;
			}

			int meshIndex = nodeJson.get("mesh").getAsInt();

			if (meshIndex < 0 || meshIndex >= meshesJson.size()) {
				continue;
			}

			JsonObject meshJson = meshesJson.get(meshIndex).getAsJsonObject();
			JsonObject skin = nodeJson.has("skin") && nodeJson.get("skin").getAsInt() < skins.size() ? skins.get(nodeJson.get("skin").getAsInt()).getAsJsonObject() : null;
			float[] defaultWeights = meshJson.has("weights") ? floats(meshJson.getAsJsonArray("weights")) : null;

			for (JsonElement primitiveElement : array(meshJson, "primitives")) {
				JsonObject primitive = primitiveElement.getAsJsonObject();
				int mode = primitive.has("mode") ? primitive.get("mode").getAsInt() : 4;

				if (mode != 4 && mode != 5 && mode != 6) {
					continue;
				}

				int material;

				if (primitive.has("material") && primitive.get("material").getAsInt() < materials.size()) {
					material = primitive.get("material").getAsInt();
				} else {
					if (defaultMaterial < 0) {
						defaultMaterial = materials.size();
						materials.add(Material.plain("default", null, AlphaMode.OPAQUE, false, new Sampling(true, true, true, true), null, false));
					}

					material = defaultMaterial;
				}

				Mesh mesh = primitive(primitive, mode, skin == null ? boneOfNode.get(node) : -1, material, materials.get(material), skin, boneOfNode, defaultWeights);

				if (mesh != null) {
					meshesOfNode.computeIfAbsent(node, k -> new ArrayList<>()).add(meshes.size());
					meshes.add(mesh);
				}
			}
		}

		Map<String, AnimationClip> animations = new LinkedHashMap<>();
		JsonArray animationsJson = array(json, "animations");

		for (int i = 0; i < animationsJson.size(); i++) {
			AnimationClip clip = animation(animationsJson.get(i).getAsJsonObject(), i, nodeNames, parents, meshesOfNode);
			animations.putIfAbsent(clip.name(), clip);
		}

		return new ModelData(List.copyOf(bones), List.copyOf(locators), List.copyOf(materials), List.copyOf(meshes), animations);
	}

	private static void transform(JsonObject node, float[] t, Quaternionf q, float[] s) {
		if (node.has("matrix")) {
			float[] m = floats(node.getAsJsonArray("matrix"));

			if (m.length == 16) {
				Matrix4f matrix = new Matrix4f().set(m);
				Vector3f translation = matrix.getTranslation(new Vector3f());
				Vector3f scale = matrix.getScale(new Vector3f());
				matrix.getUnnormalizedRotation(q);
				q.normalize();
				t[0] = translation.x;
				t[1] = translation.y;
				t[2] = translation.z;
				s[0] = scale.x;
				s[1] = scale.y;
				s[2] = scale.z;
			}

			return;
		}

		if (node.has("translation")) {
			float[] value = floats(node.getAsJsonArray("translation"));
			System.arraycopy(value, 0, t, 0, Math.min(3, value.length));
		}

		if (node.has("rotation")) {
			float[] value = floats(node.getAsJsonArray("rotation"));

			if (value.length == 4) {
				q.set(value[0], value[1], value[2], value[3]).normalize();
			}
		}

		if (node.has("scale")) {
			float[] value = floats(node.getAsJsonArray("scale"));
			System.arraycopy(value, 0, s, 0, Math.min(3, value.length));
		}
	}

	private @Nullable Mesh primitive(JsonObject primitive, int mode, int bone, int materialIndex, Material material, @Nullable JsonObject skin, Map<Integer, Integer> boneOfNode,
			float @Nullable [] defaultWeights) throws IOException {
		JsonObject attributes = primitive.getAsJsonObject("attributes");

		if (attributes == null || !attributes.has("POSITION")) {
			return null;
		}

		float[] positions = accessor(attributes.get("POSITION").getAsInt());
		int vertexCount = positions.length / 3;
		float[] normals = attributes.has("NORMAL") ? accessor(attributes.get("NORMAL").getAsInt()) : null;
		int uvSet = uvSets.getOrDefault(materialIndex, 0);
		String uvName = "TEXCOORD_" + uvSet;
		float[] uvs = attributes.has(uvName) ? accessor(attributes.get(uvName).getAsInt()) : attributes.has("TEXCOORD_0") ? accessor(attributes.get("TEXCOORD_0").getAsInt())
				: new float[vertexCount * 2];
		int[] colors = null;

		if (attributes.has("COLOR_0")) {
			int colorAccessor = attributes.get("COLOR_0").getAsInt();
			float[] color = accessor(colorAccessor);
			int components = components(array(json, "accessors").get(colorAccessor).getAsJsonObject().get("type").getAsString());
			colors = new int[vertexCount];

			for (int i = 0; i < vertexCount; i++) {
				float r = color[i * components];
				float g = color[i * components + 1];
				float b = color[i * components + 2];
				float a = components > 3 ? color[i * components + 3] : 1;
				colors[i] = argb(a, r, g, b);
			}
		}

		int[] indices;

		if (primitive.has("indices")) {
			float[] raw = accessor(primitive.get("indices").getAsInt());
			indices = new int[raw.length];

			for (int i = 0; i < raw.length; i++) {
				indices[i] = (int) raw[i];
			}
		} else {
			indices = new int[vertexCount];

			for (int i = 0; i < vertexCount; i++) {
				indices[i] = i;
			}
		}

		indices = triangles(indices, mode, vertexCount);

		if (indices.length == 0) {
			return null;
		}

		boolean skinned = skin != null && attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0");

		if (normals == null) {
			normals = flatNormals(positions, indices);
		}

		float offset = skinned ? GROUND / 16 : 0;

		for (int i = 0; i < vertexCount; i++) {
			positions[i * 3 + 1] = -positions[i * 3 + 1] + offset;
			positions[i * 3 + 2] = -positions[i * 3 + 2];
			normals[i * 3 + 1] = -normals[i * 3 + 1];
			normals[i * 3 + 2] = -normals[i * 3 + 2];
		}

		float[][] transform = uvTransforms.get(materialIndex);

		if (transform != null) {
			for (int i = 0; i < vertexCount; i++) {
				float u = uvs[i * 2];
				float v = uvs[i * 2 + 1];
				uvs[i * 2] = transform[0][0] * u + transform[0][1] * v + transform[0][2];
				uvs[i * 2 + 1] = transform[1][0] * u + transform[1][1] * v + transform[1][2];
			}
		}

		int[] joints = null;
		float[] weights = null;
		Matrix4f[] inverseBind = null;

		if (skinned) {
			JsonArray jointNodes = skin.getAsJsonArray("joints");
			float[] jointData = accessor(attributes.get("JOINTS_0").getAsInt());
			float[] weightData = accessor(attributes.get("WEIGHTS_0").getAsInt());
			joints = new int[vertexCount * 4];
			weights = new float[vertexCount * 4];

			for (int i = 0; i < vertexCount * 4; i++) {
				int joint = (int) jointData[i];
				Integer boneIndex = joint >= 0 && joint < jointNodes.size() ? boneOfNode.get(jointNodes.get(joint).getAsInt()) : null;
				joints[i] = boneIndex == null ? 0 : boneIndex;
				weights[i] = boneIndex == null ? 0 : weightData[i];
			}

			for (int i = 0; i < vertexCount; i++) {
				float total = weights[i * 4] + weights[i * 4 + 1] + weights[i * 4 + 2] + weights[i * 4 + 3];

				if (total > 0) {
					for (int k = 0; k < 4; k++) {
						weights[i * 4 + k] /= total;
					}
				} else {
					weights[i * 4] = 1;
				}
			}

			inverseBind = new Matrix4f[boneOfNode.size()];
			float[] matrices = skin.has("inverseBindMatrices") ? accessor(skin.get("inverseBindMatrices").getAsInt()) : null;
			Matrix4f c = new Matrix4f().scale(1, -1, -1);
			Matrix4f inverseA = new Matrix4f().translation(0, GROUND / 16, 0).mul(c).invert();

			for (int j = 0; j < jointNodes.size(); j++) {
				Integer boneIndex = boneOfNode.get(jointNodes.get(j).getAsInt());

				if (boneIndex == null) {
					continue;
				}

				Matrix4f matrix = new Matrix4f();

				if (matrices != null && matrices.length >= (j + 1) * 16) {
					matrix.set(java.util.Arrays.copyOfRange(matrices, j * 16, j * 16 + 16));
				}

				inverseBind[boneIndex] = new Matrix4f(c).mul(matrix).mul(inverseA);
			}
		}

		float[][] morphPositions = null;
		float[][] morphNormals = null;
		float[] morphWeights = null;
		JsonArray targets = array(primitive, "targets");

		if (!targets.isEmpty()) {
			morphPositions = new float[targets.size()][];
			morphNormals = new float[targets.size()][];
			morphWeights = new float[targets.size()];

			for (int t = 0; t < targets.size(); t++) {
				JsonObject target = targets.get(t).getAsJsonObject();
				morphPositions[t] = target.has("POSITION") ? flip(accessor(target.get("POSITION").getAsInt())) : new float[vertexCount * 3];
				morphNormals[t] = target.has("NORMAL") ? flip(accessor(target.get("NORMAL").getAsInt())) : null;
				morphWeights[t] = defaultWeights != null && t < defaultWeights.length ? defaultWeights[t] : 0;
			}
		}

		return new Mesh(bone, materialIndex, positions, normals, uvs, colors, indices, joints, weights, inverseBind, morphPositions, morphNormals, morphWeights);
	}

	private static float[] flip(float[] vectors) {
		for (int i = 0; i + 2 < vectors.length; i += 3) {
			vectors[i + 1] = -vectors[i + 1];
			vectors[i + 2] = -vectors[i + 2];
		}

		return vectors;
	}

	private static int[] triangles(int[] indices, int mode, int vertexCount) {
		int[] result;

		if (mode == 4) {
			result = java.util.Arrays.copyOf(indices, indices.length / 3 * 3);
		} else {
			List<Integer> list = new ArrayList<>();

			for (int i = 2; i < indices.length; i++) {
				if (mode == 5) {
					if (i % 2 == 0) {
						list.add(indices[i - 2]);
						list.add(indices[i - 1]);
					} else {
						list.add(indices[i - 1]);
						list.add(indices[i - 2]);
					}
				} else {
					list.add(indices[0]);
					list.add(indices[i - 1]);
				}

				list.add(indices[i]);
			}

			result = list.stream().mapToInt(Integer::intValue).toArray();
		}

		for (int index : result) {
			if (index < 0 || index >= vertexCount) {
				return new int[0];
			}
		}

		return result;
	}

	private static float[] flatNormals(float[] positions, int[] indices) {
		float[] normals = new float[positions.length];

		for (int i = 0; i + 2 < indices.length; i += 3) {
			int a = indices[i] * 3;
			int b = indices[i + 1] * 3;
			int c = indices[i + 2] * 3;
			Vector3f normal = new Vector3f(positions[b] - positions[a], positions[b + 1] - positions[a + 1], positions[b + 2] - positions[a + 2])
					.cross(positions[c] - positions[a], positions[c + 1] - positions[a + 1], positions[c + 2] - positions[a + 2]);

			for (int corner : new int[] {a, b, c}) {
				normals[corner] += normal.x;
				normals[corner + 1] += normal.y;
				normals[corner + 2] += normal.z;
			}
		}

		for (int i = 0; i < normals.length; i += 3) {
			float length = (float) Math.sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1] + normals[i + 2] * normals[i + 2]);

			if (length > 0) {
				normals[i] /= length;
				normals[i + 1] /= length;
				normals[i + 2] /= length;
			} else {
				normals[i + 1] = 1;
			}
		}

		return normals;
	}

	private Material material(JsonObject material, int index) throws IOException {
		JsonObject pbr = material.has("pbrMetallicRoughness") ? material.getAsJsonObject("pbrMetallicRoughness") : new JsonObject();
		JsonObject extensions = material.has("extensions") ? material.getAsJsonObject("extensions") : new JsonObject();
		JsonObject specGloss = extensions.has("KHR_materials_pbrSpecularGlossiness") ? extensions.getAsJsonObject("KHR_materials_pbrSpecularGlossiness") : null;
		JsonObject baseInfo = specGloss != null ? specGloss.has("diffuseTexture") ? specGloss.getAsJsonObject("diffuseTexture") : null
				: pbr.has("baseColorTexture") ? pbr.getAsJsonObject("baseColorTexture") : null;
		float[] baseFactor = specGloss != null ? floats(specGloss, "diffuseFactor", new float[] {1, 1, 1, 1}) : floats(pbr, "baseColorFactor", new float[] {1, 1, 1, 1});
		TextureData base = texture(baseInfo);
		Sampling sampling = sampling(baseInfo);
		textureCoordinates(baseInfo, index);
		AlphaMode alpha = switch (material.has("alphaMode") ? material.get("alphaMode").getAsString() : "OPAQUE") {
			case "MASK" -> AlphaMode.MASK;
			case "BLEND" -> AlphaMode.BLEND;
			default -> AlphaMode.OPAQUE;
		};
		float cutoff = material.has("alphaCutoff") ? material.get("alphaCutoff").getAsFloat() : 0.5f;
		boolean doubleSided = material.has("doubleSided") && material.get("doubleSided").getAsBoolean();
		boolean unlit = extensions.has("KHR_materials_unlit");
		TextureData emissive = texture(material.has("emissiveTexture") ? material.getAsJsonObject("emissiveTexture") : null);
		float[] emissiveColor = floats(material, "emissiveFactor", new float[] {0, 0, 0});

		if (extensions.has("KHR_materials_emissive_strength")) {
			float strength = extensions.getAsJsonObject("KHR_materials_emissive_strength").has("emissiveStrength")
					? extensions.getAsJsonObject("KHR_materials_emissive_strength").get("emissiveStrength").getAsFloat()
					: 1;

			for (int i = 0; i < 3; i++) {
				emissiveColor[i] *= strength;
			}
		}

		JsonObject normalInfo = material.has("normalTexture") ? material.getAsJsonObject("normalTexture") : null;
		TextureData normal = texture(normalInfo);
		float normalScale = normalInfo != null && normalInfo.has("scale") ? normalInfo.get("scale").getAsFloat() : 1;
		SpecularKind kind = SpecularKind.NONE;
		TextureData specular = null;
		float metallic;
		float roughness;
		float[] specularColor;

		if (specGloss != null) {
			specular = texture(specGloss.has("specularGlossinessTexture") ? specGloss.getAsJsonObject("specularGlossinessTexture") : null);
			kind = specular != null ? SpecularKind.SPECULAR_GLOSSINESS : SpecularKind.NONE;
			specularColor = floats(specGloss, "specularFactor", new float[] {1, 1, 1});
			roughness = 1 - (specGloss.has("glossinessFactor") ? specGloss.get("glossinessFactor").getAsFloat() : 1);
			metallic = 0;
		} else {
			specular = texture(pbr.has("metallicRoughnessTexture") ? pbr.getAsJsonObject("metallicRoughnessTexture") : null);
			kind = specular != null ? SpecularKind.METALLIC_ROUGHNESS : SpecularKind.NONE;
			metallic = pbr.has("metallicFactor") ? pbr.get("metallicFactor").getAsFloat() : 1;
			roughness = pbr.has("roughnessFactor") ? pbr.get("roughnessFactor").getAsFloat() : 1;
			specularColor = new float[] {0.04f, 0.04f, 0.04f};
		}

		return new Material(material.has("name") ? material.get("name").getAsString() : "material" + index, base,
				argb(baseFactor[3], baseFactor[0], baseFactor[1], baseFactor[2]), alpha, cutoff, doubleSided, unlit, emissive, emissiveColor, false, normal, normalScale,
				kind, specular, metallic, roughness, specularColor, sampling, null);
	}

	private void textureCoordinates(@Nullable JsonObject info, int material) {
		if (info == null) {
			return;
		}

		int set = info.has("texCoord") ? info.get("texCoord").getAsInt() : 0;
		JsonObject extensions = info.has("extensions") ? info.getAsJsonObject("extensions") : null;

		if (extensions != null && extensions.has("KHR_texture_transform")) {
			JsonObject transform = extensions.getAsJsonObject("KHR_texture_transform");
			float[] offset = floats(transform, "offset", new float[] {0, 0});
			float[] scale = floats(transform, "scale", new float[] {1, 1});
			float rotation = transform.has("rotation") ? transform.get("rotation").getAsFloat() : 0;
			float cos = (float) Math.cos(rotation);
			float sin = (float) Math.sin(rotation);
			uvTransforms.put(material, new float[][] {{cos * scale[0], sin * scale[1], offset[0]}, {-sin * scale[0], cos * scale[1], offset[1]}});

			if (transform.has("texCoord")) {
				set = transform.get("texCoord").getAsInt();
			}
		}

		uvSets.put(material, set);
	}

	private @Nullable TextureData texture(@Nullable JsonObject info) throws IOException {
		if (info == null || !info.has("index")) {
			return null;
		}

		JsonArray textures = array(json, "textures");
		int index = info.get("index").getAsInt();

		if (index < 0 || index >= textures.size()) {
			return null;
		}

		JsonObject texture = textures.get(index).getAsJsonObject();

		if (!texture.has("source")) {
			warnings.add("Texture " + index + " uses an image format that isn't supported (only PNG and JPEG)");
			return null;
		}

		int source = texture.get("source").getAsInt();
		TextureData cached = images.get(source);

		if (cached != null || images.containsKey(source)) {
			return cached;
		}

		JsonArray imagesJson = array(json, "images");
		TextureData data = null;

		if (source >= 0 && source < imagesJson.size()) {
			JsonObject image = imagesJson.get(source).getAsJsonObject();

			try {
				byte[] bytes;

				if (image.has("bufferView")) {
					ByteBuffer view = bufferView(image.get("bufferView").getAsInt());
					bytes = new byte[view.remaining()];
					view.get(bytes);
				} else if (image.has("uri")) {
					bytes = load(image.get("uri").getAsString());
				} else {
					bytes = null;
				}

				if (bytes != null) {
					data = new TextureData("i" + source, ImageDecoding.decode(bytes));
				}
			} catch (IOException e) {
				warnings.add("Image " + source + " can't be read: " + e.getMessage());
			}
		}

		images.put(source, data);
		return data;
	}

	private Sampling sampling(@Nullable JsonObject info) {
		if (info == null || !info.has("index")) {
			return new Sampling(true, true, true, true);
		}

		JsonArray textures = array(json, "textures");
		int index = info.get("index").getAsInt();
		JsonObject texture = index >= 0 && index < textures.size() ? textures.get(index).getAsJsonObject() : new JsonObject();
		JsonArray samplers = array(json, "samplers");
		JsonObject sampler = texture.has("sampler") && texture.get("sampler").getAsInt() < samplers.size() ? samplers.get(texture.get("sampler").getAsInt()).getAsJsonObject()
				: new JsonObject();
		int mag = sampler.has("magFilter") ? sampler.get("magFilter").getAsInt() : 9729;
		int min = sampler.has("minFilter") ? sampler.get("minFilter").getAsInt() : 9987;
		int wrapS = sampler.has("wrapS") ? sampler.get("wrapS").getAsInt() : 10497;
		int wrapT = sampler.has("wrapT") ? sampler.get("wrapT").getAsInt() : 10497;
		boolean smooth = mag != 9728;
		boolean mipmaps = min >= 9984 && min <= 9987;
		return new Sampling(smooth, mipmaps, wrapS != 33071, wrapT != 33071);
	}

	private AnimationClip animation(JsonObject animation, int index, String[] nodeNames, int[] parents, Map<Integer, List<Integer>> meshesOfNode) throws IOException {
		String name = animation.has("name") && !animation.get("name").getAsString().isEmpty() ? animation.get("name").getAsString() : "animation" + index;
		JsonArray samplers = array(animation, "samplers");
		Map<String, SampledChannel[]> channels = new LinkedHashMap<>();
		List<MorphTrack> morphs = new ArrayList<>();
		float length = 0;

		for (JsonElement element : array(animation, "channels")) {
			JsonObject channel = element.getAsJsonObject();
			JsonObject target = channel.getAsJsonObject("target");

			if (target == null || !target.has("node") || !channel.has("sampler")) {
				continue;
			}

			int node = target.get("node").getAsInt();
			int samplerIndex = channel.get("sampler").getAsInt();

			if (node < 0 || node >= nodeNames.length || nodeNames[node] == null || samplerIndex < 0 || samplerIndex >= samplers.size()) {
				continue;
			}

			JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
			float[] times = accessor(sampler.get("input").getAsInt());
			float[] values = accessor(sampler.get("output").getAsInt());
			Interpolation interpolation = switch (sampler.has("interpolation") ? sampler.get("interpolation").getAsString() : "LINEAR") {
				case "STEP" -> Interpolation.STEP;
				case "CUBICSPLINE" -> Interpolation.CUBICSPLINE;
				default -> Interpolation.LINEAR;
			};

			if (times.length == 0) {
				continue;
			}

			length = Math.max(length, times[times.length - 1]);
			String path = target.has("path") ? target.get("path").getAsString() : "";
			boolean root = parents[node] < 0;
			int stride = interpolation == Interpolation.CUBICSPLINE ? 3 : 1;

			switch (path) {
				case "translation" -> {
					for (int i = 0; i + 2 < values.length; i += 3) {
						boolean tangent = stride == 3 && (i / 3) % 3 != 1;
						values[i] *= 16;
						values[i + 1] = -values[i + 1] * 16 + (root && !tangent ? GROUND : 0);
						values[i + 2] *= -16;
					}

					channels.computeIfAbsent(nodeNames[node], k -> new SampledChannel[3])[1] = new SampledChannel(Kind.POSITION_SET, times, values, 3, interpolation);
				}
				case "rotation" -> {
					for (int i = 0; i + 3 < values.length; i += 4) {
						values[i + 1] = -values[i + 1];
						values[i + 2] = -values[i + 2];
					}

					channels.computeIfAbsent(nodeNames[node], k -> new SampledChannel[3])[0] = new SampledChannel(Kind.ROTATION_SET, times, values, 4, interpolation);
				}
				case "scale" -> channels.computeIfAbsent(nodeNames[node], k -> new SampledChannel[3])[2] = new SampledChannel(Kind.SCALE_SET, times, values, 3, interpolation);
				case "weights" -> {
					int targets = values.length / Math.max(1, times.length * stride);

					for (int mesh : meshesOfNode.getOrDefault(node, List.of())) {
						morphs.add(new MorphTrack(mesh, new SampledChannel(Kind.SCALE_SET, times, values, targets, interpolation)));
					}
				}
				default -> {
				}
			}
		}

		List<BoneTrack> bones = new ArrayList<>();
		channels.forEach((bone, set) -> bones.add(new BoneTrack(bone, set[0], set[1], set[2])));
		return new AnimationClip(name, length, Loop.LOOP, false, List.copyOf(bones), List.copyOf(morphs), List.of(), List.of());
	}


	private float[] accessor(int index) throws IOException {
		JsonArray accessors = array(json, "accessors");

		if (index < 0 || index >= accessors.size()) {
			throw new IOException("Missing accessor " + index);
		}

		JsonObject accessor = accessors.get(index).getAsJsonObject();
		int count = accessor.get("count").getAsInt();
		int components = components(accessor.get("type").getAsString());
		int componentType = accessor.get("componentType").getAsInt();
		boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();
		float[] values = new float[count * components];

		if (accessor.has("bufferView")) {
			JsonObject view = array(json, "bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
			ByteBuffer data = bufferView(accessor.get("bufferView").getAsInt());
			int offset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
			int size = componentSize(componentType);
			int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : size * components;

			for (int i = 0; i < count; i++) {
				for (int c = 0; c < components; c++) {
					values[i * components + c] = read(data, offset + i * stride + c * size, componentType, normalized);
				}
			}
		}

		if (accessor.has("sparse")) {
			JsonObject sparse = accessor.getAsJsonObject("sparse");
			int sparseCount = sparse.get("count").getAsInt();
			JsonObject indicesJson = sparse.getAsJsonObject("indices");
			JsonObject valuesJson = sparse.getAsJsonObject("values");
			ByteBuffer indexData = bufferView(indicesJson.get("bufferView").getAsInt());
			int indexOffset = indicesJson.has("byteOffset") ? indicesJson.get("byteOffset").getAsInt() : 0;
			int indexType = indicesJson.get("componentType").getAsInt();
			ByteBuffer valueData = bufferView(valuesJson.get("bufferView").getAsInt());
			int valueOffset = valuesJson.has("byteOffset") ? valuesJson.get("byteOffset").getAsInt() : 0;
			int size = componentSize(componentType);

			for (int i = 0; i < sparseCount; i++) {
				int target = (int) read(indexData, indexOffset + i * componentSize(indexType), indexType, false);

				if (target < 0 || target >= count) {
					continue;
				}

				for (int c = 0; c < components; c++) {
					values[target * components + c] = read(valueData, valueOffset + (i * components + c) * size, componentType, normalized);
				}
			}
		}

		return values;
	}

	private ByteBuffer bufferView(int index) throws IOException {
		JsonArray views = array(json, "bufferViews");

		if (index < 0 || index >= views.size()) {
			throw new IOException("Missing buffer view " + index);
		}

		JsonObject view = views.get(index).getAsJsonObject();
		int buffer = view.get("buffer").getAsInt();

		if (buffer < 0 || buffer >= buffers.size()) {
			throw new IOException("Missing buffer " + buffer);
		}

		ByteBuffer data = buffers.get(buffer);
		int offset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
		int length = view.get("byteLength").getAsInt();

		if (offset < 0 || offset + length > data.capacity()) {
			throw new IOException("Buffer view " + index + " is outside its buffer");
		}

		return data.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(offset).limit(offset + length).slice().order(ByteOrder.LITTLE_ENDIAN);
	}

	private static float read(ByteBuffer data, int offset, int type, boolean normalized) throws IOException {
		if (offset < 0 || offset + componentSize(type) > data.capacity()) {
			throw new IOException("Accessor reads outside its buffer view");
		}

		return switch (type) {
			case 5120 -> normalized ? Math.max(data.get(offset) / 127f, -1) : data.get(offset);
			case 5121 -> normalized ? (data.get(offset) & 0xFF) / 255f : data.get(offset) & 0xFF;
			case 5122 -> normalized ? Math.max(data.getShort(offset) / 32767f, -1) : data.getShort(offset);
			case 5123 -> normalized ? (data.getShort(offset) & 0xFFFF) / 65535f : data.getShort(offset) & 0xFFFF;
			case 5125 -> (float) (data.getInt(offset) & 0xFFFFFFFFL);
			case 5126 -> data.getFloat(offset);
			default -> throw new IOException("Unknown component type " + type);
		};
	}

	private static int componentSize(int type) {
		return switch (type) {
			case 5120, 5121 -> 1;
			case 5122, 5123 -> 2;
			default -> 4;
		};
	}

	private static int components(String type) {
		return switch (type) {
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4", "MAT2" -> 4;
			case "MAT3" -> 9;
			case "MAT4" -> 16;
			default -> 1;
		};
	}

	private byte[] load(String uri) throws IOException {
		if (uri.startsWith("data:")) {
			int comma = uri.indexOf(',');

			if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) {
				throw new IOException("Only base64 data URIs are supported");
			}

			return Base64.getDecoder().decode(uri.substring(comma + 1));
		}

		String decoded = URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
		byte[] data = files.read(decoded);

		if (data == null) {
			throw new IOException("The model needs " + decoded + ", which the server didn't send");
		}

		return data;
	}

	private static JsonArray array(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : new JsonArray();
	}

	private static float[] floats(JsonArray array) {
		float[] values = new float[array.size()];

		for (int i = 0; i < values.length; i++) {
			values[i] = array.get(i).getAsFloat();
		}

		return values;
	}

	private static float[] floats(JsonObject json, String key, float[] fallback) {
		if (!json.has(key) || !json.get(key).isJsonArray()) {
			return fallback;
		}

		float[] values = floats(json.getAsJsonArray(key));
		return values.length >= fallback.length ? values : fallback;
	}

	private static int argb(float a, float r, float g, float b) {
		return Math.clamp(Math.round(a * 255), 0, 255) << 24 | Math.clamp(Math.round(r * 255), 0, 255) << 16 | Math.clamp(Math.round(g * 255), 0, 255) << 8
				| Math.clamp(Math.round(b * 255), 0, 255);
	}
}
