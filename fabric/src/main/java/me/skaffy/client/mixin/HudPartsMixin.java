package me.skaffy.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.skaffy.client.gui.HudParts;
import me.skaffy.protocol.gui.GuiPacket.HudPart;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class HudPartsMixin {
	@WrapMethod(method = "extractCrosshair")
	private void skaffy$crosshair(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.CROSSHAIR, graphics, () -> original.call(graphics, delta));
	}

	@WrapMethod(method = "extractItemHotbar")
	private void skaffy$hotbar(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.HOTBAR, graphics, () -> original.call(graphics, delta));
	}

	@WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/spectator/SpectatorGui;extractHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
	private void skaffy$spectatorHotbar(SpectatorGui spectator, GuiGraphicsExtractor graphics, Operation<Void> original) {
		HudParts.draw(HudPart.HOTBAR, graphics, () -> original.call(spectator, graphics));
	}

	@WrapMethod(method = "extractHearts")
	private void skaffy$hearts(GuiGraphicsExtractor graphics, Player player, int xLeft, int yLineBase, int rowHeight, int offsetIndex, float maxHealth, int health, int oldHealth,
			int absorption, boolean blink, Operation<Void> original) {
		HudParts.draw(HudPart.HEALTH, graphics, () -> original.call(graphics, player, xLeft, yLineBase, rowHeight, offsetIndex, maxHealth, health, oldHealth, absorption, blink));
	}

	@WrapMethod(method = "extractArmor")
	private static void skaffy$armor(GuiGraphicsExtractor graphics, Player player, int yLineBase, int rows, int rowHeight, int xLeft, Operation<Void> original) {
		HudParts.draw(HudPart.ARMOR, graphics, () -> original.call(graphics, player, yLineBase, rows, rowHeight, xLeft));
	}

	@WrapMethod(method = "extractFood")
	private void skaffy$food(GuiGraphicsExtractor graphics, Player player, int yLineBase, int xRight, Operation<Void> original) {
		HudParts.draw(HudPart.FOOD, graphics, () -> original.call(graphics, player, yLineBase, xRight));
	}

	@WrapMethod(method = "extractAirBubbles")
	private void skaffy$air(GuiGraphicsExtractor graphics, Player player, int vehicleHearts, int yLineAir, int xRight, Operation<Void> original) {
		HudParts.draw(HudPart.AIR, graphics, () -> original.call(graphics, player, vehicleHearts, yLineAir, xRight));
	}

	@WrapMethod(method = "extractVehicleHealth")
	private void skaffy$mount(GuiGraphicsExtractor graphics, Operation<Void> original) {
		HudParts.draw(HudPart.MOUNT_HEALTH, graphics, () -> original.call(graphics));
	}

	@WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void skaffy$barBackground(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.EXPERIENCE, graphics, () -> original.call(bar, graphics, delta));
	}

	@WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void skaffy$bar(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.EXPERIENCE, graphics, () -> original.call(bar, graphics, delta));
	}

	@WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
	private void skaffy$level(GuiGraphicsExtractor graphics, Font font, int level, Operation<Void> original) {
		HudParts.draw(HudPart.EXPERIENCE, graphics, () -> original.call(graphics, font, level));
	}

	@WrapMethod(method = "extractSelectedItemName")
	private void skaffy$heldItemName(GuiGraphicsExtractor graphics, Operation<Void> original) {
		HudParts.draw(HudPart.HELD_ITEM_NAME, graphics, () -> original.call(graphics));
	}

	@WrapMethod(method = "extractEffects")
	private void skaffy$effects(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.EFFECTS, graphics, () -> original.call(graphics, delta));
	}

	@WrapMethod(method = "extractBossOverlay")
	private void skaffy$bossBars(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.BOSS_BARS, graphics, () -> original.call(graphics, delta));
	}

	@WrapMethod(method = "extractScoreboardSidebar")
	private void skaffy$scoreboard(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.SCOREBOARD, graphics, () -> original.call(graphics, delta));
	}

	@WrapMethod(method = "extractOverlayMessage")
	private void skaffy$actionBar(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.ACTION_BAR, graphics, () -> original.call(graphics, delta));
	}

	@WrapMethod(method = "extractTitle")
	private void skaffy$title(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.TITLE, graphics, () -> original.call(graphics, delta));
	}

	@WrapMethod(method = "extractChat")
	private void skaffy$chat(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.CHAT, graphics, () -> original.call(graphics, delta));
	}

	@WrapMethod(method = "extractTabList")
	private void skaffy$playerList(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.PLAYER_LIST, graphics, () -> original.call(graphics, delta));
	}

	@WrapMethod(method = "extractSubtitleOverlay")
	private void skaffy$subtitles(GuiGraphicsExtractor graphics, boolean defer, Operation<Void> original) {
		HudParts.draw(HudPart.SUBTITLES, graphics, () -> original.call(graphics, defer));
	}

	@WrapMethod(method = "extractVignette")
	private void skaffy$vignette(GuiGraphicsExtractor graphics, Entity camera, Operation<Void> original) {
		HudParts.draw(HudPart.VIGNETTE, graphics, () -> original.call(graphics, camera));
	}

	@WrapMethod(method = "extractSpyglassOverlay")
	private void skaffy$spyglass(GuiGraphicsExtractor graphics, float scale, Operation<Void> original) {
		HudParts.draw(HudPart.HELMET_OVERLAYS, graphics, () -> original.call(graphics, scale));
	}

	@WrapMethod(method = "extractTextureOverlay")
	private void skaffy$helmet(GuiGraphicsExtractor graphics, Identifier texture, float alpha, Operation<Void> original) {
		HudParts.draw(HudPart.HELMET_OVERLAYS, graphics, () -> original.call(graphics, texture, alpha));
	}

	@WrapMethod(method = "extractSleepOverlay")
	private void skaffy$sleep(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
		HudParts.draw(HudPart.SLEEP_FADE, graphics, () -> original.call(graphics, delta));
	}
}
