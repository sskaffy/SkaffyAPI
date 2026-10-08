package me.skaffy.client.nametag;

import java.util.function.Supplier;

import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.resources.Identifier;

record HeadImage(Supplier<PlayerSkinRenderCache.RenderInfo> skin, boolean hat) implements SpriteImage {
	@Override
	public GlyphRenderTypes renderTypes() {
		return skin.get().glyphRenderTypes();
	}

	@Override
	public Identifier texture() {
		return skin.get().playerSkin().body().texturePath();
	}

	@Override
	public float aspect() {
		return 1;
	}

	@Override
	public int uv(float[] out) {
		out[0] = 8 / 64f;
		out[1] = 8 / 64f;
		out[2] = 16 / 64f;
		out[3] = 16 / 64f;

		if (!hat) {
			return 1;
		}

		out[4] = 40 / 64f;
		out[5] = 8 / 64f;
		out[6] = 48 / 64f;
		out[7] = 16 / 64f;
		return 2;
	}
}
