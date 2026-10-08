package me.skaffy.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public interface PreparedFrameAccessor {
	@Accessor("context")
	@Nullable FeatureFrameContext skaffy$context();

	@Accessor("submitNodeStorage")
	@Nullable SubmitNodeStorage skaffy$submitNodeStorage();

	@Invoker("executePhase")
	void skaffy$executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext context, RenderPass renderPass);
}
