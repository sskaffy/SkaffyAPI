package me.skaffy.client.gui.sfy;

public final class SfyInstance {
	final SfyClass type;
	final Object[] fields;

	SfyInstance(SfyClass type, int fieldCount) {
		this.type = type;
		this.fields = new Object[fieldCount];
	}

	public SfyClass type() {
		return type;
	}

	@Override
	public String toString() {
		return type.simpleName();
	}
}
