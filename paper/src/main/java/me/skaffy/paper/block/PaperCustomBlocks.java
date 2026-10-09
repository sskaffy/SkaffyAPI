package me.skaffy.paper.block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import me.skaffy.api.block.BlockRotation;
import me.skaffy.api.block.CustomBlockType;
import me.skaffy.api.block.CustomBlocks;
import me.skaffy.api.block.PlacedCustomBlock;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.blocks.BlockDefinition;
import me.skaffy.protocol.blocks.BlockPlacement;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.blocks.BlocksPacket.DefineBlocks;
import me.skaffy.protocol.blocks.BlocksPacket.SetBlocks;
import me.skaffy.protocol.blocks.Positions;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class PaperCustomBlocks implements CustomBlocks, Listener {
	private static final int DEFINITIONS_PER_PACKET = 2048;

	private final Plugin plugin;
	private final Function<UUID, BlockSession> sessions;
	private final Map<String, CustomBlockType> types = new ConcurrentHashMap<>();
	private final Placements everyone = new Placements();
	private final Map<UUID, Placements> perPlayer = new ConcurrentHashMap<>();

	public PaperCustomBlocks(Plugin plugin, Function<UUID, BlockSession> sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
	}

	@Override
	public void register(CustomBlockType type) {
		for (String asset : new String[] {type.getModel(), type.getCollision(), type.getHitbox()}) {
			if (asset != null && !Protocol.isValidAssetId(asset)) {
				throw new IllegalArgumentException("Block " + type.getName() + " uses invalid asset id '" + asset + "'");
			}
		}

		types.put(type.getName(), type);
	}

	@Override
	public boolean unregister(String name) {
		return types.remove(name) != null;
	}

	@Override
	public Optional<CustomBlockType> getType(String name) {
		return Optional.ofNullable(types.get(name));
	}

	@Override
	public Collection<CustomBlockType> getTypes() {
		return List.copyOf(types.values());
	}

	@Override
	public void set(Location location, CustomBlockType type, BlockRotation rotation, boolean placeBarrier) {
		if (placeBarrier) {
			location.getBlock().setType(Material.BARRIER, false);
		}

		everyone.put(location, new Placement(type, rotation, location.getBlock().getBlockData()));
		sendToViewers(location);
	}

	@Override
	public boolean remove(Location location) {
		if (everyone.remove(location) == null) {
			return false;
		}

		sendToViewers(location);
		return true;
	}

	@Override
	public Optional<PlacedCustomBlock> get(Location location) {
		return Optional.ofNullable(everyone.get(location)).map(placement -> placement.placed(location));
	}

	@Override
	public Collection<PlacedCustomBlock> getAll(Chunk chunk) {
		List<PlacedCustomBlock> placed = new ArrayList<>();
		everyone.chunk(chunk.getWorld(), chunk.getChunkKey()).forEach((pos, placement) -> placed.add(placement.placed(location(chunk.getWorld(), pos))));
		return placed;
	}

	@Override
	public void set(Player player, Location location, CustomBlockType type, BlockRotation rotation) {
		perPlayer.computeIfAbsent(player.getUniqueId(), id -> new Placements()).put(location, new Placement(type, rotation, location.getBlock().getBlockData()));
		sendPosition(player, location);
	}

	@Override
	public boolean remove(Player player, Location location) {
		Placements own = perPlayer.get(player.getUniqueId());

		if (own == null || own.remove(location) == null) {
			return false;
		}

		sendPosition(player, location);
		return true;
	}

	@Override
	public Optional<PlacedCustomBlock> get(Player player, Location location) {
		return Optional.ofNullable(visibleTo(player.getUniqueId(), location)).map(placement -> placement.placed(location));
	}

	public void register(BlockSession session) {
		List<CustomBlockType> snapshot = List.copyOf(types.values());
		session.setBlockTypes(snapshot);

		for (int start = 0; start < snapshot.size(); start += DEFINITIONS_PER_PACKET) {
			List<BlockDefinition> part = snapshot.subList(start, Math.min(snapshot.size(), start + DEFINITIONS_PER_PACKET)).stream()
					.map(PaperCustomBlocks::definition)
					.toList();
			session.sendDefinition(BlocksCodec.encode(new DefineBlocks(part)));
		}
	}

	public Placement visibleTo(UUID playerId, Location location) {
		Placements own = perPlayer.get(playerId);
		Placement placement = own == null ? null : own.get(location);
		return placement != null ? placement : everyone.get(location);
	}

	@EventHandler
	public void onChunkSent(PlayerChunkLoadEvent event) {
		Player player = event.getPlayer();
		BlockSession session = sessions.apply(player.getUniqueId());

		if (session == null) {
			return;
		}

		World world = event.getChunk().getWorld();
		long chunkKey = event.getChunk().getChunkKey();
		Map<Long, Placement> merged = new HashMap<>(everyone.chunk(world, chunkKey));
		Placements own = perPlayer.get(player.getUniqueId());

		if (own != null) {
			merged.putAll(own.chunk(world, chunkKey));
		}

		Map<Integer, List<BlockPlacement>> bySection = new LinkedHashMap<>();

		merged.forEach((pos, placement) -> {
			int x = Positions.blockX(pos);
			int y = Positions.blockY(pos);
			int z = Positions.blockZ(pos);

			if (placement.type().removeWhenReplaced() && !world.getBlockData(x, y, z).equals(placement.carrier())) {
				everyone.remove(world, chunkKey, pos, placement);
				return;
			}

			BlockPlacement encoded = encode(session, placement, x, y, z);

			if (encoded.block() > 0) {
				bySection.computeIfAbsent(y >> 4, section -> new ArrayList<>()).add(encoded);
			}
		});

		int chunkX = (int) chunkKey;
		int chunkZ = (int) (chunkKey >> 32);
		bySection.forEach((sectionY, placements) -> send(player, Positions.section(chunkX, sectionY, chunkZ), placements));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Location location = event.getBlock().getLocation();
		Placement placement = everyone.get(location);

		if (placement != null && placement.type().removeWhenReplaced()) {
			everyone.remove(location);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		perPlayer.remove(event.getPlayer().getUniqueId());
	}

	private void sendToViewers(Location location) {
		for (Player player : location.getWorld().getPlayersSeeingChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
			sendPosition(player, location);
		}
	}

	private void sendPosition(Player player, Location location) {
		BlockSession session = sessions.apply(player.getUniqueId());

		if (session == null || !player.getWorld().equals(location.getWorld()) || !player.isChunkSent(Chunk.getChunkKey(location))) {
			return;
		}

		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();
		Placement placement = visibleTo(player.getUniqueId(), location);
		BlockPlacement encoded = placement == null ? new BlockPlacement(BlockPlacement.localPos(x, y, z), 0, 0) : encode(session, placement, x, y, z);

		if (placement != null) {
			player.sendBlockChange(location, location.getBlock().getBlockData());
		}

		send(player, Positions.section(x >> 4, y >> 4, z >> 4), List.of(encoded));
	}

	private void send(Player player, long sectionPos, List<BlockPlacement> placements) {
		player.sendPluginMessage(plugin, BlocksCodec.CHANNEL, BlocksCodec.encode(new SetBlocks(sectionPos, placements)));
	}

	private static BlockPlacement encode(BlockSession session, Placement placement, int x, int y, int z) {
		BlockRotation rotation = placement.rotation();
		int turns = placement.type().isRotatable() ? BlockPlacement.rotation(rotation.x() / 90, rotation.y() / 90) : 0;
		return new BlockPlacement(BlockPlacement.localPos(x, y, z), session.blockNumber(placement.type()), turns);
	}

	private static BlockDefinition definition(CustomBlockType type) {
		return new BlockDefinition(
				type.getName(),
				type.getModel(),
				type.getCollision(),
				type.getHitbox(),
				BlockDefinition.Transparency.valueOf(type.getTransparency().name()),
				type.getLight(),
				type.getHardness(),
				BlockDefinition.Tool.valueOf(type.getTool().name()),
				type.requiresTool(),
				type.removeWhenReplaced(),
				type.isRotatable());
	}

	private static Location location(World world, long pos) {
		return new Location(world, Positions.blockX(pos), Positions.blockY(pos), Positions.blockZ(pos));
	}

	public record Placement(CustomBlockType type, BlockRotation rotation, BlockData carrier) {
		public PlacedCustomBlock placed(Location location) {
			return new PlacedCustomBlock(location, type, rotation);
		}
	}

	private static final class Placements {
		private final Map<UUID, Map<Long, Map<Long, Placement>>> worlds = new ConcurrentHashMap<>();

		void put(Location location, Placement placement) {
			worlds.computeIfAbsent(location.getWorld().getUID(), id -> new ConcurrentHashMap<>())
					.computeIfAbsent(Chunk.getChunkKey(location), key -> new ConcurrentHashMap<>())
					.put(pos(location), placement);
		}

		Placement get(Location location) {
			return chunk(location.getWorld(), Chunk.getChunkKey(location)).get(pos(location));
		}

		Placement remove(Location location) {
			return chunk(location.getWorld(), Chunk.getChunkKey(location)).remove(pos(location));
		}

		void remove(World world, long chunkKey, long pos, Placement placement) {
			chunk(world, chunkKey).remove(pos, placement);
		}

		Map<Long, Placement> chunk(World world, long chunkKey) {
			Map<Long, Map<Long, Placement>> chunks = worlds.get(world.getUID());
			Map<Long, Placement> chunk = chunks == null ? null : chunks.get(chunkKey);
			return chunk == null ? new HashMap<>() : chunk;
		}

		private static long pos(Location location) {
			return Positions.block(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		}
	}
}
