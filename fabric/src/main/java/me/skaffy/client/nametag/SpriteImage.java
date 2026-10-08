package me.skaffy.client.nametag;

import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.resources.Identifier;

interface SpriteImage {
	GlyphRenderTypes renderTypes();

	Identifier texture();

	float aspect();

	int uv(float[] out);
}
