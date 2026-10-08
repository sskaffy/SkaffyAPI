package me.skaffy.client.mixin;

import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;

import me.skaffy.client.keybind.ClientKeybinds;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
	@Inject(method = "forAllKeyMappings", at = @At("HEAD"), cancellable = true)
	private static void skaffy$serverKeysWin(InputConstants.Key key, Consumer<KeyMapping> operation, CallbackInfo ci) {
		if (ClientKeybinds.dispatch(KeyMappingAccessor.skaffy$map().get(key), operation)) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "setAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;setDown(Z)V"))
	private static void skaffy$keepTakenKeysUp(KeyMapping mapping, boolean down, Operation<Void> original) {
		if (!down || !ClientKeybinds.taken(mapping)) {
			original.call(mapping, down);
		}
	}

	@Inject(method = "matches(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
	private void skaffy$takenKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (Minecraft.getInstance().gui.screen() == null && ClientKeybinds.taken((KeyMapping) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "matchesMouse", at = @At("HEAD"), cancellable = true)
	private void skaffy$takenButton(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (Minecraft.getInstance().gui.screen() == null && ClientKeybinds.taken((KeyMapping) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
