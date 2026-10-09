package me.skaffy.api.shape;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class ShapePlacement {
	private final World world;
	private final double x;
	private final double y;
	private final double z;
	private final Quaternionf rotation;
	private final Vector3f scale;

	private ShapePlacement(World world, double x, double y, double z, Quaternionfc rotation, Vector3fc scale) {
		this.world = Objects.requireNonNull(world, "world");
		this.x = x;
		this.y = y;
		this.z = z;
		this.rotation = new Quaternionf(rotation);
		this.scale = new Vector3f(scale);
	}

	public static ShapePlacement at(Location location) {
		return new ShapePlacement(location.getWorld(), location.getX(), location.getY(), location.getZ(), new Quaternionf(), new Vector3f(1));
	}

	public static ShapePlacement at(World world, double x, double y, double z) {
		return new ShapePlacement(world, x, y, z, new Quaternionf(), new Vector3f(1));
	}

	public ShapePlacement rotation(Quaternionfc rotation) {
		return new ShapePlacement(world, x, y, z, rotation, scale);
	}

	public ShapePlacement yaw(float degrees) {
		return rotation(new Quaternionf().rotationY((float) Math.toRadians(-degrees)));
	}

	public ShapePlacement scale(float scale) {
		return scale(new Vector3f(scale));
	}

	public ShapePlacement scale(Vector3fc scale) {
		return new ShapePlacement(world, x, y, z, rotation, scale);
	}

	public World getWorld() {
		return world;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public Quaternionfc getRotation() {
		return rotation;
	}

	public Vector3fc getScale() {
		return scale;
	}
}
