package me.skaffy.client.gui;

import java.util.EnumMap;
import java.util.Map;

import me.skaffy.protocol.gui.GuiPacket.HudPart;
import me.skaffy.protocol.gui.GuiPacket.SetHudPart;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.joml.Matrix3x2fStack;

public final class HudParts {
	private static final Map<HudPart, SetHudPart> PARTS = new EnumMap<>(HudPart.class);

	private HudParts() {
	}

	static void set(SetHudPart part) {
		if (part.visible() && part.x() == 0 && part.y() == 0 && part.scale() == 1) {
			PARTS.remove(part.part());
		} else {
			PARTS.put(part.part(), part);
		}
	}

	static void reset() {
		PARTS.clear();
	}

	public static void draw(HudPart part, GuiGraphicsExtractor graphics, Runnable original) {
		SetHudPart state = PARTS.get(part);

		if (state == null) {
			original.run();
			return;
		}

		if (!state.visible()) {
			return;
		}

		float[] anchor = anchor(part, graphics.guiWidth(), graphics.guiHeight());
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(anchor[0] + state.x(), anchor[1] + state.y());
		pose.scale(state.scale());
		pose.translate(-anchor[0], -anchor[1]);

		try {
			original.run();
		} finally {
			pose.popMatrix();
		}
	}

	private static float[] anchor(HudPart part, int width, int height) {
		float center = width / 2f;
		return switch (part) {
			case HOTBAR -> new float[] {center, height};
			case HEALTH, ARMOR -> new float[] {center - 91, height - 39};
			case FOOD, AIR, MOUNT_HEALTH -> new float[] {center + 91, height - 39};
			case EXPERIENCE -> new float[] {center, height - 29};
			case HELD_ITEM_NAME -> new float[] {center, height - 59};
			case ACTION_BAR -> new float[] {center, height - 68};
			case EFFECTS -> new float[] {width, 0};
			case BOSS_BARS, PLAYER_LIST -> new float[] {center, 0};
			case SCOREBOARD -> new float[] {width, height / 2f};
			case CHAT -> new float[] {0, height};
			case SUBTITLES -> new float[] {width, height};
			case CROSSHAIR, TITLE, VIGNETTE, HELMET_OVERLAYS, SLEEP_FADE -> new float[] {center, height / 2f};
		};
	}
}
