package me.skaffy.protocol.blockshapes;

import java.util.ArrayList;
import java.util.List;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.Box;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.ClearShapes;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.RemoveShapes;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.SetShapes;
import me.skaffy.protocol.blockshapes.BlockShapesPacket.Shape;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class BlockShapesCodec {
	public static final String FEATURE = "block_shapes";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int SET_SHAPES = 0;
	public static final int REMOVE_SHAPES = 1;
	public static final int CLEAR_SHAPES = 2;

	public static final int MAX_BOXES = 16;
	public static final int MAX_PER_PACKET = 8192;
	public static final int MAX_SHAPES = 262_144;
	public static final int MAX_DIMENSION = 128;

	private BlockShapesCodec() {
	}

	static void checkDimension(String dimension) {
		if (dimension.isEmpty() || dimension.length() > MAX_DIMENSION || !dimension.contains(":")) {
			throw new ProtocolException("Invalid dimension " + dimension + " (like minecraft:overworld)");
		}
	}

	public static byte[] encode(BlockShapesPacket packet) {
		PacketWriter writer = new PacketWriter(64);

		switch (packet) {
			case SetShapes set -> {
				writer.writeVarInt(SET_SHAPES).writeString(set.dimension(), MAX_DIMENSION).writeVarInt(set.shapes().size());

				for (Shape shape : set.shapes()) {
					writer.writeInt(shape.x()).writeInt(shape.y()).writeInt(shape.z());
					writeBoxes(writer, shape.collision());
					writer.writeBoolean(shape.outline() != null);

					if (shape.outline() != null) {
						writeBoxes(writer, shape.outline());
					}
				}
			}
			case RemoveShapes remove -> {
				writer.writeVarInt(REMOVE_SHAPES).writeString(remove.dimension(), MAX_DIMENSION).writeVarInt(remove.positions().size());

				for (long[] position : remove.positions()) {
					writer.writeInt((int) position[0]).writeInt((int) position[1]).writeInt((int) position[2]);
				}
			}
			case ClearShapes clear -> writer.writeVarInt(CLEAR_SHAPES).writeString(clear.dimension(), MAX_DIMENSION);
		}

		if (writer.size() > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + Protocol.MAX_CLIENTBOUND_PAYLOAD);
		}

		return writer.toByteArray();
	}

	private static void writeBoxes(PacketWriter writer, List<Box> boxes) {
		writer.writeVarInt(boxes.size());

		for (Box box : boxes) {
			writer.writeFloat(box.minX()).writeFloat(box.minY()).writeFloat(box.minZ()).writeFloat(box.maxX()).writeFloat(box.maxY()).writeFloat(box.maxZ());
		}
	}

	private static List<Box> readBoxes(PacketReader reader) {
		int count = reader.readVarInt(0, MAX_BOXES);
		List<Box> boxes = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			boxes.add(new Box(reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat()));
		}

		return boxes;
	}

	public static BlockShapesPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();
		BlockShapesPacket packet = switch (id) {
			case SET_SHAPES -> {
				String dimension = reader.readString(MAX_DIMENSION);
				int count = reader.readVarInt(0, MAX_PER_PACKET);
				List<Shape> shapes = new ArrayList<>(count);

				for (int i = 0; i < count; i++) {
					int x = reader.readInt();
					int y = reader.readInt();
					int z = reader.readInt();
					List<Box> collision = readBoxes(reader);
					List<Box> outline = reader.readBoolean() ? readBoxes(reader) : null;
					shapes.add(new Shape(x, y, z, collision, outline));
				}

				yield new SetShapes(dimension, shapes);
			}
			case REMOVE_SHAPES -> {
				String dimension = reader.readString(MAX_DIMENSION);
				int count = reader.readVarInt(0, MAX_PER_PACKET);
				List<long[]> positions = new ArrayList<>(count);

				for (int i = 0; i < count; i++) {
					positions.add(new long[] {reader.readInt(), reader.readInt(), reader.readInt()});
				}

				yield new RemoveShapes(dimension, positions);
			}
			case CLEAR_SHAPES -> new ClearShapes(reader.readString(MAX_DIMENSION));
			default -> throw new ProtocolException("Unknown clientbound block_shapes packet " + id);
		};

		reader.expectEnd();
		return packet;
	}
}
