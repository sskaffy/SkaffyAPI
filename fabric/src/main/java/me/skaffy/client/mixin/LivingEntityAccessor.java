package me.skaffy.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("swingState")
	LivingEntity.SwingState skaffy$swingState();

	@Mutable
	@Accessor("swingState")
	void skaffy$setSwingState(LivingEntity.SwingState state);

	@Mutable
	@Accessor("walkAnimation")
	void skaffy$setWalkAnimation(WalkAnimationState state);
}
