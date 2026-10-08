package me.skaffy.protocol.shapes;

import java.util.List;

import me.skaffy.protocol.ProtocolException;

public sealed interface ShapesPacket {
	enum Mode {
		OVERLAY,
		WORLD
	}

	enum Render {
		SOLID,
		CUTOUT,
		TRANSLUCENT
	}

	record Placement(String dimension, double x, double y, double z, float qx, float qy, float qz, float qw, float sx, float sy, float sz) {
		public Placement {
			ShapesCodec.checkDimension(dimension);

			for (double value : new double[] {x, y, z, qx, qy, qz, qw, sx, sy, sz}) {
				if (!Double.isFinite(value)) {
					throw new ProtocolException("Placement values must be finite numbers");
				}
			}
		}
	}

	record Vertex(float x, float y, float z, float u, float v) {
	}

	sealed interface Part {
	}

	record Line(float ax, float ay, float az, float bx, float by, float bz, int argb, float width) implements Part {
	}

	record Triangle(Vertex a, Vertex b, Vertex c, int argb) implements Part {
	}

	record Quad(Vertex a, Vertex b, Vertex c, Vertex d, int argb) implements Part {
	}

	record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int fill, int outline, float outlineWidth, float uvScale) implements Part {
	}

	record Style(Mode mode, boolean seeThrough, String texture, Render render, boolean emissive, boolean doubleSided, float viewDistance) {
		public Style {
			if (texture.length() > ShapesCodec.MAX_TEXTURE) {
				throw new ProtocolException("Texture name is longer than " + ShapesCodec.MAX_TEXTURE);
			}
		}
	}

	record SetShape(String id, Placement placement, Style style, List<Part> parts) implements ShapesPacket {
		public SetShape {
			ShapesCodec.checkId(id);
			parts = List.copyOf(parts);

			if (parts.size() > ShapesCodec.MAX_PARTS) {
				throw new ProtocolException("At most " + ShapesCodec.MAX_PARTS + " parts per shape, got " + parts.size());
			}
		}
	}

	record MoveShape(String id, Placement placement) implements ShapesPacket {
		public MoveShape {
			ShapesCodec.checkId(id);
		}
	}

	record RemoveShape(String id) implements ShapesPacket {
		public RemoveShape {
			ShapesCodec.checkId(id);
		}
	}

	record ClearShapes(String prefix) implements ShapesPacket {
		public ClearShapes {
			if (prefix.length() > ShapesCodec.MAX_ID) {
				throw new ProtocolException("Prefix is longer than " + ShapesCodec.MAX_ID);
			}
		}
	}
}
