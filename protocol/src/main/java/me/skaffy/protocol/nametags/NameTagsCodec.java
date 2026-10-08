package me.skaffy.protocol.nametags;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.nametags.NameTagsPacket.DefineSprites;
import me.skaffy.protocol.nametags.NameTagsPacket.RemoveNameTag;
import me.skaffy.protocol.nametags.NameTagsPacket.SetLine;
import me.skaffy.protocol.nametags.NameTagsPacket.SetNameTag;

public final class NameTagsCodec {
	public static final String FEATURE = "name_tags";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);
	public static final int MAX_SPRITES = 4096;

	public static final int DEFINE_SPRITES = 0;
	public static final int SET_NAME_TAG = 1;
	public static final int SET_LINE = 2;
	public static final int REMOVE_NAME_TAG = 3;

	private NameTagsCodec() {
	}

	public static byte[] encode(NameTagsPacket packet) {
		PacketWriter writer = new PacketWriter(128);

		switch (packet) {
			case DefineSprites define -> writer.writeVarInt(DEFINE_SPRITES).writeList(define.assets(), (w, asset) -> w.writeString(asset, Protocol.MAX_ASSET_ID_LENGTH));
			case SetNameTag set -> {
				writer.writeVarInt(SET_NAME_TAG).writeVarInt(set.entity());
				set.tag().write(writer);
			}
			case SetLine set -> {
				writer.writeVarInt(SET_LINE).writeVarInt(set.entity()).writeByte(set.line());
				set.content().write(writer);
			}
			case RemoveNameTag remove -> writer.writeVarInt(REMOVE_NAME_TAG).writeVarInt(remove.entity());
		}

		if (writer.size() > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + Protocol.MAX_CLIENTBOUND_PAYLOAD);
		}

		return writer.toByteArray();
	}

	public static NameTagsPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		NameTagsPacket packet = switch (id) {
			case DEFINE_SPRITES -> new DefineSprites(reader.readList(MAX_SPRITES, r -> r.readString(Protocol.MAX_ASSET_ID_LENGTH)));
			case SET_NAME_TAG -> new SetNameTag(reader.readVarInt(), NameTag.read(reader));
			case SET_LINE -> new SetLine(reader.readVarInt(), reader.readUnsignedByte(), NameTag.Line.read(reader));
			case REMOVE_NAME_TAG -> new RemoveNameTag(reader.readVarInt());
			default -> throw new ProtocolException("Unknown clientbound name tags packet " + id);
		};

		reader.expectEnd();
		return packet;
	}
}
