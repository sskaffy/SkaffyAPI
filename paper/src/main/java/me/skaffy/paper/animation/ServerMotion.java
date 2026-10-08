package me.skaffy.paper.animation;

import java.util.List;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.animations.AnimationsPacket.PathPoint;

final class ServerMotion {
	record Pose(double x, double y, double z, float yaw, float pitch, float roll, float scale) {
		Pose towards(Pose to, double t) {
			return new Pose(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t, yaw + wrap(to.yaw - yaw) * (float) t, (float) (pitch + (to.pitch - pitch) * t),
					(float) (roll + (to.roll - roll) * t), (float) (scale + (to.scale - scale) * t));
		}
	}

	record Move(Pose from, Pose to, long start, long duration, Easing easing) {
	}

	record Path(List<PathPoint> points, boolean smooth, boolean loop, boolean faceAlong, long start, float scale) {
	}

	private Pose pose;
	private Move move;
	private Path path;

	ServerMotion(Pose pose) {
		this.pose = pose;
	}

	synchronized void set(Pose pose) {
		this.pose = pose;
		move = null;
		path = null;
	}

	synchronized void moveTo(Pose target, int millis, Easing easing) {
		Pose from = at(System.nanoTime());
		path = null;

		if (millis <= 0) {
			pose = target;
			move = null;
		} else {
			move = new Move(from, target, System.nanoTime(), millis * 1_000_000L, easing);
		}
	}

	synchronized void follow(List<PathPoint> points, boolean smooth, boolean loop, boolean faceAlong, int startMillis) {
		Pose current = at(System.nanoTime());
		move = null;
		pose = current;
		path = new Path(List.copyOf(points), smooth, loop, faceAlong, System.nanoTime() - startMillis * 1_000_000L, current.scale);
	}

	synchronized Move move() {
		at(System.nanoTime());
		return move;
	}

	synchronized Path path() {
		return path;
	}

	synchronized Pose at(long now) {
		if (move != null) {
			double progress = (now - move.start) / (double) move.duration;

			if (progress >= 1) {
				pose = move.to;
				move = null;
			} else {
				return move.from.towards(move.to, move.easing.apply(Math.max(0, progress)));
			}
		}

		if (path != null) {
			return pathPose(path, (now - path.start) / 1_000_000.0);
		}

		return pose;
	}

	synchronized int pathElapsed(long now) {
		return path == null ? 0 : (int) Math.min(Integer.MAX_VALUE, (now - path.start) / 1_000_000);
	}

	private static Pose pathPose(Path path, double millis) {
		List<PathPoint> points = path.points;
		double last = points.getLast().time();

		if (path.loop && last > 0) {
			millis = ((millis % last) + last) % last;
		}

		double[] position = position(path, millis);
		float yaw = (float) position[3];
		float pitch = (float) position[4];

		if (path.faceAlong) {
			double[] ahead = position(path, millis + 50);
			double[] behind = position(path, millis - 50);
			double dx = ahead[0] - behind[0];
			double dy = ahead[1] - behind[1];
			double dz = ahead[2] - behind[2];
			double horizontal = Math.sqrt(dx * dx + dz * dz);

			if (horizontal > 1.0E-6 || Math.abs(dy) > 1.0E-6) {
				yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
			}
		}

		return new Pose(position[0], position[1], position[2], yaw, pitch, (float) position[5], path.scale);
	}

	private static double[] position(Path path, double millis) {
		List<PathPoint> points = path.points;
		int count = points.size();

		if (count == 1 || millis <= points.getFirst().time() && !path.loop) {
			return values(points.getFirst());
		}

		if (millis >= points.getLast().time()) {
			if (!path.loop) {
				return values(points.getLast());
			}

			millis = points.getLast().time();
		}

		int next = 1;

		while (next < count - 1 && points.get(next).time() <= millis) {
			next++;
		}

		int previous = next - 1;
		double gap = points.get(next).time() - points.get(previous).time();
		double t = gap <= 0 ? 1 : Math.clamp((millis - points.get(previous).time()) / gap, 0, 1);
		double[] from = values(points.get(previous));
		double[] to = values(points.get(next));
		double[] result = new double[6];

		if (path.smooth && count > 2) {
			double[] before = values(points.get(previous > 0 ? previous - 1 : path.loop ? count - 2 : previous));
			double[] after = values(points.get(next < count - 1 ? next + 1 : path.loop ? 1 : next));

			for (int i = 0; i < 3; i++) {
				double p0 = before[i];
				double p1 = from[i];
				double p2 = to[i];
				double p3 = after[i];
				result[i] = 0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t);
			}
		} else {
			for (int i = 0; i < 3; i++) {
				result[i] = from[i] + (to[i] - from[i]) * t;
			}
		}

		result[3] = from[3] + wrap((float) (to[3] - from[3])) * t;
		result[4] = from[4] + (to[4] - from[4]) * t;
		result[5] = from[5] + (to[5] - from[5]) * t;
		return result;
	}

	private static double[] values(PathPoint point) {
		return new double[] {point.x(), point.y(), point.z(), point.yaw(), point.pitch(), point.roll()};
	}

	static float wrap(float degrees) {
		float wrapped = degrees % 360;

		if (wrapped >= 180) {
			wrapped -= 360;
		}

		if (wrapped < -180) {
			wrapped += 360;
		}

		return wrapped;
	}
}
