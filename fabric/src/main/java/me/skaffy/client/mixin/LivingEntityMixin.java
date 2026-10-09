package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import me.skaffy.client.look.ClientPlayerLooks;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
	private EntityDimensions skaffy$lookHitbox(EntityDimensions original, @Local(argsOnly = true) Pose pose) {
		return (Object) this instanceof AbstractClientPlayer player ? ClientPlayerLooks.dimensions(player, pose, original) : original;
	}
}
