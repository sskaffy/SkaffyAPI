package me.skaffy.paper.keybind;

import java.util.UUID;
import java.util.function.Function;

import me.skaffy.api.event.keybind.SkaffyKeyChangeEvent;
import me.skaffy.api.event.keybind.SkaffyKeyPressEvent;
import me.skaffy.api.event.keybind.SkaffyKeyReleaseEvent;
import me.skaffy.api.keybind.CustomKeybind;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.keybinds.KeybindsCodec;
import me.skaffy.protocol.keybinds.KeybindsPacket;
import me.skaffy.protocol.keybinds.KeybindsPacket.KeyChange;
import me.skaffy.protocol.keybinds.KeybindsPacket.KeyPress;
import me.skaffy.protocol.keybinds.KeybindsPacket.KeyRelease;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class KeybindPacketListener implements PluginMessageListener {
	private final Function<UUID, KeybindSession> sessions;

	public KeybindPacketListener(Function<UUID, KeybindSession> sessions) {
		this.sessions = sessions;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		KeybindSession session = sessions.apply(player.getUniqueId());

		if (!KeybindsCodec.CHANNEL.equals(channel) || session == null) {
			return;
		}

		KeybindsPacket packet;

		try {
			packet = KeybindsCodec.decodeServerbound(message);
		} catch (ProtocolException e) {
			return;
		}

		switch (packet) {
			case KeyPress press -> {
				CustomKeybind keybind = session.keybind(press.keybind());

				if (keybind != null) {
					new SkaffyKeyPressEvent(player, keybind, press.key()).callEvent();
				}
			}
			case KeyRelease release -> {
				CustomKeybind keybind = session.keybind(release.keybind());

				if (keybind != null) {
					new SkaffyKeyReleaseEvent(player, keybind, release.key()).callEvent();
				}
			}
			case KeyChange change -> {
				CustomKeybind keybind = session.keybind(change.keybind());

				if (keybind != null) {
					String previous = session.currentKey(keybind);
					session.setCurrentKey(keybind, change.key());
					new SkaffyKeyChangeEvent(player, keybind, change.key(), previous).callEvent();
				}
			}
			default -> {
			}
		}
	}
}
