package me.skaffy.paper.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import me.skaffy.api.nametag.NameTag;
import me.skaffy.api.nametag.NameTagBackground;
import me.skaffy.api.nametag.NameTagLine;
import me.skaffy.api.nametag.NameTagObject;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.nametags.NameTagsCodec;

final class NameTagConversion {
	private static final int STORAGE_VERSION = 1;

	private NameTagConversion() {
	}

	static me.skaffy.protocol.nametags.NameTag toProtocol(NameTag tag, ToIntFunction<String> spriteNumber) {
		List<me.skaffy.protocol.nametags.NameTag.Line> lines = tag.getLines().stream().map(line -> toProtocol(line, spriteNumber)).toList();
		NameTagBackground background = tag.getBackground();
		me.skaffy.protocol.nametags.NameTag.Background wireBackground = new me.skaffy.protocol.nametags.NameTag.Background(
				background.getColor(),
				background.getTransparency(),
				background.getPaddingX(),
				background.getPaddingY(),
				background.getStyle() == NameTagBackground.Style.PER_LINE,
				background.hasShadow(),
				background.getShadowDarkness(),
				background.getShadowOffsetX(),
				background.getShadowOffsetY());
		return new me.skaffy.protocol.nametags.NameTag(
				lines,
				me.skaffy.protocol.nametags.NameTag.RenderMode.valueOf(tag.getRenderMode().name()),
				me.skaffy.protocol.nametags.NameTag.SneakMode.valueOf(tag.getSneakMode().name()),
				me.skaffy.protocol.nametags.NameTag.Alignment.valueOf(tag.getAlignment().name()),
				wireBackground,
				tag.getScale(),
				tag.getOffsetX(),
				tag.getOffsetY(),
				tag.getOffsetZ(),
				tag.getMaxDistance(),
				tag.getLineGap(),
				tag.isFullBright(),
				tag.isShownWhenInvisible(),
				tag.isShownToSelf());
	}

	static me.skaffy.protocol.nametags.NameTag.Line toProtocol(NameTagLine line, ToIntFunction<String> spriteNumber) {
		List<me.skaffy.protocol.nametags.NameTag.TagObject> objects = new ArrayList<>(line.getObjects().size());

		for (NameTagObject object : line.getObjects()) {
			me.skaffy.protocol.nametags.NameTag.Content content = switch (object.getKind()) {
				case TEXT -> new me.skaffy.protocol.nametags.NameTag.Text(object.getValue());
				case SPRITE -> new me.skaffy.protocol.nametags.NameTag.Sprite(spriteNumber.applyAsInt(object.getValue()));
				case VANILLA_SPRITE -> new me.skaffy.protocol.nametags.NameTag.VanillaSprite(object.getValue());
				case HEAD -> new me.skaffy.protocol.nametags.NameTag.Head(object.getHeadId(), object.getValue(), object.getSkin(), object.getSkinSignature(), object.hasHat());
			};
			int decorations = (object.isBold() ? me.skaffy.protocol.nametags.NameTag.BOLD : 0)
					| (object.isItalic() ? me.skaffy.protocol.nametags.NameTag.ITALIC : 0)
					| (object.isUnderlined() ? me.skaffy.protocol.nametags.NameTag.UNDERLINED : 0)
					| (object.isStrikethrough() ? me.skaffy.protocol.nametags.NameTag.STRIKETHROUGH : 0)
					| (object.isObfuscated() ? me.skaffy.protocol.nametags.NameTag.OBFUSCATED : 0);
			me.skaffy.protocol.nametags.NameTag.Style style = new me.skaffy.protocol.nametags.NameTag.Style(
					object.getColors(),
					object.getTransparency(),
					decorations,
					me.skaffy.protocol.nametags.NameTag.Font.valueOf(object.getFont().name()),
					object.hasShadow(),
					object.hasShadow() ? object.getShadowColor() : null,
					object.hasShadow() ? object.getShadowTransparency() : 0);
			objects.add(new me.skaffy.protocol.nametags.NameTag.TagObject(content, style));
		}

		return new me.skaffy.protocol.nametags.NameTag.Line(objects);
	}

	static NameTag fromProtocol(me.skaffy.protocol.nametags.NameTag tag, IntFunction<String> spriteAsset) {
		me.skaffy.protocol.nametags.NameTag.Background wire = tag.background();
		NameTagBackground background = NameTagBackground.vanilla()
				.padding(wire.paddingX(), wire.paddingY())
				.style(wire.perLine() ? NameTagBackground.Style.PER_LINE : NameTagBackground.Style.BOX)
				.shadowDarkness(wire.shadowDarkness())
				.shadowOffset(wire.shadowOffsetX(), wire.shadowOffsetY())
				.shadow(wire.shadow());

		if (wire.color() != null) {
			background = background.color(wire.color());
		}

		if (wire.transparency() != null) {
			background = background.transparency(wire.transparency());
		}

		NameTag.Builder builder = NameTag.builder()
				.renderMode(NameTag.RenderMode.valueOf(tag.render().name()))
				.sneakMode(NameTag.SneakMode.valueOf(tag.sneak().name()))
				.alignment(NameTag.Alignment.valueOf(tag.alignment().name()))
				.background(background)
				.scale(tag.scale())
				.offset(tag.offsetX(), tag.offsetY(), tag.offsetZ())
				.maxDistance(tag.maxDistance())
				.lineGap(tag.lineGap())
				.fullBright(tag.fullBright())
				.showWhenInvisible(tag.showWhenInvisible())
				.showToSelf(tag.showToSelf());

		for (me.skaffy.protocol.nametags.NameTag.Line line : tag.lines()) {
			builder.line(fromProtocol(line, spriteAsset));
		}

		return builder.build();
	}

	private static NameTagLine fromProtocol(me.skaffy.protocol.nametags.NameTag.Line line, IntFunction<String> spriteAsset) {
		List<NameTagObject> objects = new ArrayList<>(line.objects().size());

		for (me.skaffy.protocol.nametags.NameTag.TagObject wire : line.objects()) {
			NameTagObject object = switch (wire.content()) {
				case me.skaffy.protocol.nametags.NameTag.Text text -> NameTagObject.text(text.text());
				case me.skaffy.protocol.nametags.NameTag.Sprite sprite -> NameTagObject.sprite(spriteAsset.apply(sprite.number()));
				case me.skaffy.protocol.nametags.NameTag.VanillaSprite sprite -> NameTagObject.vanillaSprite(sprite.texture());
				case me.skaffy.protocol.nametags.NameTag.Head head -> NameTagObject.head(head.id(), head.name(), head.skin(), head.signature()).hat(head.hat());
			};
			me.skaffy.protocol.nametags.NameTag.Style style = wire.style();
			int[] colors = style.colors().stream().mapToInt(Integer::intValue).toArray();

			if (colors.length > 0) {
				object = object.gradient(colors);
			}

			object = object.transparency(style.transparency())
					.bold(style.has(me.skaffy.protocol.nametags.NameTag.BOLD))
					.italic(style.has(me.skaffy.protocol.nametags.NameTag.ITALIC))
					.underlined(style.has(me.skaffy.protocol.nametags.NameTag.UNDERLINED))
					.strikethrough(style.has(me.skaffy.protocol.nametags.NameTag.STRIKETHROUGH))
					.obfuscated(style.has(me.skaffy.protocol.nametags.NameTag.OBFUSCATED))
					.font(NameTagObject.Font.valueOf(style.font().name()));

			if (style.shadow()) {
				object = object.shadowTransparency(style.shadowTransparency());

				if (style.shadowColor() != null) {
					object = object.shadowColor(style.shadowColor());
				}
			}

			objects.add(object);
		}

		return NameTagLine.of(objects);
	}

	static byte[] save(NameTag tag) {
		List<String> sprites = new ArrayList<>();

		for (NameTagLine line : tag.getLines()) {
			for (NameTagObject object : line.getObjects()) {
				if (object.getKind() == NameTagObject.Kind.SPRITE && !sprites.contains(object.getValue())) {
					sprites.add(object.getValue());
				}
			}
		}

		PacketWriter writer = new PacketWriter(256);
		writer.writeVarInt(STORAGE_VERSION);
		writer.writeList(sprites, (w, asset) -> w.writeString(asset, Protocol.MAX_ASSET_ID_LENGTH));
		toProtocol(tag, asset -> sprites.indexOf(asset) + 1).write(writer);
		return writer.toByteArray();
	}

	static NameTag load(byte[] data) {
		try {
			PacketReader reader = new PacketReader(data);

			if (reader.readVarInt() != STORAGE_VERSION) {
				return null;
			}

			List<String> sprites = reader.readList(NameTagsCodec.MAX_SPRITES, r -> r.readString(Protocol.MAX_ASSET_ID_LENGTH));
			me.skaffy.protocol.nametags.NameTag tag = me.skaffy.protocol.nametags.NameTag.read(reader);
			reader.expectEnd();
			return fromProtocol(tag, number -> number >= 1 && number <= sprites.size() ? sprites.get(number - 1) : "missing");
		} catch (ProtocolException | IllegalArgumentException e) {
			return null;
		}
	}
}
