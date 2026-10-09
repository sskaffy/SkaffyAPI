package me.skaffy.api.animation;

import org.bukkit.Color;
import org.bukkit.util.Vector;

public record BoneSettings(boolean hidden, Color tint, Vector rotation, Vector position, Vector scale) {
	public static final BoneSettings NONE = new BoneSettings(false, null, new Vector(), new Vector(), new Vector(1, 1, 1));

	public BoneSettings {
		rotation = rotation == null ? new Vector() : rotation.clone();
		position = position == null ? new Vector() : position.clone();
		scale = scale == null ? new Vector(1, 1, 1) : scale.clone();
	}

	public BoneSettings hidden(boolean hidden) {
		return new BoneSettings(hidden, tint, rotation, position, scale);
	}

	public BoneSettings tint(Color tint) {
		return new BoneSettings(hidden, tint, rotation, position, scale);
	}

	public BoneSettings rotation(double x, double y, double z) {
		return new BoneSettings(hidden, tint, new Vector(x, y, z), position, scale);
	}

	public BoneSettings position(double x, double y, double z) {
		return new BoneSettings(hidden, tint, rotation, new Vector(x, y, z), scale);
	}

	public BoneSettings scale(double x, double y, double z) {
		return new BoneSettings(hidden, tint, rotation, position, new Vector(x, y, z));
	}

	public BoneSettings scale(double scale) {
		return scale(scale, scale, scale);
	}
}
