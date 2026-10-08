package me.skaffy.api.nametag;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

public final class NameTagLine {
	public static final int MAX_LENGTH = 200;
	private static final NameTagLine EMPTY = new NameTagLine(List.of());

	private final List<NameTagObject> objects;

	private NameTagLine(List<NameTagObject> objects) {
		this.objects = List.copyOf(objects);
		int length = 0;

		for (NameTagObject object : this.objects) {
			length += object.length();
		}

		if (length > MAX_LENGTH) {
			throw new IllegalArgumentException("A name tag line has at most " + MAX_LENGTH + " characters, got " + length);
		}
	}

	public static NameTagLine of(NameTagObject... objects) {
		return new NameTagLine(List.of(objects));
	}

	public static NameTagLine of(List<NameTagObject> objects) {
		return new NameTagLine(objects);
	}

	public static NameTagLine of(String text) {
		return of(NameTagObject.text(text));
	}

	public static NameTagLine of(Component component) {
		List<NameTagLine> lines = ComponentConverter.lines(component);

		if (lines.size() > 1) {
			throw new IllegalArgumentException("The component has line breaks; use NameTag.Builder#lines for several lines");
		}

		return lines.getFirst();
	}

	public static NameTagLine empty() {
		return EMPTY;
	}

	public List<NameTagObject> getObjects() {
		return objects;
	}

	public boolean isEmpty() {
		return objects.isEmpty();
	}

	public int length() {
		int length = 0;

		for (NameTagObject object : objects) {
			length += object.length();
		}

		return length;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NameTagLine that && objects.equals(that.objects);
	}

	@Override
	public int hashCode() {
		return Objects.hash(objects);
	}

	@Override
	public String toString() {
		return "NameTagLine" + objects;
	}
}
