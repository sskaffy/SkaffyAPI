package me.skaffy.client.gui.element;

public interface GuiPlatform {
	TextLayout layoutText(TextElement element, double wrapWidth);

	double textWidth(FieldElement field, String text);

	double[] naturalSize(Element element);

	String clipboard();

	void setClipboard(String text);

	boolean isMac();

	void playSound(String id, float volume, float pitch);

	void startTextInput(Object owner);

	void stopTextInput(Object owner);

	interface TextLayout {
		double width();

		double height();
	}
}
