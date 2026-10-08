package me.skaffy.protocol.playerlooks;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.playerlooks.PlayerLooksPacket.RemoveLook;
import me.skaffy.protocol.playerlooks.PlayerLooksPacket.SetLook;

public final class PlayerLooksCodec {
	public static final String FEATURE = "player_looks";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int SET_LOOK = 0;
	public static final int REMOVE_LOOK = 1;

	private PlayerLooksCodec() {
	}

	public static byte[] encode(PlayerLooksPacket packet) {
		PacketWriter writer = new PacketWriter(64);

		switch (packet) {
			case SetLook set -> {
				writer.writeVarInt(SET_LOOK).writeVarInt(set.entity());
				set.look().write(writer);
			}
			case RemoveLook remove -> writer.writeVarInt(REMOVE_LOOK).writeVarInt(remove.entity());
		}

		return writer.toByteArray();
	}

	public static PlayerLooksPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		PlayerLooksPacket packet = switch (id) {
			case SET_LOOK -> new SetLook(reader.readVarInt(), PlayerLook.read(reader));
			case REMOVE_LOOK -> new RemoveLook(reader.readVarInt());
			default -> throw new ProtocolException("Unknown clientbound player looks packet " + id);
		};

		reader.expectEnd();
		return packet;
	}
}
