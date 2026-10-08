package me.skaffy.paper.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import me.skaffy.api.event.SkaffyClientReadyEvent;
import me.skaffy.api.shape.Shape;
import me.skaffy.api.shape.ShapePlacement;
import me.skaffy.api.shape.SkaffyShapes;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.shapes.ShapesCodec;
import me.skaffy.protocol.shapes.ShapesPacket;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public final class PaperShapes implements SkaffyShapes, Listener {
	private static final String PLACED = "w:";
	private static final String PLAYER = "p:";

	private final Plugin plugin;
	private final Predicate<UUID> ready;
	private final Map<String, Placed> placed = new ConcurrentHashMap<>();

	private record Placed(Shape shape, ShapePlacement placement, byte[] packet) {
	}

	public PaperShapes(Plugin plugin, Predicate<UUID> ready) {
		this.plugin = plugin;
		this.ready = ready;
	}

	private static void checkId(String id) {
		if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) {
			throw new IllegalArgumentException("Shape ids must be 1 to " + MAX_ID_LENGTH + " characters: " + id);
		}
	}

	private static byte[] encode(ShapesPacket packet) {
		byte[] data;

		try {
			data = ShapesCodec.encode(packet);
		} catch (ProtocolException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (data.length > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new IllegalArgumentException("The shape is too big to send (" + data.length + " bytes)");
		}

		return data;
	}

	private void sendToEveryone(byte[] packet) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (ready.test(player.getUniqueId())) {
				player.sendPluginMessage(plugin, ShapesCodec.CHANNEL, packet);
			}
		}
	}


	@Override
	public void place(String id, Shape shape, ShapePlacement placement) {
		checkId(id);
		byte[] packet = encode(new ShapesPacket.SetShape(PLACED + id, wire(placement), style(shape), parts(shape)));
		placed.put(id, new Placed(shape, placement, packet));
		sendToEveryone(packet);
	}

	@Override
	public boolean move(String id, ShapePlacement placement) {
		Placed current = placed.get(id);

		if (current == null) {
			return false;
		}

		placed.put(id, new Placed(current.shape(), placement, encode(new ShapesPacket.SetShape(PLACED + id, wire(placement), style(current.shape()), parts(current.shape())))));
		sendToEveryone(encode(new ShapesPacket.MoveShape(PLACED + id, wire(placement))));
		return true;
	}

	@Override
	public boolean remove(String id) {
		if (placed.remove(id) == null) {
			return false;
		}

		sendToEveryone(encode(new ShapesPacket.RemoveShape(PLACED + id)));
		return true;
	}

	@Override
	public Optional<Shape> getPlaced(String id) {
		return Optional.ofNullable(placed.get(id)).map(Placed::shape);
	}

	@Override
	public Optional<ShapePlacement> getPlacement(String id) {
		return Optional.ofNullable(placed.get(id)).map(Placed::placement);
	}

	@Override
	public Set<String> getPlacedIds() {
		return Set.copyOf(placed.keySet());
	}


	@Override
	public boolean show(Player player, String id, Shape shape, ShapePlacement placement) {
		checkId(id);

		if (!ready.test(player.getUniqueId())) {
			return false;
		}

		player.sendPluginMessage(plugin, ShapesCodec.CHANNEL, encode(new ShapesPacket.SetShape(PLAYER + id, wire(placement), style(shape), parts(shape))));
		return true;
	}

	@Override
	public boolean move(Player player, String id, ShapePlacement placement) {
		checkId(id);

		if (!ready.test(player.getUniqueId())) {
			return false;
		}

		player.sendPluginMessage(plugin, ShapesCodec.CHANNEL, encode(new ShapesPacket.MoveShape(PLAYER + id, wire(placement))));
		return true;
	}

	@Override
	public void hide(Player player, String id) {
		checkId(id);

		if (ready.test(player.getUniqueId())) {
			player.sendPluginMessage(plugin, ShapesCodec.CHANNEL, encode(new ShapesPacket.RemoveShape(PLAYER + id)));
		}
	}

	@Override
	public void hideAll(Player player, String prefix) {
		if (ready.test(player.getUniqueId())) {
			player.sendPluginMessage(plugin, ShapesCodec.CHANNEL, encode(new ShapesPacket.ClearShapes(PLAYER + prefix)));
		}
	}

	@Override
	public boolean isSupported(Player player) {
		return ready.test(player.getUniqueId());
	}


	private static ShapesPacket.Placement wire(ShapePlacement placement) {
		Quaternionfc q = placement.getRotation();
		Vector3fc s = placement.getScale();
		return new ShapesPacket.Placement(placement.getWorld().getKey().toString(), placement.getX(), placement.getY(), placement.getZ(), q.x(), q.y(), q.z(), q.w(), s.x(), s.y(), s.z());
	}

	private static ShapesPacket.Style style(Shape shape) {
		return new ShapesPacket.Style(shape.isWorld() ? ShapesPacket.Mode.WORLD : ShapesPacket.Mode.OVERLAY, shape.isSeeThrough(), shape.getTexture(),
				ShapesPacket.Render.valueOf(shape.getRender().name()), shape.isEmissive(), shape.isDoubleSided(), shape.getViewDistance());
	}

	private static int argb(Color color) {
		return color == null ? 0 : color.asARGB();
	}

	private static ShapesPacket.Vertex vertex(Shape.Vertex vertex) {
		return new ShapesPacket.Vertex((float) vertex.x(), (float) vertex.y(), (float) vertex.z(), (float) vertex.u(), (float) vertex.v());
	}

	private static List<ShapesPacket.Part> parts(Shape shape) {
		List<ShapesPacket.Part> parts = new ArrayList<>(shape.getParts().size());

		for (Shape.Part part : shape.getParts()) {
			parts.add(switch (part) {
				case Shape.Line line -> new ShapesPacket.Line((float) line.from().getX(), (float) line.from().getY(), (float) line.from().getZ(),
						(float) line.to().getX(), (float) line.to().getY(), (float) line.to().getZ(), argb(line.color()), line.width());
				case Shape.Triangle triangle -> new ShapesPacket.Triangle(vertex(triangle.a()), vertex(triangle.b()), vertex(triangle.c()), argb(triangle.color()));
				case Shape.Quad quad -> new ShapesPacket.Quad(vertex(quad.a()), vertex(quad.b()), vertex(quad.c()), vertex(quad.d()), argb(quad.color()));
				case Shape.Box box -> new ShapesPacket.Box((float) box.box().getMinX(), (float) box.box().getMinY(), (float) box.box().getMinZ(),
						(float) box.box().getMaxX(), (float) box.box().getMaxY(), (float) box.box().getMaxZ(), argb(box.fill()), argb(box.outline()), box.outlineWidth(), box.uvScale());
			});
		}

		return parts;
	}


	private void sendPlaced(Player player) {
		if (!player.isOnline() || !ready.test(player.getUniqueId())) {
			return;
		}

		player.sendPluginMessage(plugin, ShapesCodec.CHANNEL, encode(new ShapesPacket.ClearShapes(PLACED)));

		for (Placed shape : placed.values()) {
			player.sendPluginMessage(plugin, ShapesCodec.CHANNEL, shape.packet());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		player.getScheduler().runDelayed(plugin, task -> sendPlaced(player), null, 1);
	}

	@EventHandler
	public void onReady(SkaffyClientReadyEvent event) {
		Player player = Bukkit.getPlayer(event.getClient().getPlayerId());

		if (player != null) {
			player.getScheduler().runDelayed(plugin, task -> sendPlaced(player), null, 1);
		}
	}
}
