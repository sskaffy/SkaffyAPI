package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.skaffy.client.keybind.ClientKeybinds;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.MutableComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin {
	@ModifyVariable(method = "<init>", ordinal = 0, at = @At(value = "INVOKE", target = "Ljava/util/Arrays;sort([Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private KeyMapping[] skaffy$addServerKeybinds(KeyMapping[] sorted) {
		return ClientKeybinds.withServerKeybinds(sorted);
	}

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent skaffy$serverKeybindName(String key, Operation<MutableComponent> original) {
		MutableComponent label = ClientKeybinds.label(key);
		return label != null ? label : original.call(key);
	}
}
