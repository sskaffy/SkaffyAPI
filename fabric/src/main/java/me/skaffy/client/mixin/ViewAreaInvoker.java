package me.skaffy.client.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface ViewAreaInvoker {
	@Invoker("getRenderSection")
	SectionRenderDispatcher.@Nullable RenderSection skaffy$getRenderSection(long sectionNode);
}
