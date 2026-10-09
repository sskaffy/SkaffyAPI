package me.skaffy.api.event;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.papermc.paper.connection.PlayerConnection;
import me.skaffy.api.SkaffyClient;
import org.bukkit.event.HandlerList;

public class SkaffyClientRegisterEvent extends SkaffyClientEvent {
	private static final HandlerList HANDLER_LIST = new HandlerList();

	private final Map<String, byte[]> assets = new LinkedHashMap<>();
	private final Set<String> deletedAssets = new LinkedHashSet<>();
	private final Map<String, String> keys = new LinkedHashMap<>();
	private final Supplier<Set<String>> cachedAssetIds;

	public SkaffyClientRegisterEvent(SkaffyClient client, PlayerConnection connection, Supplier<Set<String>> cachedAssetIds) {
		super(client, connection);
		this.cachedAssetIds = cachedAssetIds;
	}

	public void addAsset(String id, byte[] data) {
		assets.put(id, data.clone());
	}

	public Map<String, byte[]> getAssets() {
		return Collections.unmodifiableMap(assets);
	}

	public Set<String> getCachedAssetIds() {
		return cachedAssetIds.get();
	}

	public void deleteAssets(Collection<String> ids) {
		deletedAssets.addAll(ids);
	}

	public Set<String> getDeletedAssets() {
		return Collections.unmodifiableSet(deletedAssets);
	}

	public void setKey(String keybindId, String key) {
		if (key.isEmpty() || key.length() > 64) {
			throw new IllegalArgumentException("Key name must be 1 to 64 characters: " + key);
		}

		keys.put(keybindId, key);
	}

	public Map<String, String> getKeys() {
		return Collections.unmodifiableMap(keys);
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLER_LIST;
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
	}
}
