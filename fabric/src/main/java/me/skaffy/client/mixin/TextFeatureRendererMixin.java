package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.skaffy.client.nametag.NameTagGlyphs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextFeatureRenderer.class)
public abstract class TextFeatureRendererMixin {
	@WrapOperation(method = "renderText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
	private static Font.PreparedText skaffy$nameTagGlyphs(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow, boolean includeEmpty, int background, Operation<Font.PreparedText> original) {
		return text instanceof NameTagGlyphs glyphs ? glyphs : original.call(font, text, x, y, color, shadow, includeEmpty, background);
	}
}
