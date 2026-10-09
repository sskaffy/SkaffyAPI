package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.skaffy.client.keybind.ClientKeybinds;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.MutableComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyBindsListKeyEntryMixin {
	@ModifyExpressionValue(method = "refreshEntry", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"))
	private KeyMapping[] skaffy$withServerKeybinds(KeyMapping[] vanilla) {
		return ClientKeybinds.withServerKeybinds(vanilla);
	}

	@WrapOperation(method = "refreshEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent skaffy$serverKeybindName(String key, Operation<MutableComponent> original) {
		MutableComponent label = ClientKeybinds.label(key);
		return label != null ? label : original.call(key);
	}
}
