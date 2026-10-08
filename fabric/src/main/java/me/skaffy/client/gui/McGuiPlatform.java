package me.skaffy.client.gui;

import java.util.Locale;

import me.skaffy.client.gui.element.Element;
import me.skaffy.client.gui.element.FieldElement;
import me.skaffy.client.gui.element.GuiPlatform;
import me.skaffy.client.gui.element.PictureElements.EntityElement;
import me.skaffy.client.gui.element.PictureElements.HeadElement;
import me.skaffy.client.gui.element.PictureElements.ImageElement;
import me.skaffy.client.gui.element.PictureElements.ItemElement;
import me.skaffy.client.gui.element.Prop;
import me.skaffy.client.gui.element.TextElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;

final class McGuiPlatform implements GuiPlatform {
	static final McGuiPlatform INSTANCE = new McGuiPlatform();

	private McGuiPlatform() {
	}

	@Override
	public TextLayout layoutText(TextElement element, double wrapWidth) {
		return GuiText.layout(element, wrapWidth);
	}

	@Override
	public double textWidth(FieldElement field, String text) {
		Style style = Style.EMPTY.withFont(GuiText.fontOf(field.font, field.customFont));
		return GuiText.measure(text, style, field.number(Prop.FONT_SIZE) / 8);
	}

	@Override
	public double[] naturalSize(Element element) {
		return switch (element) {
			case ImageElement image -> {
				if (image.region != null) {
					yield new double[] {image.region[2], image.region[3]};
				}

				if (image.guiSprite) {
					TextureAtlasSprite sprite = GuiTextures.guiSprite(image.source);
					yield sprite == null ? new double[] {16, 16} : new double[] {sprite.contents().width(), sprite.contents().height()};
				}

				GuiTextures.Texture texture = GuiTextures.image(image.source);
				yield new double[] {texture.frameWidth, texture.frameHeight};
			}
			case HeadElement ignored -> new double[] {16, 16};
			case ItemElement ignored -> new double[] {16, 16};
			case EntityElement ignored -> new double[] {50, 72};
			default -> null;
		};
	}

	@Override
	public String clipboard() {
		return Minecraft.getInstance().keyboardHandler.getClipboard();
	}

	@Override
	public void setClipboard(String text) {
		Minecraft.getInstance().keyboardHandler.setClipboard(text);
	}

	@Override
	public boolean isMac() {
		return Util.getPlatform() == Util.OS.OSX;
	}

	@Override
	public void playSound(String id, float volume, float pitch) {
		Identifier sound = Identifier.tryParse(id.toLowerCase(Locale.ROOT));

		if (sound != null) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sound), pitch, volume));
		}
	}

	@Override
	public void startTextInput(Object owner) {
		Minecraft.getInstance().textInputManager().startTextInput(owner);
	}

	@Override
	public void stopTextInput(Object owner) {
		Minecraft.getInstance().textInputManager().stopTextInput(owner);
	}
}
