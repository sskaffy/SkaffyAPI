package me.skaffy.api.particle;

import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class ParticleSpawn {
	public enum Spread {
		GAUSSIAN,
		BOX
	}

	public static final int MAX_COUNT = 16_384;

	private final CustomParticleType type;
	private final Location location;
	private final int count;
	private final Vector spread;
	private final Spread spreadShape;
	private final Vector velocity;
	private final boolean force;
	private final Color color;
	private final Float size;
	private final Integer lifetime;
	private final Float yaw;
	private final Float pitch;

	private ParticleSpawn(Builder builder) {
		this.type = builder.type;
		this.location = builder.location.clone();
		this.count = builder.count;
		this.spread = builder.spread.clone();
		this.spreadShape = builder.spreadShape;
		this.velocity = builder.velocity.clone();
		this.force = builder.force;
		this.color = builder.color;
		this.size = builder.size;
		this.lifetime = builder.lifetime;
		this.yaw = builder.yaw;
		this.pitch = builder.pitch;
	}

	public static Builder builder(CustomParticleType type, Location location) {
		return new Builder(type, location);
	}

	public CustomParticleType getType() {
		return type;
	}

	public Location getLocation() {
		return location.clone();
	}

	public int getCount() {
		return count;
	}

	public Vector getSpread() {
		return spread.clone();
	}

	public Spread getSpreadShape() {
		return spreadShape;
	}

	public Vector getVelocity() {
		return velocity.clone();
	}

	public boolean isForce() {
		return force;
	}

	public Color getColor() {
		return color;
	}

	public Float getSize() {
		return size;
	}

	public Integer getLifetime() {
		return lifetime;
	}

	public Float getYaw() {
		return yaw;
	}

	public Float getPitch() {
		return pitch;
	}

	public static final class Builder {
		private final CustomParticleType type;
		private final Location location;
		private int count = 1;
		private Vector spread = new Vector();
		private Spread spreadShape = Spread.GAUSSIAN;
		private Vector velocity = new Vector();
		private boolean force;
		private Color color;
		private Float size;
		private Integer lifetime;
		private Float yaw;
		private Float pitch;

		private Builder(CustomParticleType type, Location location) {
			this.type = Objects.requireNonNull(type, "type");
			this.location = Objects.requireNonNull(location, "location");
			Objects.requireNonNull(location.getWorld(), "location has no world");
		}

		public Builder count(int count) {
			this.count = count;
			return this;
		}

		public Builder spread(double spread) {
			return spread(spread, spread, spread);
		}

		public Builder spread(double x, double y, double z) {
			this.spread = new Vector(x, y, z);
			return this;
		}

		public Builder spreadShape(Spread shape) {
			this.spreadShape = Objects.requireNonNull(shape, "shape");
			return this;
		}

		public Builder velocity(Vector velocity) {
			this.velocity = velocity.clone();
			return this;
		}

		public Builder velocity(double x, double y, double z) {
			return velocity(new Vector(x, y, z));
		}

		public Builder force(boolean force) {
			this.force = force;
			return this;
		}

		public Builder color(Color color) {
			this.color = color;
			return this;
		}

		public Builder size(float multiplier) {
			this.size = multiplier;
			return this;
		}

		public Builder lifetime(int ticks) {
			this.lifetime = ticks;
			return this;
		}

		public Builder rotation(float yaw, float pitch) {
			this.yaw = yaw;
			this.pitch = pitch;
			return this;
		}

		public ParticleSpawn build() {
			if (count < 1 || count > MAX_COUNT) {
				throw new IllegalArgumentException("Count must be 1 to " + MAX_COUNT + ", got " + count);
			}

			if (spread.getX() < 0 || spread.getY() < 0 || spread.getZ() < 0) {
				throw new IllegalArgumentException("Spread can't be negative");
			}

			if (size != null && !(size >= 0)) {
				throw new IllegalArgumentException("Size can't be negative, got " + size);
			}

			if (lifetime != null && (lifetime < 1 || lifetime > CustomParticleType.MAX_LIFETIME)) {
				throw new IllegalArgumentException("Lifetime must be 1 to " + CustomParticleType.MAX_LIFETIME + " ticks, got " + lifetime);
			}

			return new ParticleSpawn(this);
		}
	}
}
