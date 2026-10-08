package me.skaffy.api.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class Shape {
	public static final int MAX_PARTS = 16384;

	public enum Render {
		SOLID,
		CUTOUT,
		TRANSLUCENT
	}

	public record Vertex(double x, double y, double z, double u, double v) {
		public static Vertex of(double x, double y, double z) {
			return new Vertex(x, y, z, 0, 0);
		}

		public static Vertex of(double x, double y, double z, double u, double v) {
			return new Vertex(x, y, z, u, v);
		}

		public static Vertex of(Vector position, double u, double v) {
			return new Vertex(position.getX(), position.getY(), position.getZ(), u, v);
		}
	}

	public sealed interface Part {
	}

	public record Line(Vector from, Vector to, Color color, float width) implements Part {
	}

	public record Triangle(Vertex a, Vertex b, Vertex c, Color color) implements Part {
	}

	public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, Color color) implements Part {
	}

	public record Box(BoundingBox box, Color fill, Color outline, float outlineWidth, float uvScale) implements Part {
	}

	private final boolean world;
	private final boolean seeThrough;
	private final String texture;
	private final Render render;
	private final boolean emissive;
	private final boolean doubleSided;
	private final float viewDistance;
	private final List<Part> parts;

	private Shape(Builder builder) {
		this.world = builder.world;
		this.seeThrough = builder.seeThrough;
		this.texture = builder.texture;
		this.render = builder.render;
		this.emissive = builder.emissive;
		this.doubleSided = builder.doubleSided;
		this.viewDistance = builder.viewDistance;
		this.parts = List.copyOf(builder.parts);
	}

	public static Builder overlay() {
		return new Builder(false, "");
	}

	public static Builder world(String texture) {
		return new Builder(true, Objects.requireNonNull(texture, "texture"));
	}

	public boolean isWorld() {
		return world;
	}

	public boolean isSeeThrough() {
		return seeThrough;
	}

	public String getTexture() {
		return texture;
	}

	public Render getRender() {
		return render;
	}

	public boolean isEmissive() {
		return emissive;
	}

	public boolean isDoubleSided() {
		return doubleSided;
	}

	public float getViewDistance() {
		return viewDistance;
	}

	public List<Part> getParts() {
		return parts;
	}

	public static final class Builder {
		private final boolean world;
		private final String texture;
		private boolean seeThrough;
		private Render render = Render.SOLID;
		private boolean emissive;
		private boolean doubleSided;
		private float viewDistance;
		private final List<Part> parts = new ArrayList<>();

		private Builder(boolean world, String texture) {
			this.world = world;
			this.texture = texture;
		}

		public Builder seeThrough(boolean seeThrough) {
			this.seeThrough = seeThrough;
			return this;
		}

		public Builder render(Render render) {
			this.render = Objects.requireNonNull(render, "render");
			return this;
		}

		public Builder emissive(boolean emissive) {
			this.emissive = emissive;
			return this;
		}

		public Builder doubleSided(boolean doubleSided) {
			this.doubleSided = doubleSided;
			return this;
		}

		public Builder viewDistance(float blocks) {
			this.viewDistance = Math.max(0, blocks);
			return this;
		}

		public Builder line(Vector from, Vector to, Color color, float width) {
			return add(new Line(from.clone(), to.clone(), color, width));
		}

		public Builder triangle(Vertex a, Vertex b, Vertex c, Color color) {
			return add(new Triangle(a, b, c, color));
		}

		public Builder quad(Vertex a, Vertex b, Vertex c, Vertex d, Color color) {
			return add(new Quad(a, b, c, d, color));
		}

		public Builder box(BoundingBox box, Color fill, Color outline, float outlineWidth) {
			return add(new Box(box.clone(), fill, outline, outlineWidth, 1));
		}

		public Builder box(BoundingBox box, Color tint, float uvScale) {
			return add(new Box(box.clone(), tint, null, 0, uvScale));
		}

		public Builder add(Part part) {
			if (parts.size() >= MAX_PARTS) {
				throw new IllegalStateException("A shape has at most " + MAX_PARTS + " parts");
			}

			parts.add(Objects.requireNonNull(part, "part"));
			return this;
		}

		public Shape build() {
			return new Shape(this);
		}
	}
}
