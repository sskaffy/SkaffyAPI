package me.skaffy.client.gui;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import me.skaffy.client.gui.element.Affine;
import me.skaffy.client.gui.element.CanvasElement;
import me.skaffy.client.gui.element.Element;
import me.skaffy.client.gui.element.FieldElement;
import me.skaffy.client.gui.element.GuiDocument;
import me.skaffy.client.gui.element.GuiEnums.TextAlign;
import me.skaffy.client.gui.element.PictureElements.EntityElement;
import me.skaffy.client.gui.element.PictureElements.HeadElement;
import me.skaffy.client.gui.element.PictureElements.ImageElement;
import me.skaffy.client.gui.element.PictureElements.ItemElement;
import me.skaffy.client.gui.element.Prop;
import me.skaffy.client.gui.element.TextElement;
import me.skaffy.client.gui.element.WidgetElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix3x2fStack;

final class GuiPainter {
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
	private static final Identifier SCROLLER_BACKGROUND = Identifier.withDefaultNamespace("widget/scroller_background");

	private final Map<Element, double[]> scrollState = new WeakHashMap<>();
	private final Set<Element> failed = Collections.newSetFromMap(new WeakHashMap<>());

	static void setPose(Matrix3x2fStack pose, Affine affine) {
		pose.set((float) affine.a(), (float) affine.b(), (float) affine.c(), (float) affine.d(), (float) affine.tx(), (float) affine.ty());
	}

	static int faded(int color, double opacity) {
		int alpha = (int) Math.round((color >>> 24) * Math.clamp(opacity, 0, 1));
		return alpha << 24 | color & 0xFFFFFF;
	}

	private static double[] radii(Element element) {
		return new double[] {element.number(Prop.RADIUS_TOP_LEFT), element.number(Prop.RADIUS_TOP_RIGHT), element.number(Prop.RADIUS_BOTTOM_RIGHT), element.number(Prop.RADIUS_BOTTOM_LEFT)};
	}

	void paint(GuiGraphicsExtractor graphics, GuiDocument document, GuiSession session) {
		paint(graphics, document, session, true);
	}

	void paint(GuiGraphicsExtractor graphics, GuiDocument document, GuiSession session, boolean interactive) {
		paintElement(graphics, document.root, document);

		if (interactive) {
			tooltip(graphics, document);
			graphics.requestCursor(cursor(document));
		}
	}

	private void paintElement(GuiGraphicsExtractor graphics, Element element, GuiDocument document) {
		if (!element.visible || element.opacity <= 0.002) {
			return;
		}

		boolean wasInverting = Shapes.invert;
		Shapes.invert |= element.invert;

		try {
			paintElementInner(graphics, element, document);
		} finally {
			Shapes.invert = wasInverting;
		}
	}

	private void paintElementInner(GuiGraphicsExtractor graphics, Element element, GuiDocument document) {
		Affine matrix = element.matrix;
		double opacity = element.opacity;
		double[] radii = radii(element);
		int shadow = element.color(Prop.SHADOW_COLOR);

		if ((shadow >>> 24) > 0) {
			Shapes.shadow(graphics, matrix, element.lw, element.lh, radii, shadow, element.number(Prop.SHADOW_BLUR),
					element.number(Prop.SHADOW_X), element.number(Prop.SHADOW_Y), element.number(Prop.SHADOW_SPREAD), opacity);
		}

		Shapes.box(graphics, matrix, element.lw, element.lh, radii, element.pixelCorners, element.color(element.fillProp()), element.gradient, element.gradientAngle,
				element.number(Prop.BORDER_WIDTH), element.color(Prop.BORDER_COLOR), opacity);

		try {
			switch (element) {
				case TextElement text -> drawText(graphics, text);
				case ImageElement image -> drawImage(graphics, image, radii);
				case HeadElement head -> GuiTextures.drawHead(graphics, head.player, head.hat, matrix.translate(head.padLeft, head.padTop), head.innerWidth(), head.innerHeight(),
						insetRadii(radii, head.padLeft), faded(head.color(Prop.COLOR), opacity));
				case ItemElement item -> drawItem(graphics, item);
				case EntityElement entity -> drawEntity(graphics, entity, document);
				case FieldElement field -> drawField(graphics, field);
				case WidgetElement widget -> drawWidget(graphics, widget, document);
				case CanvasElement canvas -> drawCanvas(graphics, canvas);
				default -> {
				}
			}
		} catch (RuntimeException e) {
			if (failed.add(element)) {
				me.skaffy.client.SkaffySAPIClient.LOGGER.warn("Couldn't draw a GUI {}: {}", element.typeName(), e.toString());
			}
		}

		boolean clip = (element.clip || element.scrollable) && element != document.root;

		if (clip) {
			pushScissor(graphics, matrix, 0, 0, element.lw, element.lh);
		}

		for (Element child : element.paintOrderPublic()) {
			paintElement(graphics, child, document);
		}

		if (clip) {
			graphics.disableScissor();
		}

		if (element.scrollable) {
			scrollbars(graphics, element, document);
		}
	}

	private static double[] insetRadii(double[] radii, double by) {
		return new double[] {Math.max(0, radii[0] - by), Math.max(0, radii[1] - by), Math.max(0, radii[2] - by), Math.max(0, radii[3] - by)};
	}

	private static void pushScissor(GuiGraphicsExtractor graphics, Affine matrix, double x, double y, double width, double height) {
		double[] xs = {matrix.x(x, y), matrix.x(x + width, y), matrix.x(x, y + height), matrix.x(x + width, y + height)};
		double[] ys = {matrix.y(x, y), matrix.y(x + width, y), matrix.y(x, y + height), matrix.y(x + width, y + height)};
		double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
		double maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
		double minY = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]));
		double maxY = Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]));
		graphics.enableScissor((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY));
	}


	private static void drawText(GuiGraphicsExtractor graphics, TextElement text) {
		if (!(text.textLayout instanceof GuiText.Layout layout)) {
			return;
		}

		int color = faded(text.color(Prop.COLOR), text.opacity);
		Integer shadowColor = text.textShadowColor;
		GuiText.draw(graphics, layout, text.matrix.translate(text.padLeft, text.padTop), text.innerWidth(), color, text.textShadow, shadowColor, text.opacity);
	}

	private static void drawImage(GuiGraphicsExtractor graphics, ImageElement image, double[] radii) {
		double x = image.padLeft;
		double y = image.padTop;
		double width = image.innerWidth();
		double height = image.innerHeight();
		int tint = faded(image.color(Prop.COLOR), image.opacity);

		if (width <= 0 || height <= 0 || (tint >>> 24) == 0) {
			return;
		}

		if (image.guiSprite) {
			TextureAtlasSprite sprite = GuiTextures.guiSprite(image.source);
			Identifier id = sprite == null ? Identifier.withDefaultNamespace("missing") : sprite.contents().name();
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			setPose(pose, image.matrix.translate(x, y));
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, 0, 0, Math.max(1, (int) Math.round(width)), Math.max(1, (int) Math.round(height)), tint);
			pose.popMatrix();
			return;
		}

		GuiTextures.Texture texture = GuiTextures.image(image.source);
		int[] origin = texture.frameOrigin();
		double textureWidth = texture.imageWidth;
		double textureHeight = texture.imageHeight;
		double u = origin[0];
		double v = origin[1];
		double regionWidth = texture.frameWidth;
		double regionHeight = texture.frameHeight;

		if (image.region != null) {
			u += image.region[0];
			v += image.region[1];
			regionWidth = image.region[2];
			regionHeight = image.region[3];
		}

		Affine matrix = image.matrix;
		double[] inner = insetRadii(radii, Math.min(image.padLeft, image.padTop));

		switch (image.mode) {
			case STRETCH -> Shapes.texturedRect(graphics, matrix, x, y, width, height, inner, texture, u / textureWidth, v / textureHeight,
					(u + regionWidth) / textureWidth, (v + regionHeight) / textureHeight, tint);
			case FIT -> {
				double scale = Math.min(width / regionWidth, height / regionHeight);
				double w = regionWidth * scale;
				double h = regionHeight * scale;
				Shapes.texturedRect(graphics, matrix, x + (width - w) / 2, y + (height - h) / 2, w, h, inner, texture, u / textureWidth, v / textureHeight,
						(u + regionWidth) / textureWidth, (v + regionHeight) / textureHeight, tint);
			}
			case FILL -> {
				double scale = Math.max(width / regionWidth, height / regionHeight);
				double visibleWidth = width / scale;
				double visibleHeight = height / scale;
				double cropU = u + (regionWidth - visibleWidth) / 2;
				double cropV = v + (regionHeight - visibleHeight) / 2;
				Shapes.texturedRect(graphics, matrix, x, y, width, height, inner, texture, cropU / textureWidth, cropV / textureHeight,
						(cropU + visibleWidth) / textureWidth, (cropV + visibleHeight) / textureHeight, tint);
			}
			case TILE -> {
				pushScissor(graphics, matrix, x, y, width, height);

				for (double ty = 0; ty < height; ty += regionHeight) {
					for (double tx = 0; tx < width; tx += regionWidth) {
						double w = Math.min(regionWidth, width - tx);
						double h = Math.min(regionHeight, height - ty);
						Shapes.texturedRect(graphics, matrix, x + tx, y + ty, w, h, null, texture, u / textureWidth, v / textureHeight,
								(u + w) / textureWidth, (v + h) / textureHeight, tint);
					}
				}

				graphics.disableScissor();
			}
			case NINE_SLICE -> {
				double left = Math.min(image.slice[0], width / 2);
				double top = Math.min(image.slice[1], height / 2);
				double right = Math.min(image.slice[2], width / 2);
				double bottom = Math.min(image.slice[3], height / 2);
				double[] xs = {x, x + left, x + width - right, x + width};
				double[] ys = {y, y + top, y + height - bottom, y + height};
				double[] us = {u, u + image.slice[0], u + regionWidth - image.slice[2], u + regionWidth};
				double[] vs = {v, v + image.slice[1], v + regionHeight - image.slice[3], v + regionHeight};

				for (int row = 0; row < 3; row++) {
					for (int column = 0; column < 3; column++) {
						Shapes.texturedRect(graphics, matrix, xs[column], ys[row], xs[column + 1] - xs[column], ys[row + 1] - ys[row], null, texture,
								us[column] / textureWidth, vs[row] / textureHeight, us[column + 1] / textureWidth, vs[row + 1] / textureHeight, tint);
					}
				}
			}
		}
	}

	private static void drawItem(GuiGraphicsExtractor graphics, ItemElement item) {
		if (item.slot >= 0) {
			net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
			ItemStack stack = player == null ? ItemStack.EMPTY : player.getInventory().getItem(item.slot);

			if (!stack.isEmpty()) {
				drawStack(graphics, item, stack);
			}

			return;
		}

		Object resolved = GuiTextures.item(item.item);

		if (!(resolved instanceof ItemStack stack)) {
			if (item.resource != resolved) {
				item.resource = resolved;
				me.skaffy.client.SkaffySAPIClient.LOGGER.warn("GUI item {} is invalid: {}", item.item, resolved);
			}

			return;
		}

		ItemStack shown = stack.copyWithCount(Math.max(1, item.count));
		item.resource = shown;
		drawStack(graphics, item, shown);
	}

	private static void drawStack(GuiGraphicsExtractor graphics, ItemElement item, ItemStack shown) {
		double size = Math.min(item.innerWidth(), item.innerHeight());
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		setPose(pose, item.matrix.translate(item.padLeft + (item.innerWidth() - size) / 2, item.padTop + (item.innerHeight() - size) / 2).scale(size / 16));
		graphics.item(shown, 0, 0);

		if (item.decorations) {
			graphics.itemDecorations(GuiText.font(), shown, 0, 0);
		}

		pose.popMatrix();
	}

	private static void drawEntity(GuiGraphicsExtractor graphics, EntityElement element, GuiDocument document) {
		Minecraft minecraft = Minecraft.getInstance();
		LivingEntity entity = element.type == null ? minecraft.player : GuiTextures.entity(element.type);

		if (entity == null) {
			return;
		}

		Affine matrix = element.matrix;
		double x0 = matrix.x(element.padLeft, element.padTop);
		double y0 = matrix.y(element.padLeft, element.padTop);
		double x1 = matrix.x(element.padLeft + element.innerWidth(), element.padTop + element.innerHeight());
		double y1 = matrix.y(element.padLeft + element.innerWidth(), element.padTop + element.innerHeight());
		int left = (int) Math.floor(Math.min(x0, x1));
		int top = (int) Math.floor(Math.min(y0, y1));
		int right = (int) Math.ceil(Math.max(x0, x1));
		int bottom = (int) Math.ceil(Math.max(y0, y1));
		float height = Math.max(0.1F, entity.getBbHeight());
		float width = Math.max(0.1F, entity.getBbWidth());
		int size = (int) Math.max(1, Math.min((right - left) / (width * 1.6), (bottom - top) / (height * 1.2)));
		float centerX = (left + right) / 2F;
		float centerY = (top + bottom) / 2F;
		float mouseX;
		float mouseY;

		if (element.followMouse) {
			mouseX = (float) (document.mouseX * document.scaleFactor);
			mouseY = (float) (document.mouseY * document.scaleFactor);
		} else {
			mouseX = centerX - (float) Math.tan(Math.clamp(element.yaw, -30, 30) / 20) * 40;
			mouseY = centerY - (float) Math.tan(Math.clamp(element.pitch, -30, 30) / 20) * 40;
		}

		InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, left, top, right, bottom, size, 0.0625F, mouseX, mouseY, entity);
	}

	private static void drawField(GuiGraphicsExtractor graphics, FieldElement field) {
		double width = field.innerWidth();
		double height = field.innerHeight();

		if (width <= 0 || height <= 0) {
			return;
		}

		Affine content = field.matrix.translate(field.padLeft, field.padTop);
		double opacity = field.opacity;
		double scale = field.number(Prop.FONT_SIZE) / 8;
		double lineHeight = field.lineHeight();
		FontDescription font = GuiText.fontOf(field.font, field.customFont);
		Style style = Style.EMPTY.withFont(font);
		int textColor = faded(field.color(Prop.TEXT_COLOR), opacity);
		boolean focused = field.isFocused();
		String value = field.editor.value();
		String display = field.display();
		pushScissor(graphics, content, 0, 0, width, height);

		if (value.isEmpty() && !field.placeholder.isEmpty()) {
			GuiText.Layout placeholder = GuiText.layout(field.placeholder, new GuiText.Look(font, false, false, false, false, false, field.number(Prop.FONT_SIZE), 1, TextAlign.LEFT), field.editor.multiline ? width : Double.POSITIVE_INFINITY);
			GuiText.draw(graphics, placeholder, content, width, faded(field.placeholderColor, opacity), false, null, opacity);
		}

		boolean multiline = field.editor.multiline;
		int selectionStart = field.editor.selectionStart();
		int selectionEnd = field.editor.selectionEnd();
		int selectionColor = faded(field.selectionColor, opacity);

		if (!multiline) {
			Affine line = content.translate(-field.scroll, 0);

			if (focused && selectionStart != selectionEnd) {
				double from = field.caretPosition(selectionStart)[0];
				double to = field.caretPosition(selectionEnd)[0];
				Shapes.rect(graphics, line, from, 0, to - from, lineHeight, 0, selectionColor);
			}

			drawString(graphics, line, display, style, scale, textColor);

			if (focused && field.suggestion != null && !field.suggestion.isEmpty() && field.editor.cursor() == value.length()) {
				double end = field.caretPosition(value.length())[0];
				drawString(graphics, line.translate(end, 0), field.suggestion, style, scale, faded(field.placeholderColor, opacity));
			}
		} else {
			for (int i = 0; i < field.lines.size(); i++) {
				int[] bounds = field.lines.get(i);
				double y = i * lineHeight - field.scroll;

				if (y + lineHeight < 0 || y > height) {
					continue;
				}

				Affine line = content.translate(0, y);
				int from = Math.max(selectionStart, bounds[0]);
				int to = Math.min(selectionEnd, bounds[1]);

				if (focused && from < to) {
					double x0 = field.caretPosition(from)[0];
					double x1 = field.caretPosition(to)[0];
					Shapes.rect(graphics, line, x0, 0, x1 - x0, lineHeight, 0, selectionColor);
				}

				String text = field.password ? "*".repeat(value.codePointCount(bounds[0], bounds[1])) : value.substring(bounds[0], bounds[1]);
				drawString(graphics, line, text, style, scale, textColor);
			}
		}

		if (focused && field.editable) {
			double[] caret = field.caretPosition(field.editor.cursor());
			double x = multiline ? caret[0] : caret[0] - field.scroll;
			double y = multiline ? caret[1] - field.scroll : 0;
			long sinceReset = (System.nanoTime() - field.caretReset) / 1_000_000L;

			if (sinceReset % 1000 < 550) {
				Shapes.rect(graphics, content, x, y, Math.max(Shapes.pixel(), scale * 0.75), lineHeight, 0, faded(field.cursorColor, opacity));
			}

			double sx = content.x(x, y + lineHeight);
			double sy = content.y(x, y + lineHeight);
			Minecraft.getInstance().textInputManager().setTextInputArea((int) sx, (int) (sy - lineHeight), (int) sx + 1, (int) sy);
		}

		graphics.disableScissor();
	}

	private static void drawString(GuiGraphicsExtractor graphics, Affine at, String text, Style style, double scale, int color) {
		if (text.isEmpty() || (color >>> 24) <= 3) {
			return;
		}

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		setPose(pose, at.translate(0, GuiText.GLYPH_OFFSET * scale).scale(scale));
		graphics.text(GuiText.font(), FormattedCharSequence.forward(text, style), 0, 0, color, false);
		pose.popMatrix();
	}

	private static void drawWidget(GuiGraphicsExtractor graphics, WidgetElement widget, GuiDocument document) {
		if (!(widget.peer instanceof VanillaPeers.Peer peer)) {
			return;
		}

		double[] local = widget.toLocal(document.mouseX, document.mouseY);
		int mouseX = local == null ? -10000 : (int) Math.floor(local[0]);
		int mouseY = local == null ? -10000 : (int) Math.floor(local[1]);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		setPose(pose, widget.matrix);
		WidgetHover.set(widget.isHovered() && !widget.disabled);

		try {
			peer.render(graphics, mouseX, mouseY, (float) widget.opacity);
		} finally {
			WidgetHover.set(null);
			pose.popMatrix();
		}
	}

	private static void drawCanvas(GuiGraphicsExtractor graphics, CanvasElement canvas) {
		Affine origin = canvas.matrix.translate(canvas.padLeft, canvas.padTop);
		double opacity = canvas.opacity;
		pushScissor(graphics, origin, 0, 0, canvas.innerWidth(), canvas.innerHeight());

		for (CanvasElement.Op op : canvas.ops) {
			switch (op) {
				case CanvasElement.Rect rect -> Shapes.rect(graphics, origin, rect.x(), rect.y(), rect.width(), rect.height(), rect.radius(), faded(rect.color(), opacity));
				case CanvasElement.Line line -> Shapes.line(graphics, origin, line.x1(), line.y1(), line.x2(), line.y2(), line.width(), faded(line.color(), opacity));
				case CanvasElement.Circle circle -> {
					if (circle.ringWidth() > 0) {
						Shapes.ring(graphics, origin, circle.x(), circle.y(), circle.radius(), circle.ringWidth(), faded(circle.color(), opacity));
					} else {
						Shapes.circle(graphics, origin, circle.x(), circle.y(), circle.radius(), faded(circle.color(), opacity));
					}
				}
				case CanvasElement.Picture picture -> {
					GuiTextures.Texture texture = GuiTextures.image(picture.source());
					int[] frame = texture.frameOrigin();
					Shapes.texturedRect(graphics, origin, picture.x(), picture.y(), picture.width(), picture.height(), null, texture,
							frame[0] / (double) texture.imageWidth, frame[1] / (double) texture.imageHeight,
							(frame[0] + texture.frameWidth) / (double) texture.imageWidth, (frame[1] + texture.frameHeight) / (double) texture.imageHeight,
							faded(picture.color(), opacity));
				}
				case CanvasElement.Label label -> {
					GuiText.Layout layout = GuiText.layout(label.text(), new GuiText.Look(FontDescription.DEFAULT, false, false, false, false, false, label.size(), 1, TextAlign.LEFT), Double.POSITIVE_INFINITY);
					GuiText.draw(graphics, layout, origin.translate(label.x(), label.y()), layout.width(), faded(label.color(), opacity), false, null, opacity);
				}
			}
		}

		graphics.disableScissor();
	}


	private void scrollbars(GuiGraphicsExtractor graphics, Element element, GuiDocument document) {
		double[] state = scrollState.computeIfAbsent(element, key -> new double[] {key.scrollX, key.scrollY, 0});
		long now = System.nanoTime();

		if (state[0] != element.scrollX || state[1] != element.scrollY) {
			state[0] = element.scrollX;
			state[1] = element.scrollY;
			state[2] = now;
		}

		for (boolean vertical : new boolean[] {true, false}) {
			double[] thumb = document.scrollbarThumb(element, vertical);

			if (thumb == null) {
				continue;
			}

			Affine matrix = element.matrix;
			double opacity = element.opacity;

			switch (element.scrollbar) {
				case VANILLA -> {
					Matrix3x2fStack pose = graphics.pose();
					pose.pushMatrix();
					setPose(pose, matrix);
					int trackX = vertical ? (int) Math.round(thumb[0]) : 0;
					int trackY = vertical ? 0 : (int) Math.round(thumb[1]);
					int trackWidth = vertical ? (int) Math.round(thumb[2]) : (int) Math.round(element.lw);
					int trackHeight = vertical ? (int) Math.round(element.lh) : (int) Math.round(thumb[3]);
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND, trackX, trackY, trackWidth, trackHeight, faded(0xFFFFFFFF, opacity));
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, (int) Math.round(thumb[0]), (int) Math.round(thumb[1]),
							Math.max(1, (int) Math.round(thumb[2])), Math.max(1, (int) Math.round(thumb[3])), faded(0xFFFFFFFF, opacity));
					pose.popMatrix();
				}
				case MODERN -> {
					double sinceScroll = (now - (long) state[2]) / 1e9;
					boolean hovered = element.isHovered() || element == document.root;
					double visibility = hovered ? 1 : Math.clamp(1 - (sinceScroll - 0.8) / 0.4, 0, 1);

					if (visibility > 0) {
						double radius = Math.min(thumb[2], thumb[3]) / 2;
						Shapes.rect(graphics, matrix, thumb[0], thumb[1], thumb[2], thumb[3], radius, faded(element.scrollbarThumb, opacity * visibility));
					}
				}
				case CUSTOM -> {
					double size = vertical ? thumb[2] : thumb[3];
					double radius = size / 2;

					if (vertical) {
						Shapes.rect(graphics, matrix, thumb[0], 0, thumb[2], element.lh, radius, faded(element.scrollbarTrack, opacity));
					} else {
						Shapes.rect(graphics, matrix, 0, thumb[1], element.lw, thumb[3], radius, faded(element.scrollbarTrack, opacity));
					}

					Shapes.rect(graphics, matrix, thumb[0], thumb[1], thumb[2], thumb[3], radius, faded(element.scrollbarThumb, opacity));
				}
				case HIDDEN -> {
				}
			}
		}
	}

	private static void tooltip(GuiGraphicsExtractor graphics, GuiDocument document) {
		Element element = document.tooltipElement;

		if (element == null) {
			return;
		}

		int mouseX = (int) Math.round(document.mouseX * document.scaleFactor);
		int mouseY = (int) Math.round(document.mouseY * document.scaleFactor);

		if (element.tooltip != null) {
			if (!element.tooltip.isEmpty()) {
				graphics.setTooltipForNextFrame(GuiText.tooltipLines(element.tooltip), mouseX, mouseY);
			}
		} else if (element instanceof ItemElement item && item.resource instanceof ItemStack stack) {
			graphics.setTooltipForNextFrame(GuiText.font(), stack, mouseX, mouseY);
		}
	}

	private static CursorType cursor(GuiDocument document) {
		return switch (document.cursor) {
			case DEFAULT -> CursorType.DEFAULT;
			case HAND -> CursorTypes.POINTING_HAND;
			case TEXT -> CursorTypes.IBEAM;
			case CROSSHAIR -> CursorTypes.CROSSHAIR;
			case RESIZE_EW -> CursorTypes.RESIZE_EW;
			case RESIZE_NS -> CursorTypes.RESIZE_NS;
			case RESIZE_ALL -> CursorTypes.RESIZE_ALL;
			case NOT_ALLOWED -> CursorTypes.NOT_ALLOWED;
		};
	}
}
