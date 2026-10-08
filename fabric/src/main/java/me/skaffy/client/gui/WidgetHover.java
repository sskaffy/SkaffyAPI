package me.skaffy.client.gui;

import org.jspecify.annotations.Nullable;

public final class WidgetHover {
	private static @Nullable Boolean current;

	private WidgetHover() {
	}

	public static @Nullable Boolean current() {
		return current;
	}

	static void set(@Nullable Boolean hovered) {
		current = hovered;
	}
}
