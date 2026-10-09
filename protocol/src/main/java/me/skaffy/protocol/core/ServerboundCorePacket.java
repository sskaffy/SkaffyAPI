package me.skaffy.protocol.core;

import java.util.List;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.asset.CachedAsset;

public sealed interface ServerboundCorePacket {
	record Hello(int protocolVersion, String modVersion, List<FeatureRange> features) implements ServerboundCorePacket {
		public Hello {
			features = List.copyOf(features);
		}
	}

	record AssetReport(int queryId, List<CachedAsset> assets, boolean last) implements ServerboundCorePacket {
		public AssetReport {
			assets = List.copyOf(assets);
		}
	}

	record AssetAck(long receivedBytes) implements ServerboundCorePacket {
	}

	record Ready() implements ServerboundCorePacket {
	}

	record Failed(String reason) implements ServerboundCorePacket {
		public Failed {
			if (reason.length() > Protocol.MAX_FAILURE_REASON_LENGTH) {
				reason = reason.substring(0, Protocol.MAX_FAILURE_REASON_LENGTH);
			}
		}
	}
}
