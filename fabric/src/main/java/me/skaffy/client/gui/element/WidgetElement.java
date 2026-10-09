package me.skaffy.client.gui.element;

public final class WidgetElement extends Element {
	public enum Kind {
		BUTTON("VanillaButton"),
		FIELD("VanillaField"),
		SLIDER("VanillaSlider"),
		CHECKBOX("Checkbox"),
		CYCLE("CycleButton");

		final String typeName;

		Kind(String typeName) {
			this.typeName = typeName;
		}
	}

	public final Kind kind;
	public WidgetPeer peer;

	public WidgetElement(GuiDocument document, Kind kind) {
		super(document);
		this.kind = kind;
	}

	@Override
	public boolean focusable() {
		return !disabled;
	}

	@Override
	public boolean catchesMouse() {
		return mouseThrough == null || !mouseThrough;
	}

	@Override
	double[] intrinsicSize(double innerWidth, double innerHeight) {
		return peer == null ? new double[] {150, 20} : peer.defaultSize();
	}

	@Override
	void afterLayout() {
		if (peer != null) {
			peer.setSize((int) Math.round(lw), (int) Math.round(lh));
			peer.setActive(!disabled);
		}
	}

	@Override
	public String typeName() {
		return kind.typeName;
	}
}
