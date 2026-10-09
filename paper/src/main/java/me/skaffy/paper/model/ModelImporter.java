package me.skaffy.paper.model;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.skaffy.api.AssetRegistry;
import me.skaffy.api.model.CustomEntityModel;
import me.skaffy.protocol.Protocol;

public final class ModelImporter {
	private static final int VERSION = 2;
	private static final int BUFFER_LIMIT = 15 * 1024 * 1024;
	private static final Gson GSON = new Gson();

	private final Path cache;
	private final AssetRegistry assets;
	private final Logger logger;

	public ModelImporter(Path cache, AssetRegistry assets, Logger logger) {
		this.cache = cache;
		this.assets = assets;
		this.logger = logger;
	}

	public static boolean isBedrockGeometry(byte[] data) {
		if (data.length == 0 || data[0] != '{') {
			return false;
		}

		try {
			JsonObject json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
			return json.has("minecraft:geometry") || json.keySet().stream().anyMatch(key -> key.startsWith("geometry."));
		} catch (RuntimeException e) {
			return false;
		}
	}

	public CustomEntityModel.Builder importModel(String name, Path source, int maxTextureSize) throws IOException {
		if (!name.matches("[a-z0-9_-]{1,64}")) {
			throw new IllegalArgumentException("Imported model names are 1 to 64 characters of a-z 0-9 _ - : " + name);
		}

		if (maxTextureSize < 16) {
			throw new IllegalArgumentException("The biggest texture size must be at least 16");
		}

		if (!Files.exists(source)) {
			throw new IOException("No such file or folder: " + source.toAbsolutePath());
		}

		String key = key(source, maxTextureSize);
		Path folder = cache.resolve(name);
		Path manifestFile = folder.resolve("manifest.json");
		Manifest manifest = null;

		if (Files.isRegularFile(manifestFile)) {
			try {
				Manifest cached = GSON.fromJson(Files.readString(manifestFile), Manifest.class);

				if (cached != null && key.equals(cached.key) && cached.files.stream().allMatch(file -> Files.isRegularFile(folder.resolve(file)))) {
					manifest = cached;
				}
			} catch (RuntimeException e) {
			}
		}

		if (manifest == null) {
			long started = System.nanoTime();
			Map<String, byte[]> output;

			try (Sources sources = Sources.open(source)) {
				output = convert(name, sources, maxTextureSize);
			}

			if (Files.isDirectory(folder)) {
				try (Stream<Path> old = Files.list(folder)) {
					for (Path file : old.toList()) {
						Files.deleteIfExists(file);
					}
				}
			}

			Files.createDirectories(folder);
			manifest = new Manifest();
			manifest.key = key;
			manifest.main = output.keySet().iterator().next();
			manifest.files = new ArrayList<>(output.keySet());
			manifest.animations = new ArrayList<>(AnimationInfo.read(output.get(manifest.main)).keySet());

			for (Map.Entry<String, byte[]> file : output.entrySet()) {
				Files.write(folder.resolve(file.getKey()), file.getValue());
			}

			manifest.summary = summary(name, output, (System.nanoTime() - started) / 1_000_000);
			Files.writeString(manifestFile, GSON.toJson(manifest));
		}

		for (String file : manifest.files) {
			assets.register(file, Files.readAllBytes(folder.resolve(file)));
		}

		logger.info(manifest.summary);
		CustomEntityModel.Builder builder = CustomEntityModel.builder(name, manifest.main);

		for (String animation : manifest.animations) {
			builder.animation(manifest.main, animation);
		}

		return builder;
	}

	private static final class Manifest {
		String key;
		String main;
		List<String> files = new ArrayList<>();
		List<String> animations = new ArrayList<>();
		String summary;
	}

	private static String key(Path source, int maxTextureSize) throws IOException {
		StringBuilder text = new StringBuilder().append(VERSION).append('|').append(maxTextureSize).append('|').append(source.toAbsolutePath().normalize());

		if (Files.isDirectory(source)) {
			try (Stream<Path> files = Files.walk(source)) {
				for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
					text.append('|').append(source.relativize(file)).append(':').append(Files.size(file)).append(':').append(Files.getLastModifiedTime(file).toMillis());
				}
			}
		} else {
			text.append(':').append(Files.size(source)).append(':').append(Files.getLastModifiedTime(source).toMillis());
		}

		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private interface Sources extends AutoCloseable {
		List<String> paths();

		byte[] read(String path) throws IOException;

		@Override
		void close() throws IOException;

		static Sources open(Path source) throws IOException {
			String lower = source.getFileName().toString().toLowerCase(Locale.ROOT);

			if (Files.isRegularFile(source) && lower.endsWith(".zip")) {
				ZipFile zip = new ZipFile(source.toFile());
				List<String> paths = zip.stream().filter(entry -> !entry.isDirectory()).map(ZipEntry::getName).toList();
				return new Sources() {
					@Override
					public List<String> paths() {
						return paths;
					}

					@Override
					public byte[] read(String path) throws IOException {
						ZipEntry entry = zip.getEntry(path);

						if (entry == null) {
							throw new IOException("The zip has no " + path);
						}

						try (var stream = zip.getInputStream(entry)) {
							return stream.readAllBytes();
						}
					}

					@Override
					public void close() throws IOException {
						zip.close();
					}
				};
			}

			Path root = Files.isDirectory(source) ? source : source.getParent();
			List<String> paths;

			if (Files.isDirectory(source)) {
				try (Stream<Path> files = Files.walk(source)) {
					paths = files.filter(Files::isRegularFile).map(file -> root.relativize(file).toString().replace('\\', '/')).sorted().toList();
				}
			} else {
				paths = List.of(source.getFileName().toString());
			}

			return new Sources() {
				@Override
				public List<String> paths() {
					return paths;
				}

				@Override
				public byte[] read(String path) throws IOException {
					return Files.readAllBytes(root.resolve(path));
				}

				@Override
				public void close() {
				}
			};
		}
	}

	private Map<String, byte[]> convert(String name, Sources sources, int maxTextureSize) throws IOException {
		String main = pick(sources.paths(), ".gltf");

		if (main == null) {
			main = pick(sources.paths(), ".glb");
		}

		if (main == null) {
			String project = pick(sources.paths(), ".bbmodel");

			if (project == null) {
				throw new IOException("No .gltf, .glb or .bbmodel found in " + sources.paths());
			}

			byte[] data = sources.read(project);

			if (data.length > Protocol.MAX_ASSET_SIZE) {
				throw new IOException(project + " is " + data.length / 1024 / 1024 + " MiB, but assets can be at most 16 MiB (use smaller textures)");
			}

			Map<String, byte[]> output = new LinkedHashMap<>();
			output.put(name + ".bbmodel", data);
			return output;
		}

		byte[] data = sources.read(main);
		String folder = main.contains("/") ? main.substring(0, main.lastIndexOf('/') + 1) : "";
		JsonObject json;
		ByteBuffer binary = null;
		ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

		if (data.length >= 12 && bytes.getInt(0) == 0x46546C67) {
			json = null;
			int offset = 12;

			while (offset + 8 <= data.length) {
				int length = bytes.getInt(offset);
				int type = bytes.getInt(offset + 4);

				if (type == 0x4E4F534A) {
					json = JsonParser.parseString(new String(data, offset + 8, length, StandardCharsets.UTF_8)).getAsJsonObject();
				} else if (type == 0x004E4942 && binary == null) {
					binary = ByteBuffer.wrap(data, offset + 8, length).slice().order(ByteOrder.LITTLE_ENDIAN);
				}

				offset += 8 + length;
			}

			if (json == null) {
				throw new IOException(main + " has no JSON");
			}
		} else {
			json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
		}

		return new GltfPacker(name, json, binary, folder, sources, maxTextureSize).pack();
	}

	private static String pick(List<String> paths, String extension) {
		return paths.stream()
				.filter(path -> path.toLowerCase(Locale.ROOT).endsWith(extension) && !path.startsWith("__MACOSX/"))
				.min((a, b) -> a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b))
				.orElse(null);
	}

	private static String summary(String name, Map<String, byte[]> output, long millis) {
		long bytes = output.values().stream().mapToLong(data -> data.length).sum();
		String detail = "";
		String main = output.keySet().iterator().next();

		if (main.endsWith(".gltf")) {
			JsonObject json = JsonParser.parseString(new String(output.get(main), StandardCharsets.UTF_8)).getAsJsonObject();
			detail = String.format(Locale.ROOT, ", %,d triangles, %d textures, size about %s blocks", triangles(json), count(json, "images"), size(json));
		}

		return String.format(Locale.ROOT, "Imported model %s: %d files, %.1f MiB%s (%d ms)", name, output.size(), bytes / 1024.0 / 1024.0, detail, millis);
	}

	private static long triangles(JsonObject json) {
		JsonArray accessors = json.getAsJsonArray("accessors");
		long count = 0;

		for (JsonElement mesh : array(json, "meshes")) {
			for (JsonElement primitive : array(mesh.getAsJsonObject(), "primitives")) {
				JsonObject p = primitive.getAsJsonObject();
				int accessor = p.has("indices") ? p.get("indices").getAsInt() : p.getAsJsonObject("attributes").get("POSITION").getAsInt();
				count += accessors.get(accessor).getAsJsonObject().get("count").getAsLong() / 3;
			}
		}

		return count;
	}

	private static int count(JsonObject json, String key) {
		int count = 0;

		for (JsonElement element : array(json, key)) {
			if (element.getAsJsonObject().has("uri")) {
				count++;
			}
		}

		return count;
	}

	private static String size(JsonObject json) {
		JsonArray nodes = array(json, "nodes");
		JsonArray meshes = array(json, "meshes");
		JsonArray accessors = array(json, "accessors");
		double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
		double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		JsonArray scenes = array(json, "scenes");
		List<Integer> roots = new ArrayList<>();

		if (!scenes.isEmpty()) {
			for (JsonElement root : array(scenes.get(json.has("scene") ? json.get("scene").getAsInt() : 0).getAsJsonObject(), "nodes")) {
				roots.add(root.getAsInt());
			}
		}

		for (int root : roots) {
			visit(nodes, meshes, accessors, root, new org.joml.Matrix4d(), min, max, 0);
		}

		if (min[0] > max[0]) {
			return "0 x 0 x 0";
		}

		return String.format(Locale.ROOT, "%.1f x %.1f x %.1f", max[0] - min[0], max[1] - min[1], max[2] - min[2]);
	}

	private static void visit(JsonArray nodes, JsonArray meshes, JsonArray accessors, int index, org.joml.Matrix4d parent, double[] min, double[] max, int depth) {
		if (index < 0 || index >= nodes.size() || depth > 256) {
			return;
		}

		JsonObject node = nodes.get(index).getAsJsonObject();
		org.joml.Matrix4d matrix = new org.joml.Matrix4d(parent);

		if (node.has("matrix")) {
			double[] values = new double[16];

			for (int i = 0; i < 16; i++) {
				values[i] = node.getAsJsonArray("matrix").get(i).getAsDouble();
			}

			matrix.mul(new org.joml.Matrix4d().set(values));
		} else {
			if (node.has("translation")) {
				JsonArray t = node.getAsJsonArray("translation");
				matrix.translate(t.get(0).getAsDouble(), t.get(1).getAsDouble(), t.get(2).getAsDouble());
			}

			if (node.has("rotation")) {
				JsonArray r = node.getAsJsonArray("rotation");
				matrix.rotate(new org.joml.Quaterniond(r.get(0).getAsDouble(), r.get(1).getAsDouble(), r.get(2).getAsDouble(), r.get(3).getAsDouble()).normalize());
			}

			if (node.has("scale")) {
				JsonArray s = node.getAsJsonArray("scale");
				matrix.scale(s.get(0).getAsDouble(), s.get(1).getAsDouble(), s.get(2).getAsDouble());
			}
		}

		if (node.has("mesh") && node.get("mesh").getAsInt() < meshes.size()) {
			for (JsonElement primitive : array(meshes.get(node.get("mesh").getAsInt()).getAsJsonObject(), "primitives")) {
				JsonObject position = accessors.get(primitive.getAsJsonObject().getAsJsonObject("attributes").get("POSITION").getAsInt()).getAsJsonObject();

				if (!position.has("min") || !position.has("max")) {
					continue;
				}

				JsonArray low = position.getAsJsonArray("min");
				JsonArray high = position.getAsJsonArray("max");

				for (int corner = 0; corner < 8; corner++) {
					org.joml.Vector3d point = matrix.transformPosition(new org.joml.Vector3d(
							((corner & 1) == 0 ? low : high).get(0).getAsDouble(),
							((corner & 2) == 0 ? low : high).get(1).getAsDouble(),
							((corner & 4) == 0 ? low : high).get(2).getAsDouble()));
					min[0] = Math.min(min[0], point.x);
					min[1] = Math.min(min[1], point.y);
					min[2] = Math.min(min[2], point.z);
					max[0] = Math.max(max[0], point.x);
					max[1] = Math.max(max[1], point.y);
					max[2] = Math.max(max[2], point.z);
				}
			}
		}

		for (JsonElement child : array(node, "children")) {
			visit(nodes, meshes, accessors, child.getAsInt(), matrix, min, max, depth + 1);
		}
	}

	private static JsonArray array(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : new JsonArray();
	}

	private static final class GltfPacker {
		private final String name;
		private final JsonObject json;
		private final ByteBuffer binary;
		private final String folder;
		private final Sources sources;
		private final int maxTextureSize;
		private final Map<Integer, ByteBuffer> buffers = new HashMap<>();

		GltfPacker(String name, JsonObject json, ByteBuffer binary, String folder, Sources sources, int maxTextureSize) {
			this.name = name;
			this.json = json;
			this.binary = binary;
			this.folder = folder;
			this.sources = sources;
			this.maxTextureSize = maxTextureSize;
		}

		Map<String, byte[]> pack() throws IOException {
			JsonArray accessors = array(json, "accessors");
			Set<Integer> kept = new HashSet<>();
			JsonArray materials = array(json, "materials");

			for (JsonElement mesh : array(json, "meshes")) {
				for (JsonElement element : array(mesh.getAsJsonObject(), "primitives")) {
					JsonObject primitive = element.getAsJsonObject();
					JsonObject attributes = primitive.getAsJsonObject("attributes");
					Set<String> wanted = new HashSet<>(Set.of("POSITION", "NORMAL", "COLOR_0", "JOINTS_0", "WEIGHTS_0", "TEXCOORD_0"));

					if (primitive.has("material") && primitive.get("material").getAsInt() < materials.size()) {
						wanted.add("TEXCOORD_" + baseTextureSet(materials.get(primitive.get("material").getAsInt()).getAsJsonObject()));
					}

					for (String attribute : new ArrayList<>(attributes.keySet())) {
						if (wanted.contains(attribute)) {
							kept.add(attributes.get(attribute).getAsInt());
						} else {
							attributes.remove(attribute);
						}
					}

					if (primitive.has("indices")) {
						kept.add(primitive.get("indices").getAsInt());
					}

					for (JsonElement target : array(primitive, "targets")) {
						JsonObject morph = target.getAsJsonObject();

						for (String attribute : new ArrayList<>(morph.keySet())) {
							if (attribute.equals("POSITION") || attribute.equals("NORMAL")) {
								kept.add(morph.get(attribute).getAsInt());
							} else {
								morph.remove(attribute);
							}
						}
					}
				}
			}

			for (JsonElement skin : array(json, "skins")) {
				if (skin.getAsJsonObject().has("inverseBindMatrices")) {
					kept.add(skin.getAsJsonObject().get("inverseBindMatrices").getAsInt());
				}
			}

			for (JsonElement animation : array(json, "animations")) {
				for (JsonElement sampler : array(animation.getAsJsonObject(), "samplers")) {
					kept.add(sampler.getAsJsonObject().get("input").getAsInt());
					kept.add(sampler.getAsJsonObject().get("output").getAsInt());
				}
			}

			JsonArray views = new JsonArray();
			List<ByteArrayOutputStream> chunks = new ArrayList<>();
			ByteArrayOutputStream chunk = new ByteArrayOutputStream();
			chunks.add(chunk);

			for (int i = 0; i < accessors.size(); i++) {
				JsonObject accessor = accessors.get(i).getAsJsonObject();

				if (!kept.contains(i)) {
					accessor.remove("bufferView");
					accessor.remove("byteOffset");
					accessor.remove("sparse");
					continue;
				}

				byte[] data = dense(accessor);

				if (data.length > BUFFER_LIMIT) {
					throw new IOException("One of the model's meshes has " + data.length / 1024 / 1024 + " MiB of data in one piece; split it into smaller meshes");
				}

				if (chunk.size() + data.length > BUFFER_LIMIT) {
					chunk = new ByteArrayOutputStream();
					chunks.add(chunk);
				}

				while (chunk.size() % 4 != 0) {
					chunk.write(0);
				}

				JsonObject view = new JsonObject();
				view.addProperty("buffer", chunks.size() - 1);
				view.addProperty("byteOffset", chunk.size());
				view.addProperty("byteLength", data.length);
				chunk.write(data);
				accessor.remove("sparse");
				accessor.remove("byteOffset");
				accessor.addProperty("bufferView", views.size());
				views.add(view);
			}

			Map<String, byte[]> files = new LinkedHashMap<>();
			files.put(name + ".gltf", new byte[0]);
			JsonArray newBuffers = new JsonArray();

			for (int i = 0; i < chunks.size(); i++) {
				byte[] data = chunks.get(i).toByteArray();

				if (data.length == 0 && chunks.size() > 1) {
					continue;
				}

				String id = name + "-b" + i + ".bin";
				JsonObject buffer = new JsonObject();
				buffer.addProperty("uri", id);
				buffer.addProperty("byteLength", data.length);
				newBuffers.add(buffer);
				files.put(id, data);
			}

			Set<Integer> usedTextures = new HashSet<>();

			for (JsonElement element : materials) {
				JsonObject material = element.getAsJsonObject();
				material.remove("occlusionTexture");
				JsonObject pbr = material.has("pbrMetallicRoughness") ? material.getAsJsonObject("pbrMetallicRoughness") : new JsonObject();
				JsonObject extensions = material.has("extensions") ? material.getAsJsonObject("extensions") : new JsonObject();
				JsonObject specGloss = extensions.has("KHR_materials_pbrSpecularGlossiness") ? extensions.getAsJsonObject("KHR_materials_pbrSpecularGlossiness") : new JsonObject();

				for (JsonObject holder : List.of(pbr, material, specGloss)) {
					for (String slot : List.of("baseColorTexture", "metallicRoughnessTexture", "normalTexture", "emissiveTexture", "diffuseTexture", "specularGlossinessTexture")) {
						if (holder.has(slot) && holder.getAsJsonObject(slot).has("index")) {
							usedTextures.add(holder.getAsJsonObject(slot).get("index").getAsInt());
						}
					}
				}
			}

			JsonArray textures = array(json, "textures");
			Set<Integer> usedImages = new HashSet<>();

			for (int texture : usedTextures) {
				if (texture >= 0 && texture < textures.size() && textures.get(texture).getAsJsonObject().has("source")) {
					usedImages.add(textures.get(texture).getAsJsonObject().get("source").getAsInt());
				}
			}

			JsonArray images = array(json, "images");

			for (int i = 0; i < images.size(); i++) {
				JsonObject image = images.get(i).getAsJsonObject();
				byte[] data = usedImages.contains(i) ? imageData(image) : null;
				image.remove("uri");
				image.remove("bufferView");
				image.remove("mimeType");

				if (data == null) {
					continue;
				}

				boolean jpeg = data.length > 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
				byte[] shrunk = shrink(data);
				String id = name + "-i" + i + (shrunk == data && jpeg ? ".jpg" : ".png");

				if (shrunk.length > Protocol.MAX_ASSET_SIZE) {
					throw new IOException("Image " + i + " is still " + shrunk.length / 1024 / 1024 + " MiB; import with a smaller biggest texture size");
				}

				image.addProperty("uri", id);
				files.put(id, shrunk);
			}

			json.add("buffers", newBuffers);
			json.add("bufferViews", views);
			JsonObject ordered = new JsonObject();

			if (json.has("asset")) {
				ordered.add("asset", json.get("asset"));
			}

			json.entrySet().stream().filter(entry -> !entry.getKey().equals("asset")).forEach(entry -> ordered.add(entry.getKey(), entry.getValue()));
			byte[] text = GSON.toJson(ordered).getBytes(StandardCharsets.UTF_8);

			if (text.length > Protocol.MAX_ASSET_SIZE) {
				throw new IOException("The model's description is " + text.length / 1024 / 1024 + " MiB, more than an asset may be");
			}

			files.put(name + ".gltf", text);
			return files;
		}

		private static int baseTextureSet(JsonObject material) {
			JsonObject pbr = material.has("pbrMetallicRoughness") ? material.getAsJsonObject("pbrMetallicRoughness") : new JsonObject();
			JsonObject extensions = material.has("extensions") ? material.getAsJsonObject("extensions") : new JsonObject();
			JsonObject info = extensions.has("KHR_materials_pbrSpecularGlossiness") ? extensions.getAsJsonObject("KHR_materials_pbrSpecularGlossiness").getAsJsonObject("diffuseTexture")
					: pbr.getAsJsonObject("baseColorTexture");

			if (info == null) {
				return 0;
			}

			int set = info.has("texCoord") ? info.get("texCoord").getAsInt() : 0;
			JsonObject infoExtensions = info.has("extensions") ? info.getAsJsonObject("extensions") : null;

			if (infoExtensions != null && infoExtensions.has("KHR_texture_transform") && infoExtensions.getAsJsonObject("KHR_texture_transform").has("texCoord")) {
				set = infoExtensions.getAsJsonObject("KHR_texture_transform").get("texCoord").getAsInt();
			}

			return set;
		}

		private byte[] dense(JsonObject accessor) throws IOException {
			int count = accessor.get("count").getAsInt();
			int componentSize = switch (accessor.get("componentType").getAsInt()) {
				case 5120, 5121 -> 1;
				case 5122, 5123 -> 2;
				default -> 4;
			};
			int components = switch (accessor.get("type").getAsString()) {
				case "SCALAR" -> 1;
				case "VEC2" -> 2;
				case "VEC3" -> 3;
				case "VEC4", "MAT2" -> 4;
				case "MAT3" -> 9;
				case "MAT4" -> 16;
				default -> 1;
			};
			int elementSize = componentSize * components;
			byte[] data = new byte[count * elementSize];

			if (accessor.has("bufferView")) {
				JsonObject view = array(json, "bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
				ByteBuffer source = view(accessor.get("bufferView").getAsInt());
				int offset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
				int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : elementSize;

				for (int i = 0; i < count; i++) {
					int from = offset + i * stride;

					if (from + elementSize > source.capacity()) {
						throw new IOException("An accessor reads outside its buffer");
					}

					source.get(from, data, i * elementSize, elementSize);
				}
			}

			if (accessor.has("sparse")) {
				JsonObject sparse = accessor.getAsJsonObject("sparse");
				int sparseCount = sparse.get("count").getAsInt();
				JsonObject indices = sparse.getAsJsonObject("indices");
				JsonObject values = sparse.getAsJsonObject("values");
				ByteBuffer indexData = view(indices.get("bufferView").getAsInt());
				int indexOffset = indices.has("byteOffset") ? indices.get("byteOffset").getAsInt() : 0;
				int indexType = indices.get("componentType").getAsInt();
				ByteBuffer valueData = view(values.get("bufferView").getAsInt());
				int valueOffset = values.has("byteOffset") ? values.get("byteOffset").getAsInt() : 0;

				for (int i = 0; i < sparseCount; i++) {
					int target = switch (indexType) {
						case 5121 -> indexData.get(indexOffset + i) & 0xFF;
						case 5123 -> indexData.getShort(indexOffset + i * 2) & 0xFFFF;
						default -> indexData.getInt(indexOffset + i * 4);
					};

					if (target >= 0 && target < count) {
						valueData.get(valueOffset + i * elementSize, data, target * elementSize, elementSize);
					}
				}
			}

			return data;
		}

		private ByteBuffer view(int index) throws IOException {
			JsonObject view = array(json, "bufferViews").get(index).getAsJsonObject();
			ByteBuffer buffer = buffer(view.get("buffer").getAsInt());
			int offset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
			int length = view.get("byteLength").getAsInt();
			return buffer.duplicate().position(offset).limit(offset + length).slice().order(ByteOrder.LITTLE_ENDIAN);
		}

		private ByteBuffer buffer(int index) throws IOException {
			ByteBuffer cached = buffers.get(index);

			if (cached != null) {
				return cached;
			}

			JsonObject buffer = array(json, "buffers").get(index).getAsJsonObject();
			ByteBuffer data;

			if (!buffer.has("uri")) {
				if (binary == null) {
					throw new IOException("Buffer " + index + " has no data");
				}

				data = binary;
			} else {
				data = ByteBuffer.wrap(load(buffer.get("uri").getAsString())).order(ByteOrder.LITTLE_ENDIAN);
			}

			buffers.put(index, data);
			return data;
		}

		private byte[] imageData(JsonObject image) throws IOException {
			if (image.has("bufferView")) {
				ByteBuffer view = view(image.get("bufferView").getAsInt());
				byte[] data = new byte[view.remaining()];
				view.get(data);
				return data;
			}

			return image.has("uri") ? load(image.get("uri").getAsString()) : null;
		}

		private byte[] load(String uri) throws IOException {
			if (uri.startsWith("data:")) {
				return Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
			}

			String path = URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
			return sources.read(normalize(folder + path));
		}

		private static String normalize(String path) {
			List<String> parts = new ArrayList<>();

			for (String part : path.replace('\\', '/').split("/")) {
				if (part.isEmpty() || part.equals(".")) {
					continue;
				}

				if (part.equals("..")) {
					if (!parts.isEmpty()) {
						parts.removeLast();
					}
				} else {
					parts.add(part);
				}
			}

			return String.join("/", parts);
		}

		private byte[] shrink(byte[] data) throws IOException {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));

			if (image == null) {
				throw new IOException("An image can't be read (only PNG and JPEG are supported)");
			}

			int width = image.getWidth();
			int height = image.getHeight();

			if (Math.max(width, height) <= maxTextureSize) {
				return data;
			}

			BufferedImage current = toArgb(image);

			while (Math.max(current.getWidth(), current.getHeight()) > maxTextureSize) {
				double factor = Math.max(0.5, maxTextureSize / (double) Math.max(current.getWidth(), current.getHeight()));
				int newWidth = Math.max(1, (int) Math.round(current.getWidth() * factor));
				int newHeight = Math.max(1, (int) Math.round(current.getHeight() * factor));
				BufferedImage next = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = next.createGraphics();
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				graphics.drawImage(current, 0, 0, newWidth, newHeight, null);
				graphics.dispose();
				current = next;
			}

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(current, "png", out);
			return out.toByteArray();
		}

		private static BufferedImage toArgb(BufferedImage image) {
			if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
				return image;
			}

			BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = converted.createGraphics();
			graphics.drawImage(image, 0, 0, null);
			graphics.dispose();
			return converted;
		}
	}
}
