package me.skaffy.protocol.keybinds;

import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record KeybindDefinition(String name, String defaultKey, String key, boolean active, boolean wins) {
	public KeybindDefinition {
		checkName("Keybind name", name);
		checkKey(defaultKey);
		checkKey(key);
	}

	public static KeybindDefinition read(PacketReader reader) {
		return new KeybindDefinition(reader.readString(KeybindsCodec.MAX_NAME_LENGTH), reader.readString(KeybindsCodec.MAX_KEY_LENGTH), reader.readString(KeybindsCodec.MAX_KEY_LENGTH),
				reader.readBoolean(), reader.readBoolean());
	}

	public void write(PacketWriter writer) {
		writer.writeString(name, KeybindsCodec.MAX_NAME_LENGTH);
		writer.writeString(defaultKey, KeybindsCodec.MAX_KEY_LENGTH);
		writer.writeString(key, KeybindsCodec.MAX_KEY_LENGTH);
		writer.writeBoolean(active);
		writer.writeBoolean(wins);
	}

	static void checkName(String what, String name) {
		if (name.isEmpty() || name.length() > KeybindsCodec.MAX_NAME_LENGTH) {
			throw new ProtocolException(what + " must be 1 to " + KeybindsCodec.MAX_NAME_LENGTH + " characters");
		}
	}

	static void checkKey(String key) {
		if (key.isEmpty() || key.length() > KeybindsCodec.MAX_KEY_LENGTH) {
			throw new ProtocolException("Key name must be 1 to " + KeybindsCodec.MAX_KEY_LENGTH + " characters");
		}
	}
}
