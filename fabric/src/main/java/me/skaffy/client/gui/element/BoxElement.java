package me.skaffy.client.gui.element;

public class BoxElement extends Element {
	public BoxElement(GuiDocument document) {
		super(document);
		base[Prop.COLOR.ordinal()] = 0;
		shown[Prop.COLOR.ordinal()] = 0;
	}

	@Override
	public Prop fillProp() {
		return Prop.COLOR;
	}

	@Override
	public String typeName() {
		return "Box";
	}
}
