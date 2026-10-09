package me.skaffy.client.gui.element;

import me.skaffy.client.gui.element.GuiEnums.MouseButton;

public final class GuiEvents {
	private GuiEvents() {
	}

	public static final int SHIFT = 3;
	public static final int CONTROL = 192;
	public static final int ALT = 768;
	public static final int SUPER = 3072;

	public abstract static class Event {
		boolean stopped;

		public void stop() {
			stopped = true;
		}

		public boolean isStopped() {
			return stopped;
		}
	}

	public static final class MouseEvent extends Event {
		public final Element element;
		public final Element target;
		public final double x;
		public final double y;
		public final double screenX;
		public final double screenY;
		public final MouseButton button;
		public final int modifiers;

		public MouseEvent(Element element, Element target, double x, double y, double screenX, double screenY, MouseButton button, int modifiers) {
			this.element = element;
			this.target = target;
			this.x = x;
			this.y = y;
			this.screenX = screenX;
			this.screenY = screenY;
			this.button = button;
			this.modifiers = modifiers;
		}
	}

	public static final class ScrollEvent extends Event {
		public final Element element;
		public final double x;
		public final double y;
		public final double deltaX;
		public final double deltaY;
		public final int modifiers;

		public ScrollEvent(Element element, double x, double y, double deltaX, double deltaY, int modifiers) {
			this.element = element;
			this.x = x;
			this.y = y;
			this.deltaX = deltaX;
			this.deltaY = deltaY;
			this.modifiers = modifiers;
		}
	}

	public static final class KeyInput extends Event {
		public static final int BACKSPACE = 8;
		public static final int TAB = 9;
		public static final int ENTER = 13;
		public static final int ESCAPE = 27;
		public static final int SPACE = 32;
		public static final int DELETE = 127;
		public static final int RIGHT = 1073741903;
		public static final int LEFT = 1073741904;
		public static final int DOWN = 1073741905;
		public static final int UP = 1073741906;
		public static final int HOME = 1073741898;
		public static final int PAGE_UP = 1073741899;
		public static final int END = 1073741901;
		public static final int PAGE_DOWN = 1073741902;
		public static final int KEYPAD_ENTER = 1073741912;

		public final String name;
		public final int code;
		public final int modifiers;

		public KeyInput(String name, int code, int modifiers) {
			this.name = name;
			this.code = code;
			this.modifiers = modifiers;
		}

		public boolean shift() {
			return (modifiers & SHIFT) != 0;
		}

		public boolean control() {
			return (modifiers & CONTROL) != 0;
		}

		public boolean alt() {
			return (modifiers & ALT) != 0;
		}

		public boolean command() {
			return (modifiers & SUPER) != 0;
		}

		public boolean isEnter() {
			return code == ENTER || code == KEYPAD_ENTER;
		}
	}

	public static final class DropEvent extends Event {
		public final Element element;
		public final Element target;
		public final double screenX;
		public final double screenY;

		public DropEvent(Element element, Element target, double screenX, double screenY) {
			this.element = element;
			this.target = target;
			this.screenX = screenX;
			this.screenY = screenY;
		}
	}

	static MouseButton button(int sdlButton) {
		return switch (sdlButton) {
			case 1 -> MouseButton.LEFT;
			case 2 -> MouseButton.MIDDLE;
			case 3 -> MouseButton.RIGHT;
			default -> MouseButton.OTHER;
		};
	}
}
