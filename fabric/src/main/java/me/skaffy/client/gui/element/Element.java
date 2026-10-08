package me.skaffy.client.gui.element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import me.skaffy.client.gui.element.GuiEnums.Align;
import me.skaffy.client.gui.element.GuiEnums.Axis;
import me.skaffy.client.gui.element.GuiEnums.Cursor;
import me.skaffy.client.gui.element.GuiEnums.Justify;
import me.skaffy.client.gui.element.GuiEnums.LayoutMode;
import me.skaffy.client.gui.element.GuiEnums.ScrollbarStyle;
import me.skaffy.client.gui.sfy.LengthValue;
import me.skaffy.client.gui.text.RichText;

public class Element implements PropTarget {
	private static final Comparator<Element> BY_Z = Comparator.comparingInt(element -> element.z);

	public final GuiDocument document;
	Element parent;
	final List<Element> children = new ArrayList<>();
	public boolean internal;

	final Object[] base = new Object[Prop.COUNT];
	final Object[] shown = new Object[Prop.COUNT];
	final Object[] lastTarget = new Object[Prop.COUNT];
	final boolean[] animated = new boolean[Prop.COUNT];
	private final Tween[] tweens = new Tween[Prop.COUNT];
	private boolean propsStarted;
	public PropSet hoverStyle;
	public PropSet pressedStyle;
	public PropSet focusedStyle;
	public PropSet disabledStyle;
	public long transitionMillis;
	public Easing transitionEasing = Easing.EASE_OUT_QUAD;
	final List<Animation> animations = new ArrayList<>();

	public double padTop;
	public double padRight;
	public double padBottom;
	public double padLeft;
	public LayoutMode layout = LayoutMode.NONE;
	public double gap;
	public boolean wrap;
	public Align alignItems = Align.START;
	public Justify justify = Justify.START;
	public boolean absolute;
	public boolean fixed;
	public int z;
	public boolean visible = true;
	public boolean disabled;
	public boolean clip;
	public boolean invert;
	public boolean scrollable;
	public ScrollbarStyle scrollbar = ScrollbarStyle.MODERN;
	public int scrollbarThumb = 0x80FFFFFF;
	public int scrollbarTrack = 0x20000000;
	public double scrollbarWidth = 4;
	public double scrollX;
	public double scrollY;
	double scrollTargetX;
	double scrollTargetY;
	public Cursor cursor;
	public RichText tooltip;
	public Boolean mouseThrough;
	public boolean draggable;
	public Axis dragAxis = Axis.BOTH;
	public boolean dragInParent;
	public LengthValue originX = new LengthValue(50, 0);
	public LengthValue originY = new LengthValue(50, 0);
	public boolean pixelCorners;
	public int[] gradient;
	public double gradientAngle = 180;
	public int tabIndex;

	public Consumer<Object> onClick;
	public Consumer<Object> onRightClick;
	public Consumer<Object> onDoubleClick;
	public Consumer<Object> onPress;
	public Consumer<Object> onRelease;
	public Consumer<Object> onHover;
	public Consumer<Object> onLeave;
	public Consumer<Object> onMouseMove;
	public Consumer<Object> onScroll;
	public Consumer<Object> onDragStart;
	public Consumer<Object> onDrag;
	public Consumer<Object> onDrop;
	public Consumer<Object> onFocus;
	public Consumer<Object> onBlur;

	public double lx;
	public double ly;
	public double lw;
	public double lh;
	public double contentW;
	public double contentH;
	double autoW;
	double autoH;
	public Affine matrix = Affine.IDENTITY;
	public double opacity = 1;

	public Element(GuiDocument document) {
		this.document = document;
		base[Prop.X.ordinal()] = LengthValue.ZERO;
		base[Prop.Y.ordinal()] = LengthValue.ZERO;
		base[Prop.OFFSET_X.ordinal()] = 0.0;
		base[Prop.OFFSET_Y.ordinal()] = 0.0;
		base[Prop.ROTATION.ordinal()] = 0.0;
		base[Prop.SCALE.ordinal()] = 1.0;
		base[Prop.TRANSPARENCY.ordinal()] = 0.0;
		base[Prop.COLOR.ordinal()] = 0xFFFFFFFF;
		base[Prop.BACKGROUND.ordinal()] = 0;
		base[Prop.TEXT_COLOR.ordinal()] = 0xFFFFFFFF;
		base[Prop.BORDER_WIDTH.ordinal()] = 0.0;
		base[Prop.BORDER_COLOR.ordinal()] = 0;
		base[Prop.RADIUS_TOP_LEFT.ordinal()] = 0.0;
		base[Prop.RADIUS_TOP_RIGHT.ordinal()] = 0.0;
		base[Prop.RADIUS_BOTTOM_RIGHT.ordinal()] = 0.0;
		base[Prop.RADIUS_BOTTOM_LEFT.ordinal()] = 0.0;
		base[Prop.SHADOW_COLOR.ordinal()] = 0;
		base[Prop.SHADOW_BLUR.ordinal()] = 0.0;
		base[Prop.SHADOW_X.ordinal()] = 0.0;
		base[Prop.SHADOW_Y.ordinal()] = 0.0;
		base[Prop.SHADOW_SPREAD.ordinal()] = 0.0;
		base[Prop.FONT_SIZE.ordinal()] = 8.0;
		System.arraycopy(base, 0, shown, 0, Prop.COUNT);
	}

	public Prop fillProp() {
		return Prop.BACKGROUND;
	}


	@Override
	public void set(Prop prop, Object value) {
		base[prop.ordinal()] = value;
	}

	public Object base(Prop prop) {
		return base[prop.ordinal()];
	}

	public Object shown(Prop prop) {
		return shown[prop.ordinal()];
	}

	public double number(Prop prop) {
		Object value = shown[prop.ordinal()];
		return value instanceof Double number ? number : 0;
	}

	public int color(Prop prop) {
		Object value = shown[prop.ordinal()];
		return value instanceof Integer color ? color : 0;
	}

	public LengthValue length(Prop prop) {
		return (LengthValue) shown[prop.ordinal()];
	}

	Object currentValue(Prop prop) {
		return propsStarted ? shown[prop.ordinal()] : targetValue(prop);
	}

	Object targetValue(Prop prop) {
		if (disabled && disabledStyle != null && disabledStyle.has(prop)) {
			return disabledStyle.get(prop);
		}

		if (!disabled) {
			if (pressedStyle != null && pressedStyle.has(prop) && isPressed()) {
				return pressedStyle.get(prop);
			}

			if (focusedStyle != null && focusedStyle.has(prop) && isFocused()) {
				return focusedStyle.get(prop);
			}

			if (hoverStyle != null && hoverStyle.has(prop) && isHovered()) {
				return hoverStyle.get(prop);
			}
		}

		return base[prop.ordinal()];
	}

	Object resolveAuto(Prop prop, Object value) {
		if (value != null) {
			return value;
		}

		if (prop == Prop.WIDTH) {
			return LengthValue.px(autoW);
		}

		if (prop == Prop.HEIGHT) {
			return LengthValue.px(autoH);
		}

		return null;
	}

	void updateProps(long now) {
		for (Prop prop : Prop.ALL) {
			int index = prop.ordinal();
			Object target = targetValue(prop);

			if (!propsStarted) {
				lastTarget[index] = target;
				shown[index] = target;
				continue;
			}

			if (!Objects.equals(target, lastTarget[index])) {
				if (transitionMillis > 0 && !animated[index]) {
					tweens[index] = new Tween(resolveAuto(prop, shown[index]), target, now, transitionMillis, transitionEasing);
				} else {
					tweens[index] = null;
				}

				lastTarget[index] = target;
			}

			Tween tween = tweens[index];

			if (tween != null) {
				double t = tween.progress(now);

				if (t >= 1) {
					tweens[index] = null;
					shown[index] = target;
				} else {
					shown[index] = prop.lerp(tween.from, resolveAuto(prop, tween.to), tween.easing.apply(t));
				}
			} else {
				shown[index] = target;
			}

			animated[index] = false;
		}

		propsStarted = true;

		if (!animations.isEmpty()) {
			for (Animation animation : List.copyOf(animations)) {
				animation.update(now);
			}

			animations.removeIf(Animation::isFinished);
		}
	}

	public Animation animate(long durationMillis) {
		Animation animation = new Animation(this, durationMillis);
		animations.add(animation);
		return animation;
	}

	private record Tween(Object from, Object to, long start, long duration, Easing easing) {
		double progress(long now) {
			return duration <= 0 ? 1 : (now - start) / 1_000_000.0 / duration;
		}
	}


	public Element parent() {
		return parent;
	}

	public List<Element> children() {
		List<Element> visibleChildren = new ArrayList<>();

		for (Element child : children) {
			if (!child.internal) {
				visibleChildren.add(child);
			}
		}

		return visibleChildren;
	}

	List<Element> allChildren() {
		return children;
	}

	public void add(Element child) {
		if (child == this) {
			throw new IllegalArgumentException("An element can't contain itself");
		}

		for (Element ancestor = this; ancestor != null; ancestor = ancestor.parent) {
			if (ancestor == child) {
				throw new IllegalArgumentException("An element can't be put inside its own child");
			}
		}

		boolean wasAttached = child.isAttached();
		boolean attaching = isAttached();
		int size = child.subtreeSize();

		if (attaching && !wasAttached && document.attachedCount + size > GuiDocument.MAX_ELEMENTS) {
			throw new IllegalStateException("A GUI can show at most " + GuiDocument.MAX_ELEMENTS + " elements");
		}

		if (child.parent != null) {
			child.parent.children.remove(child);
		}

		child.parent = this;
		children.add(child);

		if (attaching != wasAttached) {
			document.attachedCount += attaching ? size : -size;
		}

		document.elementAdded();
	}

	public void remove() {
		if (parent != null) {
			boolean wasAttached = isAttached();
			parent.children.remove(this);
			parent = null;

			if (wasAttached) {
				document.attachedCount -= subtreeSize();
			}

			document.detached(this);
		}
	}

	int subtreeSize() {
		int size = 1;

		for (Element child : children) {
			size += child.subtreeSize();
		}

		return size;
	}

	public void clear() {
		for (Element child : List.copyOf(children)) {
			if (!child.internal) {
				child.remove();
			}
		}
	}

	public List<Element> paintOrderPublic() {
		return paintOrder();
	}

	List<Element> paintOrder() {
		boolean sorted = true;

		for (int i = 1; i < children.size(); i++) {
			if (children.get(i - 1).z > children.get(i).z) {
				sorted = false;
				break;
			}
		}

		if (sorted) {
			return children;
		}

		List<Element> copy = new ArrayList<>(children);
		copy.sort(BY_Z);
		return copy;
	}

	public boolean isAttached() {
		for (Element element = this; element != null; element = element.parent) {
			if (element == document.root) {
				return true;
			}
		}

		return false;
	}

	public boolean isShown() {
		for (Element element = this; element != null; element = element.parent) {
			if (!element.visible) {
				return false;
			}
		}

		return isAttached();
	}


	public boolean isHovered() {
		return document.hoverChain.contains(this);
	}

	public boolean isPressed() {
		return document.pressedChain.contains(this);
	}

	public boolean isFocused() {
		return document.focused == this;
	}

	public boolean focusable() {
		return false;
	}

	public boolean hasMouseHandlers() {
		return onClick != null || onRightClick != null || onDoubleClick != null || onPress != null || onRelease != null || onHover != null || onLeave != null
				|| onMouseMove != null || onScroll != null || onDrag != null || onDragStart != null || onDrop != null;
	}

	public boolean catchesMouse() {
		if (mouseThrough != null) {
			return !mouseThrough;
		}

		return hasMouseHandlers() || tooltip != null || hoverStyle != null || pressedStyle != null || cursor != null || draggable || focusable();
	}


	double[] intrinsicSize(double innerWidth, double innerHeight) {
		return new double[] {0, 0};
	}

	void afterLayout() {
	}

	void tick(long now) {
	}


	public double[] toLocal(double screenX, double screenY) {
		return matrix.inverse(screenX, screenY);
	}

	public double contentLeft() {
		return padLeft;
	}

	public double contentTop() {
		return padTop;
	}

	public double innerWidth() {
		return Math.max(0, lw - padLeft - padRight);
	}

	public double innerHeight() {
		return Math.max(0, lh - padTop - padBottom);
	}

	public double maxScrollX() {
		return Math.max(0, contentW - innerWidth());
	}

	public double maxScrollY() {
		return Math.max(0, contentH - innerHeight());
	}

	public void scrollTo(double x, double y) {
		scrollTargetX = Math.clamp(x, 0, maxScrollX());
		scrollTargetY = Math.clamp(y, 0, maxScrollY());
		scrollX = scrollTargetX;
		scrollY = scrollTargetY;
	}

	void scrollBy(double dx, double dy) {
		scrollTargetX = Math.clamp(scrollTargetX + dx, 0, maxScrollX());
		scrollTargetY = Math.clamp(scrollTargetY + dy, 0, maxScrollY());
	}

	void smoothScroll(double seconds) {
		scrollTargetX = Math.clamp(scrollTargetX, 0, maxScrollX());
		scrollTargetY = Math.clamp(scrollTargetY, 0, maxScrollY());
		double factor = 1 - Math.exp(-seconds * 18);
		scrollX += (scrollTargetX - scrollX) * factor;
		scrollY += (scrollTargetY - scrollY) * factor;

		if (Math.abs(scrollTargetX - scrollX) < 0.05) {
			scrollX = scrollTargetX;
		}

		if (Math.abs(scrollTargetY - scrollY) < 0.05) {
			scrollY = scrollTargetY;
		}
	}

	public boolean canScrollY(double direction) {
		return direction < 0 ? scrollTargetY > 0.01 : scrollTargetY < maxScrollY() - 0.01;
	}

	public boolean canScrollX(double direction) {
		return direction < 0 ? scrollTargetX > 0.01 : scrollTargetX < maxScrollX() - 0.01;
	}

	public String typeName() {
		return "Element";
	}

	@Override
	public String toString() {
		return typeName();
	}
}
