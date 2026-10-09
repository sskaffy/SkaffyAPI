package me.skaffy.protocol.core;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record FeatureRange(String id, int minVersion, int maxVersion) {
	public FeatureRange {
		if (!Protocol.isValidFeatureId(id)) {
			throw new ProtocolException("Invalid feature id " + id);
		}

		if (minVersion < 1 || maxVersion < minVersion) {
			throw new ProtocolException("Invalid version range " + minVersion + ".." + maxVersion + " for feature " + id);
		}
	}

	public boolean supports(int version) {
		return version >= minVersion && version <= maxVersion;
	}

	public static FeatureRange read(PacketReader reader) {
		return new FeatureRange(reader.readString(32), reader.readVarInt(), reader.readVarInt());
	}

	public void write(PacketWriter writer) {
		writer.writeString(id, 32);
		writer.writeVarInt(minVersion);
		writer.writeVarInt(maxVersion);
	}
}
