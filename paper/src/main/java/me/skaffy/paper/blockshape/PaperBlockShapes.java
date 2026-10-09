package me.skaffy.paper.blockshape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import me.skaffy.api.blockshape.BlockShapes;
import me.skaffy.api.event.SkaffyClientReadyEvent;
import me.skaffy.protocol.blockshapes.BlockShapesCodec;
import me.skaffy.protocol.blockshapes.BlockShapesPacket;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.Box;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.Shape;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

public final class PaperBlockShapes implements BlockShapes, Listener {
	private final Plugin plugin;
	private final Predicate<UUID> ready;
	private final Map<UUID, Map<Long, Shape>> worlds = new ConcurrentHashMap<>();
	private final Map<UUID, Map<Long, Change>> pending = new ConcurrentHashMap<>();

	private record Change(int x, int y, int z, Shape shape) {
	}

	public PaperBlockShapes(Plugin plugin, Predicate<UUID> ready) {
		this.plugin = plugin;
		this.ready = ready;
		Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> flush(), 1, 1);
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
	}

	private static long key(Block block) {
		return key(block.getX(), block.getY(), block.getZ());
	}

	@Override
	public void set(Block block, List<BoundingBox> collision, List<BoundingBox> outline) {
		Shape shape = new Shape(block.getX(), block.getY(), block.getZ(), boxes(collision), outline == null ? null : boxes(outline));
		worlds.computeIfAbsent(block.getWorld().getUID(), id -> new ConcurrentHashMap<>()).put(key(block), shape);
		queue(block.getWorld(), key(block), new Change(block.getX(), block.getY(), block.getZ(), shape));
	}

	private static List<Box> boxes(List<BoundingBox> boxes) {
		if (boxes.size() > MAX_BOXES) {
			throw new IllegalArgumentException("At most " + MAX_BOXES + " boxes per block, got " + boxes.size());
		}

		List<Box> wire = new ArrayList<>(boxes.size());

		for (BoundingBox box : boxes) {
			try {
				wire.add(new Box((float) box.getMinX(), (float) box.getMinY(), (float) box.getMinZ(), (float) box.getMaxX(), (float) box.getMaxY(), (float) box.getMaxZ()));
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("Boxes must be inside the block (0 to 1): " + box, e);
			}
		}

		return wire;
	}

	@Override
	public void remove(Block block) {
		Map<Long, Shape> world = worlds.get(block.getWorld().getUID());

		if (world != null && world.remove(key(block)) != null) {
			queue(block.getWorld(), key(block), new Change(block.getX(), block.getY(), block.getZ(), null));
		}
	}

	@Override
	public Optional<List<BoundingBox>> getCollision(Block block) {
		return shape(block).map(shape -> bukkit(shape.collision()));
	}

	@Override
	public Optional<List<BoundingBox>> getOutline(Block block) {
		return shape(block).map(shape -> bukkit(shape.outline() != null ? shape.outline() : shape.collision()));
	}

	@Override
	public boolean has(Block block) {
		return shape(block).isPresent();
	}

	public boolean has(World world, int x, int y, int z) {
		Map<Long, Shape> shapes = worlds.get(world.getUID());
		return shapes != null && shapes.containsKey(key(x, y, z));
	}

	private Optional<Shape> shape(Block block) {
		Map<Long, Shape> world = worlds.get(block.getWorld().getUID());
		return Optional.ofNullable(world == null ? null : world.get(key(block)));
	}

	private static List<BoundingBox> bukkit(List<Box> boxes) {
		return boxes.stream().map(box -> new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())).toList();
	}

	@Override
	public void clear(World world) {
		worlds.remove(world.getUID());
		pending.remove(world.getUID());
		byte[] packet = BlockShapesCodec.encode(new BlockShapesPacket.ClearShapes(world.getKey().toString()));

		for (Player player : world.getPlayers()) {
			if (ready.test(player.getUniqueId())) {
				player.sendPluginMessage(plugin, BlockShapesCodec.CHANNEL, packet);
			}
		}
	}

	private void queue(World world, long key, Change change) {
		pending.computeIfAbsent(world.getUID(), id -> new ConcurrentHashMap<>()).put(key, change);
	}

	private void flush() {
		for (UUID worldId : List.copyOf(pending.keySet())) {
			Map<Long, Change> changes = pending.remove(worldId);
			World world = Bukkit.getWorld(worldId);

			if (changes == null || changes.isEmpty() || world == null) {
				continue;
			}

			List<Shape> set = new ArrayList<>();
			List<long[]> removed = new ArrayList<>();

			for (Change change : changes.values()) {
				if (change.shape() == null) {
					removed.add(new long[] {change.x(), change.y(), change.z()});
				} else {
					set.add(change.shape());
				}
			}

			List<byte[]> packets = packets(world.getKey().toString(), set, removed);

			for (Player player : world.getPlayers()) {
				if (ready.test(player.getUniqueId())) {
					packets.forEach(packet -> player.sendPluginMessage(plugin, BlockShapesCodec.CHANNEL, packet));
				}
			}
		}
	}

	private static List<byte[]> packets(String dimension, List<Shape> set, List<long[]> removed) {
		List<byte[]> packets = new ArrayList<>();

		for (int i = 0; i < removed.size(); i += BlockShapesCodec.MAX_PER_PACKET) {
			packets.add(BlockShapesCodec.encode(new BlockShapesPacket.RemoveShapes(dimension, removed.subList(i, Math.min(removed.size(), i + BlockShapesCodec.MAX_PER_PACKET)))));
		}

		int chunk = 2048;

		for (int i = 0; i < set.size(); i += chunk) {
			packets.add(BlockShapesCodec.encode(new BlockShapesPacket.SetShapes(dimension, set.subList(i, Math.min(set.size(), i + chunk)))));
		}

		return packets;
	}

	private void sendWorld(Player player) {
		if (!player.isOnline() || !ready.test(player.getUniqueId())) {
			return;
		}

		World world = player.getWorld();
		Map<Long, Shape> shapes = worlds.getOrDefault(world.getUID(), Map.of());
		String dimension = world.getKey().toString();
		player.sendPluginMessage(plugin, BlockShapesCodec.CHANNEL, BlockShapesCodec.encode(new BlockShapesPacket.ClearShapes(dimension)));

		for (byte[] packet : packets(dimension, List.copyOf(new LinkedHashMap<>(shapes).values()), List.of())) {
			player.sendPluginMessage(plugin, BlockShapesCodec.CHANNEL, packet);
		}
	}

	private void sendWorldLater(Player player) {
		player.getScheduler().runDelayed(plugin, task -> sendWorld(player), null, 1);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		sendWorldLater(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldChange(PlayerChangedWorldEvent event) {
		sendWorldLater(event.getPlayer());
	}

	@EventHandler
	public void onReady(SkaffyClientReadyEvent event) {
		Player player = Bukkit.getPlayer(event.getClient().getPlayerId());

		if (player != null) {
			sendWorldLater(player);
		}
	}
}
