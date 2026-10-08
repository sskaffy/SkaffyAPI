package me.skaffy.client.gui.element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import me.skaffy.client.gui.element.GuiEnums.Axis;
import me.skaffy.client.gui.element.GuiEnums.Cursor;
import me.skaffy.client.gui.element.GuiEnums.MouseButton;
import me.skaffy.client.gui.element.GuiEnums.ScrollbarStyle;
import me.skaffy.client.gui.element.GuiEvents.DropEvent;
import me.skaffy.client.gui.element.GuiEvents.KeyInput;
import me.skaffy.client.gui.element.GuiEvents.MouseEvent;
import me.skaffy.client.gui.element.GuiEvents.ScrollEvent;
import me.skaffy.client.gui.element.PictureElements.ItemElement;

public final class GuiDocument {
	public static final int MAX_ELEMENTS = 16384;
	private static final double SCROLL_STEP = 24;
	private static final double DRAG_THRESHOLD = 3;

	public final GuiPlatform platform;
	public BoxElement root;
	int attachedCount;
	public double width;
	public double height;
	public double scaleFactor = 1;

	public int blur;
	public int tintTop;
	public int tintBottom;
	public boolean menuBackground;
	public boolean hideHud;
	public int guiScale;
	public Consumer<Object> onEscape;
	final List<Consumer<Object>> frameHandlers = new ArrayList<>();
	final List<Consumer<Object>> resizeHandlers = new ArrayList<>();
	final List<Consumer<Object>> keyHandlers = new ArrayList<>();
	final List<Consumer<Object>> keyReleaseHandlers = new ArrayList<>();
	final List<GuiTimer> timers = new ArrayList<>();

	final List<Element> hoverChain = new ArrayList<>();
	final List<Element> pressedChain = new ArrayList<>();
	Element hoverTarget;
	Element focused;
	private Element pressTarget;
	private int pressButton = -1;
	private int pressModifiers;
	private double pressX;
	private double pressY;
	private Element dragCandidate;
	private boolean dragging;
	private Element scrollbarDrag;
	private boolean scrollbarVertical;
	private double scrollbarGrab;
	private SliderElement sliderDrag;
	private FieldElement fieldDrag;
	private WidgetElement widgetPress;
	private boolean pendingAutoFocus;
	public double mouseX = -1e9;
	public double mouseY = -1e9;
	private long lastUpdate;
	private final long[] escapes = new long[3];
	private int escapeIndex;

	public Runnable closeRequest = () -> {
	};

	public Cursor cursor = Cursor.DEFAULT;
	public Element tooltipElement;

	public GuiDocument(GuiPlatform platform) {
		this.platform = platform;
		this.root = newRoot();
	}

	private BoxElement newRoot() {
		BoxElement element = new BoxElement(this);
		element.scrollable = true;
		element.mouseThrough = true;
		return element;
	}

	public void reset() {
		for (GuiTimer timer : timers) {
			timer.cancel();
		}

		setFocus(null);
		timers.clear();
		frameHandlers.clear();
		resizeHandlers.clear();
		keyHandlers.clear();
		keyReleaseHandlers.clear();
		onEscape = null;
		blur = 0;
		tintTop = 0;
		tintBottom = 0;
		menuBackground = false;
		hideHud = false;
		guiScale = 0;
		hoverChain.clear();
		pressedChain.clear();
		hoverTarget = null;
		pressTarget = null;
		dragCandidate = null;
		dragging = false;
		scrollbarDrag = null;
		sliderDrag = null;
		fieldDrag = null;
		widgetPress = null;
		root = newRoot();
		attachedCount = 0;
		pendingAutoFocus = true;
	}


	public GuiTimer after(long millis, Consumer<Object> action) {
		GuiTimer timer = new GuiTimer(action, System.nanoTime() + Math.max(0, millis) * 1_000_000L, 0);
		timers.add(timer);
		return timer;
	}

	public GuiTimer every(long millis, Consumer<Object> action) {
		long interval = Math.max(1, millis) * 1_000_000L;
		GuiTimer timer = new GuiTimer(action, System.nanoTime() + interval, interval);
		timers.add(timer);
		return timer;
	}

	public void onFrame(Consumer<Object> handler) {
		frameHandlers.add(handler);
	}

	public void onResize(Consumer<Object> handler) {
		resizeHandlers.add(handler);
	}

	public void onKey(Consumer<Object> handler) {
		keyHandlers.add(handler);
	}

	public void onKeyRelease(Consumer<Object> handler) {
		keyReleaseHandlers.add(handler);
	}

	public Element focused() {
		return focused;
	}

	void elementAdded() {
		pendingAutoFocus = true;
	}

	void detached(Element element) {
		if (focused != null && isInside(focused, element)) {
			setFocus(null);
		}

		if (pressTarget != null && isInside(pressTarget, element)) {
			pressTarget = null;
			pressedChain.clear();
			dragCandidate = null;
			dragging = false;
		}

		if (hoverTarget != null && isInside(hoverTarget, element)) {
			hoverTarget = null;
			hoverChain.clear();
		}
	}

	private static boolean isInside(Element element, Element ancestor) {
		for (Element current = element; current != null; current = current.parent) {
			if (current == ancestor) {
				return true;
			}
		}

		return false;
	}


	public void resize(double newWidth, double newHeight, double newScaleFactor) {
		boolean changed = lastUpdate != 0 && (Math.abs(newWidth - width) > 1e-6 || Math.abs(newHeight - height) > 1e-6);
		width = newWidth;
		height = newHeight;
		scaleFactor = newScaleFactor;

		if (changed) {
			for (Consumer<Object> handler : List.copyOf(resizeHandlers)) {
				handler.accept(null);
			}
		}
	}

	public void update(long now) {
		double deltaMillis = lastUpdate == 0 ? 0 : (now - lastUpdate) / 1_000_000.0;
		lastUpdate = now;

		for (GuiTimer timer : List.copyOf(timers)) {
			if (timer.cancelled) {
				continue;
			}

			if (now >= timer.due) {
				if (timer.intervalNanos > 0) {
					timer.due += timer.intervalNanos;

					if (timer.due <= now) {
						timer.due = now + timer.intervalNanos;
					}
				} else {
					timer.cancelled = true;
				}

				timer.action.accept(null);
			}
		}

		timers.removeIf(timer -> timer.cancelled);

		for (Consumer<Object> handler : List.copyOf(frameHandlers)) {
			handler.accept(deltaMillis);
		}

		BoxElement currentRoot = root;
		tick(currentRoot, now);
		Layout.run(this);
		smoothScroll(currentRoot, deltaMillis / 1000);

		if (pendingAutoFocus) {
			pendingAutoFocus = false;
			autoFocus(root);
		}

		refreshHover();
		updateCursorAndTooltip();
	}

	private void tick(Element element, long now) {
		element.tick(now);
		element.updateProps(now);

		for (Element child : List.copyOf(element.allChildren())) {
			tick(child, now);
		}
	}

	private void smoothScroll(Element element, double seconds) {
		if (element.scrollable) {
			element.smoothScroll(seconds);
		}

		for (Element child : element.allChildren()) {
			if (child.visible) {
				smoothScroll(child, seconds);
			}
		}
	}

	private boolean autoFocus(Element element) {
		if (!element.visible) {
			return false;
		}

		if (element instanceof FieldElement field && field.autoFocus && focused == null) {
			setFocus(field);
			return true;
		}

		for (Element child : element.allChildren()) {
			if (autoFocus(child)) {
				return true;
			}
		}

		return false;
	}

	private void updateCursorAndTooltip() {
		Cursor found = null;
		Element tip = null;

		if (scrollbarDrag != null) {
			found = Cursor.DEFAULT;
		}

		for (Element element : hoverChain) {
			if (found == null && element.cursor != null) {
				found = element.disabled && element.cursor == Cursor.HAND ? Cursor.DEFAULT : element.cursor;
			}

			if (tip == null && (element.tooltip != null || element instanceof ItemElement item && item.itemTooltip)) {
				tip = element;
			}
		}

		cursor = found == null ? Cursor.DEFAULT : found;
		tooltipElement = pressTarget != null ? null : tip;
	}


	public Element hit(double x, double y, Predicate<Element> filter, Element exclude) {
		return hitIn(root, x, y, filter, exclude);
	}

	private Element hitIn(Element element, double x, double y, Predicate<Element> filter, Element exclude) {
		if (!element.visible || element == exclude) {
			return null;
		}

		double[] local = element.toLocal(x, y);
		boolean inside = local != null && local[0] >= 0 && local[1] >= 0 && local[0] <= element.lw && local[1] <= element.lh;

		if ((element.clip || element.scrollable) && !inside) {
			return null;
		}

		List<Element> order = element.paintOrder();

		for (int i = order.size() - 1; i >= 0; i--) {
			Element found = hitIn(order.get(i), x, y, filter, exclude);

			if (found != null) {
				return found;
			}
		}

		return inside && filter.test(element) ? element : null;
	}

	private void refreshHover() {
		Element target = scrollbarDrag != null ? null : hit(mouseX, mouseY, Element::catchesMouse, null);

		if (dragging && pressTarget != null) {
			target = pressTarget;
		}

		if (target == hoverTarget && chainValid()) {
			return;
		}

		List<Element> chain = chain(target);
		List<Element> left = new ArrayList<>();

		for (Element element : hoverChain) {
			if (!chain.contains(element)) {
				left.add(element);
			}
		}

		List<Element> entered = new ArrayList<>();

		for (Element element : chain) {
			if (!hoverChain.contains(element)) {
				entered.add(element);
			}
		}

		hoverChain.clear();
		hoverChain.addAll(chain);
		hoverTarget = target;

		for (Element element : left) {
			if (element.onLeave != null && !element.disabled) {
				element.onLeave.accept(mouseEvent(element, target, MouseButton.OTHER, 0));
			}
		}

		for (int i = entered.size() - 1; i >= 0; i--) {
			Element element = entered.get(i);

			if (element.onHover != null && !element.disabled) {
				element.onHover.accept(mouseEvent(element, target, MouseButton.OTHER, 0));
			}
		}
	}

	private boolean chainValid() {
		for (Element element : hoverChain) {
			if (!element.isAttached()) {
				return false;
			}
		}

		return true;
	}

	private static List<Element> chain(Element element) {
		List<Element> result = new ArrayList<>();

		for (Element current = element; current != null; current = current.parent) {
			result.add(current);
		}

		return result;
	}

	private MouseEvent mouseEvent(Element element, Element target, MouseButton button, int modifiers) {
		double[] local = element.toLocal(mouseX, mouseY);
		return new MouseEvent(element, target, local == null ? 0 : local[0], local == null ? 0 : local[1], mouseX, mouseY, button, modifiers);
	}

	private boolean bubble(Element target, java.util.function.Function<Element, Consumer<Object>> handler, MouseButton button, int modifiers) {
		for (Element element = target; element != null; element = element.parent) {
			if (element.disabled) {
				return false;
			}

			Consumer<Object> action = handler.apply(element);

			if (action != null) {
				MouseEvent event = mouseEvent(element, target, button, modifiers);
				action.accept(event);

				if (event.isStopped()) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean disabledChain(Element element) {
		for (Element current = element; current != null; current = current.parent) {
			if (current.disabled) {
				return true;
			}
		}

		return false;
	}


	public double[] scrollbarThumb(Element element, boolean vertical) {
		if (!element.scrollable || element.scrollbar == ScrollbarStyle.HIDDEN) {
			return null;
		}

		double max = vertical ? element.maxScrollY() : element.maxScrollX();

		if (max <= 0.5) {
			return null;
		}

		double size = element.scrollbar == ScrollbarStyle.VANILLA ? 6 : element.scrollbarWidth;
		double track = vertical ? element.lh : element.lw;
		double visible = vertical ? element.innerHeight() : element.innerWidth();
		double content = visible + max;
		double length = Math.max(12, track * visible / content);
		double position = (track - length) * (vertical ? element.scrollY : element.scrollX) / max;
		double inset = element.scrollbar == ScrollbarStyle.VANILLA ? 0 : 2;
		return vertical ? new double[] {element.lw - size - inset, position, size, length}
				: new double[] {position, element.lh - size - inset, length, size};
	}

	private boolean startScrollbarDrag(double x, double y) {
		Element element = hit(x, y, candidate -> candidate.scrollable, null);

		while (element != null) {
			for (boolean vertical : new boolean[] {true, false}) {
				double[] thumb = scrollbarThumb(element, vertical);

				if (thumb == null) {
					continue;
				}

				double[] local = element.toLocal(x, y);

				if (local == null) {
					continue;
				}

				double size = vertical ? thumb[2] : thumb[3];
				boolean inBar = vertical ? local[0] >= element.lw - size - 4 && local[0] <= element.lw : local[1] >= element.lh - size - 4 && local[1] <= element.lh;

				if (!inBar) {
					continue;
				}

				scrollbarDrag = element;
				scrollbarVertical = vertical;
				double along = vertical ? local[1] : local[0];
				double thumbStart = vertical ? thumb[1] : thumb[0];
				double thumbLength = vertical ? thumb[3] : thumb[2];

				if (along < thumbStart || along > thumbStart + thumbLength) {
					scrollbarGrab = thumbLength / 2;
					dragScrollbar(x, y);
				} else {
					scrollbarGrab = along - thumbStart;
				}

				return true;
			}

			element = element.parent;
		}

		return false;
	}

	private void dragScrollbar(double x, double y) {
		Element element = scrollbarDrag;
		double[] local = element.toLocal(x, y);
		double[] thumb = scrollbarThumb(element, scrollbarVertical);

		if (local == null || thumb == null) {
			return;
		}

		double track = scrollbarVertical ? element.lh : element.lw;
		double length = scrollbarVertical ? thumb[3] : thumb[2];
		double along = (scrollbarVertical ? local[1] : local[0]) - scrollbarGrab;
		double t = track - length <= 0 ? 0 : Math.clamp(along / (track - length), 0, 1);

		if (scrollbarVertical) {
			element.scrollTo(element.scrollX, t * element.maxScrollY());
		} else {
			element.scrollTo(t * element.maxScrollX(), element.scrollY);
		}
	}


	public void mouseMoved(double x, double y) {
		double dx = x - mouseX;
		double dy = y - mouseY;
		mouseX = x;
		mouseY = y;

		if (scrollbarDrag != null) {
			dragScrollbar(x, y);
			return;
		}

		if (pressTarget != null) {
			drag(x, y, dx, dy);
		}

		refreshHover();
		updateCursorAndTooltip();

		if (hoverTarget != null && !dragging) {
			bubble(hoverTarget, element -> element.onMouseMove, MouseButton.OTHER, 0);
		}
	}

	private void drag(double x, double y, double dx, double dy) {
		if (widgetPress != null && widgetPress.peer != null) {
			double[] local = widgetPress.toLocal(x, y);

			if (local != null) {
				double[] vector = widgetPress.matrix.inverseVector(dx, dy);
				widgetPress.peer.mouseDragged(local[0], local[1], pressButton, pressModifiers, vector[0], vector[1]);
			}
		}

		if (sliderDrag != null) {
			double[] local = sliderDrag.toLocal(x, y);

			if (local != null && sliderDrag.setFromPoint(local[0], local[1]) && sliderDrag.onChange != null) {
				sliderDrag.onChange.accept(sliderDrag.value);
			}
		}

		if (fieldDrag != null) {
			double[] local = fieldDrag.toLocal(x, y);

			if (local != null) {
				double[] point = fieldDrag.unscrolled(local[0], local[1]);
				fieldDrag.editor.setCursor(fieldDrag.indexAt(point[0], point[1]), true);
				fieldDrag.keepCursorVisible();
			}
		}

		if (dragCandidate == null) {
			return;
		}

		if (!dragging) {
			if (Math.hypot(x - pressX, y - pressY) < DRAG_THRESHOLD) {
				return;
			}

			dragging = true;

			if (dragCandidate.onDragStart != null) {
				dragCandidate.onDragStart.accept(mouseEvent(dragCandidate, dragCandidate, GuiEvents.button(pressButton), pressModifiers));
			}
		}

		Element element = dragCandidate;
		Affine parentSpace = element.parent == null ? Affine.IDENTITY : element.parent.matrix;
		double[] vector = parentSpace.inverseVector(dx, dy);
		double moveX = element.dragAxis == Axis.Y ? 0 : vector[0];
		double moveY = element.dragAxis == Axis.X ? 0 : vector[1];
		double offsetX = ((Double) element.base(Prop.OFFSET_X)) + moveX;
		double offsetY = ((Double) element.base(Prop.OFFSET_Y)) + moveY;

		if (element.dragInParent && element.parent != null) {
			Element parent = element.parent;
			offsetX = Math.clamp(offsetX, -element.lx, Math.max(-element.lx, parent.innerWidth() - element.lx - element.lw));
			offsetY = Math.clamp(offsetY, -element.ly, Math.max(-element.ly, parent.innerHeight() - element.ly - element.lh));
		}

		element.set(Prop.OFFSET_X, offsetX);
		element.set(Prop.OFFSET_Y, offsetY);
		element.shown[Prop.OFFSET_X.ordinal()] = offsetX;
		element.shown[Prop.OFFSET_Y.ordinal()] = offsetY;
		element.lastTarget[Prop.OFFSET_X.ordinal()] = offsetX;
		element.lastTarget[Prop.OFFSET_Y.ordinal()] = offsetY;
		Layout.run(this);

		if (element.onDrag != null) {
			element.onDrag.accept(mouseEvent(element, element, GuiEvents.button(pressButton), pressModifiers));
		}
	}

	public void mousePressed(double x, double y, int button, int modifiers, boolean doubleClick) {
		mouseX = x;
		mouseY = y;

		if (pressTarget != null) {
			return;
		}

		if (button == 1 && startScrollbarDrag(x, y)) {
			return;
		}

		refreshHover();
		Element target = hoverTarget;
		Element focusable = null;

		for (Element element = target; element != null; element = element.parent) {
			if (element.focusable()) {
				focusable = element;
				break;
			}
		}

		if (focusable != focused && !(focused instanceof FieldElement field && field.keepFocus)) {
			setFocus(focusable);
		}

		if (target == null) {
			return;
		}

		pressTarget = target;
		pressButton = button;
		pressModifiers = modifiers;
		pressX = x;
		pressY = y;
		pressedChain.clear();
		pressedChain.addAll(chain(target));

		if (disabledChain(target)) {
			return;
		}

		double[] local = target.toLocal(x, y);

		if (target instanceof WidgetElement widget && widget.peer != null && local != null) {
			widgetPress = widget;
			widget.peer.mousePressed(local[0], local[1], button, modifiers, doubleClick);
		} else if (target instanceof SliderElement slider && button == 1 && local != null) {
			sliderDrag = slider;

			if (slider.setFromPoint(local[0], local[1]) && slider.onChange != null) {
				slider.onChange.accept(slider.value);
			}
		} else if (target instanceof FieldElement field && button == 1 && local != null) {
			fieldDrag = field;
			double[] point = field.unscrolled(local[0], local[1]);
			int index = field.indexAt(point[0], point[1]);

			if (doubleClick) {
				field.editor.selectWord(index);
			} else {
				field.editor.setCursor(index, (modifiers & GuiEvents.SHIFT) != 0);
			}

			field.caretReset = System.nanoTime();
		}

		bubble(target, element -> element.onPress, GuiEvents.button(button), modifiers);

		if (button == 1) {
			for (Element element = target; element != null; element = element.parent) {
				if (element.draggable && !element.disabled) {
					dragCandidate = element;
					break;
				}
			}
		}

		pressedDoubleClick = doubleClick;
	}

	private boolean pressedDoubleClick;

	public void mouseReleased(double x, double y, int button, int modifiers) {
		mouseX = x;
		mouseY = y;

		if (scrollbarDrag != null) {
			scrollbarDrag = null;
			refreshHover();
			return;
		}

		if (pressTarget == null || button != pressButton) {
			return;
		}

		Element target = pressTarget;
		boolean wasDragging = dragging;
		Element dragged = dragCandidate;
		pressTarget = null;
		dragCandidate = null;
		dragging = false;
		pressedChain.clear();

		if (widgetPress != null) {
			double[] local = widgetPress.toLocal(x, y);

			if (local != null && widgetPress.peer != null) {
				widgetPress.peer.mouseReleased(local[0], local[1], button, modifiers);
			}

			widgetPress = null;
		}

		if (sliderDrag != null) {
			SliderElement slider = sliderDrag;
			sliderDrag = null;

			if (slider.onDone != null) {
				slider.onDone.accept(slider.value);
			}
		}

		fieldDrag = null;

		if (wasDragging && dragged != null) {
			Element over = hit(x, y, Element::catchesMouse, dragged);

			if (dragged.onDrop != null) {
				dragged.onDrop.accept(new DropEvent(dragged, over, x, y));
			}

			refreshHover();
			return;
		}

		if (disabledChain(target) || !target.isAttached()) {
			refreshHover();
			return;
		}

		MouseButton which = GuiEvents.button(button);
		bubble(target, element -> element.onRelease, which, modifiers);
		Element over = hit(x, y, Element::catchesMouse, null);

		if (over != null && isInside(over, target) && !(target instanceof WidgetElement)) {
			click(target, which, modifiers, pressedDoubleClick);
		}

		refreshHover();
	}

	private void click(Element target, MouseButton which, int modifiers, boolean doubleClick) {
		if (which == MouseButton.LEFT) {
			for (Element element = target; element != null; element = element.parent) {
				if (element instanceof ButtonElement button && button.sound != null && !button.disabled) {
					platform.playSound(button.sound, 1, 1);
					break;
				}
			}

			bubble(target, element -> element.onClick, which, modifiers);

			if (doubleClick) {
				bubble(target, element -> element.onDoubleClick, which, modifiers);
			}
		} else if (which == MouseButton.RIGHT) {
			bubble(target, element -> element.onRightClick, which, modifiers);
		}
	}

	public void mouseScrolled(double x, double y, double wheelY, int modifiers) {
		mouseX = x;
		mouseY = y;
		refreshHover();
		boolean horizontal = (modifiers & (platform.isMac() ? GuiEvents.SUPER : GuiEvents.ALT)) != 0;
		double stepsDown = -wheelY;
		double deltaX = horizontal ? stepsDown : 0;
		double deltaY = horizontal ? 0 : stepsDown;

		for (Element element = hoverTarget; element != null; element = element.parent) {
			if (element.disabled) {
				break;
			}

			if (element.onScroll != null) {
				double[] local = element.toLocal(x, y);
				ScrollEvent event = new ScrollEvent(element, local == null ? 0 : local[0], local == null ? 0 : local[1], deltaX, deltaY, modifiers);
				element.onScroll.accept(event);

				if (event.isStopped()) {
					return;
				}
			}
		}

		if (hoverTarget instanceof WidgetElement widget && widget.peer != null && !widget.disabled) {
			double[] local = widget.toLocal(x, y);

			if (local != null && widget.peer.mouseScrolled(local[0], local[1], 0, wheelY)) {
				return;
			}
		}

		if (hoverTarget instanceof SliderElement slider && !slider.disabled) {
			if (slider.setValue(slider.value + (horizontal ? deltaX : -deltaY) * slider.nudge())) {
				if (slider.onChange != null) {
					slider.onChange.accept(slider.value);
				}

				if (slider.onDone != null) {
					slider.onDone.accept(slider.value);
				}
			}

			return;
		}

		Element area = hit(x, y, candidate -> candidate.scrollable, null);

		for (Element element = area; element != null; element = element.parent) {
			if (!element.scrollable) {
				continue;
			}

			boolean can = horizontal ? element.canScrollX(deltaX) : element.canScrollY(deltaY);

			if (can) {
				element.scrollBy(deltaX * SCROLL_STEP, deltaY * SCROLL_STEP);
				return;
			}
		}
	}


	public void setFocus(Element element) {
		if (element == focused) {
			return;
		}

		Element previous = focused;
		focused = element;

		if (previous != null) {
			if (previous instanceof FieldElement field) {
				field.flushChange();
				platform.stopTextInput(field);
			}

			if (previous instanceof WidgetElement widget && widget.peer != null) {
				widget.peer.setFocused(false);
			}

			if (previous.onBlur != null) {
				previous.onBlur.accept(null);
			}
		}

		if (element != null) {
			if (element instanceof FieldElement field) {
				platform.startTextInput(field);
				field.caretReset = System.nanoTime();
			}

			if (element instanceof WidgetElement widget && widget.peer != null) {
				widget.peer.setFocused(true);
			}

			if (element.onFocus != null) {
				element.onFocus.accept(null);
			}
		}
	}

	private boolean forcedEscape(long now) {
		escapes[escapeIndex] = now;
		escapeIndex = (escapeIndex + 1) % escapes.length;
		long oldest = escapes[escapeIndex];
		return oldest != 0 && now - oldest < 1_000_000_000L;
	}

	public void keyPressed(KeyInput key) {
		long now = System.nanoTime();

		if (key.code == KeyInput.ESCAPE) {
			if (forcedEscape(now)) {
				closeRequest.run();
				return;
			}

			if (focused instanceof FieldElement field) {
				if (!field.keepFocus) {
					setFocus(null);
					return;
				}
			} else if (focused instanceof WidgetElement widget && widget.peer != null && widget.peer.capturesKeys()) {
				setFocus(null);
				return;
			}

			if (onEscape != null) {
				onEscape.accept(key);
			} else {
				closeRequest.run();
			}

			return;
		}

		Element target = focused;

		if (target != null && !target.disabled && target.isShown()) {
			switch (target) {
				case FieldElement field -> {
					if (!field.key(key, now) && key.code == KeyInput.TAB) {
						focusNext(!key.shift());
					}

					return;
				}
				case WidgetElement widget -> {
					if (widget.peer != null && widget.peer.keyPressed(key)) {
						return;
					}

					if (widget.peer != null && widget.peer.capturesKeys()) {
						if (key.code == KeyInput.TAB) {
							focusNext(!key.shift());
						}

						return;
					}
				}
				case ButtonElement button -> {
					if (key.isEnter() || key.code == KeyInput.SPACE) {
						click(button, MouseButton.LEFT, key.modifiers, false);
						return;
					}
				}
				case SliderElement slider -> {
					double direction = switch (key.code) {
						case KeyInput.LEFT, KeyInput.DOWN -> -1;
						case KeyInput.RIGHT, KeyInput.UP -> 1;
						default -> 0;
					};

					if (direction != 0) {
						if (slider.setValue(slider.value + direction * slider.nudge())) {
							if (slider.onChange != null) {
								slider.onChange.accept(slider.value);
							}

							if (slider.onDone != null) {
								slider.onDone.accept(slider.value);
							}
						}

						return;
					}
				}
				default -> {
				}
			}
		}

		for (Consumer<Object> handler : List.copyOf(keyHandlers)) {
			handler.accept(key);

			if (key.isStopped()) {
				return;
			}
		}

		if (key.code == KeyInput.TAB) {
			focusNext(!key.shift());
		}
	}

	public void keyReleased(KeyInput key) {
		if (focused instanceof WidgetElement widget && widget.peer != null) {
			widget.peer.keyReleased(key);
		}

		for (Consumer<Object> handler : List.copyOf(keyReleaseHandlers)) {
			handler.accept(key);

			if (key.isStopped()) {
				return;
			}
		}
	}

	public void charTyped(String text) {
		long now = System.nanoTime();

		if (focused instanceof FieldElement field && !field.disabled) {
			field.typed(text, now);
		} else if (focused instanceof WidgetElement widget && widget.peer != null && !widget.disabled) {
			widget.peer.charTyped(text);
		}
	}

	public void focusNext(boolean forward) {
		List<Element> candidates = new ArrayList<>();
		collectFocusable(root, candidates);

		if (candidates.isEmpty()) {
			return;
		}

		candidates.sort(Comparator.comparingInt(element -> element.tabIndex));
		int index = candidates.indexOf(focused);
		int next = index < 0 ? (forward ? 0 : candidates.size() - 1) : Math.floorMod(index + (forward ? 1 : -1), candidates.size());
		setFocus(candidates.get(next));
	}

	private void collectFocusable(Element element, List<Element> out) {
		if (!element.visible) {
			return;
		}

		if (element.focusable()) {
			out.add(element);
		}

		for (Element child : element.paintOrder()) {
			collectFocusable(child, out);
		}
	}

	public void mouseLeft() {
		mouseX = -1e9;
		mouseY = -1e9;
		refreshHover();
	}
}
