package me.skaffy.client.gui.element;

import java.util.ArrayList;
import java.util.List;

import me.skaffy.client.gui.element.GuiEnums.Align;
import me.skaffy.client.gui.element.GuiEnums.LayoutMode;
import me.skaffy.client.gui.sfy.LengthValue;

final class Layout {
	private Layout() {
	}

	static void run(GuiDocument document) {
		Element root = document.root;
		root.lx = 0;
		root.ly = 0;
		root.lw = document.width;
		root.lh = document.height;
		double[] content = children(root, root.innerWidth(), root.innerHeight());
		root.contentW = content[0];
		root.contentH = content[1];
		root.afterLayout();
		place(root, Affine.IDENTITY.scale(document.scaleFactor), 1);
	}

	static void measure(Element element, double roomWidth, double roomHeight) {
		double width = size(element.length(Prop.WIDTH), roomWidth);
		double height = size(element.length(Prop.HEIGHT), roomHeight);
		layoutAt(element, width, height);
	}

	private static void layoutAt(Element element, double width, double height) {
		double padX = element.padLeft + element.padRight;
		double padY = element.padTop + element.padBottom;
		double innerWidth = Double.isNaN(width) ? Double.NaN : Math.max(0, width - padX);
		double innerHeight = Double.isNaN(height) ? Double.NaN : Math.max(0, height - padY);
		double[] intrinsic = element.intrinsicSize(innerWidth, innerHeight);
		double[] content = children(element, innerWidth, innerHeight);
		element.autoW = Math.max(intrinsic[0], content[0]) + padX;
		element.autoH = Math.max(intrinsic[1], content[1]) + padY;
		element.lw = Math.max(0, Double.isNaN(width) ? element.autoW : width);
		element.lh = Math.max(0, Double.isNaN(height) ? element.autoH : height);
		element.contentW = Math.max(content[0], intrinsic[0]);
		element.contentH = Math.max(content[1], intrinsic[1]);

		if (element.layout != LayoutMode.NONE && (Double.isNaN(innerWidth) || Double.isNaN(innerHeight))) {
			flow(element, element.innerWidth(), element.innerHeight(), false);
		}

		element.afterLayout();
	}

	private static double size(LengthValue length, double room) {
		if (length == null) {
			return Double.NaN;
		}

		if (Double.isNaN(room)) {
			return length.hasPercent() ? Double.NaN : length.pixels();
		}

		return Math.max(0, length.resolve(room));
	}

	private static double position(LengthValue length, double room) {
		if (length == null) {
			return 0;
		}

		return Double.isNaN(room) ? length.pixels() : length.resolve(room);
	}

	private static double[] children(Element element, double innerWidth, double innerHeight) {
		double reachX = 0;
		double reachY = 0;

		for (Element child : element.allChildren()) {
			if (!child.visible) {
				continue;
			}

			boolean inFlow = element.layout != LayoutMode.NONE && !child.absolute;
			measure(child, innerWidth, innerHeight);

			if (!inFlow) {
				child.lx = position(child.length(Prop.X), innerWidth);
				child.ly = position(child.length(Prop.Y), innerHeight);

				if (!child.internal) {
					reachX = Math.max(reachX, child.lx + child.lw);
					reachY = Math.max(reachY, child.ly + child.lh);
				}
			}
		}

		if (element.layout != LayoutMode.NONE) {
			double[] flowReach = flow(element, innerWidth, innerHeight, true);
			reachX = Math.max(reachX, flowReach[0]);
			reachY = Math.max(reachY, flowReach[1]);
		}

		return new double[] {reachX, reachY};
	}

	private static double[] flow(Element element, double innerWidth, double innerHeight, boolean allowStretch) {
		boolean row = element.layout == LayoutMode.ROW;
		double mainLimit = row ? innerWidth : innerHeight;
		double crossLimit = row ? innerHeight : innerWidth;
		List<List<Element>> lines = new ArrayList<>();
		List<Element> line = new ArrayList<>();
		double used = 0;

		for (Element child : element.allChildren()) {
			if (!child.visible || child.absolute) {
				continue;
			}

			double main = row ? child.lw : child.lh;

			if (element.wrap && !Double.isNaN(mainLimit) && !line.isEmpty() && used + element.gap + main > mainLimit + 1e-6) {
				lines.add(line);
				line = new ArrayList<>();
				used = 0;
			}

			used += (line.isEmpty() ? 0 : element.gap) + main;
			line.add(child);
		}

		if (!line.isEmpty()) {
			lines.add(line);
		}

		double crossCursor = 0;
		double reachMain = 0;

		for (int l = 0; l < lines.size(); l++) {
			List<Element> items = lines.get(l);
			double lineMain = 0;
			double lineCross = 0;

			for (Element item : items) {
				lineMain += row ? item.lw : item.lh;
				lineCross = Math.max(lineCross, row ? item.lh : item.lw);
			}

			lineMain += element.gap * (items.size() - 1);

			if (lines.size() == 1 && !Double.isNaN(crossLimit)) {
				lineCross = Math.max(lineCross, crossLimit);
			}

			double free = Double.isNaN(mainLimit) ? 0 : Math.max(0, mainLimit - lineMain);
			double start = 0;
			double between = element.gap;

			switch (element.justify) {
				case START -> {
				}
				case CENTER -> start = free / 2;
				case END -> start = free;
				case BETWEEN -> between += items.size() > 1 ? free / (items.size() - 1) : 0;
				case AROUND -> {
					double each = free / items.size();
					start = each / 2;
					between += each;
				}
				case EVENLY -> {
					double each = free / (items.size() + 1);
					start = each;
					between += each;
				}
			}

			double cursor = start;

			for (Element item : items) {
				double itemCross = row ? item.lh : item.lw;
				double crossPosition = switch (element.alignItems) {
					case START -> 0;
					case CENTER -> (lineCross - itemCross) / 2;
					case END -> lineCross - itemCross;
					case STRETCH -> 0;
				};

				if (element.alignItems == Align.STRETCH && allowStretch && Math.abs(itemCross - lineCross) > 1e-6
						&& (row ? item.base(Prop.HEIGHT) == null : item.base(Prop.WIDTH) == null)) {
					layoutAt(item, row ? item.lw : lineCross, row ? lineCross : item.lh);
				}

				double offsetMain = position(item.length(row ? Prop.X : Prop.Y), mainLimit);
				double offsetCross = position(item.length(row ? Prop.Y : Prop.X), crossLimit);

				if (row) {
					item.lx = cursor + offsetMain;
					item.ly = crossCursor + crossPosition + offsetCross;
				} else {
					item.ly = cursor + offsetMain;
					item.lx = crossCursor + crossPosition + offsetCross;
				}

				cursor += (row ? item.lw : item.lh) + between;
			}

			reachMain = Math.max(reachMain, lineMain + start);
			crossCursor += lineCross + (l < lines.size() - 1 ? element.gap : 0);
		}

		return row ? new double[] {reachMain, crossCursor} : new double[] {crossCursor, reachMain};
	}

	private static void place(Element element, Affine matrix, double parentOpacity) {
		element.matrix = matrix;
		element.opacity = parentOpacity * Math.clamp(1 - element.number(Prop.TRANSPARENCY) / 100, 0, 1);
		Affine content = matrix.translate(element.padLeft, element.padTop);
		Affine scrolled = content.translate(-element.scrollX, -element.scrollY);

		for (Element child : element.allChildren()) {
			if (!child.visible) {
				continue;
			}

			Affine base = child.fixed ? content : scrolled;
			Affine childMatrix = base.translate(child.lx + child.number(Prop.OFFSET_X), child.ly + child.number(Prop.OFFSET_Y));
			double rotation = child.number(Prop.ROTATION);
			Object scaleValue = child.shown(Prop.SCALE);
			double scale = scaleValue instanceof Double number ? number : 1;

			if (rotation != 0 || scale != 1) {
				double ox = child.originX.resolve(child.lw);
				double oy = child.originY.resolve(child.lh);
				childMatrix = childMatrix.translate(ox, oy).rotate(rotation).scale(scale).translate(-ox, -oy);
			}

			place(child, childMatrix, element.opacity);
		}
	}
}
