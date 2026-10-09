package me.skaffy.client.gui.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RichText {
	public static final RichText EMPTY = new RichText(List.of());

	private final List<Span> spans;

	public RichText(List<Span> spans) {
		this.spans = List.copyOf(spans);
	}

	public static RichText plain(String text) {
		return text.isEmpty() ? EMPTY : new RichText(List.of(new Chars(text, SpanStyle.NONE)));
	}

	public List<Span> spans() {
		return spans;
	}

	public boolean isEmpty() {
		return spans.isEmpty();
	}

	public String plain() {
		StringBuilder builder = new StringBuilder();

		for (Span span : spans) {
			if (span instanceof Chars chars) {
				builder.append(chars.text());
			}
		}

		return builder.toString();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RichText text && text.spans.equals(spans);
	}

	@Override
	public int hashCode() {
		return spans.hashCode();
	}

	@Override
	public String toString() {
		return plain();
	}

	public sealed interface Span permits Chars, Picture {
		SpanStyle style();
	}

	public record Chars(String text, SpanStyle style) implements Span {
	}

	public enum PictureKind {
		SPRITE,
		HEAD
	}

	public record Picture(PictureKind kind, String source, boolean hat, SpanStyle style) implements Span {
	}

	public record SpanStyle(Integer color, Boolean bold, Boolean italic, Boolean underlined, Boolean strikethrough, Boolean obfuscated, String font, Boolean shadow, Integer shadowColor) {
		public static final SpanStyle NONE = new SpanStyle(null, null, null, null, null, null, null, null, null);

		public SpanStyle withColor(Integer value) {
			return new SpanStyle(value, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor);
		}
	}

	static RichText merged(List<Span> spans) {
		List<Span> result = new ArrayList<>();

		for (Span span : spans) {
			if (span instanceof Chars chars && chars.text().isEmpty()) {
				continue;
			}

			if (!result.isEmpty() && span instanceof Chars chars && result.getLast() instanceof Chars previous && Objects.equals(previous.style(), chars.style())) {
				result.set(result.size() - 1, new Chars(previous.text() + chars.text(), chars.style()));
			} else {
				result.add(span);
			}
		}

		return new RichText(result);
	}
}
