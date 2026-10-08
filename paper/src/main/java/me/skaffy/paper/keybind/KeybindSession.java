package me.skaffy.paper.keybind;

import java.util.List;

import me.skaffy.api.keybind.CustomKeybind;

public interface KeybindSession {
	void setKeybinds(List<CustomKeybind> keybinds);

	int keybindNumber(CustomKeybind keybind);

	CustomKeybind keybind(int number);

	String currentKey(CustomKeybind keybind);

	void setCurrentKey(CustomKeybind keybind, String key);

	boolean[] keybindState(CustomKeybind keybind);

	void setKeybindState(CustomKeybind keybind, boolean active, boolean wins);

	void sendKeybindDefinition(byte[] data);
}
