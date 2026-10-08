package me.skaffy.protocol.blocks;

public final class Positions {
	private Positions() {
	}

	public static long block(int x, int y, int z) {
		return (x & 0x3FFFFFFL) << 38 | (z & 0x3FFFFFFL) << 12 | y & 0xFFFL;
	}

	public static int blockX(long packed) {
		return (int) (packed >> 38);
	}

	public static int blockY(long packed) {
		return (int) (packed << 52 >> 52);
	}

	public static int blockZ(long packed) {
		return (int) (packed << 26 >> 38);
	}

	public static long section(int x, int y, int z) {
		return (x & 0x3FFFFFL) << 42 | y & 0xFFFFFL | (z & 0x3FFFFFL) << 20;
	}

	public static int sectionX(long packed) {
		return (int) (packed >> 42);
	}

	public static int sectionY(long packed) {
		return (int) (packed << 44 >> 44);
	}

	public static int sectionZ(long packed) {
		return (int) (packed << 22 >> 42);
	}
}
