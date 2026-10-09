package me.skaffy.client.gui.sfy;

import java.util.List;

public final class CompileException extends Exception {
	private final List<CompileError> errors;

	public CompileException(List<CompileError> errors) {
		super(errors.isEmpty() ? "Compile error" : errors.getFirst().toString());
		this.errors = List.copyOf(errors);
	}

	public List<CompileError> errors() {
		return errors;
	}

	public String describe() {
		StringBuilder builder = new StringBuilder();

		for (CompileError error : errors) {
			if (!builder.isEmpty()) {
				builder.append('\n');
			}

			builder.append(error);
		}

		return builder.toString();
	}
}
