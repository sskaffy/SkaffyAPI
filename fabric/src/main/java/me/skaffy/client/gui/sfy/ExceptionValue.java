package me.skaffy.client.gui.sfy;

public record ExceptionValue(String message) {
	@Override
	public String toString() {
		return message == null ? "Exception" : "Exception: " + message;
	}
}
