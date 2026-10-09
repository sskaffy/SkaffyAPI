package me.skaffy.protocol.keybinds;

import java.util.List;

import me.skaffy.protocol.ProtocolException;

public sealed interface KeybindsPacket {
	record DefineKeybinds(String category, List<KeybindDefinition> keybinds) implements KeybindsPacket {
		public DefineKeybinds {
			KeybindDefinition.checkName("Category name", category);
			keybinds = List.copyOf(keybinds);

			if (keybinds.size() > KeybindsCodec.MAX_KEYBINDS) {
				throw new ProtocolException("At most " + KeybindsCodec.MAX_KEYBINDS + " keybinds, got " + keybinds.size());
			}
		}
	}

	record SetKey(int keybind, String key) implements KeybindsPacket {
		public SetKey {
			checkNumber(keybind);
			KeybindDefinition.checkKey(key);
		}
	}

	record SetKeybindState(int keybind, boolean active, boolean wins) implements KeybindsPacket {
		public SetKeybindState {
			checkNumber(keybind);
		}
	}

	record KeyPress(int keybind, String key) implements KeybindsPacket {
		public KeyPress {
			checkNumber(keybind);
			KeybindDefinition.checkKey(key);
		}
	}

	record KeyRelease(int keybind, String key) implements KeybindsPacket {
		public KeyRelease {
			checkNumber(keybind);
			KeybindDefinition.checkKey(key);
		}
	}

	record KeyChange(int keybind, String key) implements KeybindsPacket {
		public KeyChange {
			checkNumber(keybind);
			KeybindDefinition.checkKey(key);
		}
	}

	private static void checkNumber(int keybind) {
		if (keybind < 1 || keybind > KeybindsCodec.MAX_KEYBINDS) {
			throw new ProtocolException("Invalid keybind " + keybind);
		}
	}
}
