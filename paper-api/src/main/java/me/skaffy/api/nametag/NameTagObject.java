package me.skaffy.api.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.util.RGBLike;
import org.bukkit.Color;
import org.bukkit.entity.Player;

public final class NameTagObject {
	public static final int MAX_GRADIENT = 16;

	public enum Kind {
		TEXT,
		SPRITE,
		VANILLA_SPRITE,
		HEAD
	}

	public enum Font {
		DEFAULT,
		UNIFORM,
		ALT,
		ILLAGERALT
	}

	private final Kind kind;
	private final String value;
	private final UUID headId;
	private final String skin;
	private final String skinSignature;
	private final boolean hat;
	private final List<Integer> colors;
	private final int transparency;
	private final boolean bold;
	private final boolean italic;
	private final boolean underlined;
	private final boolean strikethrough;
	private final boolean obfuscated;
	private final Font font;
	private final boolean shadow;
	private final Integer shadowColor;
	private final int shadowTransparency;

	private NameTagObject(Kind kind, String value, UUID headId, String skin, String skinSignature, boolean hat, List<Integer> colors, int transparency,
			boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated, Font font, boolean shadow, Integer shadowColor, int shadowTransparency) {
		this.kind = kind;
		this.value = value;
		this.headId = headId;
		this.skin = skin;
		this.skinSignature = skinSignature;
		this.hat = hat;
		this.colors = List.copyOf(colors);
		this.transparency = transparency;
		this.bold = bold;
		this.italic = italic;
		this.underlined = underlined;
		this.strikethrough = strikethrough;
		this.obfuscated = obfuscated;
		this.font = font;
		this.shadow = shadow;
		this.shadowColor = shadowColor;
		this.shadowTransparency = shadowTransparency;
	}

	private static NameTagObject of(Kind kind, String value, UUID headId, String skin, String skinSignature) {
		return new NameTagObject(kind, value, headId, skin, skinSignature, true, List.of(), 0, false, false, false, false, false, Font.DEFAULT, false, null, 0);
	}

	public static NameTagObject text(String text) {
		Objects.requireNonNull(text, "text");
		int length = text.codePointCount(0, text.length());

		if (length == 0 || length > NameTagLine.MAX_LENGTH) {
			throw new IllegalArgumentException("Name tag text must be 1 to " + NameTagLine.MAX_LENGTH + " characters, got " + length);
		}

		return of(Kind.TEXT, text, null, null, null);
	}

	public static NameTagObject sprite(String asset) {
		Objects.requireNonNull(asset, "asset");
		return of(Kind.SPRITE, asset, null, null, null);
	}

	public static NameTagObject vanillaSprite(String texture) {
		Objects.requireNonNull(texture, "texture");
		return of(Kind.VANILLA_SPRITE, texture, null, null, null);
	}

	public static NameTagObject head(Player player) {
		return head(player.getPlayerProfile());
	}

	public static NameTagObject head(PlayerProfile profile) {
		ProfileProperty textures = profile.getProperties().stream().filter(property -> property.getName().equals("textures")).findFirst().orElse(null);
		return head(profile.getId(), profile.getName(), textures == null ? null : textures.getValue(), textures == null ? null : textures.getSignature());
	}

	public static NameTagObject head(UUID id) {
		return head(id, null, null, null);
	}

	public static NameTagObject head(String name) {
		return head(null, name, null, null);
	}

	public static NameTagObject head(UUID id, String name, String skin, String skinSignature) {
		if (id == null && name == null && skin == null) {
			throw new IllegalArgumentException("A head needs a UUID, a name or a skin");
		}

		return of(Kind.HEAD, name, id, skin, skinSignature);
	}

	private NameTagObject copy(List<Integer> colors, int transparency, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated,
			Font font, boolean shadow, Integer shadowColor, int shadowTransparency, boolean hat) {
		return new NameTagObject(kind, value, headId, skin, skinSignature, hat, colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency);
	}

	public NameTagObject color(int rgb) {
		return copy(List.of(rgb & 0xFFFFFF), transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject color(Color color) {
		return color(color.asRGB());
	}

	public NameTagObject color(RGBLike color) {
		return color(rgb(color));
	}

	public NameTagObject gradient(int... rgb) {
		if (rgb.length < 1 || rgb.length > MAX_GRADIENT) {
			throw new IllegalArgumentException("A gradient has 1 to " + MAX_GRADIENT + " colors");
		}

		List<Integer> list = new ArrayList<>(rgb.length);

		for (int color : rgb) {
			list.add(color & 0xFFFFFF);
		}

		return copy(list, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject gradient(Color... colors) {
		int[] rgb = new int[colors.length];

		for (int i = 0; i < colors.length; i++) {
			rgb[i] = colors[i].asRGB();
		}

		return gradient(rgb);
	}

	public NameTagObject gradient(RGBLike... colors) {
		int[] rgb = new int[colors.length];

		for (int i = 0; i < colors.length; i++) {
			rgb[i] = rgb(colors[i]);
		}

		return gradient(rgb);
	}

	public NameTagObject transparency(int transparency) {
		return copy(colors, percent("Transparency", transparency), bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject bold() {
		return bold(true);
	}

	public NameTagObject bold(boolean bold) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject italic() {
		return italic(true);
	}

	public NameTagObject italic(boolean italic) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject underlined() {
		return underlined(true);
	}

	public NameTagObject underlined(boolean underlined) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject strikethrough() {
		return strikethrough(true);
	}

	public NameTagObject strikethrough(boolean strikethrough) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject obfuscated() {
		return obfuscated(true);
	}

	public NameTagObject obfuscated(boolean obfuscated) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject font(Font font) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, Objects.requireNonNull(font, "font"), shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject shadow() {
		return shadow(true);
	}

	public NameTagObject shadow(boolean shadow) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public NameTagObject shadowColor(int rgb) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, true, rgb & 0xFFFFFF, shadowTransparency, hat);
	}

	public NameTagObject shadowColor(Color color) {
		return shadowColor(color.asRGB());
	}

	public NameTagObject shadowColor(RGBLike color) {
		return shadowColor(rgb(color));
	}

	public NameTagObject shadowTransparency(int transparency) {
		return copy(colors, this.transparency, bold, italic, underlined, strikethrough, obfuscated, font, true, shadowColor, percent("Shadow transparency", transparency), hat);
	}

	public NameTagObject hat(boolean hat) {
		return copy(colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	public Kind getKind() {
		return kind;
	}

	public String getValue() {
		return value;
	}

	public UUID getHeadId() {
		return headId;
	}

	public String getSkin() {
		return skin;
	}

	public String getSkinSignature() {
		return skinSignature;
	}

	public boolean hasHat() {
		return hat;
	}

	public List<Integer> getColors() {
		return colors;
	}

	public int getTransparency() {
		return transparency;
	}

	public boolean isBold() {
		return bold;
	}

	public boolean isItalic() {
		return italic;
	}

	public boolean isUnderlined() {
		return underlined;
	}

	public boolean isStrikethrough() {
		return strikethrough;
	}

	public boolean isObfuscated() {
		return obfuscated;
	}

	public Font getFont() {
		return font;
	}

	public boolean hasShadow() {
		return shadow;
	}

	public Integer getShadowColor() {
		return shadowColor;
	}

	public int getShadowTransparency() {
		return shadowTransparency;
	}

	public int length() {
		return kind == Kind.TEXT ? value.codePointCount(0, value.length()) : 1;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NameTagObject that && kind == that.kind && hat == that.hat && transparency == that.transparency && bold == that.bold
				&& italic == that.italic && underlined == that.underlined && strikethrough == that.strikethrough && obfuscated == that.obfuscated
				&& shadow == that.shadow && shadowTransparency == that.shadowTransparency && font == that.font && colors.equals(that.colors)
				&& Objects.equals(value, that.value) && Objects.equals(headId, that.headId) && Objects.equals(skin, that.skin)
				&& Objects.equals(skinSignature, that.skinSignature) && Objects.equals(shadowColor, that.shadowColor);
	}

	@Override
	public int hashCode() {
		return Objects.hash(kind, value, headId, skin, colors, transparency, bold, italic, underlined, strikethrough, obfuscated, font, shadow, shadowColor, shadowTransparency, hat);
	}

	@Override
	public String toString() {
		return "NameTagObject{" + kind + " " + (value != null ? value : headId) + "}";
	}

	static int percent(String what, int value) {
		if (value < 0 || value > 100) {
			throw new IllegalArgumentException(what + " must be 0 to 100, got " + value);
		}

		return value;
	}

	private static int rgb(RGBLike color) {
		return color.red() << 16 | color.green() << 8 | color.blue();
	}
}
