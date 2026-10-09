package me.skaffy.api.block;

public record BlockRotation(int x, int y) {
	public static final BlockRotation NONE = new BlockRotation(0, 0);

	public BlockRotation {
		if (x % 90 != 0 || y % 90 != 0) {
			throw new IllegalArgumentException("Rotations must be multiples of 90 degrees, got x=" + x + " y=" + y);
		}

		x = Math.floorMod(x, 360);
		y = Math.floorMod(y, 360);
	}

	public static BlockRotation y(int degrees) {
		return new BlockRotation(0, degrees);
	}
}
