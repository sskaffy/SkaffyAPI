package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;

import me.skaffy.client.block.WorldBlocks;
import me.skaffy.client.look.ClientPlayerLooks;
import me.skaffy.client.model.ClientEntityModels;
import me.skaffy.client.nametag.ClientNameTags;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@ModifyVariable(method = "setServerVerifiedBlockState", at = @At("HEAD"), argsOnly = true)
	private BlockState skaffy$customBlock(BlockState state, @Local(argsOnly = true) BlockPos pos) {
		return WorldBlocks.serverState((ClientLevel) (Object) this, pos, state);
	}

	@Inject(method = "removeEntity", at = @At("HEAD"))
	private void skaffy$forgetModel(int id, Entity.RemovalReason reason, CallbackInfo ci) {
		ClientEntityModels.forget(id);
		ClientNameTags.forget(id);
		ClientPlayerLooks.forget(id);
	}

	@Inject(method = "addEntity", at = @At("TAIL"))
	private void skaffy$lookHitbox(Entity entity, CallbackInfo ci) {
		ClientPlayerLooks.added(entity);
	}
}
