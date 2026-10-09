package me.skaffy.client.pack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import org.jspecify.annotations.Nullable;

public final class HiddenPack implements PackResources {
	private static volatile HiddenPack active;

	private final PackLocationInfo location = new PackLocationInfo("skaffys-api", Component.literal("Skaffy's API"), PackSource.BUILT_IN, Optional.empty());
	private final Map<Identifier, byte[]> files;
	private final Set<String> namespaces;

	public HiddenPack(Map<Identifier, byte[]> files) {
		this.files = Map.copyOf(files);
		this.namespaces = files.keySet().stream().map(Identifier::getNamespace).collect(Collectors.toUnmodifiableSet());
	}

	public static @Nullable HiddenPack active() {
		return active;
	}

	public static void setActive(@Nullable HiddenPack pack) {
		active = pack;
	}

	@Override
	public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
		byte[] data = type == PackType.CLIENT_RESOURCES ? files.get(location) : null;
		return data == null ? null : () -> new ByteArrayInputStream(data);
	}

	@Override
	public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
		if (type != PackType.CLIENT_RESOURCES) {
			return;
		}

		String prefix = directory.endsWith("/") ? directory : directory + "/";

		files.forEach((id, data) -> {
			if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
				output.accept(id, () -> new ByteArrayInputStream(data));
			}
		});
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		return type == PackType.CLIENT_RESOURCES ? namespaces : Set.of();
	}

	@Override
	public PackLocationInfo location() {
		return location;
	}

	@Override
	public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
		return null;
	}

	@Override
	public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
		return null;
	}

	@Override
	public void close() {
	}
}
