package me.skaffy.client.gui.sfy;

public record DurationValue(long millis) {
	public static final DurationValue ZERO = new DurationValue(0);

	@Override
	public String toString() {
		return millis % 1000 == 0 && millis != 0 ? millis / 1000 + "s" : millis + "ms";
	}
}
