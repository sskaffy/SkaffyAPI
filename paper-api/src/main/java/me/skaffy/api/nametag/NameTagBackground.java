package me.skaffy.api.nametag;

import java.util.Objects;

import net.kyori.adventure.util.RGBLike;
import org.bukkit.Color;

public final class NameTagBackground {
	public static final int MAX_PIXELS = 64;
	private static final NameTagBackground VANILLA = new NameTagBackground(null, null, 1, 1, Style.BOX, false, 50, 2, 2);

	public enum Style {
		BOX,
		PER_LINE
	}

	private final Integer color;
	private final Integer transparency;
	private final int paddingX;
	private final int paddingY;
	private final Style style;
	private final boolean shadow;
	private final int shadowDarkness;
	private final int shadowOffsetX;
	private final int shadowOffsetY;

	private NameTagBackground(Integer color, Integer transparency, int paddingX, int paddingY, Style style, boolean shadow, int shadowDarkness, int shadowOffsetX, int shadowOffsetY) {
		this.color = color;
		this.transparency = transparency;
		this.paddingX = paddingX;
		this.paddingY = paddingY;
		this.style = style;
		this.shadow = shadow;
		this.shadowDarkness = shadowDarkness;
		this.shadowOffsetX = shadowOffsetX;
		this.shadowOffsetY = shadowOffsetY;
	}

	public static NameTagBackground vanilla() {
		return VANILLA;
	}

	public static NameTagBackground none() {
		return VANILLA.transparency(100);
	}

	public NameTagBackground color(int rgb) {
		return new NameTagBackground(rgb & 0xFFFFFF, transparency, paddingX, paddingY, style, shadow, shadowDarkness, shadowOffsetX, shadowOffsetY);
	}

	public NameTagBackground color(Color color) {
		return color(color.asRGB());
	}

	public NameTagBackground color(RGBLike color) {
		return color(color.red() << 16 | color.green() << 8 | color.blue());
	}

	public NameTagBackground transparency(int transparency) {
		return new NameTagBackground(color, NameTagObject.percent("Transparency", transparency), paddingX, paddingY, style, shadow, shadowDarkness, shadowOffsetX, shadowOffsetY);
	}

	public NameTagBackground padding(int x, int y) {
		return new NameTagBackground(color, transparency, pixels(x, 0), pixels(y, 0), style, shadow, shadowDarkness, shadowOffsetX, shadowOffsetY);
	}

	public NameTagBackground style(Style style) {
		return new NameTagBackground(color, transparency, paddingX, paddingY, Objects.requireNonNull(style, "style"), shadow, shadowDarkness, shadowOffsetX, shadowOffsetY);
	}

	public NameTagBackground shadow(boolean shadow) {
		return new NameTagBackground(color, transparency, paddingX, paddingY, style, shadow, shadowDarkness, shadowOffsetX, shadowOffsetY);
	}

	public NameTagBackground shadowDarkness(int darkness) {
		return new NameTagBackground(color, transparency, paddingX, paddingY, style, true, NameTagObject.percent("Shadow darkness", darkness), shadowOffsetX, shadowOffsetY);
	}

	public NameTagBackground shadowOffset(int x, int y) {
		return new NameTagBackground(color, transparency, paddingX, paddingY, style, true, shadowDarkness, pixels(x, -MAX_PIXELS), pixels(y, -MAX_PIXELS));
	}

	public Integer getColor() {
		return color;
	}

	public Integer getTransparency() {
		return transparency;
	}

	public int getPaddingX() {
		return paddingX;
	}

	public int getPaddingY() {
		return paddingY;
	}

	public Style getStyle() {
		return style;
	}

	public boolean hasShadow() {
		return shadow;
	}

	public int getShadowDarkness() {
		return shadowDarkness;
	}

	public int getShadowOffsetX() {
		return shadowOffsetX;
	}

	public int getShadowOffsetY() {
		return shadowOffsetY;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NameTagBackground that && Objects.equals(color, that.color) && Objects.equals(transparency, that.transparency)
				&& paddingX == that.paddingX && paddingY == that.paddingY && style == that.style && shadow == that.shadow
				&& shadowDarkness == that.shadowDarkness && shadowOffsetX == that.shadowOffsetX && shadowOffsetY == that.shadowOffsetY;
	}

	@Override
	public int hashCode() {
		return Objects.hash(color, transparency, paddingX, paddingY, style, shadow, shadowDarkness, shadowOffsetX, shadowOffsetY);
	}

	private static int pixels(int value, int min) {
		if (value < min || value > MAX_PIXELS) {
			throw new IllegalArgumentException("Must be " + min + " to " + MAX_PIXELS + " pixels, got " + value);
		}

		return value;
	}
}
