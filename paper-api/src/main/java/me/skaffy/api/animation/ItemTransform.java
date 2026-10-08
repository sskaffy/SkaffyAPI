package me.skaffy.api.animation;

import org.bukkit.util.Vector;

public record ItemTransform(Vector offset, Vector rotation, float scale) {
	public static final ItemTransform NONE = new ItemTransform(new Vector(), new Vector(), 1);

	public ItemTransform {
		offset = offset == null ? new Vector() : offset.clone();
		rotation = rotation == null ? new Vector() : rotation.clone();

		if (!(scale > 0) || !Float.isFinite(scale)) {
			throw new IllegalArgumentException("Scale must be above 0");
		}
	}

	public ItemTransform offset(double x, double y, double z) {
		return new ItemTransform(new Vector(x, y, z), rotation, scale);
	}

	public ItemTransform rotation(double x, double y, double z) {
		return new ItemTransform(offset, new Vector(x, y, z), scale);
	}

	public ItemTransform scale(float scale) {
		return new ItemTransform(offset, rotation, scale);
	}
}
