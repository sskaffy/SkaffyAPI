package me.skaffy.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderTarget.class)
public interface RenderTargetAccessor {
	@Accessor("colorTexture")
	GpuTexture skaffy$colorTexture();

	@Accessor("colorTexture")
	void skaffy$setColorTexture(GpuTexture texture);

	@Accessor("colorTextureView")
	GpuTextureView skaffy$colorTextureView();

	@Accessor("colorTextureView")
	void skaffy$setColorTextureView(GpuTextureView view);
}
