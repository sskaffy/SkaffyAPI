package me.skaffy.client.mixin;

import me.skaffy.client.gui.ClientGuis;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow
	public abstract boolean isHidden();

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void skaffy$hideForGui(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (ClientGuis.hidesHud()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void skaffy$hudLayers(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!isHidden() && !(Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen)) {
			ClientGuis.paintHuds(graphics);
		}
	}
}
