package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.skaffy.client.look.PlayerLookHooks;
import me.skaffy.client.model.ModelAnimator;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
	@WrapOperation(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
	private void skaffy$animate(Model<Object> model, Object state, Operation<Void> original) {
		original.call(model, state);
		ModelAnimator.afterSetupAnim(model, state);
		PlayerLookHooks.afterSetupAnim(model, state);
	}
}
