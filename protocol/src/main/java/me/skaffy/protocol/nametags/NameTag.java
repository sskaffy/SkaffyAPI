package me.skaffy.protocol.nametags;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record NameTag(
		List<Line> lines,
		RenderMode render,
		SneakMode sneak,
		Alignment alignment,
		Background background,
		float scale,
		float offsetX,
		float offsetY,
		float offsetZ,
		float maxDistance,
		int lineGap,
		boolean fullBright,
		boolean showWhenInvisible,
		boolean showToSelf) {
	public static final int MAX_LINES = 5;
	public static final int MAX_LINE_LENGTH = 200;
	public static final int MAX_GRADIENT = 16;
	public static final int MAX_PIXELS = 64;
	public static final int MAX_TEXTURE_LENGTH = 128;
	public static final int MAX_PROFILE_NAME_LENGTH = 16;
	public static final int MAX_SKIN_LENGTH = 4096;
	public static final float MAX_SCALE = 64;

	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int UNDERLINED = 4;
	public static final int STRIKETHROUGH = 8;
	public static final int OBFUSCATED = 16;

	private static final Pattern TEXTURE = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

	public enum RenderMode {
		DEFAULT,
		BLOCK,
		ALWAYS
	}

	public enum SneakMode {
		DEFAULT,
		HIDE,
		SOFT_HIDE,
		SHOW
	}

	public enum Alignment {
		CENTER,
		LEFT,
		RIGHT
	}

	public enum Font {
		DEFAULT,
		UNIFORM,
		ALT,
		ILLAGERALT
	}

	public NameTag {
		lines = List.copyOf(lines);

		if (lines.size() > MAX_LINES) {
			throw new ProtocolException("A name tag has at most " + MAX_LINES + " lines");
		}

		if (!(scale > 0) || scale > MAX_SCALE) {
			throw new ProtocolException("Name tag scale must be above 0 and at most " + MAX_SCALE + ", got " + scale);
		}

		if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY) || !Float.isFinite(offsetZ)) {
			throw new ProtocolException("Name tag offset must be finite");
		}

		if (!(maxDistance >= 0) || !Float.isFinite(maxDistance)) {
			throw new ProtocolException("Name tag distance must be 0 or more, got " + maxDistance);
		}

		checkRange("Line gap", lineGap, 0, MAX_PIXELS);
	}

	public NameTag withLine(int index, Line line) {
		if (index < 0 || index >= lines.size()) {
			throw new ProtocolException("The name tag has no line " + index);
		}

		List<Line> changed = new ArrayList<>(lines);
		changed.set(index, line);
		return new NameTag(changed, render, sneak, alignment, background, scale, offsetX, offsetY, offsetZ, maxDistance, lineGap, fullBright, showWhenInvisible, showToSelf);
	}

	public record Line(List<TagObject> objects) {
		public Line {
			objects = List.copyOf(objects);
			int length = 0;

			for (TagObject object : objects) {
				length += object.content().length();
			}

			if (length > MAX_LINE_LENGTH) {
				throw new ProtocolException("A name tag line has at most " + MAX_LINE_LENGTH + " characters, got " + length);
			}
		}

		public static Line read(PacketReader reader) {
			return new Line(reader.readList(MAX_LINE_LENGTH, TagObject::read));
		}

		public void write(PacketWriter writer) {
			writer.writeList(objects, (w, object) -> object.write(w));
		}
	}

	public record TagObject(Content content, Style style) {
		private static final int TEXT = 0;
		private static final int SPRITE = 1;
		private static final int VANILLA_SPRITE = 2;
		private static final int HEAD = 3;

		static TagObject read(PacketReader reader) {
			int kind = reader.readUnsignedByte();
			Content content = switch (kind) {
				case TEXT -> new Text(reader.readString(MAX_LINE_LENGTH * 2));
				case SPRITE -> new Sprite(reader.readVarInt());
				case VANILLA_SPRITE -> new VanillaSprite(reader.readString(MAX_TEXTURE_LENGTH));
				case HEAD -> {
					UUID id = reader.readBoolean() ? reader.readUuid() : null;
					String name = reader.readBoolean() ? reader.readString(MAX_PROFILE_NAME_LENGTH) : null;
					String skin = null;
					String signature = null;

					if (reader.readBoolean()) {
						skin = reader.readString(MAX_SKIN_LENGTH);
						signature = reader.readBoolean() ? reader.readString(MAX_SKIN_LENGTH) : null;
					}

					yield new Head(id, name, skin, signature, reader.readBoolean());
				}
				default -> throw new ProtocolException("Unknown name tag object " + kind);
			};

			return new TagObject(content, Style.read(reader));
		}

		void write(PacketWriter writer) {
			switch (content) {
				case Text text -> writer.writeByte(TEXT).writeString(text.text(), MAX_LINE_LENGTH * 2);
				case Sprite sprite -> writer.writeByte(SPRITE).writeVarInt(sprite.number());
				case VanillaSprite sprite -> writer.writeByte(VANILLA_SPRITE).writeString(sprite.texture(), MAX_TEXTURE_LENGTH);
				case Head head -> {
					writer.writeByte(HEAD).writeBoolean(head.id() != null);

					if (head.id() != null) {
						writer.writeUuid(head.id());
					}

					writer.writeBoolean(head.name() != null);

					if (head.name() != null) {
						writer.writeString(head.name(), MAX_PROFILE_NAME_LENGTH);
					}

					writer.writeBoolean(head.skin() != null);

					if (head.skin() != null) {
						writer.writeString(head.skin(), MAX_SKIN_LENGTH).writeBoolean(head.signature() != null);

						if (head.signature() != null) {
							writer.writeString(head.signature(), MAX_SKIN_LENGTH);
						}
					}

					writer.writeBoolean(head.hat());
				}
			}

			style.write(writer);
		}
	}

	public sealed interface Content {
		int length();
	}

	public record Text(String text) implements Content {
		public Text {
			if (text.isEmpty()) {
				throw new ProtocolException("Name tag text can't be empty");
			}

			if (text.codePointCount(0, text.length()) > MAX_LINE_LENGTH) {
				throw new ProtocolException("Name tag text is longer than " + MAX_LINE_LENGTH + " characters");
			}
		}

		@Override
		public int length() {
			return text.codePointCount(0, text.length());
		}
	}

	public record Sprite(int number) implements Content {
		public Sprite {
			if (number < 0) {
				throw new ProtocolException("Invalid sprite " + number);
			}
		}

		@Override
		public int length() {
			return 1;
		}
	}

	public record VanillaSprite(String texture) implements Content {
		public VanillaSprite {
			if (texture.isEmpty() || texture.length() > MAX_TEXTURE_LENGTH || !TEXTURE.matcher(texture).matches()) {
				throw new ProtocolException("Invalid vanilla texture " + texture);
			}

			for (String segment : texture.split("[:/]", -1)) {
				if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
					throw new ProtocolException("Invalid vanilla texture " + texture);
				}
			}
		}

		@Override
		public int length() {
			return 1;
		}
	}

	public record Head(UUID id, String name, String skin, String signature, boolean hat) implements Content {
		public Head {
			if (id == null && name == null && skin == null) {
				throw new ProtocolException("A head needs a UUID, a name or a skin");
			}

			if (name != null && (name.isEmpty() || name.length() > MAX_PROFILE_NAME_LENGTH)) {
				throw new ProtocolException("Head player name must be 1 to " + MAX_PROFILE_NAME_LENGTH + " characters");
			}

			if (skin != null && (skin.isEmpty() || skin.length() > MAX_SKIN_LENGTH)) {
				throw new ProtocolException("Head skin must be 1 to " + MAX_SKIN_LENGTH + " characters");
			}

			if (signature != null && (skin == null || signature.isEmpty() || signature.length() > MAX_SKIN_LENGTH)) {
				throw new ProtocolException("Head skin signature must be 1 to " + MAX_SKIN_LENGTH + " characters, and only with a skin");
			}
		}

		@Override
		public int length() {
			return 1;
		}
	}

	public record Style(List<Integer> colors, int transparency, int decorations, Font font, boolean shadow, Integer shadowColor, int shadowTransparency) {
		public static final Style PLAIN = new Style(List.of(), 0, 0, Font.DEFAULT, false, null, 0);

		public Style {
			colors = List.copyOf(colors);

			if (colors.size() > MAX_GRADIENT) {
				throw new ProtocolException("A gradient has at most " + MAX_GRADIENT + " colors");
			}

			checkRange("Transparency", transparency, 0, 100);
			checkRange("Shadow transparency", shadowTransparency, 0, 100);

			if ((decorations & ~31) != 0) {
				throw new ProtocolException("Unknown decorations " + decorations);
			}
		}

		public boolean has(int decoration) {
			return (decorations & decoration) != 0;
		}

		static Style read(PacketReader reader) {
			List<Integer> colors = reader.readList(MAX_GRADIENT, PacketReader::readInt);
			int transparency = reader.readUnsignedByte();
			int decorations = reader.readUnsignedByte();
			Font font = reader.readEnum(Font.values(), "font");
			boolean shadow = reader.readBoolean();
			Integer shadowColor = null;
			int shadowTransparency = 0;

			if (shadow) {
				shadowColor = reader.readBoolean() ? reader.readInt() : null;
				shadowTransparency = reader.readUnsignedByte();
			}

			return new Style(colors, transparency, decorations, font, shadow, shadowColor, shadowTransparency);
		}

		void write(PacketWriter writer) {
			writer.writeList(colors, (w, color) -> w.writeInt(color & 0xFFFFFF));
			writer.writeByte(transparency).writeByte(decorations).writeByte(font.ordinal()).writeBoolean(shadow);

			if (shadow) {
				writer.writeBoolean(shadowColor != null);

				if (shadowColor != null) {
					writer.writeInt(shadowColor & 0xFFFFFF);
				}

				writer.writeByte(shadowTransparency);
			}
		}
	}

	public record Background(Integer color, Integer transparency, int paddingX, int paddingY, boolean perLine, boolean shadow, int shadowDarkness, int shadowOffsetX, int shadowOffsetY) {
		public static final Background VANILLA = new Background(null, null, 1, 1, false, false, 50, 2, 2);

		public Background {
			if (transparency != null) {
				checkRange("Background transparency", transparency, 0, 100);
			}

			checkRange("Padding", paddingX, 0, MAX_PIXELS);
			checkRange("Padding", paddingY, 0, MAX_PIXELS);
			checkRange("Shadow darkness", shadowDarkness, 0, 100);
			checkRange("Shadow offset", shadowOffsetX, -MAX_PIXELS, MAX_PIXELS);
			checkRange("Shadow offset", shadowOffsetY, -MAX_PIXELS, MAX_PIXELS);
		}

		static Background read(PacketReader reader) {
			Integer color = reader.readBoolean() ? reader.readInt() & 0xFFFFFF : null;
			Integer transparency = reader.readBoolean() ? reader.readUnsignedByte() : null;
			int paddingX = reader.readUnsignedByte();
			int paddingY = reader.readUnsignedByte();
			boolean perLine = reader.readEnum(new Boolean[] {false, true}, "background style");
			boolean shadow = reader.readBoolean();

			if (!shadow) {
				return new Background(color, transparency, paddingX, paddingY, perLine, false, VANILLA.shadowDarkness, VANILLA.shadowOffsetX, VANILLA.shadowOffsetY);
			}

			return new Background(color, transparency, paddingX, paddingY, perLine, true, reader.readUnsignedByte(), reader.readByte(), reader.readByte());
		}

		void write(PacketWriter writer) {
			writer.writeBoolean(color != null);

			if (color != null) {
				writer.writeInt(color & 0xFFFFFF);
			}

			writer.writeBoolean(transparency != null);

			if (transparency != null) {
				writer.writeByte(transparency);
			}

			writer.writeByte(paddingX).writeByte(paddingY).writeByte(perLine ? 1 : 0).writeBoolean(shadow);

			if (shadow) {
				writer.writeByte(shadowDarkness).writeByte(shadowOffsetX).writeByte(shadowOffsetY);
			}
		}
	}

	public static NameTag read(PacketReader reader) {
		List<Line> lines = reader.readList(MAX_LINES, Line::read);
		RenderMode render = reader.readEnum(RenderMode.values(), "render mode");
		SneakMode sneak = reader.readEnum(SneakMode.values(), "sneak mode");
		Alignment alignment = reader.readEnum(Alignment.values(), "alignment");
		Background background = Background.read(reader);
		float scale = reader.readFloat();
		float offsetX = reader.readFloat();
		float offsetY = reader.readFloat();
		float offsetZ = reader.readFloat();
		float maxDistance = reader.readFloat();
		int lineGap = reader.readUnsignedByte();
		boolean fullBright = reader.readBoolean();
		boolean showWhenInvisible = reader.readBoolean();
		boolean showToSelf = reader.readBoolean();
		return new NameTag(lines, render, sneak, alignment, background, scale, offsetX, offsetY, offsetZ, maxDistance, lineGap, fullBright, showWhenInvisible, showToSelf);
	}

	public void write(PacketWriter writer) {
		writer.writeList(lines, (w, line) -> line.write(w));
		writer.writeByte(render.ordinal()).writeByte(sneak.ordinal()).writeByte(alignment.ordinal());
		background.write(writer);
		writer.writeFloat(scale).writeFloat(offsetX).writeFloat(offsetY).writeFloat(offsetZ).writeFloat(maxDistance);
		writer.writeByte(lineGap).writeBoolean(fullBright).writeBoolean(showWhenInvisible).writeBoolean(showToSelf);
	}

	private static void checkRange(String what, int value, int min, int max) {
		if (value < min || value > max) {
			throw new ProtocolException(what + " must be " + min + " to " + max + ", got " + value);
		}
	}
}
