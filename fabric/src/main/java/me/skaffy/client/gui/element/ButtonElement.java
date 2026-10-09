package me.skaffy.client.gui.element;

import me.skaffy.client.gui.element.GuiEnums.Align;
import me.skaffy.client.gui.element.GuiEnums.Cursor;
import me.skaffy.client.gui.element.GuiEnums.Justify;
import me.skaffy.client.gui.element.GuiEnums.LayoutMode;
import me.skaffy.client.gui.text.RichText;

public final class ButtonElement extends BoxElement {
	public final TextElement label;
	public String sound = "minecraft:ui.button.click";

	public ButtonElement(GuiDocument document, RichText text) {
		super(document);
		layout = LayoutMode.ROW;
		alignItems = Align.CENTER;
		justify = Justify.CENTER;
		gap = 4;
		padTop = 5;
		padBottom = 5;
		padLeft = 10;
		padRight = 10;
		cursor = Cursor.HAND;
		transitionMillis = 90;
		set(Prop.COLOR, 0xFF3C3C3C);
		set(Prop.RADIUS_TOP_LEFT, 3.0);
		set(Prop.RADIUS_TOP_RIGHT, 3.0);
		set(Prop.RADIUS_BOTTOM_RIGHT, 3.0);
		set(Prop.RADIUS_BOTTOM_LEFT, 3.0);
		hoverStyle = new PropSet();
		hoverStyle.put(Prop.COLOR, 0xFF505050);
		pressedStyle = new PropSet();
		pressedStyle.put(Prop.COLOR, 0xFF2A2A2A);
		focusedStyle = new PropSet();
		focusedStyle.put(Prop.BORDER_WIDTH, 1.0);
		focusedStyle.put(Prop.BORDER_COLOR, 0xFFB0B0B0);
		disabledStyle = new PropSet();
		disabledStyle.put(Prop.COLOR, 0xFF2A2A2A);
		disabledStyle.put(Prop.TEXT_COLOR, 0xFF7A7A7A);
		label = new TextElement(document);
		label.internal = true;
		label.mouseThrough = true;
		label.setText(text);
		children.add(label);
		label.parent = this;
	}

	@Override
	public boolean focusable() {
		return !disabled;
	}

	@Override
	void tick(long now) {
		label.set(Prop.COLOR, color(Prop.TEXT_COLOR));
		label.set(Prop.FONT_SIZE, number(Prop.FONT_SIZE));
	}

	@Override
	public String typeName() {
		return "Button";
	}
}
