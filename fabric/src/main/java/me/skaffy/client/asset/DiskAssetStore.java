package me.skaffy.client.asset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.asset.AssetHash;
import me.skaffy.protocol.asset.AssetStore;

public final class DiskAssetStore implements AssetStore {
	private static final int FILES_PER_FOLDER = 500;
	private static final String TEMP_PREFIX = ".tmp-";
	private static final Map<Path, CachedHash> HASHES = new ConcurrentHashMap<>();

	private final Path root;
	private final Map<String, Path> files = new HashMap<>();
	private final Map<String, Long> sizes = new HashMap<>();
	private final Map<Integer, Integer> folderCounts = new TreeMap<>();
	private long totalSize;
	private boolean loaded;

	public DiskAssetStore(Path root) {
		this.root = root;
	}

	@Override
	public long size(String id) {
		load();
		return sizes.getOrDefault(id, -1L);
	}

	@Override
	public AssetHash hash(String id) throws IOException {
		load();
		Path file = files.get(id);

		if (file == null) {
			throw new IOException("No cached file " + id);
		}

		BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
		CachedHash cached = HASHES.get(file);

		if (cached != null && cached.size == attributes.size() && cached.modified == attributes.lastModifiedTime().toMillis()) {
			return cached.hash;
		}

		MessageDigest digest = AssetHash.sha256();
		byte[] buffer = new byte[64 * 1024];

		try (InputStream stream = Files.newInputStream(file)) {
			for (int read; (read = stream.read(buffer)) > 0; ) {
				digest.update(buffer, 0, read);
			}
		}

		AssetHash hash = AssetHash.fromBytes(digest.digest());
		HASHES.put(file, new CachedHash(attributes.size(), attributes.lastModifiedTime().toMillis(), hash));
		return hash;
	}

	public byte[] read(String id) throws IOException {
		load();
		Path file = files.get(id);
		return file == null ? null : Files.readAllBytes(file);
	}

	@Override
	public List<String> ids() {
		load();
		return List.copyOf(files.keySet());
	}

	@Override
	public long totalSize() {
		load();
		return totalSize;
	}

	@Override
	public int fileCount() {
		load();
		return files.size();
	}

	@Override
	public void write(String id, byte[] data) throws IOException {
		load();
		Path target = files.get(id);
		boolean isNew = target == null;

		if (isNew) {
			target = root.resolve(Integer.toString(freeFolder())).resolve(Protocol.requireAssetId(id));
		}

		Files.createDirectories(target.getParent());
		Path temp = target.resolveSibling(TEMP_PREFIX + id);
		Files.write(temp, data);

		try {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}

		HASHES.remove(target);
		totalSize += data.length - sizes.getOrDefault(id, 0L);
		sizes.put(id, (long) data.length);
		files.put(id, target);

		if (isNew) {
			folderCounts.merge(folderNumber(target.getParent()), 1, Integer::sum);
		}
	}

	@Override
	public void delete(String id) throws IOException {
		load();
		Path file = files.remove(id);

		if (file == null) {
			return;
		}

		Files.deleteIfExists(file);
		HASHES.remove(file);
		totalSize -= sizes.remove(id);
		folderCounts.merge(folderNumber(file.getParent()), -1, Integer::sum);
	}

	private int freeFolder() {
		for (int folder = 0; ; folder++) {
			if (folderCounts.getOrDefault(folder, 0) < FILES_PER_FOLDER) {
				return folder;
			}
		}
	}

	private void load() {
		if (loaded) {
			return;
		}

		loaded = true;

		if (!Files.isDirectory(root)) {
			return;
		}

		try (DirectoryStream<Path> folders = Files.newDirectoryStream(root, Files::isDirectory)) {
			for (Path folder : folders) {
				Integer number = folderNumber(folder);

				if (number != null) {
					loadFolder(number, folder);
				}
			}
		} catch (IOException e) {
			SkaffySAPIClient.LOGGER.warn("Could not read cache folder {}", root, e);
		}
	}

	private void loadFolder(int number, Path folder) throws IOException {
		int count = 0;

		try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
			for (Path file : entries) {
				String name = file.getFileName().toString();

				if (name.startsWith(TEMP_PREFIX)) {
					Files.deleteIfExists(file);
					continue;
				}

				if (!Protocol.isValidAssetId(name) || !Files.isRegularFile(file)) {
					continue;
				}

				if (files.containsKey(name)) {
					Files.deleteIfExists(file);
					continue;
				}

				long size = Files.size(file);
				files.put(name, file);
				sizes.put(name, size);
				totalSize += size;
				count++;
			}
		}

		folderCounts.put(number, count);
	}

	private static Integer folderNumber(Path folder) {
		String name = folder.getFileName().toString();
		return name.matches("[0-9]{1,6}") ? Integer.valueOf(name) : null;
	}

	private record CachedHash(long size, long modified, AssetHash hash) {
	}
}
