package me.skaffy.client.gui.element;

import java.util.function.Consumer;

import me.skaffy.client.gui.element.GuiEnums.Cursor;
import me.skaffy.client.gui.sfy.LengthValue;

public final class SliderElement extends Element {
	public final BoxElement track;
	public final BoxElement fill;
	public final BoxElement knob;
	public double min;
	public double max;
	public double value;
	public double step;
	public boolean vertical;
	public Consumer<Object> onChange;
	public Consumer<Object> onDone;

	public SliderElement(GuiDocument document, double min, double max) {
		super(document);
		this.min = min;
		this.max = max;
		this.value = min;
		cursor = Cursor.HAND;
		track = part(0x40FFFFFF, 4, 2);
		fill = part(0xFFD8D8D8, 4, 2);
		knob = part(0xFFFFFFFF, 10, 5);
		knob.set(Prop.SHADOW_COLOR, 0x80000000);
		knob.set(Prop.SHADOW_BLUR, 3.0);
	}

	private BoxElement part(int color, double size, double radius) {
		BoxElement part = new BoxElement(document);
		part.internal = true;
		part.mouseThrough = true;
		part.absolute = true;
		part.set(Prop.COLOR, color);
		part.set(Prop.WIDTH, LengthValue.px(size));
		part.set(Prop.HEIGHT, LengthValue.px(size));
		part.set(Prop.RADIUS_TOP_LEFT, radius);
		part.set(Prop.RADIUS_TOP_RIGHT, radius);
		part.set(Prop.RADIUS_BOTTOM_RIGHT, radius);
		part.set(Prop.RADIUS_BOTTOM_LEFT, radius);
		part.parent = this;
		children.add(part);
		return part;
	}

	@Override
	public boolean focusable() {
		return !disabled;
	}

	@Override
	public boolean catchesMouse() {
		return mouseThrough == null || !mouseThrough;
	}

	@Override
	double[] intrinsicSize(double innerWidth, double innerHeight) {
		return vertical ? new double[] {16, 150} : new double[] {150, 16};
	}

	private static double px(Element part, Prop prop, double fallback) {
		Object value = part.shown(prop);
		return value instanceof LengthValue length ? length.pixels() : fallback;
	}

	public double fraction() {
		return max == min ? 0 : Math.clamp((value - min) / (max - min), 0, 1);
	}

	@Override
	void afterLayout() {
		double length = vertical ? innerHeight() : innerWidth();
		double across = vertical ? innerWidth() : innerHeight();
		double thickness = px(track, vertical ? Prop.WIDTH : Prop.HEIGHT, 4);
		double knobAlong = px(knob, vertical ? Prop.HEIGHT : Prop.WIDTH, 10);
		double knobAcross = px(knob, vertical ? Prop.WIDTH : Prop.HEIGHT, 10);
		double travel = Math.max(0, length - knobAlong);
		double position = knobAlong / 2 + travel * (vertical ? 1 - fraction() : fraction());
		place(track, 0, (across - thickness) / 2, length, thickness);

		if (vertical) {
			place(fill, position, (across - thickness) / 2, length - position, thickness);
		} else {
			place(fill, 0, (across - thickness) / 2, position, thickness);
		}

		place(knob, position - knobAlong / 2, (across - knobAcross) / 2, knobAlong, knobAcross);
	}

	private void place(Element part, double along, double across, double alongSize, double acrossSize) {
		if (vertical) {
			part.lx = across;
			part.ly = along;
			part.lw = acrossSize;
			part.lh = alongSize;
		} else {
			part.lx = along;
			part.ly = across;
			part.lw = alongSize;
			part.lh = acrossSize;
		}
	}

	boolean setFromPoint(double x, double y) {
		double length = vertical ? innerHeight() : innerWidth();
		double knobAlong = px(knob, vertical ? Prop.HEIGHT : Prop.WIDTH, 10);
		double travel = Math.max(1, length - knobAlong);
		double along = (vertical ? y - padTop : x - padLeft) - knobAlong / 2;
		double t = Math.clamp(along / travel, 0, 1);

		if (vertical) {
			t = 1 - t;
		}

		return setValue(min + (max - min) * t);
	}

	public boolean setValue(double requested) {
		double low = Math.min(min, max);
		double high = Math.max(min, max);
		double snapped = requested;

		if (step > 0) {
			snapped = min + Math.round((requested - min) / step) * step;
		}

		snapped = Math.clamp(snapped, low, high);

		if (snapped == value) {
			return false;
		}

		value = snapped;
		return true;
	}

	double nudge() {
		return step > 0 ? step : Math.abs(max - min) / 100;
	}

	@Override
	public String typeName() {
		return "Slider";
	}
}
