package me.skaffy.api.block;

import java.util.Objects;

public final class CustomBlockType {
	public enum Transparency {
		SOLID,
		CUTOUT,
		TRANSLUCENT
	}

	public enum Tool {
		NONE,
		PICKAXE,
		AXE,
		SHOVEL,
		HOE,
		SWORD
	}

	private final String name;
	private final String model;
	private final String collision;
	private final String hitbox;
	private final Transparency transparency;
	private final int light;
	private final float hardness;
	private final Tool tool;
	private final boolean requiresTool;
	private final boolean removeWhenReplaced;
	private final boolean rotatable;

	private CustomBlockType(Builder builder) {
		this.name = builder.name;
		this.model = builder.model;
		this.collision = builder.collision;
		this.hitbox = builder.hitbox;
		this.transparency = builder.transparency;
		this.light = builder.light;
		this.hardness = builder.hardness;
		this.tool = builder.tool;
		this.requiresTool = builder.requiresTool;
		this.removeWhenReplaced = builder.removeWhenReplaced;
		this.rotatable = builder.rotatable;
	}

	public static Builder builder(String name, String model) {
		return new Builder(name, model);
	}

	public String getName() {
		return name;
	}

	public String getModel() {
		return model;
	}

	public String getCollision() {
		return collision;
	}

	public String getHitbox() {
		return hitbox;
	}

	public Transparency getTransparency() {
		return transparency;
	}

	public int getLight() {
		return light;
	}

	public float getHardness() {
		return hardness;
	}

	public Tool getTool() {
		return tool;
	}

	public boolean requiresTool() {
		return requiresTool;
	}

	public boolean removeWhenReplaced() {
		return removeWhenReplaced;
	}

	public boolean isRotatable() {
		return rotatable;
	}

	@Override
	public String toString() {
		return "CustomBlockType[" + name + "]";
	}

	public static final class Builder {
		private final String name;
		private final String model;
		private String collision;
		private String hitbox;
		private Transparency transparency = Transparency.SOLID;
		private int light;
		private float hardness = 1.5f;
		private Tool tool = Tool.NONE;
		private boolean requiresTool;
		private boolean removeWhenReplaced = true;
		private boolean rotatable = true;

		private Builder(String name, String model) {
			this.name = Objects.requireNonNull(name, "name");
			this.model = Objects.requireNonNull(model, "model");
			this.collision = model;
		}

		public Builder collision(String collision) {
			this.collision = Objects.requireNonNull(collision, "collision");
			return this;
		}

		public Builder noCollision() {
			this.collision = null;
			return this;
		}

		public Builder hitbox(String hitbox) {
			this.hitbox = hitbox;
			return this;
		}

		public Builder transparency(Transparency transparency) {
			this.transparency = Objects.requireNonNull(transparency, "transparency");
			return this;
		}

		public Builder light(int light) {
			this.light = light;
			return this;
		}

		public Builder hardness(float hardness) {
			this.hardness = hardness;
			return this;
		}

		public Builder unbreakable() {
			this.hardness = -1;
			return this;
		}

		public Builder tool(Tool tool) {
			this.tool = Objects.requireNonNull(tool, "tool");
			return this;
		}

		public Builder requiresTool(boolean requiresTool) {
			this.requiresTool = requiresTool;
			return this;
		}

		public Builder removeWhenReplaced(boolean removeWhenReplaced) {
			this.removeWhenReplaced = removeWhenReplaced;
			return this;
		}

		public Builder rotatable(boolean rotatable) {
			this.rotatable = rotatable;
			return this;
		}

		public CustomBlockType build() {
			if (name.isEmpty() || name.length() > 64) {
				throw new IllegalArgumentException("Block name must be 1 to 64 characters: " + name);
			}

			if (light < 0 || light > 15) {
				throw new IllegalArgumentException("Light must be 0 to 15, got " + light);
			}

			if (collision == null && hitbox == null) {
				throw new IllegalArgumentException("Block " + name + " has no collision, so it needs a hitbox model");
			}

			return new CustomBlockType(this);
		}
	}
}
