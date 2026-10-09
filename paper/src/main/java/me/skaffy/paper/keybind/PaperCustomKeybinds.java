package me.skaffy.paper.keybind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import me.skaffy.api.keybind.CustomKeybind;
import me.skaffy.api.keybind.CustomKeybinds;
import me.skaffy.protocol.keybinds.KeybindDefinition;
import me.skaffy.protocol.keybinds.KeybindsCodec;
import me.skaffy.protocol.keybinds.KeybindsPacket.DefineKeybinds;
import me.skaffy.protocol.keybinds.KeybindsPacket.SetKey;
import me.skaffy.protocol.keybinds.KeybindsPacket.SetKeybindState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PaperCustomKeybinds implements CustomKeybinds {
	private final Plugin plugin;
	private final Function<UUID, KeybindSession> sessions;
	private final Map<String, CustomKeybind> keybinds = new LinkedHashMap<>();
	private volatile String category;

	public PaperCustomKeybinds(Plugin plugin, Function<UUID, KeybindSession> sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
	}

	@Override
	public void setCategory(String name) {
		if (name.isEmpty() || name.length() > KeybindsCodec.MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("Category name must be 1 to " + KeybindsCodec.MAX_NAME_LENGTH + " characters: " + name);
		}

		category = name;
	}

	@Override
	public Optional<String> getCategory() {
		return Optional.ofNullable(category);
	}

	@Override
	public synchronized void register(CustomKeybind keybind) {
		if (category == null) {
			throw new IllegalStateException("Name the keybind category with setCategory before registering keybinds");
		}

		if (!keybinds.containsKey(keybind.getId()) && keybinds.size() >= MAX_KEYBINDS) {
			throw new IllegalStateException("A server has at most " + MAX_KEYBINDS + " keybinds");
		}

		keybinds.put(keybind.getId(), keybind);
	}

	@Override
	public synchronized boolean unregister(String id) {
		return keybinds.remove(id) != null;
	}

	@Override
	public synchronized Optional<CustomKeybind> getKeybind(String id) {
		return Optional.ofNullable(keybinds.get(id));
	}

	@Override
	public synchronized Collection<CustomKeybind> getKeybinds() {
		return List.copyOf(keybinds.values());
	}

	@Override
	public boolean setKey(Player player, CustomKeybind keybind, String key) {
		if (key.isEmpty() || key.length() > KeybindsCodec.MAX_KEY_LENGTH) {
			throw new IllegalArgumentException("Key name must be 1 to " + KeybindsCodec.MAX_KEY_LENGTH + " characters: " + key);
		}

		KeybindSession session = sessions.apply(player.getUniqueId());
		int number = session == null ? 0 : session.keybindNumber(keybind);

		if (number == 0) {
			return false;
		}

		session.setCurrentKey(keybind, key);
		player.sendPluginMessage(plugin, KeybindsCodec.CHANNEL, KeybindsCodec.encode(new SetKey(number, key)));
		return true;
	}

	@Override
	public Optional<String> getKey(Player player, CustomKeybind keybind) {
		KeybindSession session = sessions.apply(player.getUniqueId());
		return session == null ? Optional.empty() : Optional.ofNullable(session.currentKey(keybind));
	}

	@Override
	public boolean setActive(Player player, CustomKeybind keybind, boolean active) {
		return setState(player, keybind, active, null);
	}

	@Override
	public boolean isActive(Player player, CustomKeybind keybind) {
		KeybindSession session = sessions.apply(player.getUniqueId());
		return session != null && session.keybindNumber(keybind) != 0 && session.keybindState(keybind)[0];
	}

	@Override
	public boolean setWinsOverVanilla(Player player, CustomKeybind keybind, boolean wins) {
		return setState(player, keybind, null, wins);
	}

	@Override
	public boolean winsOverVanilla(Player player, CustomKeybind keybind) {
		KeybindSession session = sessions.apply(player.getUniqueId());
		return session != null && session.keybindNumber(keybind) != 0 && session.keybindState(keybind)[1];
	}

	private boolean setState(Player player, CustomKeybind keybind, Boolean active, Boolean wins) {
		KeybindSession session = sessions.apply(player.getUniqueId());
		int number = session == null ? 0 : session.keybindNumber(keybind);

		if (number == 0) {
			return false;
		}

		boolean[] state = session.keybindState(keybind);
		boolean newActive = active != null ? active : state[0];
		boolean newWins = wins != null ? wins : state[1];
		session.setKeybindState(keybind, newActive, newWins);
		player.sendPluginMessage(plugin, KeybindsCodec.CHANNEL, KeybindsCodec.encode(new SetKeybindState(number, newActive, newWins)));
		return true;
	}

	public void register(KeybindSession session, Map<String, String> playerKeys) {
		List<CustomKeybind> snapshot = List.copyOf(getKeybinds());
		String name = category;

		if (name == null || snapshot.isEmpty()) {
			session.setKeybinds(List.of());
			return;
		}

		session.setKeybinds(snapshot);
		List<KeybindDefinition> definitions = new ArrayList<>(snapshot.size());

		for (CustomKeybind keybind : snapshot) {
			String key = playerKeys.getOrDefault(keybind.getId(), keybind.getDefaultKey());
			session.setCurrentKey(keybind, key);
			definitions.add(new KeybindDefinition(keybind.getName(), keybind.getDefaultKey(), key, keybind.isActiveAtStart(), keybind.winsOverVanillaAtStart()));
		}

		session.sendKeybindDefinition(KeybindsCodec.encode(new DefineKeybinds(name, definitions)));
	}
}
