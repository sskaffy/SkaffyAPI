package me.skaffy.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.skaffy.client.gui.element.Affine;
import me.skaffy.client.gui.element.GuiEnums.FontKind;
import me.skaffy.client.gui.element.GuiEnums.TextAlign;
import me.skaffy.client.gui.element.GuiPlatform;
import me.skaffy.client.gui.element.TextElement;
import me.skaffy.client.gui.text.RichText;
import me.skaffy.client.gui.text.RichText.Chars;
import me.skaffy.client.gui.text.RichText.Picture;
import me.skaffy.client.gui.text.RichText.Span;
import me.skaffy.client.gui.text.RichText.SpanStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import org.joml.Matrix3x2fStack;

final class GuiText {
	private static final float LINE = 9;
	private static final float GLYPH = 8;
	static final double GLYPH_OFFSET = (LINE - GLYPH) / 2;

	private GuiText() {
	}

	record Look(FontDescription font, boolean bold, boolean italic, boolean underline, boolean strikethrough, boolean obfuscated, double size, double lineSpacing, TextAlign align) {
		double scale() {
			return size / GLYPH;
		}

		double lineHeight() {
			return LINE * scale() * lineSpacing;
		}
	}

	sealed interface Piece permits Run, Pic {
		double x();

		double width();
	}

	record Run(String text, Style style, Integer color, Boolean shadow, Integer shadowColor, double x, double width) implements Piece {
	}

	record Pic(Picture picture, Integer color, double x, double width) implements Piece {
	}

	record Line(List<Piece> pieces, double width) {
	}

	static final class Layout implements GuiPlatform.TextLayout {
		final List<Line> lines;
		final Look look;
		final double width;
		final double height;

		Layout(List<Line> lines, Look look) {
			this.lines = lines;
			this.look = look;
			double widest = 0;

			for (Line line : lines) {
				widest = Math.max(widest, line.width);
			}

			this.width = widest;
			this.height = lines.isEmpty() ? 0 : lines.size() * look.lineHeight() - (look.lineSpacing - 1) * LINE * look.scale();
		}

		@Override
		public double width() {
			return width;
		}

		@Override
		public double height() {
			return height;
		}
	}

	static Font font() {
		return Minecraft.getInstance().font;
	}

	static FontDescription fontOf(FontKind kind, String custom) {
		if (custom != null) {
			return namedFont(custom);
		}

		return switch (kind) {
			case DEFAULT -> FontDescription.DEFAULT;
			case UNIFORM -> new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"));
			case ALT -> new FontDescription.Resource(Identifier.withDefaultNamespace("alt"));
			case ILLAGERALT -> new FontDescription.Resource(Identifier.withDefaultNamespace("illageralt"));
		};
	}

	static FontDescription namedFont(String name) {
		Identifier server = ClientGuis.font(name);

		if (server != null) {
			return new FontDescription.Resource(server);
		}

		String lower = name.toLowerCase(Locale.ROOT);
		Identifier id = lower.contains(":") ? Identifier.tryParse(lower) : Identifier.withDefaultNamespace(lower);
		return id == null ? FontDescription.DEFAULT : new FontDescription.Resource(id);
	}

	static Look look(TextElement element) {
		return new Look(fontOf(element.font, element.customFont), element.bold, element.italic, element.underline, element.strikethrough, element.obfuscated,
				element.number(me.skaffy.client.gui.element.Prop.FONT_SIZE), element.lineSpacing, element.align);
	}

	static Style style(Look look, SpanStyle span) {
		Style style = Style.EMPTY.withFont(span.font() != null ? namedFont(span.font()) : look.font)
				.withBold(span.bold() != null ? span.bold() : look.bold)
				.withItalic(span.italic() != null ? span.italic() : look.italic)
				.withUnderlined(span.underlined() != null ? span.underlined() : look.underline)
				.withStrikethrough(span.strikethrough() != null ? span.strikethrough() : look.strikethrough)
				.withObfuscated(span.obfuscated() != null ? span.obfuscated() : look.obfuscated);
		return style;
	}

	static double measure(String text, Style style, double scale) {
		return font().getSplitter().stringWidth(FormattedText.of(text, style)) * scale;
	}

	private record Atom(String text, Span span, Style style, double width, boolean space, boolean newline) {
	}

	static Layout layout(TextElement element, double wrapWidth) {
		return layout(element.text, look(element), wrapWidth);
	}

	static Layout layout(RichText text, Look look, double wrapWidth) {
		double scale = look.scale();
		List<Atom> atoms = new ArrayList<>();

		for (Span span : text.spans()) {
			switch (span) {
				case Chars chars -> {
					Style style = style(look, chars.style());
					String value = chars.text();
					int start = 0;

					for (int i = 0; i <= value.length(); i++) {
						boolean end = i == value.length();
						char c = end ? 0 : value.charAt(i);

						if (end || c == ' ' || c == '\n') {
							if (i > start) {
								String word = value.substring(start, i);
								atoms.add(new Atom(word, span, style, measure(word, style, scale), false, false));
							}

							if (!end) {
								atoms.add(c == '\n' ? new Atom("", span, style, 0, false, true)
										: new Atom(" ", span, style, measure(" ", style, scale), true, false));
							}

							start = i + 1;
						}
					}
				}
				case Picture picture -> atoms.add(new Atom("", span, null, (GLYPH * GuiTextures.aspect(picture) + 1) * scale, false, false));
			}
		}

		List<List<Atom>> lines = new ArrayList<>();
		List<Atom> line = new ArrayList<>();
		double used = 0;

		for (Atom atom : atoms) {
			if (atom.newline) {
				lines.add(line);
				line = new ArrayList<>();
				used = 0;
				continue;
			}

			if (atom.space && line.isEmpty() && !lines.isEmpty()) {
				continue;
			}

			if (!atom.space && used + atom.width > wrapWidth + 0.01 && !line.isEmpty()) {
				lines.add(trimmed(line));
				line = new ArrayList<>();
				used = 0;
			}

			if (!atom.space && atom.width > wrapWidth + 0.01 && atom.style != null) {
				for (Atom part : splitWord(atom, wrapWidth - used, wrapWidth, scale)) {
					if (used + part.width > wrapWidth + 0.01 && !line.isEmpty()) {
						lines.add(line);
						line = new ArrayList<>();
						used = 0;
					}

					line.add(part);
					used += part.width;
				}

				continue;
			}

			line.add(atom);
			used += atom.width;
		}

		lines.add(trimmed(line));

		if (lines.size() == 1 && lines.getFirst().isEmpty()) {
			return new Layout(List.of(), look);
		}

		List<Line> result = new ArrayList<>(lines.size());

		for (List<Atom> atomLine : lines) {
			result.add(pieces(atomLine));
		}

		return new Layout(result, look);
	}

	private static List<Atom> trimmed(List<Atom> line) {
		while (!line.isEmpty() && line.getLast().space) {
			line.removeLast();
		}

		return line;
	}

	private static List<Atom> splitWord(Atom atom, double firstRoom, double room, double scale) {
		List<Atom> parts = new ArrayList<>();
		String text = atom.text;
		int start = 0;
		double limit = firstRoom > GLYPH * scale ? firstRoom : room;

		while (start < text.length()) {
			int end = start;
			double width = 0;

			while (end < text.length()) {
				int next = text.offsetByCodePoints(end, 1);
				double candidate = measure(text.substring(start, next), atom.style, scale);

				if (candidate > limit && end > start) {
					break;
				}

				end = next;
				width = candidate;
			}

			parts.add(new Atom(text.substring(start, end), atom.span, atom.style, width, false, false));
			start = end;
			limit = room;
		}

		return parts;
	}

	private static Line pieces(List<Atom> atoms) {
		List<Piece> pieces = new ArrayList<>();
		double x = 0;
		StringBuilder run = new StringBuilder();
		Atom runStart = null;
		double runX = 0;

		for (Atom atom : atoms) {
			if (atom.span instanceof Picture picture) {
				if (runStart != null) {
					pieces.add(run(run.toString(), runStart, runX, x - runX));
					run.setLength(0);
					runStart = null;
				}

				pieces.add(new Pic(picture, picture.style().color(), x, atom.width));
				x += atom.width;
				continue;
			}

			if (runStart != null && runStart.span != atom.span) {
				pieces.add(run(run.toString(), runStart, runX, x - runX));
				run.setLength(0);
				runStart = null;
			}

			if (runStart == null) {
				runStart = atom;
				runX = x;
			}

			run.append(atom.text);
			x += atom.width;
		}

		if (runStart != null) {
			pieces.add(run(run.toString(), runStart, runX, x - runX));
		}

		return new Line(pieces, x);
	}

	private static Run run(String text, Atom first, double x, double width) {
		SpanStyle span = first.span.style();
		return new Run(text, first.style, span.color(), span.shadow(), span.shadowColor(), x, width);
	}

	static void draw(GuiGraphicsExtractor graphics, Layout layout, Affine matrix, double boxWidth, int color, boolean shadow, Integer shadowColor, double opacity) {
		Look look = layout.look;
		double scale = look.scale();
		int alpha = color >>> 24;

		if (alpha <= 3) {
			return;
		}

		Matrix3x2fStack pose = graphics.pose();

		for (int i = 0; i < layout.lines.size(); i++) {
			Line line = layout.lines.get(i);
			double offset = switch (look.align) {
				case LEFT -> 0;
				case CENTER -> (boxWidth - line.width) / 2;
				case RIGHT -> boxWidth - line.width;
			};
			double y = i * look.lineHeight() + GLYPH_OFFSET * scale;

			for (Piece piece : line.pieces) {
				Affine at = matrix.translate(offset + piece.x(), y);

				switch (piece) {
					case Run run -> {
						int runColor = run.color() != null ? alpha << 24 | run.color() : color;
						Style style = run.style();
						boolean runShadow = run.shadow() != null ? run.shadow() : shadow;
						Integer runShadowColor = run.shadowColor() != null ? run.shadowColor() : shadowColor;

						if (runShadow && runShadowColor != null) {
							int shadowAlpha = (int) Math.round((runShadowColor >>> 24) * opacity);
							style = style.withShadowColor(shadowAlpha << 24 | runShadowColor & 0xFFFFFF);
						}

						FormattedCharSequence sequence = FormattedCharSequence.forward(run.text(), style);
						pose.pushMatrix();
						GuiPainter.setPose(pose, at.scale(scale));
						graphics.text(font(), sequence, 0, 0, runColor, runShadow);
						pose.popMatrix();
					}
					case Pic pic -> {
						int tint = pic.color() != null ? alpha << 24 | pic.color() : (int) Math.round(opacity * 255) << 24 | 0xFFFFFF;
						double size = GLYPH * scale;
						GuiTextures.drawPicture(graphics, pic.picture(), at, pic.width() - scale, size, tint);
					}
				}
			}
		}
	}

	static List<FormattedCharSequence> tooltipLines(RichText text) {
		Look look = new Look(FontDescription.DEFAULT, false, false, false, false, false, GLYPH, 1, TextAlign.LEFT);
		List<FormattedCharSequence> lines = new ArrayList<>();
		List<FormattedCharSequence> current = new ArrayList<>();

		for (Span span : text.spans()) {
			if (!(span instanceof Chars chars)) {
				continue;
			}

			Style style = style(look, chars.style());

			if (chars.style().color() != null) {
				style = style.withColor(TextColor.fromRgb(chars.style().color()));
			}

			String[] parts = chars.text().split("\n", -1);

			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					lines.add(FormattedCharSequence.composite(List.copyOf(current)));
					current.clear();
				}

				if (!parts[i].isEmpty()) {
					current.add(FormattedCharSequence.forward(parts[i], style));
				}
			}
		}

		lines.add(FormattedCharSequence.composite(List.copyOf(current)));
		return lines;
	}
}
