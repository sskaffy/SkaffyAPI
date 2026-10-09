package me.skaffy.client.blockshape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.blockshapes.BlockShapesCodec;
import me.skaffy.protocol.blockshapes.BlockShapesPacket;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.Box;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.ClearShapes;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.RemoveShapes;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.SetShapes;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.Shape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

public final class ClientBlockShapes {
	private static final Map<String, Map<Long, Entry>> SHAPES = new ConcurrentHashMap<>();
	private static volatile boolean any;
	private static int count;
	private static volatile Level cachedLevel;
	private static volatile Map<Long, Entry> cachedShapes;

	public record Entry(VoxelShape collision, VoxelShape outline, boolean full) {
	}

	private ClientBlockShapes() {
	}

	public static void receive(byte[] data) {
		BlockShapesPacket packet;

		try {
			packet = BlockShapesCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed block_shapes packet: {}", e.getMessage());
			return;
		}

		Minecraft.getInstance().execute(() -> apply(packet));
	}

	private static void apply(BlockShapesPacket packet) {
		switch (packet) {
			case SetShapes set -> {
				Map<Long, Entry> map = SHAPES.computeIfAbsent(set.dimension(), key -> new ConcurrentHashMap<>());

				for (Shape shape : set.shapes()) {
					long key = BlockPos.asLong(shape.x(), shape.y(), shape.z());

					if (!map.containsKey(key) && count >= BlockShapesCodec.MAX_SHAPES) {
						SkaffySAPIClient.LOGGER.warn("The server sent more than {} block shapes, the rest are ignored", BlockShapesCodec.MAX_SHAPES);
						break;
					}

					VoxelShape collision = shape(shape.collision());
					Entry previous = map.put(key, new Entry(collision, shape.outline() == null ? collision : shape(shape.outline()), Block.isShapeFullBlock(collision)));

					if (previous == null) {
						count++;
					}
				}
			}
			case RemoveShapes remove -> {
				Map<Long, Entry> map = SHAPES.get(remove.dimension());

				if (map != null) {
					for (long[] position : remove.positions()) {
						if (map.remove(BlockPos.asLong((int) position[0], (int) position[1], (int) position[2])) != null) {
							count--;
						}
					}
				}
			}
			case ClearShapes clear -> {
				if (clear.dimension().isEmpty()) {
					SHAPES.clear();
				} else {
					SHAPES.remove(clear.dimension());
				}

				count = SHAPES.values().stream().mapToInt(Map::size).sum();
			}
		}

		any = count > 0;
		cachedLevel = null;
	}

	private static VoxelShape shape(java.util.List<Box> boxes) {
		VoxelShape shape = Shapes.empty();

		for (Box box : boxes) {
			shape = Shapes.or(shape, Shapes.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
		}

		return shape.optimize();
	}

	public static @Nullable Entry at(BlockGetter getter, BlockPos pos) {
		if (!any) {
			return null;
		}

		Level level = getter instanceof Level direct ? direct : getter instanceof LevelChunk chunk ? chunk.getLevel() : null;

		if (!(level instanceof ClientLevel)) {
			return null;
		}

		Map<Long, Entry> map;

		if (cachedLevel == level) {
			map = cachedShapes;
		} else {
			map = SHAPES.get(level.dimension().identifier().toString());
			cachedShapes = map;
			cachedLevel = level;
		}

		return map == null ? null : map.get(pos.asLong());
	}

	public static void unload() {
		Minecraft.getInstance().execute(() -> {
			SHAPES.clear();
			count = 0;
			any = false;
			cachedLevel = null;
		});
	}
}
