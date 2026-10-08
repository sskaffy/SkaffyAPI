package me.skaffy.client.gui.element;

import java.util.Arrays;

import me.skaffy.client.gui.element.GuiEnums.FontKind;
import me.skaffy.client.gui.element.GuiEnums.TextAlign;
import me.skaffy.client.gui.sfy.LengthValue;
import me.skaffy.client.gui.text.RichText;

public class TextElement extends Element {
	public RichText text = RichText.EMPTY;
	public FontKind font = FontKind.DEFAULT;
	public String customFont;
	public boolean bold;
	public boolean italic;
	public boolean underline;
	public boolean strikethrough;
	public boolean obfuscated;
	public boolean textShadow = true;
	public Integer textShadowColor;
	public TextAlign align = TextAlign.LEFT;
	public LengthValue maxWidth;
	public double lineSpacing = 1;

	public GuiPlatform.TextLayout textLayout;
	private Object layoutKey;

	public TextElement(GuiDocument document) {
		super(document);
		wrap = true;
	}

	public void setText(RichText value) {
		text = value == null ? RichText.EMPTY : value;
	}

	@Override
	double[] intrinsicSize(double innerWidth, double innerHeight) {
		double wrapWidth = Double.POSITIVE_INFINITY;

		if (wrap) {
			if (!Double.isNaN(innerWidth) && base(Prop.WIDTH) != null) {
				wrapWidth = innerWidth;
			} else if (maxWidth != null) {
				double parentWidth = parent == null ? document.width : parent.innerWidth();
				wrapWidth = Math.max(0, maxWidth.resolve(parentWidth) - padLeft - padRight);
			}
		}

		double size = number(Prop.FONT_SIZE);
		Object key = Arrays.asList(text, font, customFont, bold, italic, underline, strikethrough, obfuscated, lineSpacing, size, wrapWidth, align);

		if (textLayout == null || !key.equals(layoutKey)) {
			textLayout = document.platform.layoutText(this, wrapWidth);
			layoutKey = key;
		}

		return new double[] {textLayout.width(), textLayout.height()};
	}

	@Override
	public String typeName() {
		return "Text";
	}
}
