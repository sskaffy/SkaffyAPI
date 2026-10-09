package me.skaffy.client.mixin;

import me.skaffy.client.keybind.ClientKeybinds;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.Category.class)
public abstract class KeyMappingCategoryMixin {
	@Inject(method = "label", at = @At("HEAD"), cancellable = true)
	private void skaffy$serverLabel(CallbackInfoReturnable<Component> cir) {
		Component label = ClientKeybinds.categoryLabel((KeyMapping.Category) (Object) this);

		if (label != null) {
			cir.setReturnValue(label);
		}
	}
}
