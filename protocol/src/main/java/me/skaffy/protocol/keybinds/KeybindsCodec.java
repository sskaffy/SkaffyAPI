package me.skaffy.protocol.keybinds;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.keybinds.KeybindsPacket.DefineKeybinds;
import me.skaffy.protocol.keybinds.KeybindsPacket.KeyChange;
import me.skaffy.protocol.keybinds.KeybindsPacket.KeyPress;
import me.skaffy.protocol.keybinds.KeybindsPacket.KeyRelease;
import me.skaffy.protocol.keybinds.KeybindsPacket.SetKey;
import me.skaffy.protocol.keybinds.KeybindsPacket.SetKeybindState;

public final class KeybindsCodec {
	public static final String FEATURE = "keybinds";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);
	public static final int MAX_KEYBINDS = 256;
	public static final int MAX_NAME_LENGTH = 64;
	public static final int MAX_KEY_LENGTH = 64;

	public static final int DEFINE_KEYBINDS = 0;
	public static final int SET_KEY = 1;
	public static final int SET_KEYBIND_STATE = 2;

	public static final int KEY_PRESS = 0;
	public static final int KEY_RELEASE = 1;
	public static final int KEY_CHANGE = 2;

	private KeybindsCodec() {
	}

	public static byte[] encode(KeybindsPacket packet) {
		PacketWriter writer = new PacketWriter(64);
		boolean clientbound = false;

		switch (packet) {
			case DefineKeybinds define -> {
				clientbound = true;
				writer.writeVarInt(DEFINE_KEYBINDS)
						.writeString(define.category(), MAX_NAME_LENGTH)
						.writeList(define.keybinds(), (w, keybind) -> keybind.write(w));
			}
			case SetKey set -> {
				clientbound = true;
				writer.writeVarInt(SET_KEY).writeVarInt(set.keybind()).writeString(set.key(), MAX_KEY_LENGTH);
			}
			case SetKeybindState state -> {
				clientbound = true;
				writer.writeVarInt(SET_KEYBIND_STATE).writeVarInt(state.keybind()).writeBoolean(state.active()).writeBoolean(state.wins());
			}
			case KeyPress press -> writer.writeVarInt(KEY_PRESS).writeVarInt(press.keybind()).writeString(press.key(), MAX_KEY_LENGTH);
			case KeyRelease release -> writer.writeVarInt(KEY_RELEASE).writeVarInt(release.keybind()).writeString(release.key(), MAX_KEY_LENGTH);
			case KeyChange change -> writer.writeVarInt(KEY_CHANGE).writeVarInt(change.keybind()).writeString(change.key(), MAX_KEY_LENGTH);
		}

		int max = clientbound ? Protocol.MAX_CLIENTBOUND_PAYLOAD : Protocol.MAX_SERVERBOUND_PAYLOAD;

		if (writer.size() > max) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + max);
		}

		return writer.toByteArray();
	}

	public static KeybindsPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		KeybindsPacket packet = switch (id) {
			case DEFINE_KEYBINDS -> new DefineKeybinds(reader.readString(MAX_NAME_LENGTH), reader.readList(MAX_KEYBINDS, KeybindDefinition::read));
			case SET_KEY -> new SetKey(reader.readVarInt(), reader.readString(MAX_KEY_LENGTH));
			case SET_KEYBIND_STATE -> new SetKeybindState(reader.readVarInt(), reader.readBoolean(), reader.readBoolean());
			default -> throw new ProtocolException("Unknown clientbound keybinds packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static KeybindsPacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		KeybindsPacket packet = switch (id) {
			case KEY_PRESS -> new KeyPress(reader.readVarInt(), reader.readString(MAX_KEY_LENGTH));
			case KEY_RELEASE -> new KeyRelease(reader.readVarInt(), reader.readString(MAX_KEY_LENGTH));
			case KEY_CHANGE -> new KeyChange(reader.readVarInt(), reader.readString(MAX_KEY_LENGTH));
			default -> throw new ProtocolException("Unknown serverbound keybinds packet " + id);
		};

		reader.expectEnd();
		return packet;
	}
}
