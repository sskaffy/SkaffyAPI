package me.skaffy.client.gui.element;

import java.util.function.Consumer;

public final class GuiTimer {
	final Consumer<Object> action;
	final long intervalNanos;
	long due;
	boolean cancelled;

	GuiTimer(Consumer<Object> action, long due, long intervalNanos) {
		this.action = action;
		this.due = due;
		this.intervalNanos = intervalNanos;
	}

	public void cancel() {
		cancelled = true;
	}

	public boolean isCancelled() {
		return cancelled;
	}
}
