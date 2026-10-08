package me.skaffy.client.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.renderpearl.api.pipeline.ShaderSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PipelineCache.class)
public interface PipelineCacheAccessor {
	@Accessor("shaderSource")
	ShaderSource skaffy$shaderSource();
}
