package me.skaffy.client.gui.element;

public final class GuiEnums {
	private GuiEnums() {
	}

	public enum Align {
		START,
		CENTER,
		END,
		STRETCH
	}

	public enum Justify {
		START,
		CENTER,
		END,
		BETWEEN,
		AROUND,
		EVENLY
	}

	public enum TextAlign {
		LEFT,
		CENTER,
		RIGHT
	}

	public enum Cursor {
		DEFAULT,
		HAND,
		TEXT,
		CROSSHAIR,
		RESIZE_EW,
		RESIZE_NS,
		RESIZE_ALL,
		NOT_ALLOWED
	}

	public enum ImageMode {
		STRETCH,
		TILE,
		NINE_SLICE,
		FIT,
		FILL
	}

	public enum ScrollbarStyle {
		VANILLA,
		MODERN,
		CUSTOM,
		HIDDEN
	}

	public enum FontKind {
		DEFAULT,
		UNIFORM,
		ALT,
		ILLAGERALT
	}

	public enum Allow {
		ANY,
		DIGITS,
		DECIMAL,
		LETTERS,
		LETTERS_DIGITS
	}

	public enum Axis {
		BOTH,
		X,
		Y
	}

	public enum MouseButton {
		LEFT,
		RIGHT,
		MIDDLE,
		OTHER
	}

	public enum Blend {
		NORMAL,
		INVERT
	}

	public enum LayoutMode {
		NONE,
		ROW,
		COLUMN
	}
}
