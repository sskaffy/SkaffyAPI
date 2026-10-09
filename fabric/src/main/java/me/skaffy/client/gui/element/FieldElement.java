package me.skaffy.client.gui.element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import me.skaffy.client.gui.element.GuiEnums.Cursor;
import me.skaffy.client.gui.element.GuiEnums.FontKind;
import me.skaffy.client.gui.element.GuiEvents.KeyInput;
import me.skaffy.client.gui.text.RichText;

public final class FieldElement extends BoxElement {
	public final TextEditor editor = new TextEditor();
	public RichText placeholder = RichText.EMPTY;
	public int placeholderColor = 0xFF7A7A7A;
	public int cursorColor = 0xFFFFFFFF;
	public int selectionColor = 0x803A7BD5;
	public boolean password;
	public boolean autoFocus;
	public boolean keepFocus;
	public boolean editable = true;
	public String suggestion;
	public FontKind font = FontKind.DEFAULT;
	public String customFont;
	public Consumer<Object> onChange;
	public Consumer<Object> onSubmit;
	public long debounceMillis;
	private boolean changePending;
	private long changeDue;
	public double scroll;
	public long caretReset;
	public List<int[]> lines = List.of();

	public FieldElement(GuiDocument document) {
		super(document);
		cursor = Cursor.TEXT;
		padTop = 4;
		padBottom = 4;
		padLeft = 5;
		padRight = 5;
		transitionMillis = 90;
		set(Prop.COLOR, 0xFF181818);
		set(Prop.BORDER_WIDTH, 1.0);
		set(Prop.BORDER_COLOR, 0xFF3A3A3A);
		set(Prop.RADIUS_TOP_LEFT, 2.0);
		set(Prop.RADIUS_TOP_RIGHT, 2.0);
		set(Prop.RADIUS_BOTTOM_RIGHT, 2.0);
		set(Prop.RADIUS_BOTTOM_LEFT, 2.0);
		focusedStyle = new PropSet();
		focusedStyle.put(Prop.BORDER_COLOR, 0xFF9A9A9A);
		hoverStyle = new PropSet();
		hoverStyle.put(Prop.BORDER_COLOR, 0xFF5A5A5A);
	}

	@Override
	public boolean focusable() {
		return !disabled;
	}

	@Override
	public boolean catchesMouse() {
		return mouseThrough == null || !mouseThrough;
	}

	public double lineHeight() {
		return number(Prop.FONT_SIZE) * 9 / 8;
	}

	public String display() {
		String value = editor.value();
		return password ? "*".repeat(value.codePointCount(0, value.length())) : value;
	}

	public double textWidth(String text) {
		return document.platform.textWidth(this, text);
	}

	private double widthTo(int start, int end) {
		String value = editor.value();

		if (password) {
			return textWidth("*".repeat(value.codePointCount(start, end)));
		}

		return textWidth(value.substring(start, end));
	}

	@Override
	double[] intrinsicSize(double innerWidth, double innerHeight) {
		double width = Double.isNaN(innerWidth) ? 150 - padLeft - padRight : innerWidth;

		if (editor.multiline) {
			wrapLines(width);
			return new double[] {width, lineHeight() * Math.max(3, lines.size())};
		}

		return new double[] {width, lineHeight()};
	}

	@Override
	void afterLayout() {
		if (editor.multiline) {
			wrapLines(innerWidth());
		}

		keepCursorVisible();
	}

	private void wrapLines(double width) {
		String value = editor.value();
		List<int[]> result = new ArrayList<>();
		int lineStart = 0;

		while (true) {
			int newline = value.indexOf('\n', lineStart);
			int paragraphEnd = newline < 0 ? value.length() : newline;
			int start = lineStart;

			while (true) {
				int end = fit(start, paragraphEnd, width);

				if (end >= paragraphEnd) {
					result.add(new int[] {start, paragraphEnd});
					break;
				}

				int breakAt = end;

				while (breakAt > start && value.charAt(breakAt - 1) != ' ') {
					breakAt--;
				}

				if (breakAt == start) {
					breakAt = Math.max(end, start + 1);
				}

				result.add(new int[] {start, breakAt});
				start = breakAt;
			}

			if (newline < 0) {
				break;
			}

			lineStart = newline + 1;
		}

		lines = result;
	}

	private int fit(int start, int end, double width) {
		String value = editor.value();

		if (widthTo(start, end) <= width) {
			return end;
		}

		int low = start;
		int high = end;

		while (low < high) {
			int middle = (low + high + 1) / 2;

			if (widthTo(start, middle) <= width) {
				low = middle;
			} else {
				high = middle - 1;
			}
		}

		if (low > start && low < value.length() && Character.isLowSurrogate(value.charAt(low))) {
			low--;
		}

		return low;
	}

	public int lineOf(int index) {
		for (int i = 0; i < lines.size(); i++) {
			int[] line = lines.get(i);
			boolean last = i == lines.size() - 1;

			if (index >= line[0] && (index < line[1] || index == line[1] && (last || lines.get(i + 1)[0] > index))) {
				return i;
			}
		}

		return Math.max(0, lines.size() - 1);
	}

	public double[] caretPosition(int index) {
		if (!editor.multiline) {
			return new double[] {widthTo(0, index), 0};
		}

		int line = lineOf(index);
		int[] bounds = lines.isEmpty() ? new int[] {0, 0} : lines.get(line);
		return new double[] {widthTo(bounds[0], Math.min(index, bounds[1])), line * lineHeight()};
	}

	public int indexAt(double x, double y) {
		int start = 0;
		int end = editor.value().length();

		if (editor.multiline && !lines.isEmpty()) {
			int line = Math.clamp((int) Math.floor(y / lineHeight()), 0, lines.size() - 1);
			start = lines.get(line)[0];
			end = lines.get(line)[1];
		}

		String value = editor.value();
		int best = start;
		double bestDistance = Double.MAX_VALUE;

		for (int i = start; i <= end; i = i < end ? value.offsetByCodePoints(i, 1) : end + 1) {
			double distance = Math.abs(widthTo(start, i) - x);

			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}

			if (i == end) {
				break;
			}
		}

		return best;
	}

	public void keepCursorVisible() {
		double[] caret = caretPosition(editor.cursor());

		if (editor.multiline) {
			double height = innerHeight();

			if (caret[1] < scroll) {
				scroll = caret[1];
			} else if (caret[1] + lineHeight() > scroll + height) {
				scroll = caret[1] + lineHeight() - height;
			}

			scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() * lineHeight() - height));
		} else {
			double width = innerWidth();

			if (caret[0] < scroll) {
				scroll = caret[0];
			} else if (caret[0] > scroll + width - 1) {
				scroll = caret[0] - width + 1;
			}

			scroll = Math.clamp(scroll, 0, Math.max(0, widthTo(0, editor.value().length()) - width + 1));
		}
	}

	void edited(long now) {
		caretReset = now;
		keepCursorVisible();

		if (onChange == null) {
			return;
		}

		if (debounceMillis > 0) {
			changePending = true;
			changeDue = now + debounceMillis * 1_000_000L;
		} else {
			onChange.accept(editor.value());
		}
	}

	@Override
	void tick(long now) {
		if (changePending && now >= changeDue) {
			changePending = false;
			onChange.accept(editor.value());
		}
	}

	boolean key(KeyInput key, long now) {
		boolean shortcut = document.platform.isMac() ? key.command() : key.control();
		boolean wordModifier = document.platform.isMac() ? key.alt() : key.control();
		boolean mac = document.platform.isMac();
		caretReset = now;

		switch (key.code) {
			case KeyInput.BACKSPACE -> {
				if (editable && editor.deleteBackward(wordModifier, now)) {
					edited(now);
				}

				return true;
			}
			case KeyInput.DELETE -> {
				if (editable && editor.deleteForward(wordModifier, now)) {
					edited(now);
				}

				return true;
			}
			case KeyInput.LEFT, KeyInput.RIGHT -> {
				int direction = key.code == KeyInput.LEFT ? -1 : 1;

				if (direction > 0 && suggestion != null && !suggestion.isEmpty() && editor.cursor() == editor.value().length() && !key.shift()) {
					acceptSuggestion(now);
					return true;
				}

				if (mac && key.command()) {
					if (direction < 0) {
						home(key.shift());
					} else {
						end(key.shift());
					}
				} else {
					editor.moveHorizontal(direction, wordModifier, key.shift());
				}

				keepCursorVisible();
				return true;
			}
			case KeyInput.HOME -> {
				home(key.shift());
				return true;
			}
			case KeyInput.END -> {
				end(key.shift());
				return true;
			}
			case KeyInput.UP, KeyInput.DOWN -> {
				if (!editor.multiline) {
					return false;
				}

				vertical(key.code == KeyInput.UP ? -1 : 1, key.shift());
				return true;
			}
			case KeyInput.ENTER, KeyInput.KEYPAD_ENTER -> {
				if (editor.multiline && !key.shift() && editable) {
					if (editor.insert("\n", now)) {
						edited(now);
					}

					return true;
				}

				if (onSubmit != null) {
					flushChange();
					onSubmit.accept(editor.value());
				}

				return true;
			}
			case KeyInput.TAB -> {
				if (suggestion != null && !suggestion.isEmpty() && !key.shift()) {
					acceptSuggestion(now);
					return true;
				}

				return false;
			}
			default -> {
			}
		}

		if (!shortcut || key.alt()) {
			return false;
		}

		switch (key.code) {
			case 'a' -> {
				editor.selectAll();
				return true;
			}
			case 'c' -> {
				if (!password && editor.hasSelection()) {
					document.platform.setClipboard(editor.selected());
				}

				return true;
			}
			case 'x' -> {
				if (!password && editor.hasSelection()) {
					document.platform.setClipboard(editor.selected());

					if (editable && editor.insert("", now)) {
						edited(now);
					}
				}

				return true;
			}
			case 'v' -> {
				if (editable && editor.insert(document.platform.clipboard(), now)) {
					edited(now);
				}

				return true;
			}
			case 'z' -> {
				if (editable && (key.shift() ? editor.redo() : editor.undo())) {
					edited(now);
				}

				return true;
			}
			case 'y' -> {
				if (!mac && editable && editor.redo()) {
					edited(now);
				}

				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private void acceptSuggestion(long now) {
		String text = suggestion;
		suggestion = null;

		if (editable && editor.insert(text, now)) {
			edited(now);
		}
	}

	void flushChange() {
		if (changePending) {
			changePending = false;
			onChange.accept(editor.value());
		}
	}

	private void home(boolean select) {
		int target = editor.multiline && !lines.isEmpty() ? lines.get(lineOf(editor.cursor()))[0] : 0;
		editor.setCursor(target, select);
		keepCursorVisible();
	}

	private void end(boolean select) {
		int target = editor.multiline && !lines.isEmpty() ? lines.get(lineOf(editor.cursor()))[1] : editor.value().length();
		editor.setCursor(target, select);
		keepCursorVisible();
	}

	private void vertical(int direction, boolean select) {
		int line = lineOf(editor.cursor());
		int target = line + direction;

		if (target < 0) {
			editor.setCursor(0, select);
		} else if (target >= lines.size()) {
			editor.setCursor(editor.value().length(), select);
		} else {
			double x = caretPosition(editor.cursor())[0];
			editor.setCursor(indexAt(x, target * lineHeight() + 1), select);
		}

		keepCursorVisible();
	}

	boolean typed(String text, long now) {
		if (!editable) {
			return false;
		}

		caretReset = now;

		if (editor.insert(text, now)) {
			edited(now);
		}

		return true;
	}

	public double[] unscrolled(double localX, double localY) {
		double x = localX - padLeft;
		double y = localY - padTop;
		return editor.multiline ? new double[] {x, y + scroll} : new double[] {x + scroll, y};
	}

	@Override
	public String typeName() {
		return "Field";
	}
}
