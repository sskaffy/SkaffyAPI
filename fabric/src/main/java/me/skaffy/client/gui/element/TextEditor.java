package me.skaffy.client.gui.element;

import java.util.ArrayDeque;
import java.util.Deque;

import me.skaffy.client.gui.element.GuiEnums.Allow;

public final class TextEditor {
	private static final int HISTORY = 200;
	private static final long MERGE_NANOS = 1_000_000_000L;

	private String value = "";
	private int cursor;
	private int anchor;
	public int maxLength = 32767;
	public boolean multiline;
	public Allow allow = Allow.ANY;
	public String allowChars;

	private record Snapshot(String value, int cursor, int anchor) {
	}

	private final Deque<Snapshot> undo = new ArrayDeque<>();
	private final Deque<Snapshot> redo = new ArrayDeque<>();
	private long lastTyping;
	private boolean typing;

	public String value() {
		return value;
	}

	public int cursor() {
		return cursor;
	}

	public int anchor() {
		return anchor;
	}

	public boolean hasSelection() {
		return cursor != anchor;
	}

	public int selectionStart() {
		return Math.min(cursor, anchor);
	}

	public int selectionEnd() {
		return Math.max(cursor, anchor);
	}

	public String selected() {
		return value.substring(selectionStart(), selectionEnd());
	}

	public void setValue(String text) {
		value = limit(clean(text, true));
		cursor = value.length();
		anchor = cursor;
		undo.clear();
		redo.clear();
		typing = false;
	}

	public void setCursor(int position, boolean select) {
		cursor = Math.clamp(position, 0, value.length());

		if (!select) {
			anchor = cursor;
		}

		typing = false;
	}

	public void selectAll() {
		anchor = 0;
		cursor = value.length();
		typing = false;
	}

	public void select(int start, int end) {
		anchor = Math.clamp(start, 0, value.length());
		cursor = Math.clamp(end, 0, value.length());
		typing = false;
	}

	public boolean insert(String input, long now) {
		String text = clean(input, false);
		int start = selectionStart();
		int end = selectionEnd();
		int room = maxLength - (value.length() - (end - start));

		if (text.length() > room) {
			text = text.substring(0, Math.max(0, room));

			if (!text.isEmpty() && Character.isHighSurrogate(text.charAt(text.length() - 1))) {
				text = text.substring(0, text.length() - 1);
			}
		}

		if (text.isEmpty() && start == end) {
			return false;
		}

		boolean single = text.length() <= 2 && start == end;
		remember(single, now);
		value = value.substring(0, start) + text + value.substring(end);
		cursor = start + text.length();
		anchor = cursor;
		return true;
	}

	public boolean deleteBackward(boolean word, long now) {
		if (hasSelection()) {
			return insert("", now);
		}

		int target = word ? wordStart(cursor) : previous(cursor);

		if (target == cursor) {
			return false;
		}

		remember(false, now);
		value = value.substring(0, target) + value.substring(cursor);
		cursor = target;
		anchor = cursor;
		return true;
	}

	public boolean deleteForward(boolean word, long now) {
		if (hasSelection()) {
			return insert("", now);
		}

		int target = word ? wordEnd(cursor) : next(cursor);

		if (target == cursor) {
			return false;
		}

		remember(false, now);
		value = value.substring(0, cursor) + value.substring(target);
		anchor = cursor;
		return true;
	}

	public void moveHorizontal(int direction, boolean word, boolean select) {
		int target;

		if (!select && hasSelection() && !word) {
			target = direction < 0 ? selectionStart() : selectionEnd();
		} else if (word) {
			target = direction < 0 ? wordStart(cursor) : wordEnd(cursor);
		} else {
			target = direction < 0 ? previous(cursor) : next(cursor);
		}

		setCursor(target, select);
	}

	public boolean undo() {
		if (undo.isEmpty()) {
			return false;
		}

		redo.push(new Snapshot(value, cursor, anchor));
		Snapshot snapshot = undo.pop();
		restore(snapshot);
		return true;
	}

	public boolean redo() {
		if (redo.isEmpty()) {
			return false;
		}

		undo.push(new Snapshot(value, cursor, anchor));
		restore(redo.pop());
		return true;
	}

	private void restore(Snapshot snapshot) {
		value = snapshot.value;
		cursor = Math.min(snapshot.cursor, value.length());
		anchor = Math.min(snapshot.anchor, value.length());
		typing = false;
	}

	private void remember(boolean isTyping, long now) {
		if (!(isTyping && typing && now - lastTyping < MERGE_NANOS)) {
			undo.push(new Snapshot(value, cursor, anchor));

			while (undo.size() > HISTORY) {
				undo.removeLast();
			}
		}

		typing = isTyping;
		lastTyping = now;
		redo.clear();
	}

	private String limit(String text) {
		return text.length() > maxLength ? text.substring(0, maxLength) : text;
	}

	String clean(String input, boolean fromCode) {
		StringBuilder result = new StringBuilder(input.length());
		boolean decimalSeen = !fromCode && allow == Allow.DECIMAL && value.substring(0, selectionStart()).concat(value.substring(selectionEnd())).contains(".");

		for (int i = 0; i < input.length(); ) {
			int codepoint = input.codePointAt(i);
			i += Character.charCount(codepoint);

			if (codepoint == '\r') {
				continue;
			}

			if (codepoint == '\n') {
				if (multiline) {
					result.append('\n');
				}

				continue;
			}

			if (codepoint == '\t') {
				codepoint = ' ';
			}

			if (Character.isISOControl(codepoint) || codepoint == 0xA7) {
				continue;
			}

			if (!allowed(codepoint)) {
				continue;
			}

			if (allow == Allow.DECIMAL && codepoint == '.') {
				if (decimalSeen) {
					continue;
				}

				decimalSeen = true;
			}

			result.appendCodePoint(codepoint);
		}

		return result.toString();
	}

	private boolean allowed(int codepoint) {
		if (allowChars != null && allowChars.indexOf(codepoint) >= 0) {
			return true;
		}

		return switch (allow) {
			case ANY -> allowChars == null;
			case DIGITS -> codepoint >= '0' && codepoint <= '9';
			case DECIMAL -> codepoint >= '0' && codepoint <= '9' || codepoint == '.' || codepoint == '-';
			case LETTERS -> Character.isLetter(codepoint);
			case LETTERS_DIGITS -> Character.isLetterOrDigit(codepoint);
		};
	}

	private int previous(int position) {
		return position <= 0 ? 0 : value.offsetByCodePoints(position, -1);
	}

	private int next(int position) {
		return position >= value.length() ? value.length() : value.offsetByCodePoints(position, 1);
	}

	private static boolean isSpace(char c) {
		return c == ' ' || c == '\n';
	}

	public int wordStart(int position) {
		int result = position;

		while (result > 0 && isSpace(value.charAt(result - 1))) {
			result--;
		}

		while (result > 0 && !isSpace(value.charAt(result - 1))) {
			result--;
		}

		return result;
	}

	public int wordEnd(int position) {
		int result = position;
		int length = value.length();

		while (result < length && !isSpace(value.charAt(result))) {
			result++;
		}

		while (result < length && value.charAt(result) == ' ') {
			result++;
		}

		return result;
	}

	public void selectWord(int position) {
		int start = position;
		int end = position;

		while (start > 0 && !isSpace(value.charAt(start - 1))) {
			start--;
		}

		while (end < value.length() && !isSpace(value.charAt(end))) {
			end++;
		}

		select(start, end);
	}
}
