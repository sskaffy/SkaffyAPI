package me.skaffy.protocol.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record FeatureVersion(String id, int version) {
	public FeatureVersion {
		if (!Protocol.isValidFeatureId(id)) {
			throw new ProtocolException("Invalid feature id " + id);
		}

		if (version < 1) {
			throw new ProtocolException("Invalid version " + version + " for feature " + id);
		}
	}

	public static FeatureVersion read(PacketReader reader) {
		return new FeatureVersion(reader.readString(32), reader.readVarInt());
	}

	public void write(PacketWriter writer) {
		writer.writeString(id, 32);
		writer.writeVarInt(version);
	}

	public static List<FeatureVersion> negotiate(List<FeatureRange> client, List<FeatureRange> server) {
		Map<String, FeatureRange> serverById = new HashMap<>();

		for (FeatureRange range : server) {
			serverById.put(range.id(), range);
		}

		List<FeatureVersion> enabled = new ArrayList<>();

		for (FeatureRange clientRange : client) {
			FeatureRange serverRange = serverById.get(clientRange.id());

			if (serverRange == null) {
				continue;
			}

			int version = Math.min(clientRange.maxVersion(), serverRange.maxVersion());

			if (version >= Math.max(clientRange.minVersion(), serverRange.minVersion())) {
				enabled.add(new FeatureVersion(clientRange.id(), version));
			}
		}

		return List.copyOf(enabled);
	}
}
