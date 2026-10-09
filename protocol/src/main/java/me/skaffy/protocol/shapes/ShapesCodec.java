package me.skaffy.protocol.shapes;

import java.util.ArrayList;
import java.util.List;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.shapes.ShapesPacket.Box;
import me.skaffy.protocol.shapes.ShapesPacket.ClearShapes;
import me.skaffy.protocol.shapes.ShapesPacket.Line;
import me.skaffy.protocol.shapes.ShapesPacket.Mode;
import me.skaffy.protocol.shapes.ShapesPacket.MoveShape;
import me.skaffy.protocol.shapes.ShapesPacket.Part;
import me.skaffy.protocol.shapes.ShapesPacket.Placement;
import me.skaffy.protocol.shapes.ShapesPacket.Quad;
import me.skaffy.protocol.shapes.ShapesPacket.RemoveShape;
import me.skaffy.protocol.shapes.ShapesPacket.Render;
import me.skaffy.protocol.shapes.ShapesPacket.SetShape;
import me.skaffy.protocol.shapes.ShapesPacket.Style;
import me.skaffy.protocol.shapes.ShapesPacket.Triangle;
import me.skaffy.protocol.shapes.ShapesPacket.Vertex;

public final class ShapesCodec {
	public static final String FEATURE = "shapes";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int SET_SHAPE = 0;
	public static final int MOVE_SHAPE = 1;
	public static final int REMOVE_SHAPE = 2;
	public static final int CLEAR_SHAPES = 3;

	public static final int LINE = 0;
	public static final int TRIANGLE = 1;
	public static final int QUAD = 2;
	public static final int BOX = 3;

	public static final int MAX_ID = 128;
	public static final int MAX_TEXTURE = 256;
	public static final int MAX_DIMENSION = 128;
	public static final int MAX_PARTS = 16384;
	public static final int MAX_SHAPES = 65536;

	private ShapesCodec() {
	}

	static void checkId(String id) {
		if (id.isEmpty() || id.length() > MAX_ID) {
			throw new ProtocolException("Shape ids must be 1 to " + MAX_ID + " characters");
		}
	}

	static void checkDimension(String dimension) {
		if (dimension.isEmpty() || dimension.length() > MAX_DIMENSION || !dimension.contains(":")) {
			throw new ProtocolException("Invalid dimension " + dimension + " (like minecraft:overworld)");
		}
	}

	public static byte[] encode(ShapesPacket packet) {
		PacketWriter writer = new PacketWriter(256);

		switch (packet) {
			case SetShape set -> {
				writer.writeVarInt(SET_SHAPE).writeString(set.id(), MAX_ID);
				writePlacement(writer, set.placement());
				Style style = set.style();
				writer.writeByte(style.mode().ordinal()).writeBoolean(style.seeThrough()).writeString(style.texture(), MAX_TEXTURE).writeByte(style.render().ordinal())
						.writeBoolean(style.emissive()).writeBoolean(style.doubleSided()).writeFloat(style.viewDistance());
				writer.writeVarInt(set.parts().size());

				for (Part part : set.parts()) {
					writePart(writer, part);
				}
			}
			case MoveShape move -> {
				writer.writeVarInt(MOVE_SHAPE).writeString(move.id(), MAX_ID);
				writePlacement(writer, move.placement());
			}
			case RemoveShape remove -> writer.writeVarInt(REMOVE_SHAPE).writeString(remove.id(), MAX_ID);
			case ClearShapes clear -> writer.writeVarInt(CLEAR_SHAPES).writeString(clear.prefix(), MAX_ID);
		}

		if (writer.size() > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + Protocol.MAX_CLIENTBOUND_PAYLOAD + " (use fewer parts)");
		}

		return writer.toByteArray();
	}

	private static void writePlacement(PacketWriter writer, Placement placement) {
		writer.writeString(placement.dimension(), MAX_DIMENSION).writeDouble(placement.x()).writeDouble(placement.y()).writeDouble(placement.z())
				.writeFloat(placement.qx()).writeFloat(placement.qy()).writeFloat(placement.qz()).writeFloat(placement.qw())
				.writeFloat(placement.sx()).writeFloat(placement.sy()).writeFloat(placement.sz());
	}

	private static Placement readPlacement(PacketReader reader) {
		return new Placement(reader.readString(MAX_DIMENSION), reader.readDouble(), reader.readDouble(), reader.readDouble(),
				reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat());
	}

	private static void writeVertex(PacketWriter writer, Vertex vertex) {
		writer.writeFloat(vertex.x()).writeFloat(vertex.y()).writeFloat(vertex.z()).writeFloat(vertex.u()).writeFloat(vertex.v());
	}

	private static Vertex readVertex(PacketReader reader) {
		return new Vertex(reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat());
	}

	private static void writePart(PacketWriter writer, Part part) {
		switch (part) {
			case Line line -> writer.writeByte(LINE).writeFloat(line.ax()).writeFloat(line.ay()).writeFloat(line.az()).writeFloat(line.bx()).writeFloat(line.by()).writeFloat(line.bz())
					.writeInt(line.argb()).writeFloat(line.width());
			case Triangle triangle -> {
				writer.writeByte(TRIANGLE);
				writeVertex(writer, triangle.a());
				writeVertex(writer, triangle.b());
				writeVertex(writer, triangle.c());
				writer.writeInt(triangle.argb());
			}
			case Quad quad -> {
				writer.writeByte(QUAD);
				writeVertex(writer, quad.a());
				writeVertex(writer, quad.b());
				writeVertex(writer, quad.c());
				writeVertex(writer, quad.d());
				writer.writeInt(quad.argb());
			}
			case Box box -> writer.writeByte(BOX).writeFloat(box.minX()).writeFloat(box.minY()).writeFloat(box.minZ()).writeFloat(box.maxX()).writeFloat(box.maxY()).writeFloat(box.maxZ())
					.writeInt(box.fill()).writeInt(box.outline()).writeFloat(box.outlineWidth()).writeFloat(box.uvScale());
		}
	}

	private static Part readPart(PacketReader reader) {
		int type = reader.readUnsignedByte();
		return switch (type) {
			case LINE -> new Line(reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readInt(), reader.readFloat());
			case TRIANGLE -> new Triangle(readVertex(reader), readVertex(reader), readVertex(reader), reader.readInt());
			case QUAD -> new Quad(readVertex(reader), readVertex(reader), readVertex(reader), readVertex(reader), reader.readInt());
			case BOX -> new Box(reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readFloat(), reader.readInt(), reader.readInt(),
					reader.readFloat(), reader.readFloat());
			default -> throw new ProtocolException("Unknown shape part " + type);
		};
	}

	public static ShapesPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();
		ShapesPacket packet = switch (id) {
			case SET_SHAPE -> {
				String shapeId = reader.readString(MAX_ID);
				Placement placement = readPlacement(reader);
				Style style = new Style(reader.readEnum(Mode.values(), "shape mode"), reader.readBoolean(), reader.readString(MAX_TEXTURE), reader.readEnum(Render.values(), "shape render"),
						reader.readBoolean(), reader.readBoolean(), reader.readFloat());
				int count = reader.readVarInt(0, MAX_PARTS);
				List<Part> parts = new ArrayList<>(count);

				for (int i = 0; i < count; i++) {
					parts.add(readPart(reader));
				}

				yield new SetShape(shapeId, placement, style, parts);
			}
			case MOVE_SHAPE -> new MoveShape(reader.readString(MAX_ID), readPlacement(reader));
			case REMOVE_SHAPE -> new RemoveShape(reader.readString(MAX_ID));
			case CLEAR_SHAPES -> new ClearShapes(reader.readString(MAX_ID));
			default -> throw new ProtocolException("Unknown clientbound shapes packet " + id);
		};

		reader.expectEnd();
		return packet;
	}
}
