package me.skaffy.client.gui.element;

import me.skaffy.client.gui.element.GuiEvents.KeyInput;

public interface WidgetPeer {
	double[] defaultSize();

	void setSize(int width, int height);

	void setActive(boolean active);

	void setFocused(boolean focused);

	boolean mousePressed(double x, double y, int button, int modifiers, boolean doubleClick);

	boolean mouseReleased(double x, double y, int button, int modifiers);

	boolean mouseDragged(double x, double y, int button, int modifiers, double dx, double dy);

	boolean mouseScrolled(double x, double y, double dx, double dy);

	boolean keyPressed(KeyInput key);

	boolean keyReleased(KeyInput key);

	boolean charTyped(String text);

	boolean capturesKeys();
}
