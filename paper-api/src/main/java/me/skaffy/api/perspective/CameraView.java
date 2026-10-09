package me.skaffy.api.perspective;

import org.bukkit.entity.Entity;

public final class CameraView {
	public static final float MAX_OFFSET = 4096;

	public enum LookMode {
		ANGLES,
		POINT,
		PLAYER,
		ENTITY,
		PLAYER_VIEW,
		BONE_FACING
	}

	private final double x;
	private final double y;
	private final double z;
	private final boolean turnWithPlayer;
	private final boolean turnWithPitch;
	private final LookMode lookMode;
	private final float yaw;
	private final float pitch;
	private final double lookX;
	private final double lookY;
	private final double lookZ;
	private final Entity lookEntity;
	private final float roll;
	private final boolean pullInFrontOfWalls;
	private final me.skaffy.api.animation.AnimationEntity mount;
	private final String mountBone;

	private CameraView(Builder builder) {
		this.x = builder.x;
		this.y = builder.y;
		this.z = builder.z;
		this.turnWithPlayer = builder.turnWithPlayer || builder.turnWithPitch;
		this.turnWithPitch = builder.turnWithPitch;
		this.lookMode = builder.lookMode;
		this.yaw = builder.yaw;
		this.pitch = builder.pitch;
		this.lookX = builder.lookX;
		this.lookY = builder.lookY;
		this.lookZ = builder.lookZ;
		this.lookEntity = builder.lookEntity;
		this.roll = builder.roll;
		this.pullInFrontOfWalls = builder.pullInFrontOfWalls;
		this.mount = builder.mount;
		this.mountBone = builder.mountBone;
	}

	public static Builder at(double x, double y, double z) {
		return new Builder(x, y, z);
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

	public boolean turnsWithPlayer() {
		return turnWithPlayer;
	}

	public boolean turnsWithPitch() {
		return turnWithPitch;
	}

	public LookMode getLookMode() {
		return lookMode;
	}

	public float getYaw() {
		return yaw;
	}

	public float getPitch() {
		return pitch;
	}

	public double getLookX() {
		return lookX;
	}

	public double getLookY() {
		return lookY;
	}

	public double getLookZ() {
		return lookZ;
	}

	public Entity getLookEntity() {
		return lookEntity;
	}

	public float getRoll() {
		return roll;
	}

	public boolean pullsInFrontOfWalls() {
		return pullInFrontOfWalls;
	}

	public me.skaffy.api.animation.AnimationEntity getMount() {
		return mount;
	}

	public String getMountBone() {
		return mountBone;
	}

	public static final class Builder {
		private final double x;
		private final double y;
		private final double z;
		private boolean turnWithPlayer;
		private boolean turnWithPitch;
		private LookMode lookMode = LookMode.PLAYER;
		private float yaw;
		private float pitch;
		private double lookX;
		private double lookY;
		private double lookZ;
		private Entity lookEntity;
		private float roll;
		private boolean pullInFrontOfWalls;
		private me.skaffy.api.animation.AnimationEntity mount;
		private String mountBone = "";

		private Builder(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public Builder turnWithPlayer(boolean turnWithPlayer) {
			this.turnWithPlayer = turnWithPlayer;
			return this;
		}

		public Builder turnWithPitch(boolean turnWithPitch) {
			this.turnWithPitch = turnWithPitch;
			return this;
		}

		public Builder look(float yaw, float pitch) {
			this.lookMode = LookMode.ANGLES;
			this.yaw = yaw;
			this.pitch = pitch;
			return this;
		}

		public Builder lookAt(double x, double y, double z) {
			this.lookMode = LookMode.POINT;
			this.lookX = x;
			this.lookY = y;
			this.lookZ = z;
			return this;
		}

		public Builder lookAtPlayer() {
			this.lookMode = LookMode.PLAYER;
			return this;
		}

		public Builder lookAt(Entity entity) {
			if (entity == null) {
				throw new IllegalArgumentException("entity");
			}

			this.lookMode = LookMode.ENTITY;
			this.lookEntity = entity;
			return this;
		}

		public Builder lookWithPlayer() {
			this.lookMode = LookMode.PLAYER_VIEW;
			return this;
		}

		public Builder mountOn(me.skaffy.api.animation.AnimationEntity entity, String bone) {
			this.mount = entity;
			this.mountBone = bone == null ? "" : bone;
			return this;
		}

		public Builder lookAlongBone() {
			this.lookMode = LookMode.BONE_FACING;
			return this;
		}

		public Builder roll(float roll) {
			this.roll = roll;
			return this;
		}

		public Builder pullInFrontOfWalls(boolean pullInFrontOfWalls) {
			this.pullInFrontOfWalls = pullInFrontOfWalls;
			return this;
		}

		public CameraView build() {
			check(x, y, z);

			if (lookMode == LookMode.POINT) {
				check(lookX, lookY, lookZ);
			}

			if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
				throw new IllegalArgumentException("Camera angles must be finite");
			}

			return new CameraView(this);
		}

		private static void check(double x, double y, double z) {
			if (!(Math.abs(x) <= MAX_OFFSET && Math.abs(y) <= MAX_OFFSET && Math.abs(z) <= MAX_OFFSET)) {
				throw new IllegalArgumentException("Camera offsets must be within " + MAX_OFFSET + " blocks, got " + x + ", " + y + ", " + z);
			}
		}
	}
}
