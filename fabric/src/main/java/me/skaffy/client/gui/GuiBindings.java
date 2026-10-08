package me.skaffy.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.gui.element.Animation;
import me.skaffy.client.gui.element.BoxElement;
import me.skaffy.client.gui.element.ButtonElement;
import me.skaffy.client.gui.element.CanvasElement;
import me.skaffy.client.gui.element.Easing;
import me.skaffy.client.gui.element.Element;
import me.skaffy.client.gui.element.FieldElement;
import me.skaffy.client.gui.element.GuiDocument;
import me.skaffy.client.gui.element.GuiEnums;
import me.skaffy.client.gui.element.GuiEnums.LayoutMode;
import me.skaffy.client.gui.element.GuiEvents;
import me.skaffy.client.gui.element.GuiEvents.DropEvent;
import me.skaffy.client.gui.element.GuiEvents.KeyInput;
import me.skaffy.client.gui.element.GuiEvents.MouseEvent;
import me.skaffy.client.gui.element.GuiEvents.ScrollEvent;
import me.skaffy.client.gui.element.GuiTimer;
import me.skaffy.client.gui.element.PictureElements.EntityElement;
import me.skaffy.client.gui.element.PictureElements.HeadElement;
import me.skaffy.client.gui.element.PictureElements.ImageElement;
import me.skaffy.client.gui.element.PictureElements.ItemElement;
import me.skaffy.client.gui.element.Prop;
import me.skaffy.client.gui.element.PropSet;
import me.skaffy.client.gui.element.PropTarget;
import me.skaffy.client.gui.element.SliderElement;
import me.skaffy.client.gui.element.TextElement;
import me.skaffy.client.gui.element.WidgetElement;
import me.skaffy.client.gui.sfy.ColorValue;
import me.skaffy.client.gui.sfy.DurationValue;
import me.skaffy.client.gui.sfy.Fn;
import me.skaffy.client.gui.sfy.Invoker;
import me.skaffy.client.gui.sfy.LengthValue;
import me.skaffy.client.gui.sfy.ScriptException;
import me.skaffy.client.gui.sfy.SfyClass;
import me.skaffy.client.gui.sfy.SfyLibrary;
import me.skaffy.client.gui.text.MiniMessage;
import me.skaffy.client.gui.text.RichText;
import me.skaffy.protocol.gui.GuiPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

final class GuiBindings {
	private GuiBindings() {
	}


	static GuiSession session() {
		GuiSession session = ClientGuis.current();

		if (session == null) {
			throw new ScriptException("No GUI is open");
		}

		return session;
	}

	static GuiDocument document() {
		return session().document();
	}

	private static Consumer<Object> handler(Object fn) {
		return fn == null ? null : session().handler((Fn) fn);
	}

	private static double d(Object value) {
		return (Double) value;
	}

	private static int i(Object value) {
		return (Integer) value;
	}

	private static boolean b(Object value) {
		return (Boolean) value;
	}

	private static int color(Object value) {
		return value == null ? 0 : ((ColorValue) value).argb();
	}

	private static long ms(Object value) {
		return ((DurationValue) value).millis();
	}

	private static LengthValue length(Object value) {
		return (LengthValue) value;
	}

	private static String s(Object value) {
		return value == null ? "" : (String) value;
	}

	private static RichText rich(Object value) {
		return value instanceof RichText text ? text : RichText.plain(s(value));
	}

	private interface Setter {
		void set(Object self, Object[] args) throws Exception;
	}

	private static Invoker chain(Setter setter) {
		return (self, args) -> {
			setter.set(self, args);
			return self;
		};
	}

	record StyleHandle(Element element, int which) implements PropTarget {
		@Override
		public void set(Prop prop, Object value) {
			PropSet set = switch (which) {
				case 0 -> element.hoverStyle == null ? element.hoverStyle = new PropSet() : element.hoverStyle;
				case 1 -> element.pressedStyle == null ? element.pressedStyle = new PropSet() : element.pressedStyle;
				case 2 -> element.focusedStyle == null ? element.focusedStyle = new PropSet() : element.focusedStyle;
				default -> element.disabledStyle == null ? element.disabledStyle = new PropSet() : element.disabledStyle;
			};
			set.put(prop, value);
		}
	}


	static SfyLibrary create() {
		SfyLibrary lib = new SfyLibrary();
		lib.library("skaffy.gui");
		lib.annotation("Gui");
		lib.annotation("Hud");
		types(lib);
		props(lib, "Element");
		props(lib, "Style");
		props(lib, "Animation");
		element(lib);
		text(lib);
		pictures(lib);
		field(lib);
		button(lib);
		slider(lib);
		canvas(lib);
		widgets(lib);
		events(lib);
		misc(lib);
		globals(lib);
		HudData.register(lib);
		lib.onGuiSwitch((instance, method) -> {
			GuiSession session = ClientGuis.current();

			if (session != null) {
				session.switchTo(method);
			}
		});
		lib.onLog((level, message) -> {
			GuiSession session = ClientGuis.current();

			if (session != null) {
				session.log(level, message);
			} else {
				SkaffySAPIClient.LOGGER.info("[GUI] {}", message);
			}
		});
		return lib;
	}

	private static void types(SfyLibrary lib) {
		lib.type("Element", null, Element.class);
		lib.type("Box", "Element", BoxElement.class);
		lib.type("Text", "Element", TextElement.class);
		lib.type("Image", "Element", ImageElement.class);
		lib.type("Head", "Element", HeadElement.class);
		lib.type("Item", "Element", ItemElement.class);
		lib.type("EntityView", "Element", EntityElement.class);
		lib.type("Field", "Box", FieldElement.class);
		lib.type("Button", "Box", ButtonElement.class);
		lib.type("Slider", "Element", SliderElement.class);
		lib.type("Canvas", "Element", CanvasElement.class);
		lib.type("VanillaButton", "Element", WidgetElement.class);
		lib.type("VanillaField", "Element", WidgetElement.class);
		lib.type("VanillaSlider", "Element", WidgetElement.class);
		lib.type("Checkbox", "Element", WidgetElement.class);
		lib.type("CycleButton", "Element", WidgetElement.class);
		lib.type("Graphics", null, CanvasElement.Graphics.class);
		lib.type("Style", null, StyleHandle.class);
		lib.type("Animation", null, Animation.class);
		lib.type("Timer", null, GuiTimer.class);
		lib.type("MouseEvent", null, MouseEvent.class);
		lib.type("ScrollEvent", null, ScrollEvent.class);
		lib.type("KeyEvent", null, KeyInput.class);
		lib.type("DropEvent", null, DropEvent.class);
		lib.type("RichText", null, RichText.class);
		lib.enumType("Align", GuiEnums.Align.class);
		lib.enumType("Justify", GuiEnums.Justify.class);
		lib.enumType("TextAlign", GuiEnums.TextAlign.class);
		lib.enumType("Cursor", GuiEnums.Cursor.class);
		lib.enumType("Easing", Easing.class);
		lib.enumType("ImageMode", GuiEnums.ImageMode.class);
		lib.enumType("ScrollbarStyle", GuiEnums.ScrollbarStyle.class);
		lib.enumType("Font", GuiEnums.FontKind.class);
		lib.enumType("Allow", GuiEnums.Allow.class);
		lib.enumType("Axis", GuiEnums.Axis.class);
		lib.enumType("MouseButton", GuiEnums.MouseButton.class);
		lib.enumType("Blend", GuiEnums.Blend.class);
	}

	private static void props(SfyLibrary lib, String owner) {
		lib.method(owner, "color", "SELF", "Color color", chain((self, a) -> ((PropTarget) self).set(Prop.COLOR, color(a[0]))));
		lib.method(owner, "background", "SELF", "Color color", chain((self, a) -> ((PropTarget) self).set(Prop.BACKGROUND, color(a[0]))));
		lib.method(owner, "textColor", "SELF", "Color color", chain((self, a) -> ((PropTarget) self).set(Prop.TEXT_COLOR, color(a[0]))));
		lib.method(owner, "transparency", "SELF", "double transparency", chain((self, a) -> ((PropTarget) self).set(Prop.TRANSPARENCY, Math.clamp(d(a[0]), 0, 100))));
		lib.method(owner, "border", "SELF", "double width, Color color", chain((self, a) -> {
			((PropTarget) self).set(Prop.BORDER_WIDTH, Math.max(0, d(a[0])));
			((PropTarget) self).set(Prop.BORDER_COLOR, color(a[1]));
		}));
		lib.method(owner, "borderColor", "SELF", "Color color", chain((self, a) -> ((PropTarget) self).set(Prop.BORDER_COLOR, color(a[0]))));
		lib.method(owner, "radius", "SELF", "double radius", chain((self, a) -> {
			PropTarget target = (PropTarget) self;
			double radius = Math.max(0, d(a[0]));
			target.set(Prop.RADIUS_TOP_LEFT, radius);
			target.set(Prop.RADIUS_TOP_RIGHT, radius);
			target.set(Prop.RADIUS_BOTTOM_RIGHT, radius);
			target.set(Prop.RADIUS_BOTTOM_LEFT, radius);
		}));
		lib.method(owner, "radius", "SELF", "double topLeft, double topRight, double bottomRight, double bottomLeft", chain((self, a) -> {
			PropTarget target = (PropTarget) self;
			target.set(Prop.RADIUS_TOP_LEFT, Math.max(0, d(a[0])));
			target.set(Prop.RADIUS_TOP_RIGHT, Math.max(0, d(a[1])));
			target.set(Prop.RADIUS_BOTTOM_RIGHT, Math.max(0, d(a[2])));
			target.set(Prop.RADIUS_BOTTOM_LEFT, Math.max(0, d(a[3])));
		}));
		lib.method(owner, "shadow", "SELF", "Color color, double blur", chain((self, a) -> shadow((PropTarget) self, a[0], d(a[1]), 0, 0, 0)));
		lib.method(owner, "shadow", "SELF", "Color color, double blur, double x, double y", chain((self, a) -> shadow((PropTarget) self, a[0], d(a[1]), d(a[2]), d(a[3]), 0)));
		lib.method(owner, "shadow", "SELF", "Color color, double blur, double x, double y, double spread", chain((self, a) -> shadow((PropTarget) self, a[0], d(a[1]), d(a[2]), d(a[3]), d(a[4]))));
		lib.method(owner, "offset", "SELF", "double x, double y", chain((self, a) -> {
			((PropTarget) self).set(Prop.OFFSET_X, d(a[0]));
			((PropTarget) self).set(Prop.OFFSET_Y, d(a[1]));
		}));
		lib.method(owner, "rotate", "SELF", "double degrees", chain((self, a) -> ((PropTarget) self).set(Prop.ROTATION, d(a[0]))));
		lib.method(owner, "scale", "SELF", "double scale", chain((self, a) -> ((PropTarget) self).set(Prop.SCALE, d(a[0]))));
		lib.method(owner, "pos", "SELF", "Length x, Length y", chain((self, a) -> {
			((PropTarget) self).set(Prop.X, length(a[0]));
			((PropTarget) self).set(Prop.Y, length(a[1]));
		}));
		lib.method(owner, "x", "SELF", "Length x", chain((self, a) -> ((PropTarget) self).set(Prop.X, length(a[0]))));
		lib.method(owner, "y", "SELF", "Length y", chain((self, a) -> ((PropTarget) self).set(Prop.Y, length(a[0]))));
		lib.method(owner, "size", "SELF", "Length width, Length height", chain((self, a) -> {
			((PropTarget) self).set(Prop.WIDTH, a[0]);
			((PropTarget) self).set(Prop.HEIGHT, a[1]);
		}));
		lib.method(owner, "width", "SELF", "Length width", chain((self, a) -> ((PropTarget) self).set(Prop.WIDTH, a[0])));
		lib.method(owner, "height", "SELF", "Length height", chain((self, a) -> ((PropTarget) self).set(Prop.HEIGHT, a[0])));
		lib.method(owner, "fontSize", "SELF", "double size", chain((self, a) -> ((PropTarget) self).set(Prop.FONT_SIZE, Math.max(0.1, d(a[0])))));
	}

	private static void shadow(PropTarget target, Object color, double blur, double x, double y, double spread) {
		target.set(Prop.SHADOW_COLOR, color(color));
		target.set(Prop.SHADOW_BLUR, Math.max(0, blur));
		target.set(Prop.SHADOW_X, x);
		target.set(Prop.SHADOW_Y, y);
		target.set(Prop.SHADOW_SPREAD, spread);
	}

	private static Element el(Object self) {
		return (Element) self;
	}

	private static void element(SfyLibrary lib) {
		String e = "Element";
		lib.method(e, "x", "double", "", (self, a) -> el(self).lx);
		lib.method(e, "y", "double", "", (self, a) -> el(self).ly);
		lib.method(e, "width", "double", "", (self, a) -> el(self).lw);
		lib.method(e, "height", "double", "", (self, a) -> el(self).lh);
		lib.method(e, "screenX", "double", "", (self, a) -> el(self).matrix.x(0, 0) / document().scaleFactor);
		lib.method(e, "screenY", "double", "", (self, a) -> el(self).matrix.y(0, 0) / document().scaleFactor);
		lib.method(e, "color", "Color", "", (self, a) -> new ColorValue(el(self).color(Prop.COLOR)));
		lib.method(e, "padding", "SELF", "double all", chain((self, a) -> padding(el(self), d(a[0]), d(a[0]), d(a[0]), d(a[0]))));
		lib.method(e, "padding", "SELF", "double vertical, double horizontal", chain((self, a) -> padding(el(self), d(a[0]), d(a[1]), d(a[0]), d(a[1]))));
		lib.method(e, "padding", "SELF", "double top, double right, double bottom, double left", chain((self, a) -> padding(el(self), d(a[0]), d(a[1]), d(a[2]), d(a[3]))));
		lib.method(e, "row", "SELF", "", chain((self, a) -> el(self).layout = LayoutMode.ROW));
		lib.method(e, "row", "SELF", "double gap", chain((self, a) -> {
			el(self).layout = LayoutMode.ROW;
			el(self).gap = Math.max(0, d(a[0]));
		}));
		lib.method(e, "column", "SELF", "", chain((self, a) -> el(self).layout = LayoutMode.COLUMN));
		lib.method(e, "column", "SELF", "double gap", chain((self, a) -> {
			el(self).layout = LayoutMode.COLUMN;
			el(self).gap = Math.max(0, d(a[0]));
		}));
		lib.method(e, "gap", "SELF", "double gap", chain((self, a) -> el(self).gap = Math.max(0, d(a[0]))));
		lib.method(e, "wrap", "SELF", "boolean wrap", chain((self, a) -> el(self).wrap = b(a[0])));
		lib.method(e, "alignItems", "SELF", "Align align", chain((self, a) -> el(self).alignItems = (GuiEnums.Align) a[0]));
		lib.method(e, "justify", "SELF", "Justify justify", chain((self, a) -> el(self).justify = (GuiEnums.Justify) a[0]));
		lib.method(e, "absolute", "SELF", "boolean absolute", chain((self, a) -> el(self).absolute = b(a[0])));
		lib.method(e, "fixed", "SELF", "boolean fixed", chain((self, a) -> el(self).fixed = b(a[0])));
		lib.method(e, "z", "SELF", "int z", chain((self, a) -> el(self).z = i(a[0])));
		lib.method(e, "origin", "SELF", "Length x, Length y", chain((self, a) -> {
			el(self).originX = length(a[0]);
			el(self).originY = length(a[1]);
		}));
		lib.method(e, "visible", "SELF", "boolean visible", chain((self, a) -> el(self).visible = b(a[0])));
		lib.method(e, "show", "SELF", "", chain((self, a) -> el(self).visible = true));
		lib.method(e, "hide", "SELF", "", chain((self, a) -> el(self).visible = false));
		lib.method(e, "isVisible", "boolean", "", (self, a) -> el(self).visible);
		lib.method(e, "disabled", "SELF", "boolean disabled", chain((self, a) -> el(self).disabled = b(a[0])));
		lib.method(e, "isDisabled", "boolean", "", (self, a) -> el(self).disabled);
		lib.method(e, "clip", "SELF", "boolean clip", chain((self, a) -> el(self).clip = b(a[0])));
		lib.method(e, "blend", "SELF", "Blend blend", chain((self, a) -> el(self).invert = a[0] == GuiEnums.Blend.INVERT));
		lib.method(e, "scroll", "SELF", "boolean scroll", chain((self, a) -> el(self).scrollable = b(a[0])));
		lib.method(e, "scrollbar", "SELF", "ScrollbarStyle style", chain((self, a) -> el(self).scrollbar = (GuiEnums.ScrollbarStyle) a[0]));
		lib.method(e, "scrollbarColors", "SELF", "Color thumb, Color track", chain((self, a) -> {
			el(self).scrollbarThumb = color(a[0]);
			el(self).scrollbarTrack = color(a[1]);
		}));
		lib.method(e, "scrollbarWidth", "SELF", "double width", chain((self, a) -> el(self).scrollbarWidth = Math.max(1, d(a[0]))));
		lib.method(e, "scrollTo", "SELF", "double x, double y", chain((self, a) -> el(self).scrollTo(d(a[0]), d(a[1]))));
		lib.method(e, "scrollX", "double", "", (self, a) -> el(self).scrollX);
		lib.method(e, "scrollY", "double", "", (self, a) -> el(self).scrollY);
		lib.method(e, "cursor", "SELF", "Cursor cursor", chain((self, a) -> el(self).cursor = (GuiEnums.Cursor) a[0]));
		lib.method(e, "tooltip", "SELF", "String text", chain((self, a) -> el(self).tooltip = a[0] == null ? null : RichText.plain(s(a[0]))));
		lib.method(e, "tooltip", "SELF", "RichText text", chain((self, a) -> el(self).tooltip = (RichText) a[0]));
		lib.method(e, "mouseThrough", "SELF", "boolean through", chain((self, a) -> el(self).mouseThrough = b(a[0])));
		lib.method(e, "draggable", "SELF", "boolean draggable", chain((self, a) -> el(self).draggable = b(a[0])));
		lib.method(e, "dragAxis", "SELF", "Axis axis", chain((self, a) -> el(self).dragAxis = (GuiEnums.Axis) a[0]));
		lib.method(e, "dragInParent", "SELF", "boolean inParent", chain((self, a) -> el(self).dragInParent = b(a[0])));
		lib.method(e, "tabIndex", "SELF", "int index", chain((self, a) -> el(self).tabIndex = i(a[0])));
		lib.method(e, "pixelCorners", "SELF", "boolean pixel", chain((self, a) -> el(self).pixelCorners = b(a[0])));
		lib.method(e, "gradient", "SELF", "Color... colors", chain((self, a) -> gradient(el(self), 180, (Object[]) a[0])));
		lib.method(e, "gradient", "SELF", "double angle, Color... colors", chain((self, a) -> gradient(el(self), d(a[0]), (Object[]) a[1])));
		lib.method(e, "noGradient", "SELF", "", chain((self, a) -> el(self).gradient = null));
		lib.method(e, "add", "ARG0", "Element child", (self, a) -> {
			if (a[0] == null) {
				throw new ScriptException("Can't add null");
			}

			el(self).add(el(a[0]));
			return a[0];
		});
		lib.method(e, "remove", "void", "", (self, a) -> {
			el(self).remove();
			return null;
		});
		lib.method(e, "clear", "SELF", "", chain((self, a) -> el(self).clear()));
		lib.method(e, "children", "List<Element>", "", (self, a) -> new ArrayList<Object>(el(self).children()));
		lib.method(e, "parent", "Element", "", (self, a) -> {
			Element parent = el(self).parent();
			return parent == null || parent == document().root ? null : parent;
		});
		lib.method(e, "focus", "SELF", "", chain((self, a) -> document().setFocus(el(self))));
		lib.method(e, "blur", "SELF", "", chain((self, a) -> {
			if (document().focused() == el(self)) {
				document().setFocus(null);
			}
		}));
		lib.method(e, "isFocused", "boolean", "", (self, a) -> el(self).isFocused());
		lib.method(e, "isHovered", "boolean", "", (self, a) -> el(self).isHovered());
		lib.method(e, "isPressed", "boolean", "", (self, a) -> el(self).isPressed());
		lib.method(e, "hover", "Style", "", (self, a) -> new StyleHandle(el(self), 0));
		lib.method(e, "pressed", "Style", "", (self, a) -> new StyleHandle(el(self), 1));
		lib.method(e, "focused", "Style", "", (self, a) -> new StyleHandle(el(self), 2));
		lib.method(e, "disabledStyle", "Style", "", (self, a) -> new StyleHandle(el(self), 3));
		lib.method(e, "transition", "SELF", "Duration duration", chain((self, a) -> el(self).transitionMillis = Math.max(0, ms(a[0]))));
		lib.method(e, "transition", "SELF", "Duration duration, Easing easing", chain((self, a) -> {
			el(self).transitionMillis = Math.max(0, ms(a[0]));
			el(self).transitionEasing = (Easing) a[1];
		}));
		lib.method(e, "animate", "Animation", "Duration duration", (self, a) -> el(self).animate(ms(a[0])));

		for (String event : List.of("onClick", "onRightClick", "onDoubleClick", "onPress", "onRelease", "onHover", "onLeave", "onMouseMove", "onDragStart", "onDrag")) {
			Invoker set = chain((self, a) -> setEvent(el(self), event, handler(a[0])));
			lib.method(e, event, "SELF", "Runnable handler", set);
			lib.method(e, event, "SELF", "Consumer<MouseEvent> handler", set);
		}

		lib.method(e, "onScroll", "SELF", "Consumer<ScrollEvent> handler", chain((self, a) -> el(self).onScroll = handler(a[0])));
		lib.method(e, "onDrop", "SELF", "Consumer<DropEvent> handler", chain((self, a) -> el(self).onDrop = handler(a[0])));
		lib.method(e, "onFocus", "SELF", "Runnable handler", chain((self, a) -> el(self).onFocus = handler(a[0])));
		lib.method(e, "onBlur", "SELF", "Runnable handler", chain((self, a) -> el(self).onBlur = handler(a[0])));
	}

	private static void setEvent(Element element, String event, Consumer<Object> handler) {
		switch (event) {
			case "onClick" -> element.onClick = handler;
			case "onRightClick" -> element.onRightClick = handler;
			case "onDoubleClick" -> element.onDoubleClick = handler;
			case "onPress" -> element.onPress = handler;
			case "onRelease" -> element.onRelease = handler;
			case "onHover" -> element.onHover = handler;
			case "onLeave" -> element.onLeave = handler;
			case "onMouseMove" -> element.onMouseMove = handler;
			case "onDragStart" -> element.onDragStart = handler;
			case "onDrag" -> element.onDrag = handler;
			default -> throw new IllegalArgumentException(event);
		}
	}

	private static void padding(Element element, double top, double right, double bottom, double left) {
		element.padTop = Math.max(0, top);
		element.padRight = Math.max(0, right);
		element.padBottom = Math.max(0, bottom);
		element.padLeft = Math.max(0, left);
	}

	private static void gradient(Element element, double angle, Object[] colors) {
		if (colors.length == 0) {
			element.gradient = null;
			return;
		}

		if (colors.length > 16) {
			throw new ScriptException("A gradient has at most 16 colors");
		}

		int[] argb = new int[Math.max(2, colors.length)];

		for (int i = 0; i < argb.length; i++) {
			argb[i] = color(colors[Math.min(i, colors.length - 1)]);
		}

		element.gradient = argb;
		element.gradientAngle = angle;
	}

	private static TextElement tx(Object self) {
		return (TextElement) self;
	}

	private static void text(SfyLibrary lib) {
		String t = "Text";
		lib.method(t, "text", "SELF", "String text", chain((self, a) -> tx(self).setText(RichText.plain(s(a[0])))));
		lib.method(t, "text", "SELF", "RichText text", chain((self, a) -> tx(self).setText((RichText) a[0])));
		lib.method(t, "text", "String", "", (self, a) -> tx(self).text.plain());
		lib.method(t, "font", "SELF", "Font font", chain((self, a) -> {
			tx(self).font = (GuiEnums.FontKind) a[0];
			tx(self).customFont = null;
		}));
		lib.method(t, "font", "SELF", "String name", chain((self, a) -> tx(self).customFont = s(a[0])));
		lib.method(t, "bold", "SELF", "boolean bold", chain((self, a) -> tx(self).bold = b(a[0])));
		lib.method(t, "italic", "SELF", "boolean italic", chain((self, a) -> tx(self).italic = b(a[0])));
		lib.method(t, "underline", "SELF", "boolean underline", chain((self, a) -> tx(self).underline = b(a[0])));
		lib.method(t, "strikethrough", "SELF", "boolean strikethrough", chain((self, a) -> tx(self).strikethrough = b(a[0])));
		lib.method(t, "obfuscated", "SELF", "boolean obfuscated", chain((self, a) -> tx(self).obfuscated = b(a[0])));
		lib.method(t, "textShadow", "SELF", "boolean shadow", chain((self, a) -> tx(self).textShadow = b(a[0])));
		lib.method(t, "textShadowColor", "SELF", "Color color", chain((self, a) -> {
			tx(self).textShadow = true;
			tx(self).textShadowColor = a[0] == null ? null : color(a[0]);
		}));
		lib.method(t, "align", "SELF", "TextAlign align", chain((self, a) -> tx(self).align = (GuiEnums.TextAlign) a[0]));
		lib.method(t, "maxWidth", "SELF", "Length width", chain((self, a) -> tx(self).maxWidth = (LengthValue) a[0]));
		lib.method(t, "lineSpacing", "SELF", "double spacing", chain((self, a) -> tx(self).lineSpacing = Math.max(0.1, d(a[0]))));
	}

	private static void pictures(SfyLibrary lib) {
		lib.method("Image", "source", "SELF", "String source", chain((self, a) -> ((ImageElement) self).source = s(a[0])));
		lib.method("Image", "mode", "SELF", "ImageMode mode", chain((self, a) -> ((ImageElement) self).mode = (GuiEnums.ImageMode) a[0]));
		lib.method("Image", "nineSlice", "SELF", "int left, int top, int right, int bottom", chain((self, a) -> {
			ImageElement image = (ImageElement) self;
			image.mode = GuiEnums.ImageMode.NINE_SLICE;
			image.slice = new int[] {Math.max(0, i(a[0])), Math.max(0, i(a[1])), Math.max(0, i(a[2])), Math.max(0, i(a[3]))};
		}));
		lib.method("Image", "region", "SELF", "int u, int v, int width, int height", chain((self, a) ->
				((ImageElement) self).region = new int[] {i(a[0]), i(a[1]), Math.max(1, i(a[2])), Math.max(1, i(a[3]))}));
		lib.method("Head", "player", "SELF", "String player", chain((self, a) -> ((HeadElement) self).player = s(a[0])));
		lib.method("Head", "hat", "SELF", "boolean hat", chain((self, a) -> ((HeadElement) self).hat = b(a[0])));
		lib.method("Item", "item", "SELF", "String item", chain((self, a) -> ((ItemElement) self).item = s(a[0])));
		lib.method("Item", "count", "SELF", "int count", chain((self, a) -> ((ItemElement) self).count = Math.clamp(i(a[0]), 1, 99)));
		lib.method("Item", "decorations", "SELF", "boolean decorations", chain((self, a) -> ((ItemElement) self).decorations = b(a[0])));
		lib.method("Item", "itemTooltip", "SELF", "boolean tooltip", chain((self, a) -> ((ItemElement) self).itemTooltip = b(a[0])));
		lib.method("EntityView", "followMouse", "SELF", "boolean follow", chain((self, a) -> ((EntityElement) self).followMouse = b(a[0])));
		lib.method("EntityView", "rotation", "SELF", "double yaw, double pitch", chain((self, a) -> {
			EntityElement entity = (EntityElement) self;
			entity.followMouse = false;
			entity.yaw = d(a[0]);
			entity.pitch = d(a[1]);
		}));
	}

	private static FieldElement fe(Object self) {
		return (FieldElement) self;
	}

	private static void field(SfyLibrary lib) {
		String f = "Field";
		lib.method(f, "text", "SELF", "String text", chain((self, a) -> {
			fe(self).editor.setValue(s(a[0]));
			fe(self).keepCursorVisible();
		}));
		lib.method(f, "text", "String", "", (self, a) -> fe(self).editor.value());
		lib.method(f, "placeholder", "SELF", "String text", chain((self, a) -> fe(self).placeholder = RichText.plain(s(a[0]))));
		lib.method(f, "placeholder", "SELF", "RichText text", chain((self, a) -> fe(self).placeholder = (RichText) a[0]));
		lib.method(f, "maxLength", "SELF", "int length", chain((self, a) -> fe(self).editor.maxLength = Math.clamp(i(a[0]), 0, 32767)));
		lib.method(f, "multiline", "SELF", "boolean multiline", chain((self, a) -> fe(self).editor.multiline = b(a[0])));
		lib.method(f, "password", "SELF", "boolean password", chain((self, a) -> fe(self).password = b(a[0])));
		lib.method(f, "allow", "SELF", "Allow allow", chain((self, a) -> fe(self).editor.allow = (GuiEnums.Allow) a[0]));
		lib.method(f, "allowChars", "SELF", "String characters", chain((self, a) -> fe(self).editor.allowChars = (String) a[0]));
		lib.method(f, "autoFocus", "SELF", "boolean autoFocus", chain((self, a) -> fe(self).autoFocus = b(a[0])));
		lib.method(f, "keepFocus", "SELF", "boolean keepFocus", chain((self, a) -> fe(self).keepFocus = b(a[0])));
		lib.method(f, "editable", "SELF", "boolean editable", chain((self, a) -> fe(self).editable = b(a[0])));
		lib.method(f, "suggestion", "SELF", "String suggestion", chain((self, a) -> fe(self).suggestion = (String) a[0]));
		lib.method(f, "placeholderColor", "SELF", "Color color", chain((self, a) -> fe(self).placeholderColor = color(a[0])));
		lib.method(f, "cursorColor", "SELF", "Color color", chain((self, a) -> fe(self).cursorColor = color(a[0])));
		lib.method(f, "selectionColor", "SELF", "Color color", chain((self, a) -> fe(self).selectionColor = color(a[0])));
		lib.method(f, "font", "SELF", "Font font", chain((self, a) -> {
			fe(self).font = (GuiEnums.FontKind) a[0];
			fe(self).customFont = null;
		}));
		lib.method(f, "font", "SELF", "String name", chain((self, a) -> fe(self).customFont = s(a[0])));
		lib.method(f, "selectAll", "SELF", "", chain((self, a) -> fe(self).editor.selectAll()));
		lib.method(f, "caret", "SELF", "int position", chain((self, a) -> {
			fe(self).editor.setCursor(i(a[0]), false);
			fe(self).keepCursorVisible();
		}));
		lib.method(f, "caret", "int", "", (self, a) -> fe(self).editor.cursor());
		lib.method(f, "onChange", "SELF", "Consumer<String> handler", chain((self, a) -> {
			fe(self).onChange = handler(a[0]);
			fe(self).debounceMillis = 0;
		}));
		lib.method(f, "onChange", "SELF", "Duration wait, Consumer<String> handler", chain((self, a) -> {
			fe(self).onChange = handler(a[1]);
			fe(self).debounceMillis = Math.max(0, ms(a[0]));
		}));
		lib.method(f, "onSubmit", "SELF", "Consumer<String> handler", chain((self, a) -> fe(self).onSubmit = handler(a[0])));
	}

	private static void button(SfyLibrary lib) {
		lib.method("Button", "label", "SELF", "String label", chain((self, a) -> ((ButtonElement) self).label.setText(RichText.plain(s(a[0])))));
		lib.method("Button", "label", "SELF", "RichText label", chain((self, a) -> ((ButtonElement) self).label.setText((RichText) a[0])));
		lib.method("Button", "labelText", "Text", "", (self, a) -> ((ButtonElement) self).label);
		lib.method("Button", "sound", "SELF", "String sound", chain((self, a) -> ((ButtonElement) self).sound = (String) a[0]));
	}

	private static SliderElement sl(Object self) {
		return (SliderElement) self;
	}

	private static void slider(SfyLibrary lib) {
		String s = "Slider";
		lib.method(s, "value", "SELF", "double value", chain((self, a) -> sl(self).setValue(d(a[0]))));
		lib.method(s, "value", "double", "", (self, a) -> sl(self).value);
		lib.method(s, "min", "SELF", "double min", chain((self, a) -> {
			sl(self).min = d(a[0]);
			sl(self).setValue(sl(self).value);
		}));
		lib.method(s, "max", "SELF", "double max", chain((self, a) -> {
			sl(self).max = d(a[0]);
			sl(self).setValue(sl(self).value);
		}));
		lib.method(s, "step", "SELF", "double step", chain((self, a) -> {
			sl(self).step = Math.max(0, d(a[0]));
			sl(self).setValue(sl(self).value);
		}));
		lib.method(s, "vertical", "SELF", "boolean vertical", chain((self, a) -> sl(self).vertical = b(a[0])));
		lib.method(s, "track", "Box", "", (self, a) -> sl(self).track);
		lib.method(s, "fill", "Box", "", (self, a) -> sl(self).fill);
		lib.method(s, "knob", "Box", "", (self, a) -> sl(self).knob);
		lib.method(s, "onChange", "SELF", "Consumer<Double> handler", chain((self, a) -> sl(self).onChange = handler(a[0])));
		lib.method(s, "onDone", "SELF", "Consumer<Double> handler", chain((self, a) -> sl(self).onDone = handler(a[0])));
	}

	private static CanvasElement.Graphics g(Object self) {
		return (CanvasElement.Graphics) self;
	}

	private static void canvas(SfyLibrary lib) {
		lib.method("Canvas", "draw", "SELF", "Consumer<Graphics> draw", chain((self, a) -> ((CanvasElement) self).draw = handler(a[0])));
		String g = "Graphics";
		lib.method(g, "width", "double", "", (self, a) -> g(self).width());
		lib.method(g, "height", "double", "", (self, a) -> g(self).height());
		lib.method(g, "rect", "void", "double x, double y, double width, double height, Color color", (self, a) -> {
			g(self).rect(d(a[0]), d(a[1]), d(a[2]), d(a[3]), color(a[4]));
			return null;
		});
		lib.method(g, "roundRect", "void", "double x, double y, double width, double height, double radius, Color color", (self, a) -> {
			g(self).roundRect(d(a[0]), d(a[1]), d(a[2]), d(a[3]), d(a[4]), color(a[5]));
			return null;
		});
		lib.method(g, "line", "void", "double x1, double y1, double x2, double y2, double width, Color color", (self, a) -> {
			g(self).line(d(a[0]), d(a[1]), d(a[2]), d(a[3]), d(a[4]), color(a[5]));
			return null;
		});
		lib.method(g, "circle", "void", "double x, double y, double radius, Color color", (self, a) -> {
			g(self).circle(d(a[0]), d(a[1]), d(a[2]), color(a[3]));
			return null;
		});
		lib.method(g, "ring", "void", "double x, double y, double radius, double width, Color color", (self, a) -> {
			g(self).ring(d(a[0]), d(a[1]), d(a[2]), d(a[3]), color(a[4]));
			return null;
		});
		lib.method(g, "image", "void", "String source, double x, double y, double width, double height", (self, a) -> {
			g(self).image(s(a[0]), d(a[1]), d(a[2]), d(a[3]), d(a[4]));
			return null;
		});
		lib.method(g, "text", "void", "String text, double x, double y, Color color", (self, a) -> {
			g(self).text(RichText.plain(s(a[0])), d(a[1]), d(a[2]), color(a[3]), 8);
			return null;
		});
		lib.method(g, "text", "void", "RichText text, double x, double y, Color color", (self, a) -> {
			g(self).text((RichText) a[0], d(a[1]), d(a[2]), color(a[3]), 8);
			return null;
		});
		lib.method(g, "text", "void", "String text, double x, double y, Color color, double size", (self, a) -> {
			g(self).text(RichText.plain(s(a[0])), d(a[1]), d(a[2]), color(a[3]), Math.max(0.1, d(a[4])));
			return null;
		});
	}

	private static <T> T peer(Object self, Class<T> type) {
		WidgetElement element = (WidgetElement) self;

		if (!type.isInstance(element.peer)) {
			throw new ScriptException("This " + element.typeName() + " can't do that");
		}

		return type.cast(element.peer);
	}

	private static void widgets(SfyLibrary lib) {
		lib.method("VanillaButton", "label", "SELF", "String label", chain((self, a) -> peer(self, VanillaPeers.ButtonPeer.class).label(s(a[0]))));

		String f = "VanillaField";
		lib.method(f, "text", "SELF", "String text", chain((self, a) -> peer(self, VanillaPeers.FieldPeer.class).box().setValue(s(a[0]))));
		lib.method(f, "text", "String", "", (self, a) -> peer(self, VanillaPeers.FieldPeer.class).box().getValue());
		lib.method(f, "placeholder", "SELF", "String text", chain((self, a) -> peer(self, VanillaPeers.FieldPeer.class).box().setHint(net.minecraft.network.chat.Component.literal(s(a[0])))));
		lib.method(f, "maxLength", "SELF", "int length", chain((self, a) -> peer(self, VanillaPeers.FieldPeer.class).box().setMaxLength(Math.clamp(i(a[0]), 1, 32767))));
		lib.method(f, "editable", "SELF", "boolean editable", chain((self, a) -> peer(self, VanillaPeers.FieldPeer.class).box().setEditable(b(a[0]))));
		lib.method(f, "bordered", "SELF", "boolean bordered", chain((self, a) -> peer(self, VanillaPeers.FieldPeer.class).box().setBordered(b(a[0]))));
		lib.method(f, "onChange", "SELF", "Consumer<String> handler", chain((self, a) -> peer(self, VanillaPeers.FieldPeer.class).onChange = handler(a[0])));
		lib.method(f, "onSubmit", "SELF", "Consumer<String> handler", chain((self, a) -> peer(self, VanillaPeers.FieldPeer.class).onSubmit = handler(a[0])));

		String s = "VanillaSlider";
		lib.method(s, "value", "SELF", "double value", chain((self, a) -> peer(self, VanillaPeers.SliderPeer.class).value(d(a[0]))));
		lib.method(s, "value", "double", "", (self, a) -> peer(self, VanillaPeers.SliderPeer.class).value());
		lib.method(s, "step", "SELF", "double step", chain((self, a) -> peer(self, VanillaPeers.SliderPeer.class).step(d(a[0]))));
		lib.method(s, "label", "SELF", "Function<Double, String> label", chain((self, a) -> {
			GuiSession session = session();
			Fn fn = (Fn) a[0];
			peer(self, VanillaPeers.SliderPeer.class).label(value -> {
				Object[] result = new Object[1];
				session.run(() -> result[0] = fn.call(value));
				return result[0] instanceof String text ? text : null;
			});
		}));
		lib.method(s, "onChange", "SELF", "Consumer<Double> handler", chain((self, a) -> peer(self, VanillaPeers.SliderPeer.class).onChange(handler(a[0]))));

		String c = "Checkbox";
		lib.method(c, "checked", "SELF", "boolean checked", chain((self, a) -> peer(self, VanillaPeers.CheckboxPeer.class).checked(b(a[0]))));
		lib.method(c, "checked", "boolean", "", (self, a) -> peer(self, VanillaPeers.CheckboxPeer.class).checked());
		lib.method(c, "label", "SELF", "String label", chain((self, a) -> peer(self, VanillaPeers.CheckboxPeer.class).label(s(a[0]))));
		lib.method(c, "onChange", "SELF", "Consumer<Boolean> handler", chain((self, a) -> peer(self, VanillaPeers.CheckboxPeer.class).onChange = handler(a[0])));

		String cycle = "CycleButton";
		lib.method(cycle, "values", "SELF", "List<String> values", chain((self, a) -> peer(self, VanillaPeers.CyclePeer.class).values(strings(a[0]))));
		lib.method(cycle, "value", "SELF", "String value", chain((self, a) -> peer(self, VanillaPeers.CyclePeer.class).value(s(a[0]))));
		lib.method(cycle, "value", "String", "", (self, a) -> peer(self, VanillaPeers.CyclePeer.class).value());
		lib.method(cycle, "label", "SELF", "String label", chain((self, a) -> peer(self, VanillaPeers.CyclePeer.class).label(s(a[0]))));
		lib.method(cycle, "onChange", "SELF", "Consumer<String> handler", chain((self, a) -> peer(self, VanillaPeers.CyclePeer.class).onChange = handler(a[0])));
	}

	private static List<String> strings(Object list) {
		List<String> result = new ArrayList<>();

		for (Object value : (List<?>) list) {
			result.add(value == null ? "null" : value.toString());
		}

		return result;
	}

	private static void events(SfyLibrary lib) {
		String m = "MouseEvent";
		lib.method(m, "x", "double", "", (self, a) -> ((MouseEvent) self).x);
		lib.method(m, "y", "double", "", (self, a) -> ((MouseEvent) self).y);
		lib.method(m, "screenX", "double", "", (self, a) -> ((MouseEvent) self).screenX);
		lib.method(m, "screenY", "double", "", (self, a) -> ((MouseEvent) self).screenY);
		lib.method(m, "button", "MouseButton", "", (self, a) -> ((MouseEvent) self).button);
		lib.method(m, "shift", "boolean", "", (self, a) -> (((MouseEvent) self).modifiers & GuiEvents.SHIFT) != 0);
		lib.method(m, "ctrl", "boolean", "", (self, a) -> (((MouseEvent) self).modifiers & GuiEvents.CONTROL) != 0);
		lib.method(m, "alt", "boolean", "", (self, a) -> (((MouseEvent) self).modifiers & GuiEvents.ALT) != 0);
		lib.method(m, "cmd", "boolean", "", (self, a) -> (((MouseEvent) self).modifiers & GuiEvents.SUPER) != 0);
		lib.method(m, "element", "Element", "", (self, a) -> ((MouseEvent) self).element);
		lib.method(m, "target", "Element", "", (self, a) -> ((MouseEvent) self).target);
		lib.method(m, "stop", "void", "", (self, a) -> {
			((MouseEvent) self).stop();
			return null;
		});
		String s = "ScrollEvent";
		lib.method(s, "x", "double", "", (self, a) -> ((ScrollEvent) self).x);
		lib.method(s, "y", "double", "", (self, a) -> ((ScrollEvent) self).y);
		lib.method(s, "deltaX", "double", "", (self, a) -> ((ScrollEvent) self).deltaX);
		lib.method(s, "deltaY", "double", "", (self, a) -> ((ScrollEvent) self).deltaY);
		lib.method(s, "shift", "boolean", "", (self, a) -> (((ScrollEvent) self).modifiers & GuiEvents.SHIFT) != 0);
		lib.method(s, "ctrl", "boolean", "", (self, a) -> (((ScrollEvent) self).modifiers & GuiEvents.CONTROL) != 0);
		lib.method(s, "alt", "boolean", "", (self, a) -> (((ScrollEvent) self).modifiers & GuiEvents.ALT) != 0);
		lib.method(s, "stop", "void", "", (self, a) -> {
			((ScrollEvent) self).stop();
			return null;
		});
		String k = "KeyEvent";
		lib.method(k, "key", "String", "", (self, a) -> ((KeyInput) self).name);
		lib.method(k, "shift", "boolean", "", (self, a) -> ((KeyInput) self).shift());
		lib.method(k, "ctrl", "boolean", "", (self, a) -> ((KeyInput) self).control());
		lib.method(k, "alt", "boolean", "", (self, a) -> ((KeyInput) self).alt());
		lib.method(k, "cmd", "boolean", "", (self, a) -> ((KeyInput) self).command());
		lib.method(k, "stop", "void", "", (self, a) -> {
			((KeyInput) self).stop();
			return null;
		});
		String dr = "DropEvent";
		lib.method(dr, "element", "Element", "", (self, a) -> ((DropEvent) self).element);
		lib.method(dr, "target", "Element", "", (self, a) -> {
			Element target = ((DropEvent) self).target;
			return target == document().root ? null : target;
		});
		lib.method(dr, "screenX", "double", "", (self, a) -> ((DropEvent) self).screenX);
		lib.method(dr, "screenY", "double", "", (self, a) -> ((DropEvent) self).screenY);
	}

	private static void misc(SfyLibrary lib) {
		lib.method("Timer", "cancel", "void", "", (self, a) -> {
			((GuiTimer) self).cancel();
			return null;
		});
		lib.method("Animation", "ease", "Animation", "Easing easing", (self, a) -> ((Animation) self).ease((Easing) a[0]));
		lib.method("Animation", "delay", "Animation", "Duration delay", (self, a) -> ((Animation) self).delay(ms(a[0])));
		lib.method("Animation", "then", "Animation", "Duration duration", (self, a) -> ((Animation) self).then(ms(a[0])));
		lib.method("Animation", "loop", "Animation", "", (self, a) -> ((Animation) self).loop());
		lib.method("Animation", "repeat", "Animation", "int times", (self, a) -> ((Animation) self).repeat(i(a[0])));
		lib.method("Animation", "pingPong", "Animation", "", (self, a) -> ((Animation) self).pingPong());
		lib.method("Animation", "onDone", "Animation", "Runnable handler", (self, a) -> ((Animation) self).onDone(handler(a[0])));
		lib.method("Animation", "cancel", "void", "", (self, a) -> {
			((Animation) self).cancel();
			return null;
		});
		lib.method("RichText", "plain", "String", "", (self, a) -> ((RichText) self).plain());
	}

	private static <T extends Element> T make(java.util.function.Function<GuiDocument, T> factory) {
		return factory.apply(document());
	}

	private static WidgetElement widget(WidgetElement.Kind kind, java.util.function.Function<WidgetElement, VanillaPeers.Peer> peer) {
		WidgetElement element = new WidgetElement(document(), kind);
		element.peer = peer.apply(element);
		element.cursor = kind == WidgetElement.Kind.FIELD ? GuiEnums.Cursor.TEXT : GuiEnums.Cursor.HAND;
		return element;
	}

	private static void globals(SfyLibrary lib) {
		lib.function("box", "Box", "", (self, a) -> make(BoxElement::new));
		lib.function("text", "Text", "String text", (self, a) -> {
			TextElement element = make(TextElement::new);
			element.setText(RichText.plain(s(a[0])));
			return element;
		});
		lib.function("text", "Text", "RichText text", (self, a) -> {
			TextElement element = make(TextElement::new);
			element.setText((RichText) a[0]);
			return element;
		});
		lib.function("mini", "RichText", "String miniMessage", (self, a) -> MiniMessage.parse(s(a[0])));
		lib.function("image", "Image", "String source", (self, a) -> {
			ImageElement element = make(ImageElement::new);
			element.source = s(a[0]);
			return element;
		});
		lib.function("sprite", "Image", "String guiSprite", (self, a) -> {
			ImageElement element = make(ImageElement::new);
			element.source = s(a[0]);
			element.guiSprite = true;
			return element;
		});
		lib.function("head", "Head", "String player", (self, a) -> {
			HeadElement element = make(HeadElement::new);
			element.player = s(a[0]);
			return element;
		});
		lib.function("item", "Item", "String item", (self, a) -> {
			ItemElement element = make(ItemElement::new);
			element.item = s(a[0]);
			return element;
		});
		lib.function("slotItem", "Item", "int slot", (self, a) -> {
			ItemElement element = make(ItemElement::new);
			element.slot = Math.clamp(i(a[0]), 0, 40);
			element.itemTooltip = false;
			return element;
		});
		lib.function("entity", "EntityView", "String type", (self, a) -> {
			EntityElement element = make(EntityElement::new);
			element.type = s(a[0]);
			return element;
		});
		lib.function("player", "EntityView", "", (self, a) -> make(EntityElement::new));
		lib.function("field", "Field", "", (self, a) -> make(FieldElement::new));
		lib.function("button", "Button", "String label", (self, a) -> new ButtonElement(document(), RichText.plain(s(a[0]))));
		lib.function("button", "Button", "RichText label", (self, a) -> new ButtonElement(document(), (RichText) a[0]));
		lib.function("slider", "Slider", "double min, double max", (self, a) -> new SliderElement(document(), d(a[0]), d(a[1])));
		lib.function("canvas", "Canvas", "Consumer<Graphics> draw", (self, a) -> {
			CanvasElement element = make(CanvasElement::new);
			element.draw = handler(a[0]);
			return element;
		});
		lib.function("vanillaButton", "VanillaButton", "String label", (self, a) -> widget(WidgetElement.Kind.BUTTON, element -> new VanillaPeers.ButtonPeer(element, s(a[0]))));
		lib.function("vanillaField", "VanillaField", "", (self, a) -> widget(WidgetElement.Kind.FIELD, VanillaPeers.FieldPeer::new));
		lib.function("vanillaSlider", "VanillaSlider", "double min, double max", (self, a) -> widget(WidgetElement.Kind.SLIDER, element -> new VanillaPeers.SliderPeer(element, d(a[0]), d(a[1]))));
		lib.function("checkbox", "Checkbox", "String label", (self, a) -> widget(WidgetElement.Kind.CHECKBOX, element -> new VanillaPeers.CheckboxPeer(element, s(a[0]))));
		lib.function("cycleButton", "CycleButton", "List<String> values", (self, a) -> widget(WidgetElement.Kind.CYCLE, element -> new VanillaPeers.CyclePeer(element, strings(a[0]))));

		lib.function("add", "ARG0", "Element element", (self, a) -> {
			if (a[0] == null) {
				throw new ScriptException("Can't add null");
			}

			document().root.add(el(a[0]));
			return a[0];
		});
		lib.function("root", "Box", "", (self, a) -> document().root);
		lib.function("focused", "Element", "", (self, a) -> document().focused());
		lib.function("blur", "void", "int amount", (self, a) -> {
			document().blur = Math.clamp(i(a[0]), 0, 10);
			return null;
		});
		lib.function("tint", "void", "Color color", (self, a) -> {
			document().tintTop = color(a[0]);
			document().tintBottom = color(a[0]);
			return null;
		});
		lib.function("tint", "void", "Color top, Color bottom", (self, a) -> {
			document().tintTop = color(a[0]);
			document().tintBottom = color(a[1]);
			return null;
		});
		lib.function("menuBackground", "void", "boolean show", (self, a) -> {
			document().menuBackground = b(a[0]);
			return null;
		});
		lib.function("hideHud", "void", "boolean hide", (self, a) -> {
			document().hideHud = b(a[0]);
			return null;
		});
		lib.function("guiScale", "void", "int scale", (self, a) -> {
			document().guiScale = Math.clamp(i(a[0]), 0, 16);
			return null;
		});
		lib.function("guiScale", "int", "", (self, a) -> {
			int forced = document().guiScale;
			return forced > 0 ? forced : Minecraft.getInstance().getWindow().getGuiScale();
		});
		lib.function("pageScrollbar", "void", "ScrollbarStyle style", (self, a) -> {
			document().root.scrollbar = (GuiEnums.ScrollbarStyle) a[0];
			return null;
		});
		lib.function("close", "void", "", (self, a) -> {
			GuiSession session = session();
			Minecraft.getInstance().execute(() -> session.close(GuiPacket.CloseReason.CODE));
			return null;
		});
		lib.function("onEscape", "void", "Runnable handler", (self, a) -> {
			document().onEscape = handler(a[0]);
			return null;
		});
		lib.function("send", "void", "String name, Object... values", (self, a) -> {
			send(s(a[0]), (Object[]) a[1]);
			return null;
		});
		lib.function("openLink", "void", "String url", (self, a) -> {
			GuiSession session = session();
			Minecraft.getInstance().execute(() -> GuiScreen.openLink(s(a[0]), session));
			return null;
		});
		lib.function("copy", "void", "String text", (self, a) -> {
			if (System.nanoTime() - GuiScreen.lastInput > 2_000_000_000L) {
				throw new ScriptException("copy() only works right after a click or a key press");
			}

			Minecraft.getInstance().keyboardHandler.setClipboard(s(a[0]));
			return null;
		});
		lib.function("playSound", "void", "String sound", (self, a) -> {
			McGuiPlatform.INSTANCE.playSound(s(a[0]), 1, 1);
			return null;
		});
		lib.function("playSound", "void", "String sound, double volume, double pitch", (self, a) -> {
			McGuiPlatform.INSTANCE.playSound(s(a[0]), (float) d(a[1]), (float) d(a[2]));
			return null;
		});
		lib.function("after", "Timer", "Duration delay, Runnable action", (self, a) -> document().after(ms(a[0]), handler(a[1])));
		lib.function("every", "Timer", "Duration interval, Runnable action", (self, a) -> document().every(ms(a[0]), handler(a[1])));
		lib.function("onFrame", "void", "Consumer<Double> handler", (self, a) -> {
			document().onFrame(handler(a[0]));
			return null;
		});
		lib.function("onResize", "void", "Runnable handler", (self, a) -> {
			document().onResize(handler(a[0]));
			return null;
		});
		lib.function("onKey", "void", "Consumer<KeyEvent> handler", (self, a) -> {
			document().onKey(handler(a[0]));
			return null;
		});
		lib.function("onKeyRelease", "void", "Consumer<KeyEvent> handler", (self, a) -> {
			document().onKeyRelease(handler(a[0]));
			return null;
		});
		lib.function("screenWidth", "double", "", (self, a) -> document().width);
		lib.function("screenHeight", "double", "", (self, a) -> document().height);
		lib.function("mouseX", "double", "", (self, a) -> document().mouseX);
		lib.function("mouseY", "double", "", (self, a) -> document().mouseY);
		lib.function("isKeyDown", "boolean", "String key", (self, a) -> {
			try {
				InputConstants.Key key = InputConstants.getKey(s(a[0]));
				return key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue());
			} catch (RuntimeException e) {
				throw new ScriptException("Unknown key " + a[0] + " (keys are named like key.keyboard.a)");
			}
		});
		lib.function("isMouseDown", "boolean", "MouseButton button", (self, a) -> {
			var mouse = Minecraft.getInstance().mouseHandler;
			return switch ((GuiEnums.MouseButton) a[0]) {
				case LEFT -> mouse.isLeftPressed();
				case MIDDLE -> mouse.isMiddlePressed();
				case RIGHT -> mouse.isRightPressed();
				case OTHER -> false;
			};
		});
		lib.function("time", "long", "", (self, a) -> System.currentTimeMillis());
		lib.function("formatTime", "String", "long millis, String pattern", (self, a) -> {
			try {
				return DateTimeFormatter.ofPattern(s(a[1]), Locale.ROOT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli((Long) a[0]));
			} catch (IllegalArgumentException e) {
				throw new ScriptException("Bad time pattern \"" + a[1] + "\": " + e.getMessage());
			}
		});
		lib.function("fps", "int", "", (self, a) -> Minecraft.getInstance().getFps());
		lib.function("ping", "int", "", (self, a) -> {
			Minecraft minecraft = Minecraft.getInstance();

			if (minecraft.getConnection() == null || minecraft.player == null) {
				return 0;
			}

			PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
			return info == null ? 0 : info.getLatency();
		});
		lib.function("language", "String", "", (self, a) -> Minecraft.getInstance().getLanguageManager().getSelected());
		lib.function("playerName", "String", "", (self, a) -> Minecraft.getInstance().getUser().getName());
		lib.function("playerUuid", "String", "", (self, a) -> Minecraft.getInstance().getUser().getProfileId().toString());
		lib.function("playerSkin", "String", "", (self, a) -> {
			Minecraft minecraft = Minecraft.getInstance();
			PlayerInfo info = minecraft.getConnection() == null || minecraft.player == null ? null : minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());

			if (info == null) {
				return "";
			}

			return info.getProfile().properties().get("textures").stream().findFirst().map(property -> property.value()).orElse("");
		});
	}

	private static void send(String name, Object[] values) {
		GuiSession session = session();
		List<Object> exported = new ArrayList<>(values.length);

		for (Object value : values) {
			try {
				exported.add(SfyClass.exportValue(value));
			} catch (IllegalArgumentException e) {
				throw new ScriptException("send(\"" + name + "\", ...): " + e.getMessage());
			}
		}

		if (name.isEmpty() || name.length() > 64 || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new ScriptException("send() names are 1 to 64 letters, digits and _ (starting with a letter), got \"" + name + "\"");
		}

		GuiPacket.Message message = new GuiPacket.Message(session.className(), name, exported);
		byte[] data;

		try {
			data = me.skaffy.protocol.gui.GuiCodec.encode(message);
		} catch (me.skaffy.protocol.ProtocolException e) {
			throw new ScriptException("send(\"" + name + "\", ...): " + e.getMessage());
		}

		if (data.length > me.skaffy.protocol.Protocol.MAX_SERVERBOUND_PAYLOAD) {
			throw new ScriptException("send(\"" + name + "\", ...) is " + data.length + " bytes; a client can send at most " + me.skaffy.protocol.Protocol.MAX_SERVERBOUND_PAYLOAD);
		}

		ClientGuis.send(message);
	}
}
