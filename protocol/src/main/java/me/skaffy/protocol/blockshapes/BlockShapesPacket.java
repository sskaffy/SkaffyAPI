package me.skaffy.protocol.blockshapes;

import java.util.List;

import me.skaffy.protocol.ProtocolException;

public sealed interface BlockShapesPacket {
	record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		public Box {
			for (float value : new float[] {minX, minY, minZ, maxX, maxY, maxZ}) {
				if (!(value >= 0 && value <= 1)) {
					throw new ProtocolException("Box coordinates must be 0 to 1, got " + value);
				}
			}

			if (minX > maxX || minY > maxY || minZ > maxZ) {
				throw new ProtocolException("A box's min is above its max");
			}
		}
	}

	record Shape(int x, int y, int z, List<Box> collision, List<Box> outline) {
		public Shape {
			collision = List.copyOf(collision);
			outline = outline == null ? null : List.copyOf(outline);

			if (collision.size() > BlockShapesCodec.MAX_BOXES || outline != null && outline.size() > BlockShapesCodec.MAX_BOXES) {
				throw new ProtocolException("At most " + BlockShapesCodec.MAX_BOXES + " boxes per block");
			}
		}
	}

	record SetShapes(String dimension, List<Shape> shapes) implements BlockShapesPacket {
		public SetShapes {
			BlockShapesCodec.checkDimension(dimension);
			shapes = List.copyOf(shapes);

			if (shapes.size() > BlockShapesCodec.MAX_PER_PACKET) {
				throw new ProtocolException("At most " + BlockShapesCodec.MAX_PER_PACKET + " shapes per packet");
			}
		}
	}

	record RemoveShapes(String dimension, List<long[]> positions) implements BlockShapesPacket {
		public RemoveShapes {
			BlockShapesCodec.checkDimension(dimension);
			positions = List.copyOf(positions);

			if (positions.size() > BlockShapesCodec.MAX_PER_PACKET) {
				throw new ProtocolException("At most " + BlockShapesCodec.MAX_PER_PACKET + " positions per packet");
			}
		}
	}

	record ClearShapes(String dimension) implements BlockShapesPacket {
		public ClearShapes {
			if (!dimension.isEmpty()) {
				BlockShapesCodec.checkDimension(dimension);
			}
		}
	}
}
