package me.skaffy.api.animation;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

public final class AnimationPath {
	public record Point(long millis, Location location, float roll) {
	}

	public static final int MAX_POINTS = 4096;

	private final List<Point> points;
	private final boolean smooth;
	private final boolean loop;
	private final boolean faceAlong;
	private final int startAt;

	private AnimationPath(Builder builder) {
		this.points = List.copyOf(builder.points);
		this.smooth = builder.smooth;
		this.loop = builder.loop;
		this.faceAlong = builder.faceAlong;
		this.startAt = builder.startAt;
	}

	public static Builder builder() {
		return new Builder();
	}

	public List<Point> getPoints() {
		return points;
	}

	public boolean isSmooth() {
		return smooth;
	}

	public boolean isLoop() {
		return loop;
	}

	public boolean isFaceAlong() {
		return faceAlong;
	}

	public int getStartAt() {
		return startAt;
	}

	public long getDuration() {
		return points.isEmpty() ? 0 : points.getLast().millis();
	}

	public static final class Builder {
		private final List<Point> points = new ArrayList<>();
		private boolean smooth;
		private boolean loop;
		private boolean faceAlong;
		private int startAt;

		private Builder() {
		}

		public Builder point(long millis, Location location) {
			return point(millis, location, 0);
		}

		public Builder point(long millis, Location location, float roll) {
			points.add(new Point(millis, location.clone(), roll));
			return this;
		}

		public Builder smooth(boolean smooth) {
			this.smooth = smooth;
			return this;
		}

		public Builder loop(boolean loop) {
			this.loop = loop;
			return this;
		}

		public Builder faceAlong(boolean faceAlong) {
			this.faceAlong = faceAlong;
			return this;
		}

		public Builder startAt(int millis) {
			this.startAt = millis;
			return this;
		}

		public AnimationPath build() {
			if (points.isEmpty() || points.size() > MAX_POINTS) {
				throw new IllegalArgumentException("A path has 1 to " + MAX_POINTS + " points");
			}

			for (int i = 0; i < points.size(); i++) {
				Point point = points.get(i);

				if (point.millis() < 0 || point.millis() > Integer.MAX_VALUE || i > 0 && point.millis() < points.get(i - 1).millis()) {
					throw new IllegalArgumentException("Path times must be 0 or more and not go down");
				}

				if (point.location().getWorld() != points.getFirst().location().getWorld()) {
					throw new IllegalArgumentException("A path stays in one world");
				}
			}

			if (startAt < 0) {
				throw new IllegalArgumentException("Start must be 0 or more");
			}

			return new AnimationPath(this);
		}
	}
}
