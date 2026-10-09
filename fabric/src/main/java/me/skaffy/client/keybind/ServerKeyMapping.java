package me.skaffy.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;

import me.skaffy.protocol.keybinds.KeybindsPacket.KeyPress;
import me.skaffy.protocol.keybinds.KeybindsPacket.KeyRelease;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

final class ServerKeyMapping extends KeyMapping {
	private final int number;
	private final Component label;
	private InputConstants.Key reportedKey;
	private boolean active;
	private boolean wins;

	ServerKeyMapping(int number, Component label, InputConstants.Key defaultKey, InputConstants.Key key, Category category, boolean active, boolean wins) {
		super("skaffy.keybind." + number, defaultKey.getType(), defaultKey.getValue(), category, number);
		this.number = number;
		this.label = label;
		setKey(key);
		this.reportedKey = key;
		this.active = active;
		this.wins = wins;
	}

	boolean winning() {
		return active && wins && !isUnbound();
	}

	void setState(boolean active, boolean wins) {
		this.active = active;
		this.wins = wins;

		if (!active && isDown()) {
			setDown(false);
		}
	}

	int number() {
		return number;
	}

	Component label() {
		return label;
	}

	void setKeyFromServer(InputConstants.Key newKey) {
		if (isDown()) {
			setDown(false);
		}

		setKey(newKey);
		reportedKey = newKey;
	}

	InputConstants.Key takeChangedKey() {
		if (key.equals(reportedKey)) {
			return null;
		}

		reportedKey = key;
		return key;
	}

	@Override
	public void setDown(boolean down) {
		if (down && (isUnbound() || !active)) {
			return;
		}

		if (down != isDown()) {
			ClientKeybinds.send(down ? new KeyPress(number, key.getName()) : new KeyRelease(number, key.getName()));
		}

		super.setDown(down);
	}
}
