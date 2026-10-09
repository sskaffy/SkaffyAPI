package me.skaffy.client.gui.element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import me.skaffy.client.gui.text.RichText;

public final class CanvasElement extends Element {
	public Consumer<Object> draw;
	public List<Op> ops = List.of();

	public CanvasElement(GuiDocument document) {
		super(document);
	}

	@Override
	void tick(long now) {
		if (draw == null || !isShown()) {
			ops = List.of();
			return;
		}

		Graphics graphics = new Graphics(this);
		draw.accept(graphics);
		ops = graphics.ops;
	}

	@Override
	double[] intrinsicSize(double innerWidth, double innerHeight) {
		return new double[] {100, 100};
	}

	@Override
	public String typeName() {
		return "Canvas";
	}

	public sealed interface Op permits Rect, Line, Circle, Picture, Label {
	}

	public record Rect(double x, double y, double width, double height, double radius, int color) implements Op {
	}

	public record Line(double x1, double y1, double x2, double y2, double width, int color) implements Op {
	}

	public record Circle(double x, double y, double radius, double ringWidth, int color) implements Op {
	}

	public record Picture(String source, double x, double y, double width, double height, int color) implements Op {
	}

	public record Label(RichText text, double x, double y, int color, double size) implements Op {
	}

	public static final class Graphics {
		private final CanvasElement canvas;
		final List<Op> ops = new ArrayList<>();

		Graphics(CanvasElement canvas) {
			this.canvas = canvas;
		}

		public double width() {
			return canvas.innerWidth();
		}

		public double height() {
			return canvas.innerHeight();
		}

		public void rect(double x, double y, double width, double height, int color) {
			ops.add(new Rect(x, y, width, height, 0, color));
		}

		public void roundRect(double x, double y, double width, double height, double radius, int color) {
			ops.add(new Rect(x, y, width, height, radius, color));
		}

		public void line(double x1, double y1, double x2, double y2, double width, int color) {
			ops.add(new Line(x1, y1, x2, y2, width, color));
		}

		public void circle(double x, double y, double radius, int color) {
			ops.add(new Circle(x, y, radius, 0, color));
		}

		public void ring(double x, double y, double radius, double width, int color) {
			ops.add(new Circle(x, y, radius, width, color));
		}

		public void image(String source, double x, double y, double width, double height) {
			ops.add(new Picture(source, x, y, width, height, 0xFFFFFFFF));
		}

		public void text(RichText text, double x, double y, int color, double size) {
			ops.add(new Label(text, x, y, color, size));
		}
	}
}
