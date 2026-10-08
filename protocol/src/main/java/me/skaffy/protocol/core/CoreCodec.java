package me.skaffy.protocol.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.asset.CachedAsset;
import me.skaffy.protocol.core.ClientboundCorePacket.AssetData;
import me.skaffy.protocol.core.ClientboundCorePacket.AssetDelete;
import me.skaffy.protocol.core.ClientboundCorePacket.AssetQuery;
import me.skaffy.protocol.core.ClientboundCorePacket.AssetTransfer;
import me.skaffy.protocol.core.ClientboundCorePacket.RegistrationEnd;
import me.skaffy.protocol.core.ClientboundCorePacket.Welcome;
import me.skaffy.protocol.core.ServerboundCorePacket.AssetAck;
import me.skaffy.protocol.core.ServerboundCorePacket.AssetReport;
import me.skaffy.protocol.core.ServerboundCorePacket.Failed;
import me.skaffy.protocol.core.ServerboundCorePacket.Hello;
import me.skaffy.protocol.core.ServerboundCorePacket.Ready;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class CoreCodec {
	public static final int HELLO = 0;
	public static final int ASSET_REPORT = 1;
	public static final int ASSET_ACK = 2;
	public static final int READY = 3;
	public static final int FAILED = 4;

	public static final int WELCOME = 0;
	public static final int ASSET_QUERY = 1;
	public static final int ASSET_TRANSFER = 2;
	public static final int ASSET_DATA = 3;
	public static final int ASSET_DELETE = 4;
	public static final int REGISTRATION_END = 5;

	private CoreCodec() {
	}

	public static byte[] encode(ServerboundCorePacket packet) {
		PacketWriter writer = new PacketWriter();

		switch (packet) {
			case Hello hello -> writer.writeVarInt(HELLO)
					.writeVarInt(hello.protocolVersion())
					.writeString(hello.modVersion(), Protocol.MAX_MOD_VERSION_LENGTH)
					.writeList(hello.features(), (w, feature) -> feature.write(w));
			case AssetReport report -> writer.writeVarInt(ASSET_REPORT)
					.writeVarInt(report.queryId())
					.writeList(report.assets(), (w, asset) -> asset.write(w))
					.writeBoolean(report.last());
			case AssetAck ack -> writer.writeVarInt(ASSET_ACK)
					.writeVarLong(ack.receivedBytes());
			case Ready ignored -> writer.writeVarInt(READY);
			case Failed failed -> writer.writeVarInt(FAILED)
					.writeString(failed.reason(), Protocol.MAX_FAILURE_REASON_LENGTH);
		}

		return finish(writer, Protocol.MAX_SERVERBOUND_PAYLOAD);
	}

	public static byte[] encode(ClientboundCorePacket packet) {
		PacketWriter writer = new PacketWriter(packet instanceof AssetData data ? data.data().length + 96 : 64);

		switch (packet) {
			case Welcome welcome -> writer.writeVarInt(WELCOME)
					.writeVarInt(welcome.protocolVersion())
					.writeList(welcome.features(), (w, feature) -> feature.write(w));
			case AssetQuery query -> writer.writeVarInt(ASSET_QUERY)
					.writeVarInt(query.queryId())
					.writeList(query.ids(), (w, id) -> w.writeString(id, Protocol.MAX_ASSET_ID_LENGTH))
					.writeBoolean(query.includeHashes());
			case AssetTransfer transfer -> writer.writeVarInt(ASSET_TRANSFER)
					.writeVarLong(transfer.bytes());
			case AssetData data -> writer.writeVarInt(ASSET_DATA)
					.writeString(data.id(), Protocol.MAX_ASSET_ID_LENGTH)
					.writeVarInt(data.size())
					.writeVarInt(data.offset())
					.writeBytes(data.data());
			case AssetDelete delete -> writer.writeVarInt(ASSET_DELETE)
					.writeList(delete.ids(), (w, id) -> w.writeString(id, Protocol.MAX_ASSET_ID_LENGTH));
			case RegistrationEnd ignored -> writer.writeVarInt(REGISTRATION_END);
		}

		return finish(writer, Protocol.MAX_CLIENTBOUND_PAYLOAD);
	}

	public static ServerboundCorePacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		return switch (id) {
			case HELLO -> {
				int protocolVersion = reader.readVarInt();
				String modVersion = reader.readString(Protocol.MAX_MOD_VERSION_LENGTH);
				List<FeatureRange> features = reader.readList(Protocol.MAX_FEATURES, FeatureRange::read);
				requireUnique(features, FeatureRange::id, "feature");
				yield new Hello(protocolVersion, modVersion, features);
			}
			case ASSET_REPORT -> {
				int queryId = reader.readVarInt();
				List<CachedAsset> assets = reader.readList(Protocol.MAX_CACHE_FILES, CachedAsset::read);
				boolean last = reader.readBoolean();
				reader.expectEnd();
				yield new AssetReport(queryId, assets, last);
			}
			case ASSET_ACK -> {
				long receivedBytes = reader.readVarLong();
				reader.expectEnd();

				if (receivedBytes < 0) {
					throw new ProtocolException("Negative byte count");
				}

				yield new AssetAck(receivedBytes);
			}
			case READY -> {
				reader.expectEnd();
				yield new Ready();
			}
			case FAILED -> {
				String reason = reader.readString(Protocol.MAX_FAILURE_REASON_LENGTH);
				reader.expectEnd();
				yield new Failed(reason);
			}
			default -> throw new ProtocolException("Unknown serverbound core packet " + id);
		};
	}

	public static ClientboundCorePacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		return switch (id) {
			case WELCOME -> {
				int protocolVersion = reader.readVarInt();
				List<FeatureVersion> features = reader.readList(Protocol.MAX_FEATURES, FeatureVersion::read);
				requireUnique(features, FeatureVersion::id, "feature");
				yield new Welcome(protocolVersion, features);
			}
			case ASSET_QUERY -> {
				int queryId = reader.readVarInt();
				List<String> ids = reader.readList(Protocol.MAX_QUERY_IDS, CoreCodec::readAssetId);
				boolean includeHashes = reader.readBoolean();
				reader.expectEnd();
				yield new AssetQuery(queryId, ids, includeHashes);
			}
			case ASSET_TRANSFER -> {
				long bytes = reader.readVarLong();
				reader.expectEnd();

				if (bytes < 0) {
					throw new ProtocolException("Negative byte count");
				}

				yield new AssetTransfer(bytes);
			}
			case ASSET_DATA -> {
				String assetId = readAssetId(reader);
				int size = reader.readVarInt(0, Protocol.MAX_ASSET_SIZE);
				int offset = reader.readVarInt(0, size);
				byte[] chunk = reader.readBytes(Protocol.MAX_CHUNK_SIZE);
				reader.expectEnd();

				if ((long) offset + chunk.length > size || chunk.length == 0 && size != 0) {
					throw new ProtocolException("Invalid part " + offset + "+" + chunk.length + " of " + size + " byte asset " + assetId);
				}

				yield new AssetData(assetId, size, offset, chunk);
			}
			case ASSET_DELETE -> {
				List<String> ids = reader.readList(Protocol.MAX_DELETE_IDS, CoreCodec::readAssetId);
				reader.expectEnd();
				yield new AssetDelete(ids);
			}
			case REGISTRATION_END -> {
				reader.expectEnd();
				yield new RegistrationEnd();
			}
			default -> throw new ProtocolException("Unknown clientbound core packet " + id);
		};
	}

	private static String readAssetId(PacketReader reader) {
		return Protocol.requireAssetId(reader.readString(Protocol.MAX_ASSET_ID_LENGTH));
	}

	private static byte[] finish(PacketWriter writer, int maxSize) {
		if (writer.size() > maxSize) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + maxSize);
		}

		return writer.toByteArray();
	}

	private static <T> void requireUnique(List<T> values, Function<T, String> key, String what) {
		Set<String> seen = new HashSet<>();

		for (T value : values) {
			if (!seen.add(key.apply(value))) {
				throw new ProtocolException("Duplicate " + what + " " + key.apply(value));
			}
		}
	}
}
