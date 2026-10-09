package me.skaffy.client.mixin;

import com.mojang.renderpearl.api.textures.GpuSampler;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Accessor("viewArea")
	@Nullable ViewArea skaffy$viewArea();

	@Accessor("sectionRenderDispatcher")
	@Nullable SectionRenderDispatcher skaffy$sectionRenderDispatcher();

	@Accessor("chunkLayerSampler")
	@Nullable GpuSampler skaffy$chunkLayerSampler();
}
