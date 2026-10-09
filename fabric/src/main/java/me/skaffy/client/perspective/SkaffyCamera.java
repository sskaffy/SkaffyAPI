package me.skaffy.client.perspective;

import net.minecraft.world.phys.Vec3;

public interface SkaffyCamera {
	void skaffy$setPose(Vec3 position, float yaw, float pitch, float roll);

	float skaffy$eyeHeight(float partialTicks);

	void skaffy$setDetached(boolean detached);
}
