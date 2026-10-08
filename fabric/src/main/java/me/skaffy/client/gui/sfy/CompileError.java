package me.skaffy.client.gui.sfy;

public record CompileError(String file, Pos pos, String message) {
	@Override
	public String toString() {
		return file + ":" + pos + ": " + message;
	}
}
