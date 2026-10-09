package test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.skaffy.api.shape.Shape;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

final class BuildPiece {
	static final int TILE = 3;
	static final double HALF = 0.1;
	static final double DEPTH = 0.3;
	private static final int[] RESOLUTIONS = {8, 6, 4, 3};
	private static final int COLUMN_BOXES = 12;
	static final double CONE_HEIGHT = 1.5;
	static final double CONE_RAISED = 3;
	private static final Set<Set<Integer>> WALL_EDITS = wallEdits(new int[][] {
			{1, 2, 4, 5},
			{1, 2, 5},
			{2, 5},
			{1, 4},
			{5, 7, 8},
			{6, 7, 8},
			{3, 4, 5, 6, 7, 8},
			{0, 3, 4, 5, 6, 7, 8},
			{0, 1, 2, 4},
			{4},
			{5}});

	private static Set<Set<Integer>> wallEdits(int[][] edits) {
		Set<Set<Integer>> all = new HashSet<>();
		all.add(Set.of());

		for (int[] edit : edits) {
			Set<Integer> cells = new HashSet<>();
			Set<Integer> mirrored = new HashSet<>();

			for (int cell : edit) {
				cells.add(cell);
				mirrored.add(cell / 3 * 3 + 2 - cell % 3);
			}

			all.add(Set.copyOf(cells));
			all.add(Set.copyOf(mirrored));
		}

		return all;
	}

	enum Kind {
		WALL("Wall", 9),
		FLOOR("Floor", 4),
		RAMP("Ramp", 4),
		CONE("Cone", 4);

		final String label;
		final int cells;

		Kind(String label, int cells) {
			this.label = label;
			this.cells = cells;
		}
	}

	enum Material {
		WOOD("Wood", "minecraft:block/oak_planks", "minecraft:oak_planks", 150, 5),
		BRICK("Brick", "minecraft:block/bricks", "minecraft:bricks", 300, 12),
		METAL("Metal", "minecraft:block/iron_block", "minecraft:iron_block", 500, 22);

		final String label;
		final String texture;
		final String item;
		final int maxHp;
		final int buildSeconds;

		Material(String label, String texture, String item, int maxHp, int buildSeconds) {
			this.label = label;
			this.texture = texture;
			this.item = item;
			this.maxHp = maxHp;
			this.buildSeconds = buildSeconds;
		}
	}

	private record Slab(double x0, double z0, double x1, double z1, double h0, double h1, int[] dir) {
		double heightAt(double x, double z) {
			if (dir == null) {
				return h0;
			}

			double t = dir[0] > 0 ? (x - x0) / (x1 - x0) : dir[0] < 0 ? (x1 - x) / (x1 - x0) : dir[1] > 0 ? (z - z0) / (z1 - z0) : (z1 - z) / (z1 - z0);
			return h0 + (h1 - h0) * Math.clamp(t, 0, 1);
		}

		boolean contains(double x, double z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}
	}

	final String id;
	final Kind kind;
	final World world;
	final int tx;
	final int ty;
	final int tz;
	final int axis;
	final int facing;
	final UUID owner;
	Material material;
	double hp;
	boolean built;
	final boolean[] removed;
	final List<Integer> path = new ArrayList<>();
	int baseY;
	Map<Long, List<BoundingBox>> collisionCache;
	private List<Slab> slabs;
	private final List<Integer> slabsPath = new ArrayList<>();

	BuildPiece(String id, Kind kind, World world, int tx, int ty, int tz, int axis, int facing, UUID owner, Material material) {
		this.id = id;
		this.kind = kind;
		this.world = world;
		this.tx = tx;
		this.ty = ty;
		this.tz = tz;
		this.axis = axis;
		this.facing = facing;
		this.owner = owner;
		this.material = material;
		this.removed = new boolean[kind.cells];
		this.hp = material.maxHp * 0.1;
	}

	static String slot(Kind kind, int tx, int ty, int tz, int axis) {
		return kind + ":" + tx + "," + ty + "," + tz + (kind == Kind.WALL ? ":" + axis : "");
	}

	String slot() {
		return slot(kind, tx, ty, tz, axis);
	}

	double originX() {
		return tx * TILE;
	}

	double originY() {
		return baseY + ty * TILE;
	}

	double originZ() {
		return tz * TILE;
	}

	Vector center() {
		double x = kind == Kind.WALL && axis == 0 ? 0 : 1.5;
		double z = kind == Kind.WALL && axis == 2 ? 0 : 1.5;
		double y = kind == Kind.FLOOR ? 0 : 1.5;
		return new Vector(originX() + x, originY() + y, originZ() + z);
	}

	boolean validEdit(boolean[] cells) {
		Set<Integer> removedCells = new HashSet<>();

		for (int cell = 0; cell < cells.length; cell++) {
			if (cells[cell]) {
				removedCells.add(cell);
			}
		}

		return switch (kind) {
			case WALL -> WALL_EDITS.contains(removedCells);
			case FLOOR, CONE -> removedCells.size() < cells.length;
			case RAMP -> true;
		};
	}

	boolean edited() {
		for (boolean cell : removed) {
			if (cell) {
				return true;
			}
		}

		return !path.isEmpty();
	}


	double[] turn(double x, double z) {
		double dx = x - 1.5;
		double dz = z - 1.5;

		return switch (facing) {
			case 1 -> new double[] {1.5 - dz, 1.5 + dx};
			case 2 -> new double[] {1.5 - dx, 1.5 - dz};
			case 3 -> new double[] {1.5 + dz, 1.5 - dx};
			default -> new double[] {x, z};
		};
	}

	double[] unturn(double x, double z) {
		double dx = x - 1.5;
		double dz = z - 1.5;

		return switch (facing) {
			case 1 -> new double[] {1.5 + dz, 1.5 - dx};
			case 2 -> new double[] {1.5 - dx, 1.5 - dz};
			case 3 -> new double[] {1.5 - dz, 1.5 + dx};
			default -> new double[] {x, z};
		};
	}


	private List<Slab> rampSlabs() {
		if (slabs == null || !slabsPath.equals(path)) {
			slabs = slabs();
			slabsPath.clear();
			slabsPath.addAll(path);
		}

		return slabs;
	}

	private List<Slab> slabs() {
		if (path.size() < 2) {
			return List.of(new Slab(0, 0, 3, 3, 0, 3, new int[] {0, 1}));
		}

		List<Slab> slabs = new ArrayList<>();
		int sloped = 0;
		List<int[]> dirs = new ArrayList<>();

		for (int k = 0; k < path.size(); k++) {
			int[] in = k > 0 ? delta(path.get(k - 1), path.get(k)) : null;
			int[] out = k < path.size() - 1 ? delta(path.get(k), path.get(k + 1)) : null;
			int[] dir = in == null ? out : out == null ? in : in[0] == out[0] && in[1] == out[1] ? in : null;
			dirs.add(dir);

			if (dir != null) {
				sloped++;
			}
		}

		double rise = 3.0 / Math.max(1, sloped);
		double height = 0;

		for (int k = 0; k < path.size(); k++) {
			int cell = path.get(k);
			double x0 = (cell % 2) * 1.5;
			double z0 = (cell / 2) * 1.5;
			int[] dir = dirs.get(k);
			double top = dir == null ? height : height + rise;
			slabs.add(new Slab(x0, z0, x0 + 1.5, z0 + 1.5, height, top, dir));
			height = top;
		}

		return slabs;
	}

	List<double[][]> coneFaces() {
		double[][] corner = new double[4][];
		List<Integer> up = new ArrayList<>();
		List<Integer> down = new ArrayList<>();

		for (int i = 0; i < 4; i++) {
			corner[i] = new double[] {(i % 2) * 3.0, removed[i] ? CONE_RAISED : 0, (i / 2) * 3.0};
			(removed[i] ? up : down).add(i);
		}

		return switch (up.size()) {
			case 0 -> {
				double[] top = {1.5, CONE_HEIGHT, 1.5};
				yield List.of(new double[][] {corner[0], corner[1], top}, new double[][] {corner[1], corner[3], top}, new double[][] {corner[3], corner[2], top},
						new double[][] {corner[2], corner[0], top});
			}
			case 1 -> List.<double[][]>of(new double[][] {corner[up.get(0)], corner[up.get(0) ^ 1], corner[up.get(0) ^ 2]});
			case 2 -> {
				int a = up.get(0);
				int b = up.get(1);

				if ((a ^ b) == 3) {
					yield List.of(new double[][] {corner[a], corner[b], corner[down.get(0)]}, new double[][] {corner[a], corner[b], corner[down.get(1)]});
				}

				yield List.of(new double[][] {corner[0], corner[1], corner[3]}, new double[][] {corner[0], corner[3], corner[2]});
			}
			case 3 -> {
				int low = down.get(0);
				yield List.of(new double[][] {corner[low], corner[low ^ 1], corner[3 - low]}, new double[][] {corner[low], corner[low ^ 2], corner[3 - low]});
			}
			default -> List.of();
		};
	}

	private static double heightOn(double[][] face, double x, double z) {
		double[] a = face[0];
		double[] b = face[1];
		double[] c = face[2];
		double det = (b[2] - c[2]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[2] - c[2]);

		if (Math.abs(det) < 1e-12) {
			return Double.NaN;
		}

		double l1 = ((b[2] - c[2]) * (x - c[0]) + (c[0] - b[0]) * (z - c[2])) / det;
		double l2 = ((c[2] - a[2]) * (x - c[0]) + (a[0] - c[0]) * (z - c[2])) / det;
		double l3 = 1 - l1 - l2;
		double e = -1e-9;
		return l1 < e || l2 < e || l3 < e ? Double.NaN : l1 * a[1] + l2 * b[1] + l3 * c[1];
	}

	private static int[] delta(int from, int to) {
		return new int[] {(to % 2) - (from % 2), (to / 2) - (from / 2)};
	}

	double surface(double x, double z) {
		if (kind == Kind.CONE) {
			for (double[][] face : coneFaces()) {
				double height = heightOn(face, x, z);

				if (!Double.isNaN(height)) {
					return height;
				}
			}

			return Double.NaN;
		}

		double[] own = unturn(x, z);

		for (Slab slab : rampSlabs()) {
			if (slab.contains(own[0], own[1])) {
				return slab.heightAt(own[0], own[1]);
			}
		}

		return Double.NaN;
	}


	Shape shape(boolean preview, Color tint) {
		Shape.Builder builder = preview ? Shape.overlay() : Shape.world(material.texture).render(built ? Shape.Render.SOLID : Shape.Render.TRANSLUCENT);

		if (!preview && (kind == Kind.CONE || kind == Kind.RAMP)) {
			builder.doubleSided(true);
		}

		switch (kind) {
			case WALL -> {
				for (int row = 0; row < 3; row++) {
					for (int col = 0; col < 3; col++) {
						if (!removed[row * 3 + col]) {
							box(builder, preview, tint, wallCell(row, col));
						}
					}
				}
			}
			case FLOOR -> {
				for (int cell = 0; cell < 4; cell++) {
					if (!removed[cell]) {
						box(builder, preview, tint, floorCell(cell));
					}
				}
			}
			case RAMP -> {
				for (Slab slab : rampSlabs()) {
					slabQuads(builder, slab, tint);
				}
			}
			case CONE -> {
				for (double[][] face : coneFaces()) {
					triangle(builder, tint, face[0], face[1], face[2]);
				}
			}
		}

		return builder.build();
	}

	BoundingBox wallCell(int row, int col) {
		return axis == 0 ? new BoundingBox(-HALF, row, col, HALF, row + 1, col + 1) : new BoundingBox(col, row, -HALF, col + 1, row + 1, HALF);
	}

	BoundingBox floorCell(int cell) {
		double x = (cell % 2) * 1.5;
		double z = (cell / 2) * 1.5;
		return new BoundingBox(x, -HALF, z, x + 1.5, HALF, z + 1.5);
	}

	private static void box(Shape.Builder builder, boolean preview, Color tint, BoundingBox box) {
		if (preview) {
			builder.box(box, tint, Color.fromARGB(0xC0FFFFFF), 1.5f);
		} else {
			builder.box(box, tint, 1);
		}
	}

	private double[][] slabTop(Slab slab) {
		double[][] top = new double[4][];
		double[][] own = {{slab.x0(), slab.z0()}, {slab.x1(), slab.z0()}, {slab.x1(), slab.z1()}, {slab.x0(), slab.z1()}};

		for (int i = 0; i < 4; i++) {
			double[] at = turn(own[i][0], own[i][1]);
			top[i] = new double[] {at[0], slab.heightAt(own[i][0], own[i][1]), at[1]};
		}

		return top;
	}

	private void slabQuads(Shape.Builder builder, Slab slab, Color tint) {
		double[][] top = slabTop(slab);
		double thick = 2 * HALF;
		double[][] bottom = new double[4][];

		for (int i = 0; i < 4; i++) {
			bottom[i] = new double[] {top[i][0], top[i][1] - thick, top[i][2]};
		}

		face(builder, tint, top, 1);
		face(builder, tint, new double[][] {bottom[3], bottom[2], bottom[1], bottom[0]}, -1);

		for (int i = 0; i < 4; i++) {
			int j = (i + 1) % 4;
			face(builder, tint, new double[][] {top[j], top[i], bottom[i], bottom[j]}, 0);
		}
	}

	private static void face(Shape.Builder builder, Color tint, double[][] corners, int up) {
		Vector a = new Vector(corners[0][0], corners[0][1], corners[0][2]);
		Vector b = new Vector(corners[1][0], corners[1][1], corners[1][2]);
		Vector c = new Vector(corners[2][0], corners[2][1], corners[2][2]);
		Vector d = new Vector(corners[3][0], corners[3][1], corners[3][2]);
		Vector normal = b.clone().subtract(a).crossProduct(c.clone().subtract(a));

		if (normal.lengthSquared() < 1e-9) {
			return;
		}

		boolean flip = up > 0 && normal.getY() < 0 || up < 0 && normal.getY() > 0;

		if (flip) {
			Vector swap = b;
			b = d;
			d = swap;
		}

		double width = a.distance(b);
		double height = a.distance(d);
		builder.quad(Shape.Vertex.of(a, 0, height), Shape.Vertex.of(b, width, height), Shape.Vertex.of(c, width, 0), Shape.Vertex.of(d, 0, 0), tint);
	}

	private static void triangle(Shape.Builder builder, Color tint, double[] ta, double[] tb, double[] tc) {
		Vector a = vector(ta);
		Vector b = vector(tb);
		Vector c = vector(tc);
		Vector normal = b.clone().subtract(a).crossProduct(c.clone().subtract(a));

		if (normal.lengthSquared() < 1e-12) {
			return;
		}

		if (normal.getY() < 0) {
			Vector swap = b;
			b = c;
			c = swap;
			normal.multiply(-1);
		}

		normal.normalize();
		double flat = Math.hypot(normal.getX(), normal.getZ());
		double upX = flat < 1e-6 ? 0 : -normal.getX() / flat;
		double upZ = flat < 1e-6 ? 1 : -normal.getZ() / flat;
		double stretch = flat < 1e-6 ? 1 : 1 / normal.getY();
		Vector[] points = {a, b, c};
		Shape.Vertex[] vertices = new Shape.Vertex[3];

		for (int i = 0; i < 3; i++) {
			Vector p = points[i];
			double u = p.getX() * -upZ + p.getZ() * upX;
			double v = -(p.getX() * upX + p.getZ() * upZ) * stretch;
			vertices[i] = Shape.Vertex.of(p, u, v);
		}

		builder.triangle(vertices[0], vertices[1], vertices[2], tint);
	}


	Map<Long, List<BoundingBox>> collision() {
		Map<Long, List<BoundingBox>> blocks = new HashMap<>();
		double ox = originX();
		double oy = originY();
		double oz = originZ();

		switch (kind) {
			case WALL -> {
				for (int row = 0; row < 3; row++) {
					for (int col = 0; col < 3; col++) {
						if (!removed[row * 3 + col]) {
							addWorldBox(blocks, wallCell(row, col).shift(ox, oy, oz));
						}
					}
				}
			}
			case FLOOR -> {
				for (int cell = 0; cell < 4; cell++) {
					if (!removed[cell]) {
						addWorldBox(blocks, floorCell(cell).shift(ox, oy, oz));
					}
				}
			}
			case RAMP, CONE -> {
				for (int bx = 0; bx < 3; bx++) {
					for (int bz = 0; bz < 3; bz++) {
						slopeColumn(blocks, bx, bz);
					}
				}
			}
		}

		return blocks;
	}

	private void slopeColumn(Map<Long, List<BoundingBox>> blocks, int bx, int bz) {
		double ox = originX();
		double oy = originY();
		double oz = originZ();

		for (int r = 0; r < RESOLUTIONS.length; r++) {
			int res = RESOLUTIONS[r];
			double step = 1.0 / res;
			double[][] heights = new double[res][res];

			for (int i = 0; i < res; i++) {
				for (int j = 0; j < res; j++) {
					heights[i][j] = stepTop(bx + i * step, bz + j * step, step);
				}
			}

			Map<Long, List<BoundingBox>> column = new HashMap<>();
			boolean[][] used = new boolean[res][res];

			for (int j = 0; j < res; j++) {
				for (int i = 0; i < res; i++) {
					double h = heights[i][j];

					if (used[i][j] || Double.isNaN(h)) {
						continue;
					}

					int i1 = i + 1;

					while (i1 < res && !used[i1][j] && heights[i1][j] == h) {
						i1++;
					}

					int j1 = j + 1;

					grow:
					while (j1 < res) {
						for (int k = i; k < i1; k++) {
							if (used[k][j1] || heights[k][j1] != h) {
								break grow;
							}
						}

						j1++;
					}

					for (int k = i; k < i1; k++) {
						for (int l = j; l < j1; l++) {
							used[k][l] = true;
						}
					}

					addWorldBox(column, new BoundingBox(ox + bx + i * step, oy + h - DEPTH, oz + bz + j * step, ox + bx + i1 * step, oy + h, oz + bz + j1 * step));
				}
			}

			int most = column.values().stream().mapToInt(List::size).max().orElse(0);

			if (most <= COLUMN_BOXES || r == RESOLUTIONS.length - 1) {
				column.forEach((key, boxes) -> blocks.computeIfAbsent(key, k -> new ArrayList<>()).addAll(boxes));
				return;
			}
		}
	}

	private double stepTop(double x, double z, double size) {
		double top = surface(x + size / 2, z + size / 2);

		if (Double.isNaN(top)) {
			return Double.NaN;
		}

		double in = 1e-6;

		for (double[] corner : new double[][] {{x + in, z + in}, {x + size - in, z + in}, {x + in, z + size - in}, {x + size - in, z + size - in}}) {
			double height = surface(corner[0], corner[1]);

			if (!Double.isNaN(height)) {
				top = Math.max(top, height);
			}
		}

		return Math.round(top * 1e6) / 1e6;
	}

	static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
	}

	static int[] unkey(long key) {
		int x = (int) (key >> 38);
		int y = (int) (key << 52 >> 52);
		int z = (int) (key << 26 >> 38);
		return new int[] {x, y, z};
	}

	private static void addWorldBox(Map<Long, List<BoundingBox>> blocks, BoundingBox box) {
		for (int x = (int) Math.floor(box.getMinX()); x < Math.ceil(box.getMaxX()); x++) {
			for (int y = (int) Math.floor(box.getMinY()); y < Math.ceil(box.getMaxY()); y++) {
				for (int z = (int) Math.floor(box.getMinZ()); z < Math.ceil(box.getMaxZ()); z++) {
					BoundingBox part = box.clone().intersection(new BoundingBox(x, y, z, x + 1, y + 1, z + 1));

					if (part.getVolume() > 1e-6) {
						blocks.computeIfAbsent(key(x, y, z), k -> new ArrayList<>()).add(part.shift(-x, -y, -z));
					}
				}
			}
		}
	}


	double[] hit(Vector from, Vector direction, double reach, boolean grid) {
		double best = reach;
		int bestCell = -1;
		Vector local = from.clone().subtract(new Vector(originX(), originY(), originZ()));

		if (kind == Kind.CONE && !grid) {
			for (double[][] face : coneFaces()) {
				best = Math.min(best, triangleHit(local, direction, face[0], face[1], face[2]));
			}

			return best < reach ? new double[] {best, 0} : null;
		}

		if (kind == Kind.RAMP && !grid) {
			for (Slab slab : rampSlabs()) {
				double[][] top = slabTop(slab);
				double distance = quadHit(local, direction, top[0], top[1], top[2], top[3]);

				if (distance < best) {
					best = distance;
					bestCell = 0;
				}
			}

			return bestCell < 0 ? null : new double[] {best, bestCell};
		}

		for (int cell = 0; cell < kind.cells; cell++) {
			if (!grid && removed[cell]) {
				continue;
			}

			double distance = switch (kind) {
				case WALL -> boxHit(local, direction, wallCell(cell / 3, cell % 3), best);
				case FLOOR -> boxHit(local, direction, floorCell(cell), best);
				case RAMP -> {
					double[][] quad = rampCell(cell, 0, 0);
					yield quadHit(local, direction, quad[0], quad[1], quad[2], quad[3]);
				}
				case CONE -> {
					double closest = Double.MAX_VALUE;

					for (double[][] triangle : coneQuarter(cell)) {
						closest = Math.min(closest, triangleHit(local, direction, triangle[0], triangle[1], triangle[2]));
					}

					yield closest;
				}
			};

			if (distance < best) {
				best = distance;
				bestCell = cell;
			}
		}

		return bestCell < 0 ? null : new double[] {best, bestCell};
	}

	double[][] rampCell(int cell, double inset, double lift) {
		double x0 = (cell % 2) * 1.5 + inset;
		double z0 = (cell / 2) * 1.5 + inset;
		double x1 = x0 + 1.5 - 2 * inset;
		double z1 = z0 + 1.5 - 2 * inset;
		double[][] own = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
		double[][] corners = new double[4][];

		for (int i = 0; i < 4; i++) {
			double[] at = turn(own[i][0], own[i][1]);
			corners[i] = new double[] {at[0], own[i][1] + lift, at[1]};
		}

		return corners;
	}

	static double[][][] coneQuarter(int cell) {
		double[][] corners = {{0, 0}, {3, 0}, {0, 3}, {3, 3}};
		double[] corner = corners[cell];
		double[] top = {1.5, 1.5, 1.5};
		double[] start = {corner[0], 0, corner[1]};
		return new double[][][] {{start, {1.5, 0, corner[1]}, top}, {start, {corner[0], 0, 1.5}, top}};
	}

	private static double boxHit(Vector from, Vector direction, BoundingBox box, double reach) {
		org.bukkit.util.RayTraceResult result = box.rayTrace(from, direction, reach);
		return result == null ? Double.MAX_VALUE : result.getHitPosition().distance(from);
	}

	private static double quadHit(Vector from, Vector direction, double[] a, double[] b, double[] c, double[] d) {
		return Math.min(triangleHit(from, direction, a, b, c), triangleHit(from, direction, a, c, d));
	}

	static double triangleHit(Vector from, Vector direction, double[] a, double[] b, double[] c) {
		Vector e1 = new Vector(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
		Vector e2 = new Vector(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
		Vector p = direction.clone().crossProduct(e2);
		double det = e1.dot(p);

		if (Math.abs(det) < 1e-12) {
			return Double.MAX_VALUE;
		}

		double inv = 1 / det;
		Vector s = from.clone().subtract(new Vector(a[0], a[1], a[2]));
		double u = s.dot(p) * inv;

		if (u < 0 || u > 1) {
			return Double.MAX_VALUE;
		}

		Vector q = s.clone().crossProduct(e1);
		double v = direction.dot(q) * inv;

		if (v < 0 || u + v > 1) {
			return Double.MAX_VALUE;
		}

		double t = e2.dot(q) * inv;
		return t >= 0 ? t : Double.MAX_VALUE;
	}


	void gridCell(Shape.Builder builder, int cell, Color fill, Color outline, float width) {
		switch (kind) {
			case WALL -> builder.box(wallCell(cell / 3, cell % 3).expand(axis == 0 ? 0.04 : -0.04, -0.04, axis == 2 ? 0.04 : -0.04), fill, outline, width);
			case FLOOR -> builder.box(floorCell(cell).expand(-0.04, 0.04, -0.04), fill, outline, width);
			case RAMP -> {
				for (double lift : new double[] {0.05, -2 * HALF - 0.05}) {
					double[][] quad = rampCell(cell, 0.06, lift);
					builder.quad(vertex(quad[0]), vertex(quad[1]), vertex(quad[2]), vertex(quad[3]), fill);

					for (int i = 0; i < 4; i++) {
						builder.line(vector(quad[i]), vector(quad[(i + 1) % 4]), outline, width);
					}
				}
			}
			case CONE -> {
				double[][][] quarter = coneQuarter(cell);

				for (double side : new double[] {0.04, -0.04}) {
					List<double[]> outer = new ArrayList<>();

					for (double[][] triangle : quarter) {
						Vector normal = vector(triangle[1]).subtract(vector(triangle[0])).crossProduct(vector(triangle[2]).subtract(vector(triangle[0]))).normalize();

						if (normal.getY() < 0) {
							normal.multiply(-1);
						}

						double[][] moved = new double[3][];

						for (int i = 0; i < 3; i++) {
							moved[i] = new double[] {triangle[i][0] + normal.getX() * side, triangle[i][1] + normal.getY() * side, triangle[i][2] + normal.getZ() * side};
						}

						builder.triangle(vertex(moved[0]), vertex(moved[1]), vertex(moved[2]), fill);
						outer.add(moved[0]);
						outer.add(moved[1]);
						outer.add(moved[2]);
					}

					builder.line(vector(outer.get(0)), vector(outer.get(1)), outline, width);
					builder.line(vector(outer.get(1)), vector(outer.get(2)), outline, width);
					builder.line(vector(outer.get(5)), vector(outer.get(4)), outline, width);
					builder.line(vector(outer.get(4)), vector(outer.get(3)), outline, width);
				}
			}
		}
	}

	private static Shape.Vertex vertex(double[] at) {
		return Shape.Vertex.of(at[0], at[1], at[2]);
	}

	private static Vector vector(double[] at) {
		return new Vector(at[0], at[1], at[2]);
	}
}
