package me.skaffy.client.mixin;

import me.skaffy.client.gui.WidgetHover;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@Inject(method = "containsPointInScissor", at = @At("HEAD"), cancellable = true)
	private void skaffy$widgetHover(int x, int y, CallbackInfoReturnable<Boolean> cir) {
		Boolean hovered = WidgetHover.current();

		if (hovered != null) {
			cir.setReturnValue(hovered);
		}
	}
}
