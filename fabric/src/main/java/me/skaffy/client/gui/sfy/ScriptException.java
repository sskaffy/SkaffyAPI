package me.skaffy.client.gui.sfy;

import java.util.ArrayList;
import java.util.List;

public final class ScriptException extends RuntimeException {
	private final String scriptMessage;
	private final List<String> trace = new ArrayList<>();
	int pendingLine;

	public ScriptException(String message) {
		this(message, 0);
	}

	ScriptException(String message, int line) {
		super(message, null, false, false);
		this.scriptMessage = message;
		this.pendingLine = line;
	}

	public String scriptMessage() {
		return scriptMessage;
	}

	void leave(String file, String name, int callLine) {
		trace.add("at " + name + "(" + file + (pendingLine > 0 ? ":" + pendingLine : "") + ")");
		pendingLine = callLine;
	}

	public void finish(String file, String name) {
		leave(file, name, 0);
	}

	public List<String> trace() {
		return List.copyOf(trace);
	}

	public String describe() {
		StringBuilder builder = new StringBuilder(scriptMessage);

		for (String entry : trace) {
			builder.append("\n    ").append(entry);
		}

		return builder.toString();
	}
}
