package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import me.skaffy.client.fov.ClientFov;
import me.skaffy.client.perspective.ClientPerspective;
import me.skaffy.client.perspective.SkaffyCamera;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin implements SkaffyCamera {
	@Shadow
	@Final
	private static Vector3fc FORWARDS;
	@Shadow
	@Final
	private static Vector3fc UP;
	@Shadow
	@Final
	private static Vector3fc LEFT;
	@Shadow
	private boolean isPanoramicMode;
	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f forwards;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;
	@Shadow
	private int matrixPropertiesDirty;
	@Shadow
	private boolean detached;
	@Shadow
	private float eyeHeight;
	@Shadow
	private float eyeHeightOld;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float skaffy$serverFov(float fov) {
		return ClientFov.worldFov(fov, isPanoramicMode);
	}

	@ModifyReturnValue(method = "calculateHudFov", at = @At("RETURN"))
	private float skaffy$serverHandFov(float fov) {
		return ClientFov.handFov(fov);
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void skaffy$serverPerspective(float partialTicks, CallbackInfo ci) {
		ClientPerspective.adjustCamera((Camera) (Object) this, partialTicks);
	}

	@Override
	public void skaffy$setPose(Vec3 position, float yaw, float pitch, float roll) {
		setPosition(position);
		setRotation(yaw, pitch);

		if (roll != 0) {
			rotation.rotateZ(-roll * Mth.DEG_TO_RAD);
			FORWARDS.rotate(rotation, forwards);
			UP.rotate(rotation, up);
			LEFT.rotate(rotation, left);
			matrixPropertiesDirty |= 3;
		}
	}

	@Override
	public float skaffy$eyeHeight(float partialTicks) {
		return Mth.lerp(partialTicks, eyeHeightOld, eyeHeight);
	}

	@Override
	public void skaffy$setDetached(boolean detached) {
		this.detached = detached;
	}
}
