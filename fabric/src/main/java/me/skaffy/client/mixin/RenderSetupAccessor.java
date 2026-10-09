package me.skaffy.client.mixin;

import java.util.Map;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.rendertype.RenderSetup;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
	@Accessor("pipeline")
	RenderPipeline skaffy$pipeline();

	@Accessor("textures")
	Map<String, ?> skaffy$textures();
}
