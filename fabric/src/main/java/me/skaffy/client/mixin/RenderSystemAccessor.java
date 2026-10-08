package me.skaffy.client.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {
	@Accessor("currentPipelineCache")
	static @Nullable PipelineCache skaffy$currentPipelineCache() {
		throw new AssertionError();
	}

	@Accessor("fallbackPipelineCache")
	static @Nullable PipelineCache skaffy$fallbackPipelineCache() {
		throw new AssertionError();
	}
}
