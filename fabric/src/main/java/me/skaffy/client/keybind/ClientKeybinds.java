package me.skaffy.client.keybind;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.mixin.KeyMappingAccessor;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.keybinds.KeybindDefinition;
import me.skaffy.protocol.keybinds.KeybindsCodec;
import me.skaffy.protocol.keybinds.KeybindsPacket;
import me.skaffy.protocol.keybinds.KeybindsPacket.DefineKeybinds;
import me.skaffy.protocol.keybinds.KeybindsPacket.KeyChange;
import me.skaffy.protocol.keybinds.KeybindsPacket.SetKey;
import me.skaffy.protocol.keybinds.KeybindsPacket.SetKeybindState;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

public final class ClientKeybinds {
	private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("skaffy", "server"));
	private static final Object LOCK = new Object();
	private static DefineKeybinds pending;
	private static Component categoryLabel;
	private static List<ServerKeyMapping> mappings = List.of();

	private ClientKeybinds() {
	}

	public static void receive(byte[] data, boolean registering) {
		KeybindsPacket packet;

		try {
			packet = KeybindsCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed keybinds packet: {}", e.getMessage());
			return;
		}

		switch (packet) {
			case DefineKeybinds define -> {
				if (!registering) {
					return;
				}

				synchronized (LOCK) {
					if (pending != null) {
						SkaffySAPIClient.LOGGER.warn("Server defined its keybinds twice, keeping the first ones");
						return;
					}

					pending = define;
				}
			}
			case SetKey set -> Minecraft.getInstance().execute(() -> {
				if (set.keybind() <= mappings.size()) {
					mappings.get(set.keybind() - 1).setKeyFromServer(key(set.key()));
					KeyMapping.resetMapping();
					releaseTakenKeys();
				}
			});
			case SetKeybindState state -> Minecraft.getInstance().execute(() -> {
				if (state.keybind() <= mappings.size()) {
					mappings.get(state.keybind() - 1).setState(state.active(), state.wins());
					releaseTakenKeys();
				}
			});
			default -> {
			}
		}
	}

	public static void activate() {
		DefineKeybinds define;

		synchronized (LOCK) {
			define = pending;
			pending = null;
		}

		if (define == null || define.keybinds().isEmpty()) {
			return;
		}

		List<ServerKeyMapping> created = new ArrayList<>(define.keybinds().size());

		for (int i = 0; i < define.keybinds().size(); i++) {
			KeybindDefinition keybind = define.keybinds().get(i);
			created.add(new ServerKeyMapping(i + 1, Component.literal(keybind.name()), key(keybind.defaultKey()), key(keybind.key()), CATEGORY, keybind.active(), keybind.wins()));
		}

		categoryLabel = Component.literal(define.category());
		mappings = List.copyOf(created);
		KeyMapping.resetMapping();
		releaseTakenKeys();
	}

	public static void unload() {
		synchronized (LOCK) {
			pending = null;
		}

		Minecraft.getInstance().execute(() -> {
			if (mappings.isEmpty()) {
				return;
			}

			for (ServerKeyMapping mapping : mappings) {
				KeyMappingAccessor.skaffy$all().remove(mapping.getName(), mapping);
			}

			mappings = List.of();
			categoryLabel = null;
			KeyMapping.resetMapping();
		});
	}

	public static void tick() {
		for (ServerKeyMapping mapping : mappings) {
			InputConstants.Key changed = mapping.takeChangedKey();

			if (changed != null) {
				send(new KeyChange(mapping.number(), changed.getName()));
			}
		}
	}

	public static boolean dispatch(@Nullable List<KeyMapping> onKey, java.util.function.Consumer<KeyMapping> operation) {
		if (onKey == null || !taken(onKey)) {
			return false;
		}

		for (KeyMapping mapping : List.copyOf(onKey)) {
			if (mapping instanceof ServerKeyMapping) {
				operation.accept(mapping);
			}
		}

		return true;
	}

	public static boolean taken(KeyMapping mapping) {
		if (mapping instanceof ServerKeyMapping || mappings.isEmpty()) {
			return false;
		}

		for (ServerKeyMapping server : mappings) {
			if (server.winning() && server.same(mapping)) {
				return true;
			}
		}

		return false;
	}

	private static boolean taken(List<KeyMapping> onKey) {
		for (KeyMapping mapping : onKey) {
			if (mapping instanceof ServerKeyMapping server && server.winning()) {
				return true;
			}
		}

		return false;
	}

	private static void releaseTakenKeys() {
		for (KeyMapping mapping : KeyMappingAccessor.skaffy$all().values()) {
			if (mapping.isDown() && taken(mapping)) {
				((KeyMappingAccessor) mapping).skaffy$release();
			}
		}
	}

	public static KeyMapping[] withServerKeybinds(KeyMapping[] vanilla) {
		if (mappings.isEmpty()) {
			return vanilla;
		}

		List<KeyMapping> all = new ArrayList<>(List.of(vanilla));
		all.addAll(mappings);
		return all.toArray(KeyMapping[]::new);
	}

	public static @Nullable MutableComponent label(String name) {
		for (ServerKeyMapping mapping : mappings) {
			if (mapping.getName().equals(name)) {
				return mapping.label().copy();
			}
		}

		return null;
	}

	public static @Nullable Component categoryLabel(KeyMapping.Category category) {
		return CATEGORY.equals(category) ? categoryLabel : null;
	}

	static void send(KeybindsPacket packet) {
		SkaffyConnection.sendFeature(KeybindsCodec.CHANNEL, KeybindsCodec.encode(packet));
	}

	private static InputConstants.Key key(String name) {
		try {
			return InputConstants.getKey(name);
		} catch (RuntimeException e) {
			SkaffySAPIClient.LOGGER.warn("Server keybind uses unknown key {}, leaving it unbound", name);
			return InputConstants.UNKNOWN;
		}
	}
}
