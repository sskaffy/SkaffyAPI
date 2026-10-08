package me.skaffy.client.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AnimationEntity extends Entity {
	final AnimatedObject object;
	float radius = -1;

	AnimationEntity(Level level, AnimatedObject object) {
		super(EntityTypes.MARKER, level);
		this.object = object;
		this.noPhysics = true;
	}

	public String animationId() {
		return object.id;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public void tick() {
	}

	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		float width = object == null ? 0 : object.settings.hitboxWidth();
		float height = object == null ? 0 : object.settings.hitboxHeight();

		if (width <= 0 || height <= 0) {
			return new AABB(position.x, position.y, position.z, position.x, position.y, position.z);
		}

		return new AABB(position.x - width / 2, position.y, position.z - width / 2, position.x + width / 2, position.y + height, position.z + width / 2);
	}

	void refreshHitbox() {
		setBoundingBox(makeBoundingBox(position()));
	}

	@Override
	public boolean isPickable() {
		return object != null && object.settings.hitboxWidth() > 0 && object.settings.hitboxHeight() > 0;
	}

	@Override
	public boolean isAttackable() {
		return isPickable();
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		float viewDistance = object.settings.viewDistance();

		if (viewDistance <= 0) {
			viewDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
		}

		return distance < (double) viewDistance * viewDistance;
	}
}
