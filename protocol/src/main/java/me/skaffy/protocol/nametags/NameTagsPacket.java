package me.skaffy.protocol.nametags;

import java.util.List;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;

public sealed interface NameTagsPacket {
	record DefineSprites(List<String> assets) implements NameTagsPacket {
		public DefineSprites {
			assets = List.copyOf(assets);
			assets.forEach(Protocol::requireAssetId);
		}
	}

	record SetNameTag(int entity, NameTag tag) implements NameTagsPacket {
	}

	record SetLine(int entity, int line, NameTag.Line content) implements NameTagsPacket {
		public SetLine {
			if (line < 0 || line >= NameTag.MAX_LINES) {
				throw new ProtocolException("Invalid line " + line);
			}
		}
	}

	record RemoveNameTag(int entity) implements NameTagsPacket {
	}
}
