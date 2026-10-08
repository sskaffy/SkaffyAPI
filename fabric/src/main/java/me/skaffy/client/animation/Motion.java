package me.skaffy.client.animation;

import java.util.List;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.animations.AnimationsPacket.PathPoint;

import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

final class Motion {
	record Pose(double x, double y, double z, float yaw, float pitch, float roll, float scaleX, float scaleY, float scaleZ) {
		Pose towards(Pose to, double t) {
			return new Pose(Mth.lerp(t, x, to.x), Mth.lerp(t, y, to.y), Mth.lerp(t, z, to.z), yaw + Mth.wrapDegrees(to.yaw - yaw) * (float) t,
					(float) Mth.lerp(t, pitch, to.pitch), (float) Mth.lerp(t, roll, to.roll), (float) Mth.lerp(t, scaleX, to.scaleX), (float) Mth.lerp(t, scaleY, to.scaleY),
					(float) Mth.lerp(t, scaleZ, to.scaleZ));
		}
	}

	private record Move(Pose from, Pose to, double start, double duration, Easing easing) {
	}

	private record Path(List<PathPoint> points, boolean smooth, boolean loop, boolean faceAlong, double start, float scaleX, float scaleY, float scaleZ) {
	}

	private Pose pose;
	private @Nullable Move move;
	private @Nullable Path path;

	Motion(Pose pose) {
		this.pose = pose;
	}

	void set(Pose pose) {
		this.pose = pose;
		move = null;
		path = null;
	}

	void moveTo(Pose target, double now, int durationMillis, Easing easing) {
		Pose from = at(now);
		path = null;

		if (durationMillis <= 0) {
			pose = target;
			move = null;
		} else {
			move = new Move(from, target, now, durationMillis / 1000.0, easing);
		}
	}

	void follow(List<PathPoint> points, boolean smooth, boolean loop, boolean faceAlong, int startMillis, double now) {
		Pose current = at(now);
		move = null;
		path = new Path(List.copyOf(points), smooth, loop, faceAlong, now - startMillis / 1000.0, current.scaleX(), current.scaleY(), current.scaleZ());
		pose = current;
	}

	void scale(float x, float y, float z) {
		pose = new Pose(pose.x, pose.y, pose.z, pose.yaw, pose.pitch, pose.roll, x, y, z);
	}

	Pose at(double now) {
		if (move != null) {
			double progress = (now - move.start) / move.duration;

			if (progress >= 1) {
				pose = move.to;
				move = null;
			} else {
				return move.from.towards(move.to, move.easing.apply(Math.max(0, progress)));
			}
		}

		if (path != null) {
			return pathPose(path, (now - path.start) * 1000);
		}

		return pose;
	}

	private static Pose pathPose(Path path, double millis) {
		List<PathPoint> points = path.points;
		double last = points.getLast().time();

		if (path.loop && last > 0) {
			millis = ((millis % last) + last) % last;
		}

		double[] position = position(path, millis);
		float yaw;
		float pitch;
		float roll;

		if (path.faceAlong) {
			double[] ahead = position(path, millis + 50);
			double[] behind = position(path, millis - 50);
			double dx = ahead[0] - behind[0];
			double dy = ahead[1] - behind[1];
			double dz = ahead[2] - behind[2];
			double horizontal = Math.sqrt(dx * dx + dz * dz);

			if (horizontal < 1.0E-6 && Math.abs(dy) < 1.0E-6) {
				yaw = (float) position[3];
				pitch = (float) position[4];
			} else {
				yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
			}

			roll = (float) position[5];
		} else {
			yaw = (float) position[3];
			pitch = (float) position[4];
			roll = (float) position[5];
		}

		return new Pose(position[0], position[1], position[2], yaw, pitch, roll, path.scaleX, path.scaleY, path.scaleZ);
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
		PathPoint a = points.get(previous);
		PathPoint b = points.get(next);
		double gap = b.time() - a.time();
		double t = gap <= 0 ? 1 : Math.clamp((millis - a.time()) / gap, 0, 1);
		double[] from = values(a);
		double[] to = values(b);
		double[] result = new double[6];

		if (path.smooth && count > 2) {
			double[] before = values(points.get(previous > 0 ? previous - 1 : path.loop ? count - 2 : previous));
			double[] after = values(points.get(next < count - 1 ? next + 1 : path.loop ? 1 : next));

			for (int i = 0; i < 3; i++) {
				result[i] = catmullRom(t, before[i], from[i], to[i], after[i]);
			}
		} else {
			for (int i = 0; i < 3; i++) {
				result[i] = Mth.lerp(t, from[i], to[i]);
			}
		}

		result[3] = from[3] + Mth.wrapDegrees(to[3] - from[3]) * t;
		result[4] = Mth.lerp(t, from[4], to[4]);
		result[5] = Mth.lerp(t, from[5], to[5]);
		return result;
	}

	private static double[] values(PathPoint point) {
		return new double[] {point.x(), point.y(), point.z(), point.yaw(), point.pitch(), point.roll()};
	}

	private static double catmullRom(double t, double p0, double p1, double p2, double p3) {
		return 0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t);
	}
}
