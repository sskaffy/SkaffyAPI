package me.skaffy.client.shader.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public record PackFiles(String topPackage, Map<String, String> sources, Map<String, byte[]> resources) {
	public static final long MAX_PACK_SIZE = 256L * 1024 * 1024;
	public static final int MAX_FILES = 4096;
	public static final int MAX_SOURCE_SIZE = 512 * 1024;
	public static final int MAX_PNG_SIZE = 16 * 1024 * 1024;

	public static final class PackException extends Exception {
		public PackException(String message) {
			super(message);
		}
	}

	public static PackFiles readZip(Path zip) throws IOException, PackException {
		if (Files.size(zip) > MAX_PACK_SIZE) {
			throw new PackException("The zip is over " + MAX_PACK_SIZE / 1024 / 1024 + " MiB");
		}

		Map<String, byte[]> files = new LinkedHashMap<>();
		long total = 0;

		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
			ZipEntry entry;

			while ((entry = in.getNextEntry()) != null) {
				String name = entry.getName().replace('\\', '/');

				if (entry.isDirectory() || ignored(name)) {
					continue;
				}

				if (name.startsWith("/") || name.contains("../") || name.contains(":")) {
					throw new PackException("The zip has a file outside its folder: " + name);
				}

				if (files.size() >= MAX_FILES) {
					throw new PackException("The pack has more than " + MAX_FILES + " files");
				}

				byte[] data = readLimited(in, name.endsWith(".png") ? MAX_PNG_SIZE : name.endsWith(".sfy") ? MAX_SOURCE_SIZE : MAX_PNG_SIZE, name);
				total += data.length;

				if (total > MAX_PACK_SIZE) {
					throw new PackException("The pack is over " + MAX_PACK_SIZE / 1024 / 1024 + " MiB unpacked");
				}

				files.put(name, data);
			}
		}

		TreeSet<String> tops = new TreeSet<>();
		files.keySet().forEach(name -> tops.add(name.contains("/") ? name.substring(0, name.indexOf('/')) : ""));

		if (tops.size() != 1 || tops.first().isEmpty()) {
			throw new PackException("The zip must hold exactly one folder, with shaders/ and resources/ inside it" + (tops.size() > 1 ? " (it has " + tops.size() + " things at the top)" : ""));
		}

		String prefix = tops.first() + "/";
		Map<String, byte[]> inside = new LinkedHashMap<>();
		files.forEach((name, data) -> inside.put(name.substring(prefix.length()), data));
		return of(inside);
	}

	public static PackFiles readFolder(Path folder) throws IOException, PackException {
		Map<String, byte[]> files = new LinkedHashMap<>();
		long total = 0;

		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
				String name = folder.relativize(path).toString().replace('\\', '/');

				if (ignored(name) || !(name.startsWith("shaders/") || name.startsWith("resources/"))) {
					continue;
				}

				if (files.size() >= MAX_FILES) {
					throw new PackException("The pack has more than " + MAX_FILES + " files");
				}

				long size = Files.size(path);
				long limit = name.endsWith(".sfy") ? MAX_SOURCE_SIZE : MAX_PNG_SIZE;

				if (size > limit) {
					throw new PackException(name + " is over " + limit / 1024 + " KiB");
				}

				total += size;

				if (total > MAX_PACK_SIZE) {
					throw new PackException("The pack is over " + MAX_PACK_SIZE / 1024 / 1024 + " MiB");
				}

				files.put(name, Files.readAllBytes(path));
			}
		}

		return of(files);
	}

	public static PackFiles readResources(String root, List<String> paths) throws IOException, PackException {
		Map<String, byte[]> files = new LinkedHashMap<>();

		for (String path : paths) {
			try (InputStream stream = PackFiles.class.getResourceAsStream(root + path)) {
				if (stream == null) {
					throw new PackException(path + " is missing from the mod");
				}

				files.put(path, stream.readAllBytes());
			}
		}

		return of(files);
	}

	private static boolean ignored(String name) {
		for (String part : name.split("/")) {
			if (part.startsWith(".") || part.equals("__MACOSX") || part.equalsIgnoreCase("Thumbs.db") || part.equalsIgnoreCase("desktop.ini")) {
				return true;
			}
		}

		return false;
	}

	private static byte[] readLimited(InputStream in, int limit, String name) throws IOException, PackException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[64 * 1024];
		int read;

		while ((read = in.read(buffer)) > 0) {
			if (out.size() + read > limit) {
				throw new PackException(name + " is over " + limit / 1024 + " KiB");
			}

			out.write(buffer, 0, read);
		}

		return out.toByteArray();
	}

	private static PackFiles of(Map<String, byte[]> files) throws PackException {
		Map<String, String> sources = new LinkedHashMap<>();
		Map<String, byte[]> resources = new LinkedHashMap<>();
		TreeSet<String> packages = new TreeSet<>();
		List<String> stray = new ArrayList<>();

		for (Map.Entry<String, byte[]> file : files.entrySet()) {
			String name = file.getKey();

			if (name.startsWith("resources/")) {
				if (name.endsWith(".png")) {
					resources.put(name.substring("resources/".length()), file.getValue());
				}

				continue;
			}

			if (!name.startsWith("shaders/")) {
				continue;
			}

			String inShaders = name.substring("shaders/".length());

			if (!inShaders.contains("/")) {
				stray.add(name);
				continue;
			}

			packages.add(inShaders.substring(0, inShaders.indexOf('/')));

			if (!name.endsWith(".sfy")) {
				continue;
			}

			String className = inShaders.substring(0, inShaders.length() - 4).replace('/', '.');

			if (!className.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) {
				throw new PackException("shaders/" + inShaders + ": folder and file names must be like Java names (letters, digits and _), so it can be a class");
			}

			sources.put(className, new String(file.getValue(), StandardCharsets.UTF_8));
		}

		if (!stray.isEmpty()) {
			throw new PackException("Files go in a folder inside shaders/ (the pack's name, its package), not straight in it: " + String.join(", ", stray));
		}

		if (packages.isEmpty() || sources.isEmpty()) {
			throw new PackException("No .sfy files: they go in shaders/<your pack's name>/");
		}

		if (packages.size() > 1) {
			throw new PackException("shaders/ must hold exactly one folder (the pack's name, its top package), it has " + String.join(", ", packages));
		}

		return new PackFiles(packages.first(), sources, resources);
	}
}
