package me.skaffy.client.gui.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.skaffy.client.gui.text.RichText.Chars;
import me.skaffy.client.gui.text.RichText.Picture;
import me.skaffy.client.gui.text.RichText.PictureKind;
import me.skaffy.client.gui.text.RichText.Span;
import me.skaffy.client.gui.text.RichText.SpanStyle;

public final class MiniMessage {
	private static final Map<String, Integer> NAMED = Map.ofEntries(
			Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA), Map.entry("dark_green", 0x00AA00),
			Map.entry("dark_aqua", 0x00AAAA), Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
			Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA), Map.entry("grey", 0xAAAAAA),
			Map.entry("dark_gray", 0x555555), Map.entry("dark_grey", 0x555555), Map.entry("blue", 0x5555FF),
			Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF), Map.entry("red", 0xFF5555),
			Map.entry("light_purple", 0xFF55FF), Map.entry("yellow", 0xFFFF55), Map.entry("white", 0xFFFFFF));
	private static final int MAX_DEPTH = 64;

	private MiniMessage() {
	}

	public static RichText parse(String input) {
		List<Node> roots = new ArrayList<>();
		new Reader(input).read(roots);
		List<Span> spans = new ArrayList<>();
		emit(roots, new State(SpanStyle.NONE, null), spans, 0);
		return RichText.merged(spans);
	}


	private sealed interface Node permits Text, Tag {
	}

	private record Text(String text) implements Node {
	}

	private record Tag(String name, List<String> args, String raw, List<Node> children) implements Node {
	}

	private static final class Reader {
		private final String input;
		private int index;

		Reader(String input) {
			this.input = input;
		}

		void read(List<Node> roots) {
			List<Tag> open = new ArrayList<>();
			StringBuilder text = new StringBuilder();

			while (index < input.length()) {
				char c = input.charAt(index);

				if (c == '\\' && index + 1 < input.length() && (input.charAt(index + 1) == '<' || input.charAt(index + 1) == '\\')) {
					text.append(input.charAt(index + 1));
					index += 2;
					continue;
				}

				if (c == '<') {
					int end = tagEnd(index);

					if (end > 0) {
						String raw = input.substring(index, end + 1);
						String body = input.substring(index + 1, end);
						index = end + 1;

						if (body.startsWith("/")) {
							String name = parts(body.substring(1)).getFirst().toLowerCase(Locale.ROOT);
							int match = -1;

							for (int i = open.size() - 1; i >= 0; i--) {
								if (open.get(i).name.equals(name) || name.isEmpty()) {
									match = i;
									break;
								}
							}

							if (match >= 0) {
								flush(text, open, roots);

								while (open.size() > match) {
									open.removeLast();
								}

								continue;
							}

							text.append(raw);
							continue;
						}

						List<String> parts = parts(body);
						String name = parts.getFirst().toLowerCase(Locale.ROOT);

						if (!known(name)) {
							text.append(raw);
							continue;
						}

						flush(text, open, roots);

						if (name.equals("reset")) {
							open.clear();
							continue;
						}

						Tag tag = new Tag(name, parts.subList(1, parts.size()), raw, new ArrayList<>());
						add(tag, open, roots);

						if (!selfClosing(name) && !body.endsWith("/") && open.size() < MAX_DEPTH) {
							open.add(tag);
						}

						continue;
					}
				}

				text.append(c);
				index++;
			}

			flush(text, open, roots);
		}

		private void flush(StringBuilder text, List<Tag> open, List<Node> roots) {
			if (!text.isEmpty()) {
				add(new Text(text.toString()), open, roots);
				text.setLength(0);
			}
		}

		private static void add(Node node, List<Tag> open, List<Node> roots) {
			if (open.isEmpty()) {
				roots.add(node);
			} else {
				open.getLast().children.add(node);
			}
		}

		private int tagEnd(int start) {
			char quote = 0;

			for (int i = start + 1; i < input.length(); i++) {
				char c = input.charAt(i);

				if (quote != 0) {
					if (c == '\\') {
						i++;
					} else if (c == quote) {
						quote = 0;
					}
				} else if (c == '\'' || c == '"') {
					quote = c;
				} else if (c == '>') {
					return i > start + 1 ? i : -1;
				} else if (c == '<' || c == '\n') {
					return -1;
				}
			}

			return -1;
		}

		private static List<String> parts(String body) {
			if (body.endsWith("/")) {
				body = body.substring(0, body.length() - 1);
			}

			List<String> parts = new ArrayList<>();
			StringBuilder current = new StringBuilder();
			char quote = 0;

			for (int i = 0; i < body.length(); i++) {
				char c = body.charAt(i);

				if (quote != 0) {
					if (c == '\\' && i + 1 < body.length()) {
						current.append(body.charAt(++i));
					} else if (c == quote) {
						quote = 0;
					} else {
						current.append(c);
					}
				} else if (c == '\'' || c == '"') {
					quote = c;
				} else if (c == ':') {
					parts.add(current.toString());
					current.setLength(0);
				} else {
					current.append(c);
				}
			}

			parts.add(current.toString());
			return parts;
		}
	}

	private static boolean known(String name) {
		String bare = name.startsWith("!") ? name.substring(1) : name;
		return NAMED.containsKey(bare) || bare.startsWith("#") && color(bare) != null || switch (bare) {
			case "color", "colour", "c", "bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st",
					"obfuscated", "obf", "reset", "gradient", "rainbow", "font", "shadow", "newline", "br", "sprite", "head" -> true;
			default -> false;
		};
	}

	private static boolean selfClosing(String name) {
		return switch (name) {
			case "newline", "br", "sprite", "head", "reset" -> true;
			default -> false;
		};
	}


	private static final class ColorRun {
		final int[] colors;
		final boolean rainbow;
		final double phase;
		final int total;
		int next;

		ColorRun(int[] colors, boolean rainbow, double phase, int total) {
			this.colors = colors;
			this.rainbow = rainbow;
			this.phase = phase;
			this.total = Math.max(1, total);
		}

		int color() {
			int index = next++;
			double t = total <= 1 ? 0 : index / (double) (total - 1);

			if (rainbow) {
				double hue = ((index / (double) total + phase) % 1 + 1) % 1;
				return hsb(hue);
			}

			if (colors.length == 1) {
				return colors[0];
			}

			double position = phase == 0 ? t : ((t + phase) % 1 + 1) % 1;
			double scaled = position * (colors.length - 1);
			int segment = Math.min((int) scaled, colors.length - 2);
			return lerp(colors[segment], colors[segment + 1], scaled - segment);
		}
	}

	private record State(SpanStyle style, ColorRun run) {
	}

	private static void emit(List<Node> nodes, State state, List<Span> out, int depth) {
		for (Node node : nodes) {
			switch (node) {
				case Text text -> emitText(text.text(), state, out);
				case Tag tag -> emitTag(tag, state, out, depth);
			}
		}
	}

	private static void emitText(String text, State state, List<Span> out) {
		if (state.run == null) {
			out.add(new Chars(text, state.style));
			return;
		}

		for (int i = 0; i < text.length(); ) {
			int codepoint = text.codePointAt(i);
			int length = Character.charCount(codepoint);
			String part = text.substring(i, i + length);
			out.add(new Chars(part, part.equals("\n") ? state.style : state.style.withColor(state.run.color())));
			i += length;
		}
	}

	private static void emitTag(Tag tag, State state, List<Span> out, int depth) {
		String name = tag.name;
		List<String> args = tag.args;
		SpanStyle style = state.style;
		ColorRun run = state.run;
		boolean negated = name.startsWith("!");
		String bare = negated ? name.substring(1) : name;

		switch (bare) {
			case "newline", "br" -> {
				out.add(new Chars("\n", style));
				return;
			}
			case "sprite" -> {
				if (!args.isEmpty()) {
					out.add(new Picture(PictureKind.SPRITE, String.join(":", args), false, style));
				}

				return;
			}
			case "head" -> {
				if (!args.isEmpty()) {
					boolean hat = args.size() < 2 || !args.get(1).equalsIgnoreCase("false");
					out.add(new Picture(PictureKind.HEAD, args.getFirst(), hat, style));
				}

				return;
			}
			case "color", "colour", "c" -> {
				Integer color = args.isEmpty() ? null : color(args.getFirst());

				if (color == null) {
					emitText(tag.raw, state, out);
					emit(tag.children, state, out, depth + 1);
					return;
				}

				style = style.withColor(color);
				run = null;
			}
			case "bold", "b" -> style = with(style, 0, flag(negated, args));
			case "italic", "i", "em" -> style = with(style, 1, flag(negated, args));
			case "underlined", "u" -> style = with(style, 2, flag(negated, args));
			case "strikethrough", "st" -> style = with(style, 3, flag(negated, args));
			case "obfuscated", "obf" -> style = with(style, 4, flag(negated, args));
			case "font" -> style = new SpanStyle(style.color(), style.bold(), style.italic(), style.underlined(), style.strikethrough(), style.obfuscated(),
					args.isEmpty() ? null : String.join(":", args), style.shadow(), style.shadowColor());
			case "shadow" -> {
				if (negated || !args.isEmpty() && args.getFirst().equalsIgnoreCase("false")) {
					style = new SpanStyle(style.color(), style.bold(), style.italic(), style.underlined(), style.strikethrough(), style.obfuscated(), style.font(), false, null);
				} else {
					Integer color = args.isEmpty() ? null : color(args.getFirst());
					int alpha = 255;

					if (args.size() > 1) {
						try {
							alpha = (int) Math.round(Math.clamp(Double.parseDouble(args.get(1)), 0, 1) * 255);
						} catch (NumberFormatException ignored) {
						}
					}

					style = new SpanStyle(style.color(), style.bold(), style.italic(), style.underlined(), style.strikethrough(), style.obfuscated(), style.font(),
							true, color == null ? null : alpha << 24 | color);
				}
			}
			case "gradient" -> {
				List<Integer> colors = new ArrayList<>();
				double phase = 0;

				for (String arg : args) {
					Integer color = color(arg);

					if (color != null) {
						colors.add(color);
					} else {
						try {
							phase = Double.parseDouble(arg);
						} catch (NumberFormatException ignored) {
						}
					}
				}

				if (colors.isEmpty()) {
					colors.add(0xFFFFFF);
					colors.add(0x000000);
				}

				run = new ColorRun(colors.stream().mapToInt(Integer::intValue).toArray(), false, phase, count(tag.children));
			}
			case "rainbow" -> {
				double phase = 0;
				boolean reversed = false;

				for (String arg : args) {
					String value = arg;

					if (value.startsWith("!")) {
						reversed = true;
						value = value.substring(1);
					}

					if (!value.isEmpty()) {
						try {
							phase = Double.parseDouble(value);
						} catch (NumberFormatException ignored) {
						}
					}
				}

				run = new ColorRun(new int[0], true, reversed ? -phase : phase, count(tag.children));
			}
			default -> {
				if (NAMED.containsKey(bare) || bare.startsWith("#")) {
					Integer color = NAMED.containsKey(bare) ? NAMED.get(bare) : color(bare);
					style = style.withColor(color);
					run = null;
				}
			}
		}

		if (depth < MAX_DEPTH) {
			emit(tag.children, new State(style, run), out, depth + 1);
		}
	}

	private static int count(List<Node> nodes) {
		int total = 0;

		for (Node node : nodes) {
			if (node instanceof Text text) {
				total += (int) text.text().codePoints().filter(c -> c != '\n').count();
			} else if (node instanceof Tag tag) {
				total += tag.name.equals("sprite") || tag.name.equals("head") ? 1 : count(tag.children);
			}
		}

		return total;
	}

	private static Boolean flag(boolean negated, List<String> args) {
		if (negated) {
			return false;
		}

		return args.isEmpty() || !args.getFirst().equalsIgnoreCase("false");
	}

	private static SpanStyle with(SpanStyle style, int which, Boolean value) {
		return new SpanStyle(style.color(),
				which == 0 ? value : style.bold(),
				which == 1 ? value : style.italic(),
				which == 2 ? value : style.underlined(),
				which == 3 ? value : style.strikethrough(),
				which == 4 ? value : style.obfuscated(),
				style.font(), style.shadow(), style.shadowColor());
	}

	static Integer color(String text) {
		String value = text.toLowerCase(Locale.ROOT);
		Integer named = NAMED.get(value);

		if (named != null) {
			return named;
		}

		if (value.startsWith("#") && value.length() == 7) {
			try {
				return Integer.parseInt(value.substring(1), 16);
			} catch (NumberFormatException e) {
				return null;
			}
		}

		return null;
	}

	private static int lerp(int from, int to, double t) {
		int r = (int) Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		int g = (int) Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return r << 16 | g << 8 | b;
	}

	private static int hsb(double hue) {
		double h = hue * 6;
		int sector = (int) Math.floor(h) % 6;
		double f = h - Math.floor(h);
		double[] rgb = switch (sector) {
			case 0 -> new double[] {1, f, 0};
			case 1 -> new double[] {1 - f, 1, 0};
			case 2 -> new double[] {0, 1, f};
			case 3 -> new double[] {0, 1 - f, 1};
			case 4 -> new double[] {f, 0, 1};
			default -> new double[] {1, 0, 1 - f};
		};
		return (int) Math.round(rgb[0] * 255) << 16 | (int) Math.round(rgb[1] * 255) << 8 | (int) Math.round(rgb[2] * 255);
	}
}
