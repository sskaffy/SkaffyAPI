package me.skaffy.client.nametag;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import me.skaffy.client.mixin.FontAccessor;
import me.skaffy.protocol.nametags.NameTag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

public final class NameTagRenderer {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int GLYPH_HEIGHT = 8;
	private static final float COPY_DEPTH = -0.1F;
	private static final FontDescription UNIFORM = new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"));
	private static final FontDescription ALT = new FontDescription.Resource(Identifier.withDefaultNamespace("alt"));
	private static final FontDescription ILLAGERALT = new FontDescription.Resource(Identifier.withDefaultNamespace("illageralt"));

	public enum Look {
		DEFAULT,
		BLOCK,
		ALWAYS,
		SNEAKING
	}

	private NameTagRenderer() {
	}

	private sealed interface Item permits Glyph, PictureItem {
		float x();

		float advance();

		int color();

		int shadow();

		boolean underline();

		boolean strikethrough();
	}

	private record Glyph(GlyphSource source, int codepoint, Style style, boolean obfuscated, float boldOffset, float shadowOffset,
			float x, float advance, int color, int shadow, boolean underline, boolean strikethrough) implements Item {
	}

	private record PictureItem(SpriteImage image, float x, float width, float advance, int color, int shadow, boolean underline, boolean strikethrough) implements Item {
	}

	private record Line(List<Item> items, int width) {
	}

	private record Rect(float x0, float y0, float x1, float y1) {
	}

	private record Entry(TextRenderable renderable, boolean opaque) {
	}

	public static void submit(NameTagRenderState.Data data, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, int offset) {
		NameTag tag = data.tag();

		if (tag.lines().isEmpty() || !RenderSystem.isRenderingLevel) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Font.Provider provider = ((FontAccessor) minecraft.font).skaffy$provider();
		List<Line> lines = layout(tag, provider);
		NameTag.Background background = tag.background();
		int count = lines.size();
		float pitch = GLYPH_HEIGHT + 2 * background.paddingY() + tag.lineGap();
		int widest = 0;

		for (Line line : lines) {
			widest = Math.max(widest, line.width());
		}

		float[] lineX = new float[count];
		float[] lineY = new float[count];

		for (int i = 0; i < count; i++) {
			int width = lines.get(i).width();
			lineX[i] = switch (tag.alignment()) {
				case CENTER -> -width / 2.0F;
				case LEFT -> -widest / 2.0F;
				case RIGHT -> widest / 2.0F - width;
			};
			lineY[i] = offset - (count - 1 - i) * pitch;
		}

		float vanillaOpacity = minecraft.gameRenderer.gameRenderState().optionsRenderState.getBackgroundOpacity(0.25F);
		float seeThroughText = Math.max((vanillaOpacity + 0.75F) * 0.5F, 0.5F);
		int backgroundAlpha = background.transparency() == null ? ARGB.as8BitChannel(vanillaOpacity) : alpha(background.transparency());
		int backgroundColor = ARGB.color(backgroundAlpha, background.color() == null ? 0 : background.color());
		Painter painter = new Painter(provider.effect(), lines, lineX, lineY, backgrounds(lines, lineX, lineY, background), backgroundColor, background);
		int light = tag.fullBright() ? LightCoordsUtil.FULL_BRIGHT : data.light();
		int lit = tag.fullBright() ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.lightCoordsWithEmission(data.light(), 2);

		poseStack.pushPose();
		Vec3 attachment = data.attachment();
		poseStack.translate(attachment.x + tag.offsetX(), attachment.y + 0.5 + tag.offsetY(), attachment.z + tag.offsetZ());
		poseStack.rotate(camera.orientation);
		float scale = 0.025F * tag.scale();
		poseStack.scale(scale, -scale, scale);

		switch (data.look()) {
			case DEFAULT -> {
				submitNormal(collector, poseStack, painter.paint(false, 1), lit, data.behindTranslucent());
				submitSeeThrough(collector, poseStack, painter.paint(true, seeThroughText), light);
			}
			case BLOCK -> submitNormal(collector, poseStack, painter.paint(true, 1), lit, data.behindTranslucent());
			case ALWAYS -> submitSeeThrough(collector, poseStack, painter.paint(true, 1), lit);
			case SNEAKING -> submitNormal(collector, poseStack, painter.paint(true, seeThroughText), light, data.behindTranslucent());
		}

		poseStack.popPose();
	}

	private static void submitNormal(SubmitNodeCollector collector, PoseStack poseStack, List<Entry> entries, int light, boolean behindTranslucent) {
		List<TextRenderable> opaque = new ArrayList<>();
		List<TextRenderable> translucent = new ArrayList<>();

		for (Entry entry : entries) {
			if (entry.opaque()) {
				opaque.add(entry.renderable());
			} else {
				translucent.add(new NameTagGlyphs.Translucent(entry.renderable()));
			}
		}

		SubmitNodeCollection collection = collector instanceof SubmitNodeStorage storage ? storage.order(0) : null;

		if (!opaque.isEmpty()) {
			if (collection != null) {
				collection.solid.submit(submit(poseStack, Font.DisplayMode.NORMAL, light, opaque, -1));
			} else {
				collector.submitText(poseStack, 0, 0, new NameTagGlyphs(opaque), false, Font.DisplayMode.NORMAL, light, -1, 0, 0);
			}
		}

		if (!translucent.isEmpty()) {
			if (collection != null) {
				TextFeatureRenderer.Submit submit = submit(poseStack, Font.DisplayMode.NORMAL, light, translucent, 0x80FFFFFF);

				if (!behindTranslucent && !Minecraft.getInstance().gameRenderer.useImprovedTransparency() && collector instanceof LateNameTags.Storage late) {
					late.skaffy$lateNameTags().submit(submit);
				} else {
					collection.translucentModels.submit(submit);
				}
			} else {
				collector.submitText(poseStack, 0, 0, new NameTagGlyphs(translucent), false, Font.DisplayMode.NORMAL, light, 0x80FFFFFF, 0, 0);
			}
		}
	}

	private static void submitSeeThrough(SubmitNodeCollector collector, PoseStack poseStack, List<Entry> entries, int light) {
		if (entries.isEmpty()) {
			return;
		}

		List<TextRenderable> renderables = entries.stream().map(Entry::renderable).toList();

		if (collector instanceof SubmitNodeStorage storage) {
			storage.order(0).seeThrough.submit(submit(poseStack, Font.DisplayMode.SEE_THROUGH, light, renderables, 0x80FFFFFF));
		} else {
			collector.submitText(poseStack, 0, 0, new NameTagGlyphs(renderables), false, Font.DisplayMode.SEE_THROUGH, light, 0x80FFFFFF, 0, 0);
		}
	}

	private static TextFeatureRenderer.Submit submit(PoseStack poseStack, Font.DisplayMode mode, int light, List<TextRenderable> renderables, int color) {
		TextFeatureRenderer.Content content = new TextFeatureRenderer.Content.Text(0, 0, new NameTagGlyphs(renderables), false, color, 0, 0);
		return new TextFeatureRenderer.Submit(new Matrix4f(poseStack.last().pose()), mode, light, content);
	}

	private static List<Line> layout(NameTag tag, Font.Provider provider) {
		List<Line> lines = new ArrayList<>(tag.lines().size());

		for (NameTag.Line line : tag.lines()) {
			List<Item> items = new ArrayList<>();
			float x = 0;

			for (NameTag.TagObject object : line.objects()) {
				NameTag.Style style = object.style();
				int alpha = alpha(style.transparency());
				boolean underline = style.has(NameTag.UNDERLINED);
				boolean strikethrough = style.has(NameTag.STRIKETHROUGH);

				if (object.content() instanceof NameTag.Text text) {
					FontDescription font = font(style.font());
					GlyphSource source = provider.glyphs(font);
					boolean bold = style.has(NameTag.BOLD);
					boolean obfuscated = style.has(NameTag.OBFUSCATED);
					Style vanilla = Style.EMPTY.withBold(bold).withItalic(style.has(NameTag.ITALIC)).withFont(font);
					int[] codepoints = text.text().codePoints().toArray();

					for (int i = 0; i < codepoints.length; i++) {
						int rgb = color(style.colors(), i, codepoints.length);
						GlyphInfo info = source.getGlyph(codepoints[i]).info();
						float advance = info.getAdvance(bold);
						items.add(new Glyph(source, codepoints[i], vanilla, obfuscated, bold ? info.getBoldOffset() : 0, info.getShadowOffset(),
								x, advance, ARGB.color(alpha, rgb), shadow(style, rgb, alpha), underline, strikethrough));
						x += advance;
					}
				} else {
					SpriteImage image = ClientNameTags.image(object.content());
					float width = GLYPH_HEIGHT * image.aspect();
					int rgb = color(style.colors(), 0, 1);
					items.add(new PictureItem(image, x, width, width + 1, ARGB.color(alpha, rgb), shadow(style, rgb, alpha), underline, strikethrough));
					x += width + 1;
				}
			}

			lines.add(new Line(items, Mth.ceil(x)));
		}

		return lines;
	}

	private static List<Rect> backgrounds(List<Line> lines, float[] lineX, float[] lineY, NameTag.Background background) {
		int padX = background.paddingX();
		int padY = background.paddingY();
		List<Rect> rects = new ArrayList<>();
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;

		for (int i = 0; i < lines.size(); i++) {
			int width = lines.get(i).width();

			if (width == 0) {
				continue;
			}

			float x0 = lineX[i] - padX;
			float x1 = lineX[i] + width - 1 + padX;

			if (background.perLine()) {
				rects.add(new Rect(x0, lineY[i] - padY, x1, lineY[i] + GLYPH_HEIGHT + padY));
			} else {
				minX = Math.min(minX, x0);
				maxX = Math.max(maxX, x1);
			}
		}

		if (!background.perLine() && minX <= maxX) {
			rects.add(new Rect(minX, lineY[0] - padY, maxX, lineY[lines.size() - 1] + GLYPH_HEIGHT + padY));
		}

		return rects;
	}

	private record Painter(EffectGlyph effect, List<Line> lines, float[] lineX, float[] lineY, List<Rect> backgrounds, int backgroundColor, NameTag.Background background) {
		List<Entry> paint(boolean withBackgrounds, float textAlpha) {
			List<Entry> entries = new ArrayList<>();

			if (background.shadow()) {
				layer(entries, true, withBackgrounds, textAlpha);
			}

			layer(entries, false, withBackgrounds, textAlpha);
			return entries;
		}

		private void layer(List<Entry> entries, boolean copy, boolean withBackgrounds, float textAlpha) {
			float dx = copy ? background.shadowOffsetX() : 0;
			float dy = copy ? background.shadowOffsetY() : 0;
			float keep = copy ? 1 - background.shadowDarkness() / 100F : 1;
			float depth = copy ? COPY_DEPTH : 0;

			if (withBackgrounds && ARGB.alpha(backgroundColor) > 0) {
				int color = darken(backgroundColor, keep);

				for (Rect rect : backgrounds) {
					entries.add(entry(effect.createEffect(rect.x0() + dx, rect.y0() + dy, rect.x1() + dx, rect.y1() + dy, -0.01F + depth, color, 0, 0), color, 0));
				}
			}

			List<Entry> effects = new ArrayList<>();

			for (int i = 0; i < lines.size(); i++) {
				float y = lineY[i] + dy;
				boolean first = true;

				for (Item item : lines.get(i).items()) {
					float x = lineX[i] + dx + item.x();
					int color = darken(fade(item.color(), textAlpha), keep);
					int shadow = item.shadow() == 0 ? 0 : darken(fade(item.shadow(), textAlpha), keep);
					float shadowOffset = 1;

					switch (item) {
						case Glyph glyph -> {
							shadowOffset = glyph.shadowOffset();
							BakedGlyph baked = glyph.source().getGlyph(glyph.codepoint());

							if (glyph.obfuscated() && glyph.codepoint() != ' ') {
								baked = glyph.source().getRandomGlyph(RANDOM, Mth.ceil(baked.info().getAdvance(false)));
							}

							TextRenderable renderable = baked.createGlyph(x, y, color, shadow, glyph.style(), glyph.boldOffset(), glyph.shadowOffset());

							if (renderable != null) {
								entries.add(entry(copy ? new NameTagGlyphs.Shifted(renderable, depth) : renderable, color, shadow));
							}
						}
						case PictureItem picture -> {
							float[] uv = new float[8];
							int layers = picture.image().uv(uv);
							entries.add(entry(new NameTagGlyphs.Picture(picture.image(), x, y, x + picture.width(), y + GLYPH_HEIGHT, depth, uv, layers, color, shadow), color, shadow));
						}
					}

					float effectX0 = first ? x - 1 : x;
					first = false;

					if (item.strikethrough()) {
						effects.add(entry(effect.createEffect(effectX0, y + 3.5F, x + item.advance(), y + 4.5F, 0.01F + depth, color, shadow, shadowOffset), color, shadow));
					}

					if (item.underline()) {
						effects.add(entry(effect.createEffect(effectX0, y + GLYPH_HEIGHT, x + item.advance(), y + GLYPH_HEIGHT + 1, 0.01F + depth, color, shadow, shadowOffset), color, shadow));
					}
				}
			}

			entries.addAll(effects);
		}

		private static Entry entry(TextRenderable renderable, int color, int shadow) {
			return new Entry(renderable, ARGB.alpha(color) == 255 && (shadow == 0 || ARGB.alpha(shadow) == 255));
		}
	}

	private static FontDescription font(NameTag.Font font) {
		return switch (font) {
			case DEFAULT -> FontDescription.DEFAULT;
			case UNIFORM -> UNIFORM;
			case ALT -> ALT;
			case ILLAGERALT -> ILLAGERALT;
		};
	}

	static int color(List<Integer> colors, int index, int count) {
		if (colors.isEmpty()) {
			return 0xFFFFFF;
		}

		if (colors.size() == 1 || count <= 1) {
			return colors.getFirst() & 0xFFFFFF;
		}

		float position = index / (float) (count - 1) * (colors.size() - 1);
		int segment = Math.min((int) position, colors.size() - 2);
		float t = position - segment;
		int from = colors.get(segment);
		int to = colors.get(segment + 1);
		return lerp(from >> 16 & 0xFF, to >> 16 & 0xFF, t) << 16 | lerp(from >> 8 & 0xFF, to >> 8 & 0xFF, t) << 8 | lerp(from & 0xFF, to & 0xFF, t);
	}

	private static int lerp(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}

	static int shadow(NameTag.Style style, int rgb, int alpha) {
		if (!style.shadow()) {
			return 0;
		}

		int base = style.shadowColor() != null ? style.shadowColor() : ARGB.scaleRGB(rgb, 0.25F);
		int shadowAlpha = Math.round(alpha * (100 - style.shadowTransparency()) / 100F);
		return shadowAlpha == 0 ? 0 : ARGB.color(shadowAlpha, base & 0xFFFFFF);
	}

	static int alpha(int transparency) {
		return Math.round((100 - transparency) * 255 / 100F);
	}

	private static int fade(int color, float factor) {
		return factor == 1 ? color : ARGB.color(Math.round(ARGB.alpha(color) * factor), color & 0xFFFFFF);
	}

	private static int darken(int color, float keep) {
		if (keep == 1) {
			return color;
		}

		return ARGB.color(ARGB.alpha(color), Math.round(ARGB.red(color) * keep), Math.round(ARGB.green(color) * keep), Math.round(ARGB.blue(color) * keep));
	}
}
