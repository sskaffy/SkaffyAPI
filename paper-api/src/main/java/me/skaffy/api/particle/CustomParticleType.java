package me.skaffy.api.particle;

import java.util.List;
import java.util.Objects;

import org.bukkit.Color;

public final class CustomParticleType {
	public enum FrameMode {
		AGE,
		RANDOM
	}

	public enum RenderMode {
		OPAQUE,
		TRANSLUCENT
	}

	public enum Facing {
		CAMERA,
		VERTICAL,
		HORIZONTAL,
		FIXED
	}

	public static final int MAX_TEXTURES = 256;
	public static final int MAX_LIFETIME = 6000;

	private final String name;
	private final List<String> textures;
	private final FrameMode frameMode;
	private final RenderMode render;
	private final Facing facing;
	private final float yaw;
	private final float pitch;
	private final int light;
	private final float minSize;
	private final float maxSize;
	private final int minLifetime;
	private final int maxLifetime;
	private final ParticleCurve sizeOverLife;
	private final ParticleCurve alphaOverLife;
	private final ParticleColorCurve colorOverLife;
	private final float colorVariation;
	private final float minSpin;
	private final float maxSpin;
	private final boolean randomAngle;
	private final ParticleCurve spinOverLife;
	private final float gravity;
	private final float friction;
	private final boolean collides;
	private final boolean dieOnGround;
	private final float velocityRandomness;
	private final float wanderChance;
	private final float wanderSpeed;
	private final float sway;
	private final float swayPeriod;
	private final Easing converge;

	private CustomParticleType(Builder builder) {
		this.name = builder.name;
		this.textures = builder.textures;
		this.frameMode = builder.frameMode;
		this.render = builder.render;
		this.facing = builder.facing;
		this.yaw = builder.yaw;
		this.pitch = builder.pitch;
		this.light = builder.light;
		this.minSize = builder.minSize;
		this.maxSize = builder.maxSize;
		this.minLifetime = builder.minLifetime;
		this.maxLifetime = builder.maxLifetime;
		this.sizeOverLife = builder.sizeOverLife;
		this.alphaOverLife = builder.alphaOverLife;
		this.colorOverLife = builder.colorOverLife;
		this.colorVariation = builder.colorVariation;
		this.minSpin = builder.minSpin;
		this.maxSpin = builder.maxSpin;
		this.randomAngle = builder.randomAngle;
		this.spinOverLife = builder.spinOverLife;
		this.gravity = builder.gravity;
		this.friction = builder.friction;
		this.collides = builder.collides;
		this.dieOnGround = builder.dieOnGround;
		this.velocityRandomness = builder.velocityRandomness;
		this.wanderChance = builder.wanderChance;
		this.wanderSpeed = builder.wanderSpeed;
		this.sway = builder.sway;
		this.swayPeriod = builder.swayPeriod;
		this.converge = builder.converge;
	}

	public static Builder builder(String name, String... textures) {
		return new Builder(name, textures);
	}

	public String getName() {
		return name;
	}

	public List<String> getTextures() {
		return textures;
	}

	public FrameMode getFrameMode() {
		return frameMode;
	}

	public RenderMode getRender() {
		return render;
	}

	public Facing getFacing() {
		return facing;
	}

	public float getYaw() {
		return yaw;
	}

	public float getPitch() {
		return pitch;
	}

	public int getLight() {
		return light;
	}

	public float getMinSize() {
		return minSize;
	}

	public float getMaxSize() {
		return maxSize;
	}

	public int getMinLifetime() {
		return minLifetime;
	}

	public int getMaxLifetime() {
		return maxLifetime;
	}

	public ParticleCurve getSizeOverLife() {
		return sizeOverLife;
	}

	public ParticleCurve getAlphaOverLife() {
		return alphaOverLife;
	}

	public ParticleColorCurve getColorOverLife() {
		return colorOverLife;
	}

	public float getColorVariation() {
		return colorVariation;
	}

	public float getMinSpin() {
		return minSpin;
	}

	public float getMaxSpin() {
		return maxSpin;
	}

	public boolean hasRandomAngle() {
		return randomAngle;
	}

	public ParticleCurve getSpinOverLife() {
		return spinOverLife;
	}

	public float getGravity() {
		return gravity;
	}

	public float getFriction() {
		return friction;
	}

	public boolean collides() {
		return collides;
	}

	public boolean diesOnGround() {
		return dieOnGround;
	}

	public float getVelocityRandomness() {
		return velocityRandomness;
	}

	public float getWanderChance() {
		return wanderChance;
	}

	public float getWanderSpeed() {
		return wanderSpeed;
	}

	public float getSway() {
		return sway;
	}

	public float getSwayPeriod() {
		return swayPeriod;
	}

	public Easing getConverge() {
		return converge;
	}

	@Override
	public String toString() {
		return "CustomParticleType[" + name + "]";
	}

	public static final class Builder {
		private final String name;
		private List<String> textures;
		private FrameMode frameMode = FrameMode.AGE;
		private RenderMode render = RenderMode.OPAQUE;
		private Facing facing = Facing.CAMERA;
		private float yaw;
		private float pitch;
		private int light;
		private float minSize = 0.2f;
		private float maxSize = 0.2f;
		private int minLifetime = 20;
		private int maxLifetime = 20;
		private ParticleCurve sizeOverLife;
		private ParticleCurve alphaOverLife;
		private ParticleColorCurve colorOverLife;
		private float colorVariation;
		private float minSpin;
		private float maxSpin;
		private boolean randomAngle;
		private ParticleCurve spinOverLife;
		private float gravity;
		private float friction = 0.98f;
		private boolean collides = true;
		private boolean dieOnGround;
		private float velocityRandomness;
		private float wanderChance;
		private float wanderSpeed;
		private float sway;
		private float swayPeriod = 40;
		private Easing converge;

		private Builder(String name, String... textures) {
			this.name = Objects.requireNonNull(name, "name");
			textures(textures);
		}

		public Builder style(ParticleStyle style) {
			frameMode = FrameMode.AGE;
			render = RenderMode.OPAQUE;
			facing = Facing.CAMERA;
			light = 0;
			sizeOverLife = null;
			alphaOverLife = null;
			colorOverLife = null;
			colorVariation = 0;
			minSpin = 0;
			maxSpin = 0;
			randomAngle = false;
			spinOverLife = null;
			gravity = 0;
			friction = 0.98f;
			collides = true;
			dieOnGround = false;
			velocityRandomness = 0;
			wanderChance = 0;
			wanderSpeed = 0;
			sway = 0;
			swayPeriod = 40;
			converge = null;

			switch (Objects.requireNonNull(style, "style")) {
				case FLAME -> {
					size(0.2f, 0.4f).lifetime(12, 44).light(15).friction(0.96f).collides(false).velocityRandomness(0.002f);
					sizeOverLife = ParticleCurve.of(0, 1).to(1, 0.5f, Easing.EASE_IN);
				}
				case SMOKE -> {
					size(0.15f, 0.3f).lifetime(8, 40).gravity(-0.004f).friction(0.96f).velocityRandomness(0.01f);
					sizeOverLife = ParticleCurve.of(0, 0).to(0.03f, 1);
				}
				case DUST -> {
					size(0.15f, 0.3f).lifetime(8, 40).friction(0.96f).colorVariation(0.2f).velocityRandomness(0.01f);
					sizeOverLife = ParticleCurve.of(0, 0).to(0.03f, 1);
				}
				case LEAVES -> frameMode(FrameMode.RANDOM).size(0.2f, 0.3f).lifetime(300).gravity(0.00075f).friction(1)
						.dieOnGround(true).sway(0.02f, 60).spin(-3, 3).randomAngle(true);
				case FIREFLY -> {
					render(RenderMode.TRANSLUCENT).size(0.15f, 0.3f).lifetime(200, 300).light(15).friction(0.96f).wander(0.05f, 0.05f);
					alphaOverLife = ParticleCurve.of(0, 0).to(0.3f, 1).to(0.5f, 1).to(1, 0);
				}
				case PORTAL -> {
					size(0.1f, 0.14f).lifetime(40, 50).collides(false).converge(Easing.EASE_IN);
					sizeOverLife = ParticleCurve.of(0, 0).to(1, 1, Easing.EASE_OUT);
				}
				case SPARK -> {
					render(RenderMode.TRANSLUCENT).size(0.15f, 0.3f).lifetime(48, 59).light(15).gravity(0.004f).friction(0.91f);
					alphaOverLife = ParticleCurve.of(0.5f, 1).to(1, 0.5f);
				}
				case BUBBLE -> size(0.05f, 0.3f).lifetime(8, 40).gravity(-0.002f).friction(0.85f).velocityRandomness(0.02f);
			}

			return this;
		}

		public Builder textures(String... textures) {
			this.textures = List.of(textures);
			return this;
		}

		public Builder frameMode(FrameMode frameMode) {
			this.frameMode = Objects.requireNonNull(frameMode, "frameMode");
			return this;
		}

		public Builder render(RenderMode render) {
			this.render = Objects.requireNonNull(render, "render");
			return this;
		}

		public Builder facing(Facing facing) {
			this.facing = Objects.requireNonNull(facing, "facing");
			return this;
		}

		public Builder fixed(float yaw, float pitch) {
			this.facing = Facing.FIXED;
			this.yaw = yaw;
			this.pitch = pitch;
			return this;
		}

		public Builder light(int light) {
			this.light = light;
			return this;
		}

		public Builder size(float size) {
			return size(size, size);
		}

		public Builder size(float min, float max) {
			this.minSize = min;
			this.maxSize = max;
			return this;
		}

		public Builder lifetime(int ticks) {
			return lifetime(ticks, ticks);
		}

		public Builder lifetime(int min, int max) {
			this.minLifetime = min;
			this.maxLifetime = max;
			return this;
		}

		public Builder sizeOverLife(ParticleCurve curve) {
			this.sizeOverLife = curve;
			return this;
		}

		public Builder alphaOverLife(ParticleCurve curve) {
			this.alphaOverLife = curve;
			return this;
		}

		public Builder colorOverLife(ParticleColorCurve curve) {
			this.colorOverLife = curve;
			return this;
		}

		public Builder color(Color color) {
			return colorOverLife(ParticleColorCurve.constant(color));
		}

		public Builder colorVariation(float variation) {
			this.colorVariation = variation;
			return this;
		}

		public Builder spin(float degreesPerTick) {
			return spin(degreesPerTick, degreesPerTick);
		}

		public Builder spin(float min, float max) {
			this.minSpin = min;
			this.maxSpin = max;
			return this;
		}

		public Builder randomAngle(boolean randomAngle) {
			this.randomAngle = randomAngle;
			return this;
		}

		public Builder spinOverLife(ParticleCurve curve) {
			this.spinOverLife = curve;
			return this;
		}

		public Builder gravity(float gravity) {
			this.gravity = gravity;
			return this;
		}

		public Builder friction(float friction) {
			this.friction = friction;
			return this;
		}

		public Builder collides(boolean collides) {
			this.collides = collides;
			return this;
		}

		public Builder dieOnGround(boolean dieOnGround) {
			this.dieOnGround = dieOnGround;
			return this;
		}

		public Builder velocityRandomness(float randomness) {
			this.velocityRandomness = randomness;
			return this;
		}

		public Builder wander(float chance, float speed) {
			this.wanderChance = chance;
			this.wanderSpeed = speed;
			return this;
		}

		public Builder sway(float amount, float period) {
			this.sway = amount;
			this.swayPeriod = period;
			return this;
		}

		public Builder converge(Easing easing) {
			this.converge = easing;
			return this;
		}

		public CustomParticleType build() {
			if (name.isEmpty() || name.length() > 64) {
				throw new IllegalArgumentException("Particle name must be 1 to 64 characters: " + name);
			}

			if (textures.isEmpty() || textures.size() > MAX_TEXTURES) {
				throw new IllegalArgumentException("Particle " + name + " needs 1 to " + MAX_TEXTURES + " textures");
			}

			if (light < 0 || light > 15) {
				throw new IllegalArgumentException("Light must be 0 to 15, got " + light);
			}

			if (!(minSize >= 0) || minSize > maxSize || !Float.isFinite(maxSize)) {
				throw new IllegalArgumentException("Size must be 0 or more with min <= max, got " + minSize + " to " + maxSize);
			}

			if (minLifetime < 1 || minLifetime > maxLifetime || maxLifetime > MAX_LIFETIME) {
				throw new IllegalArgumentException("Lifetime must be 1 to " + MAX_LIFETIME + " ticks with min <= max, got " + minLifetime + " to " + maxLifetime);
			}

			if (!(colorVariation >= 0 && colorVariation <= 1)) {
				throw new IllegalArgumentException("Color variation must be from 0 to 1, got " + colorVariation);
			}

			if (!(minSpin <= maxSpin)) {
				throw new IllegalArgumentException("Smallest spin " + minSpin + " is above the largest " + maxSpin);
			}

			if (!(friction >= 0) || !(velocityRandomness >= 0) || !(wanderSpeed >= 0) || !(sway >= 0)) {
				throw new IllegalArgumentException("Friction, velocity randomness, wander speed and sway can't be negative");
			}

			if (!(wanderChance >= 0 && wanderChance <= 1)) {
				throw new IllegalArgumentException("Wander chance must be from 0 to 1, got " + wanderChance);
			}

			if (!(swayPeriod > 0)) {
				throw new IllegalArgumentException("Sway period must be above 0, got " + swayPeriod);
			}

			return new CustomParticleType(this);
		}
	}
}
