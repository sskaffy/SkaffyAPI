package me.skaffy.protocol.core;

import java.util.List;

import me.skaffy.protocol.Protocol;

public sealed interface ClientboundCorePacket {
	record Welcome(int protocolVersion, List<FeatureVersion> features) implements ClientboundCorePacket {
		public Welcome {
			features = List.copyOf(features);
		}
	}

	record AssetQuery(int queryId, List<String> ids, boolean includeHashes) implements ClientboundCorePacket {
		public AssetQuery {
			ids = List.copyOf(ids);
		}
	}

	record AssetTransfer(long bytes) implements ClientboundCorePacket {
	}

	record AssetData(String id, int size, int offset, byte[] data) implements ClientboundCorePacket {
	}

	record AssetDelete(List<String> ids) implements ClientboundCorePacket {
		public AssetDelete {
			ids = List.copyOf(ids);
		}
	}

	record RegistrationEnd() implements ClientboundCorePacket {
	}
}
