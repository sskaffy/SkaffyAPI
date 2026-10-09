package me.skaffy.client.particle;

import me.skaffy.protocol.particles.ParticleDefinition;
import me.skaffy.protocol.particles.ParticleDefinition.Facing;
import me.skaffy.protocol.particles.ParticleDefinition.FrameMode;
import me.skaffy.protocol.particles.ParticleDefinition.RenderMode;
import me.skaffy.protocol.particles.ParticleMotion;
import me.skaffy.protocol.particles.ParticlesPacket.SpawnParticles;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import org.joml.Quaternionf;

final class CustomParticle extends SingleQuadParticle {
	private final ParticleDefinition definition;
	private final ParticleMotion motion;
	private final TextureAtlasSprite[] sprites;
	private final float halfSize;
	private final float tintR;
	private final float tintG;
	private final float tintB;
	private final float spinSpeed;
	private final Quaternionf fixedRotation;
	private final double originX;
	private final double originY;
	private final double originZ;
	private final double startX;
	private final double startY;
	private final double startZ;
	private final double swayPhase;
	private final double swayStep;

	CustomParticle(ClientLevel level, ParticleDefinition definition, TextureAtlasSprite[] sprites, SpawnParticles spawn, double x, double y, double z) {
		super(level, x, y, z, sprites[0]);
		this.definition = definition;
		this.motion = definition.motion();
		this.sprites = sprites;

		if (definition.frameMode() == FrameMode.RANDOM) {
			sprite = sprites[random.nextInt(sprites.length)];
		}

		lifetime = spawn.lifetime() != null ? spawn.lifetime() : between(definition.minLifetime(), definition.maxLifetime());
		float sizeMultiplier = spawn.size() != null ? spawn.size() : 1;
		halfSize = between(definition.minSize(), definition.maxSize()) * sizeMultiplier / 2;

		int tint = spawn.color() != null ? spawn.color() : 0xFFFFFF;
		float brightness = 1 - random.nextFloat() * definition.colorVariation();
		tintR = (tint >> 16 & 0xFF) / 255f * brightness;
		tintG = (tint >> 8 & 0xFF) / 255f * brightness;
		tintB = (tint & 0xFF) / 255f * brightness;

		spinSpeed = (float) Math.toRadians(between(definition.minSpin(), definition.maxSpin()));

		if (definition.randomAngle()) {
			roll = random.nextFloat() * Mth.TWO_PI;
			oRoll = roll;
		}

		fixedRotation = switch (definition.facing()) {
			case HORIZONTAL -> rotation(0, 90);
			case FIXED -> spawn.rotation() != null ? rotation(spawn.rotation().yaw(), spawn.rotation().pitch()) : rotation(definition.yaw(), definition.pitch());
			case CAMERA, VERTICAL -> null;
		};

		float randomness = motion.velocityRandomness();
		xd = spawn.velocityX() + (random.nextFloat() * 2 - 1) * randomness;
		yd = spawn.velocityY() + (random.nextFloat() * 2 - 1) * randomness;
		zd = spawn.velocityZ() + (random.nextFloat() * 2 - 1) * randomness;
		gravity = 0;
		friction = motion.friction();
		hasPhysics = motion.collides();

		originX = spawn.x();
		originY = spawn.y();
		originZ = spawn.z();
		startX = x;
		startY = y;
		startZ = z;
		swayPhase = random.nextDouble() * Mth.TWO_PI;
		swayStep = (random.nextBoolean() ? 1 : -1) * Mth.TWO_PI / motion.swayPeriod();
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		oRoll = roll;

		if (age++ >= lifetime) {
			remove();
			return;
		}

		float progress = (float) age / lifetime;
		roll += spinSpeed * definition.spin().at(progress, 1);

		if (definition.frameMode() == FrameMode.AGE) {
			sprite = sprites[age * (sprites.length - 1) / lifetime];
		}

		if (motion.converge()) {
			float t = motion.convergeEasing().apply(progress);
			setPos(Mth.lerp(t, startX, originX), Mth.lerp(t, startY, originY), Mth.lerp(t, startZ, originZ));
			return;
		}

		if (motion.wanderChance() > 0 && (age == 1 || random.nextFloat() < motion.wanderChance())) {
			float speed = motion.wanderSpeed();
			setParticleSpeed((random.nextFloat() * 2 - 1) * speed, (random.nextFloat() * 2 - 1) * speed, (random.nextFloat() * 2 - 1) * speed);
		}

		yd -= motion.gravity();
		double swayX = 0;
		double swayZ = 0;

		if (motion.sway() > 0) {
			double angle = swayPhase + age * swayStep;
			swayX = Math.cos(angle) * motion.sway();
			swayZ = Math.sin(angle) * motion.sway();
		}

		move(xd + swayX, yd, zd + swayZ);

		if (motion.dieOnGround() && onGround) {
			remove();
			return;
		}

		xd *= friction;
		yd *= friction;
		zd *= friction;

		if (onGround) {
			xd *= 0.7F;
			zd *= 0.7F;
		}
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTickTime) {
		float progress = Mth.clamp((age + partialTickTime) / lifetime, 0, 1);
		int color = definition.color().at(progress, 0xFFFFFF);
		setColor((color >> 16 & 0xFF) / 255f * tintR, (color >> 8 & 0xFF) / 255f * tintG, (color & 0xFF) / 255f * tintB);
		setAlpha(Mth.clamp(definition.alpha().at(progress, 1), 0, 1));

		if (fixedRotation == null) {
			super.extract(state, camera, partialTickTime);
			return;
		}

		Quaternionf rotation = new Quaternionf(fixedRotation).rotateZ(Mth.lerp(partialTickTime, oRoll, roll));
		extractRotatedQuad(state, camera, rotation, partialTickTime);
		extractRotatedQuad(state, camera, rotation.rotateY(Mth.PI), partialTickTime);
	}

	@Override
	public FacingCameraMode getFacingCameraMode() {
		return definition.facing() == Facing.VERTICAL ? FacingCameraMode.LOOKAT_Y : FacingCameraMode.LOOKAT_XYZ;
	}

	@Override
	public float getQuadSize(float partialTickTime) {
		return halfSize * definition.size().at(Mth.clamp((age + partialTickTime) / lifetime, 0, 1), 1);
	}

	@Override
	protected int getLightCoords(float partialTickTime) {
		int light = definition.light();

		if (light >= 15) {
			return LightCoordsUtil.FULL_BRIGHT;
		}

		int coords = super.getLightCoords(partialTickTime);
		return light > 0 ? LightCoordsUtil.withBlock(coords, Math.max(LightCoordsUtil.block(coords), light)) : coords;
	}

	@Override
	protected Layer getLayer() {
		return definition.render() == RenderMode.TRANSLUCENT ? Layer.TRANSLUCENT : Layer.OPAQUE;
	}

	private int between(int min, int max) {
		return min + random.nextInt(max - min + 1);
	}

	private float between(float min, float max) {
		return min + random.nextFloat() * (max - min);
	}

	private static Quaternionf rotation(float yaw, float pitch) {
		return new Quaternionf().rotationYXZ(Mth.PI - (float) Math.toRadians(yaw), (float) -Math.toRadians(pitch), 0);
	}
}
