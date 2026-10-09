package me.skaffy.client.mixin;

import java.util.List;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
	@Accessor("layers")
	List<RenderLayer<?, ?>> skaffy$layers();

	@Accessor("model")
	void skaffy$setModel(EntityModel<?> model);
}
