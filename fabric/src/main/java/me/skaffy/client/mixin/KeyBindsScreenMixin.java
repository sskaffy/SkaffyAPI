package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import me.skaffy.client.keybind.ClientKeybinds;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenMixin {
	@ModifyExpressionValue(method = {"lambda$addFooter$0", "extractRenderState"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"))
	private KeyMapping[] skaffy$withServerKeybinds(KeyMapping[] vanilla) {
		return ClientKeybinds.withServerKeybinds(vanilla);
	}
}
