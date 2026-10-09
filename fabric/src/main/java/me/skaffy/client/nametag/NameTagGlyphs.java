package me.skaffy.client.nametag;

import java.util.List;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.FormattedCharSequence;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public record NameTagGlyphs(List<TextRenderable> renderables) implements FormattedCharSequence, Font.PreparedText {
	@Override
	public boolean accept(FormattedCharSink sink) {
		return true;
	}

	@Override
	public void visit(Font.GlyphVisitor visitor) {
		for (TextRenderable renderable : renderables) {
			visitor.acceptRenderable(renderable);
		}
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		return null;
	}

	record Shifted(TextRenderable inner, float depth) implements TextRenderable {
		@Override
		public void render(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, boolean flat) {
			inner.render(new Matrix4f(pose).translate(0, 0, depth), buffer, packedLightCoords, flat);
		}

		@Override
		public RenderType renderType(Font.DisplayMode displayMode) {
			return inner.renderType(displayMode);
		}

		@Override
		public GpuTextureView textureView() {
			return inner.textureView();
		}

		@Override
		public RenderPipeline guiPipeline() {
			return inner.guiPipeline();
		}

		@Override
		public float left() {
			return inner.left();
		}

		@Override
		public float top() {
			return inner.top();
		}

		@Override
		public float right() {
			return inner.right();
		}

		@Override
		public float bottom() {
			return inner.bottom();
		}
	}

	record Translucent(TextRenderable inner) implements TextRenderable {
		@Override
		public void render(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, boolean flat) {
			inner.render(pose, buffer, packedLightCoords, flat);
		}

		@Override
		public RenderType renderType(Font.DisplayMode displayMode) {
			RenderType type = inner.renderType(displayMode);
			return displayMode == Font.DisplayMode.NORMAL ? NameTagRenderTypes.translucent(type) : type;
		}

		@Override
		public GpuTextureView textureView() {
			return inner.textureView();
		}

		@Override
		public RenderPipeline guiPipeline() {
			return inner.guiPipeline();
		}

		@Override
		public float left() {
			return inner.left();
		}

		@Override
		public float top() {
			return inner.top();
		}

		@Override
		public float right() {
			return inner.right();
		}

		@Override
		public float bottom() {
			return inner.bottom();
		}
	}

	record Picture(SpriteImage image, float x0, float y0, float x1, float y1, float depth, float[] uv, int layers, int color, int shadowColor) implements TextRenderable {
		@Override
		public void render(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, boolean flat) {
			float front = depth;

			if (shadowColor != 0) {
				quads(pose, buffer, packedLightCoords, 1, depth, shadowColor);
				front += 0.03F;
			}

			quads(pose, buffer, packedLightCoords, 0, front, color);
		}

		private void quads(Matrix4fc pose, VertexConsumer buffer, int light, float offset, float z, int color) {
			for (int layer = 0; layer < layers; layer++) {
				float u0 = uv[layer * 4];
				float v0 = uv[layer * 4 + 1];
				float u1 = uv[layer * 4 + 2];
				float v1 = uv[layer * 4 + 3];
				float layerZ = z + layer * 0.001F;
				buffer.addVertex(pose, x0 + offset, y0 + offset, layerZ).setUv(u0, v0).setColor(color).setLight(light);
				buffer.addVertex(pose, x0 + offset, y1 + offset, layerZ).setUv(u0, v1).setColor(color).setLight(light);
				buffer.addVertex(pose, x1 + offset, y1 + offset, layerZ).setUv(u1, v1).setColor(color).setLight(light);
				buffer.addVertex(pose, x1 + offset, y0 + offset, layerZ).setUv(u1, v0).setColor(color).setLight(light);
			}
		}

		@Override
		public RenderType renderType(Font.DisplayMode displayMode) {
			return image.renderTypes().select(displayMode);
		}

		@Override
		public GpuTextureView textureView() {
			return Minecraft.getInstance().getTextureManager().getTexture(image.texture()).getTextureView();
		}

		@Override
		public RenderPipeline guiPipeline() {
			return image.renderTypes().guiPipeline();
		}

		@Override
		public float left() {
			return x0;
		}

		@Override
		public float top() {
			return y0;
		}

		@Override
		public float right() {
			return x1 + (shadowColor != 0 ? 1 : 0);
		}

		@Override
		public float bottom() {
			return y1 + (shadowColor != 0 ? 1 : 0);
		}
	}
}
