package me.skaffy.protocol.asset;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record CachedAsset(String id, int size, AssetHash hash) {
	public CachedAsset {
		Protocol.requireAssetId(id);

		if (size < 0 || size > Protocol.MAX_ASSET_SIZE) {
			throw new ProtocolException("Invalid size " + size + " for asset " + id);
		}
	}

	public static CachedAsset read(PacketReader reader) {
		String id = reader.readString(Protocol.MAX_ASSET_ID_LENGTH);
		int size = reader.readVarInt();
		AssetHash hash = reader.readBoolean() ? AssetHash.read(reader) : null;
		return new CachedAsset(id, size, hash);
	}

	public void write(PacketWriter writer) {
		writer.writeString(id, Protocol.MAX_ASSET_ID_LENGTH);
		writer.writeVarInt(size);
		writer.writeBoolean(hash != null);

		if (hash != null) {
			hash.write(writer);
		}
	}
}
