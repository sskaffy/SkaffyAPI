package me.skaffy.client.gui;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;

import me.skaffy.client.gui.element.GuiEnums.MouseButton;
import me.skaffy.client.gui.element.GuiEvents;
import me.skaffy.client.gui.element.GuiEvents.KeyInput;
import me.skaffy.client.gui.element.WidgetElement;
import me.skaffy.client.gui.element.WidgetPeer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

final class VanillaPeers {
	private VanillaPeers() {
	}

	interface Peer extends WidgetPeer {
		void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha);
	}

	private abstract static class Base implements Peer {
		final WidgetElement element;
		AbstractWidget widget;
		private final double[] defaultSize;

		Base(WidgetElement element, AbstractWidget widget) {
			this.element = element;
			this.widget = widget;
			this.defaultSize = new double[] {widget.getWidth(), widget.getHeight()};
		}

		void replace(AbstractWidget rebuilt) {
			boolean focused = widget.isFocused();
			rebuilt.setRectangle(widget.getWidth(), widget.getHeight(), 0, 0);
			rebuilt.active = widget.active;
			widget = rebuilt;
			widget.setFocused(focused);
		}

		@Override
		public double[] defaultSize() {
			return defaultSize;
		}

		@Override
		public void setSize(int width, int height) {
			widget.setRectangle(Math.max(1, width), Math.max(1, height), 0, 0);
		}

		@Override
		public void setActive(boolean active) {
			widget.active = active;
		}

		@Override
		public void setFocused(boolean focused) {
			widget.setFocused(focused);
		}

		private static MouseButtonEvent event(double x, double y, int button, int modifiers) {
			return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
		}

		@Override
		public boolean mousePressed(double x, double y, int button, int modifiers, boolean doubleClick) {
			return widget.mouseClicked(event(x, y, button, modifiers), doubleClick);
		}

		@Override
		public boolean mouseReleased(double x, double y, int button, int modifiers) {
			return widget.mouseReleased(event(x, y, button, modifiers));
		}

		@Override
		public boolean mouseDragged(double x, double y, int button, int modifiers, double dx, double dy) {
			return widget.mouseDragged(event(x, y, button, modifiers), dx, dy);
		}

		@Override
		public boolean mouseScrolled(double x, double y, double dx, double dy) {
			return widget.mouseScrolled(x, y, dx, dy);
		}

		static KeyEvent vanilla(KeyInput key) {
			int scancode;

			try {
				scancode = InputConstants.getKey(key.name).getValue();
			} catch (RuntimeException e) {
				scancode = 0;
			}

			return new KeyEvent(scancode, key.code, key.modifiers);
		}

		@Override
		public boolean keyPressed(KeyInput key) {
			return widget.keyPressed(vanilla(key));
		}

		@Override
		public boolean keyReleased(KeyInput key) {
			return widget.keyReleased(vanilla(key));
		}

		@Override
		public boolean charTyped(String text) {
			boolean used = false;

			for (int i = 0; i < text.length(); ) {
				int codepoint = text.codePointAt(i);
				used |= widget.charTyped(new CharacterEvent(codepoint));
				i += Character.charCount(codepoint);
			}

			return used;
		}

		@Override
		public boolean capturesKeys() {
			return widget.capturesInput();
		}

		@Override
		public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
			widget.setAlpha(alpha);
			widget.extractRenderState(graphics, mouseX, mouseY, 0);
		}
	}

	private static void click(WidgetElement element) {
		if (element.onClick != null) {
			double x = element.lw / 2;
			double y = element.lh / 2;
			element.onClick.accept(new GuiEvents.MouseEvent(element, element, x, y, element.matrix.x(x, y), element.matrix.y(x, y), MouseButton.LEFT, 0));
		}
	}

	static final class ButtonPeer extends Base {
		ButtonPeer(WidgetElement element, String label) {
			super(element, Button.builder(Component.literal(label), button -> click(element)).bounds(0, 0, 150, 20).build());
		}

		void label(String label) {
			widget.setMessage(Component.literal(label));
		}
	}

	static final class FieldPeer extends Base {
		Consumer<Object> onChange;
		Consumer<Object> onSubmit;

		FieldPeer(WidgetElement element) {
			super(element, new EditBox(GuiText.font(), 0, 0, 150, 20, Component.empty()));
			box().setResponder(value -> {
				if (onChange != null) {
					onChange.accept(value);
				}
			});
			box().setMaxLength(256);
		}

		EditBox box() {
			return (EditBox) widget;
		}

		@Override
		public boolean keyPressed(KeyInput key) {
			if (key.isEnter() && widget.isFocused()) {
				if (onSubmit != null) {
					onSubmit.accept(box().getValue());
				}

				return true;
			}

			return super.keyPressed(key);
		}
	}

	private static final class RangeSlider extends AbstractSliderButton {
		double min;
		double max;
		double step;
		Consumer<Object> onChange;
		@Nullable Function<Double, String> label;

		RangeSlider(double min, double max) {
			super(0, 0, 150, 20, Component.empty(), 0);
			this.min = min;
			this.max = max;
			updateMessage();
		}

		double real() {
			double raw = min + value * (max - min);

			if (step > 0) {
				raw = min + Math.round((raw - min) / step) * step;
			}

			return Math.clamp(raw, Math.min(min, max), Math.max(min, max));
		}

		void setReal(double real) {
			value = max == min ? 0 : Math.clamp((real - min) / (max - min), 0, 1);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			String text = null;

			if (label != null) {
				text = label.apply(real());
			}

			if (text == null) {
				double shown = real();
				text = shown == Math.rint(shown) && Math.abs(shown) < 1e12 ? Long.toString((long) shown) : String.format(Locale.ROOT, "%.2f", shown);
			}

			setMessage(Component.literal(text));
		}

		@Override
		protected void applyValue() {
			if (step > 0) {
				setReal(real());
			}

			if (onChange != null) {
				onChange.accept(real());
			}
		}
	}

	static final class SliderPeer extends Base {
		SliderPeer(WidgetElement element, double min, double max) {
			super(element, new RangeSlider(min, max));
		}

		RangeSlider slider() {
			return (RangeSlider) widget;
		}

		void value(double value) {
			slider().setReal(value);
		}

		double value() {
			return slider().real();
		}

		void step(double step) {
			slider().step = Math.max(0, step);
			slider().setReal(slider().real());
		}

		void label(Function<Double, String> label) {
			slider().label = label;
			slider().updateMessage();
		}

		void onChange(Consumer<Object> handler) {
			slider().onChange = handler;
		}
	}

	static final class CheckboxPeer extends Base {
		private String label;
		private boolean checked;
		Consumer<Object> onChange;

		CheckboxPeer(WidgetElement element, String label) {
			super(element, build(label, false, null));
			this.label = label;
			replace(build(label, false, this));
		}

		private static Checkbox build(String label, boolean checked, @Nullable CheckboxPeer peer) {
			return Checkbox.builder(Component.literal(label), GuiText.font()).selected(checked).onValueChange((box, value) -> {
				if (peer != null) {
					peer.checked = value;

					if (peer.onChange != null) {
						peer.onChange.accept(value);
					}
				}
			}).build();
		}

		void checked(boolean value) {
			checked = value;
			replace(build(label, value, this));
		}

		boolean checked() {
			return checked;
		}

		void label(String value) {
			label = value;
			replace(build(value, checked, this));
		}
	}

	static final class CyclePeer extends Base {
		private List<String> values;
		private String label = "";
		private String value;
		Consumer<Object> onChange;

		CyclePeer(WidgetElement element, List<String> values) {
			super(element, build(values.isEmpty() ? List.of("") : values, values.isEmpty() ? "" : values.getFirst(), "", null));
			this.values = values.isEmpty() ? List.of("") : List.copyOf(values);
			this.value = this.values.getFirst();
			replace(build(this.values, value, label, this));
		}

		private static CycleButton<String> build(List<String> values, String value, String label, @Nullable CyclePeer peer) {
			CycleButton.Builder<String> builder = CycleButton.builder(Component::literal, value).withValues(values);

			if (label.isEmpty()) {
				builder = builder.displayOnlyValue();
			}

			return builder.create(0, 0, 150, 20, Component.literal(label), (button, chosen) -> {
				if (peer != null) {
					peer.value = chosen;

					if (peer.onChange != null) {
						peer.onChange.accept(chosen);
					}
				}
			});
		}

		void values(List<String> newValues) {
			values = newValues.isEmpty() ? List.of("") : List.copyOf(newValues);

			if (!values.contains(value)) {
				value = values.getFirst();
			}

			replace(build(values, value, label, this));
		}

		void value(String newValue) {
			if (values.contains(newValue)) {
				value = newValue;
				replace(build(values, value, label, this));
			}
		}

		String value() {
			return value;
		}

		void label(String newLabel) {
			label = newLabel;
			replace(build(values, value, label, this));
		}
	}
}
