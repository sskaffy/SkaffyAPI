package me.skaffy.paper.block;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import io.papermc.paper.event.player.PlayerFailMoveEvent;
import me.skaffy.paper.blockshape.PaperBlockShapes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.BoundingBox;

public final class BarrierMovement implements Listener {
	public enum Mode {
		EVERYONE,
		CUSTOM_BLOCKS,
		NOBODY;

		public static Mode parse(String value) {
			return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
		}
	}

	private static final double MARGIN = 0.01;

	private final Mode mode;
	private final PaperCustomBlocks blocks;
	private final PaperBlockShapes shapes;
	private final Function<UUID, BlockSession> sessions;

	public BarrierMovement(Mode mode, PaperCustomBlocks blocks, PaperBlockShapes shapes, Function<UUID, BlockSession> sessions) {
		this.mode = mode;
		this.blocks = blocks;
		this.shapes = shapes;
		this.sessions = sessions;
	}

	@EventHandler
	public void onFailMove(PlayerFailMoveEvent event) {
		if (mode == Mode.NOBODY || event.getFailReason() != PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK && event.getFailReason() != PlayerFailMoveEvent.FailReason.MOVED_WRONGLY) {
			return;
		}

		Player player = event.getPlayer();

		if (mode == Mode.CUSTOM_BLOCKS && sessions.apply(player.getUniqueId()) == null) {
			return;
		}

		Location from = event.getFrom();
		Location to = event.getTo();
		BoundingBox path = box(player, from).union(box(player, to)).union(player.getBoundingBox()).expand(MARGIN);

		if (touchesAllowedBarrier(player, to.getWorld(), path)) {
			event.setAllowed(true);
			event.setLogWarning(false);
		}
	}

	@EventHandler
	public void onSuffocate(EntityDamageEvent event) {
		if (mode == Mode.NOBODY || event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION || !(event.getEntity() instanceof Player player)) {
			return;
		}

		if (mode == Mode.CUSTOM_BLOCKS && sessions.apply(player.getUniqueId()) == null) {
			return;
		}

		Location eye = player.getEyeLocation();
		double half = player.getWidth() * 0.4;
		BoundingBox head = new BoundingBox(eye.getX() - half, eye.getY() - 0.05, eye.getZ() - half, eye.getX() + half, eye.getY() + 0.05, eye.getZ() + half);

		if (touchesAllowedBarrier(player, eye.getWorld(), head)) {
			event.setCancelled(true);
		}
	}

	private static BoundingBox box(Player player, Location at) {
		double half = player.getWidth() / 2;
		return new BoundingBox(at.getX() - half, at.getY(), at.getZ() - half, at.getX() + half, at.getY() + player.getHeight(), at.getZ() + half);
	}

	private boolean touchesAllowedBarrier(Player player, World world, BoundingBox box) {
		for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
			for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}

				for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
					if (world.getType(x, y, z) != Material.BARRIER) {
						continue;
					}

					if (mode == Mode.EVERYONE || shapes.has(world, x, y, z) || blocks.visibleTo(player.getUniqueId(), new Location(world, x, y, z)) != null) {
						return true;
					}
				}
			}
		}

		return false;
	}
}
