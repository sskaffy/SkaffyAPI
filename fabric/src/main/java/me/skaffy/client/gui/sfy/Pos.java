package me.skaffy.client.gui.sfy;

public record Pos(int line, int column) {
	public static final Pos NONE = new Pos(0, 0);

	@Override
	public String toString() {
		return line + ":" + column;
	}
}
