package me.skaffy.client.mixin;

import me.skaffy.client.gui.ClientGuis;
import me.skaffy.client.shader.Vista;

import net.minecraft.client.Options;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("HEAD"), cancellable = true)
	private void skaffy$guiBlur(CallbackInfoReturnable<Integer> cir) {
		int blur = ClientGuis.blur();

		if (blur >= 0) {
			cir.setReturnValue(blur);
		}
	}

	@Inject(method = "processOptions", at = @At("TAIL"))
	private void skaffy$vistaOptions(@Coerce Object access, CallbackInfo ci) {
		Vista.processOptions(access);
	}
}
