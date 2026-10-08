package me.skaffy.api.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class ComponentConverter {
	private final List<NameTagLine> lines = new ArrayList<>();
	private List<NameTagObject> current = new ArrayList<>();

	private ComponentConverter() {
	}

	static List<NameTagLine> lines(Component component) {
		ComponentConverter converter = new ComponentConverter();
		converter.walk(component, Style.empty());
		converter.lines.add(NameTagLine.of(converter.current));
		return List.copyOf(converter.lines);
	}

	private void walk(Component component, Style inherited) {
		Style style = inherited.merge(component.style());

		switch (component) {
			case TextComponent text -> text(text.content(), style);
			case ObjectComponent object -> {
				NameTagObject converted = object(object.contents());

				if (converted != null) {
					current.add(apply(converted, style));
				} else {
					text(PlainTextComponentSerializer.plainText().serialize(object.fallback() != null ? object.fallback() : Component.empty()), style);
				}
			}
			default -> text(PlainTextComponentSerializer.plainText().serialize(component.children(List.of())), style);
		}

		for (Component child : component.children()) {
			walk(child, style);
		}
	}

	private void text(String text, Style style) {
		String[] parts = text.split("\n", -1);

		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				lines.add(NameTagLine.of(current));
				current = new ArrayList<>();
			}

			if (!parts[i].isEmpty()) {
				current.add(apply(NameTagObject.text(parts[i]), style));
			}
		}
	}

	private static NameTagObject object(ObjectContents contents) {
		return switch (contents) {
			case SpriteObjectContents sprite -> sprite(sprite.atlas(), sprite.sprite());
			case PlayerHeadObjectContents head -> {
				PlayerHeadObjectContents.ProfileProperty textures = head.profileProperties().stream()
						.filter(property -> property.name().equals("textures"))
						.findFirst()
						.orElse(null);
				UUID id = head.id();
				String name = head.name();

				if (id == null && name == null && textures == null) {
					yield null;
				}

				yield NameTagObject.head(id, name, textures == null ? null : textures.value(), textures == null ? null : textures.signature()).hat(head.hat());
			}
			default -> null;
		};
	}

	private static NameTagObject sprite(Key atlas, Key sprite) {
		if (sprite.namespace().equals("skaffy")) {
			return NameTagObject.sprite(sprite.value());
		}

		String folder = switch (atlas.asString()) {
			case "minecraft:gui" -> "gui/sprites/";
			case "minecraft:particles" -> "particle/";
			case "minecraft:paintings" -> "painting/";
			case "minecraft:map_decorations" -> "map/decorations/";
			default -> "";
		};

		return NameTagObject.vanillaSprite(sprite.namespace() + ":" + folder + sprite.value());
	}

	private static NameTagObject apply(NameTagObject object, Style style) {
		TextColor color = style.color();

		if (color != null) {
			object = object.color(color.value());
		}

		object = object.bold(style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE)
				.italic(style.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE)
				.underlined(style.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE)
				.strikethrough(style.decoration(TextDecoration.STRIKETHROUGH) == TextDecoration.State.TRUE)
				.obfuscated(style.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE);
		Key font = style.font();

		if (font != null) {
			object = object.font(switch (font.asString()) {
				case "minecraft:uniform" -> NameTagObject.Font.UNIFORM;
				case "minecraft:alt" -> NameTagObject.Font.ALT;
				case "minecraft:illageralt" -> NameTagObject.Font.ILLAGERALT;
				default -> NameTagObject.Font.DEFAULT;
			});
		}

		ShadowColor shadow = style.shadowColor();

		if (shadow != null && shadow.alpha() > 0) {
			object = object.shadowColor(shadow.value() & 0xFFFFFF).shadowTransparency(Math.round((255 - shadow.alpha()) * 100 / 255f));
		}

		return object;
	}
}
