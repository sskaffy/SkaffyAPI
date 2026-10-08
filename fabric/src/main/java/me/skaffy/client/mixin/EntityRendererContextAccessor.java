package me.skaffy.client.mixin;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRendererProvider.Context.class)
public interface EntityRendererContextAccessor {
	@Accessor("modelSet")
	EntityModelSet skaffy$modelSet();

	@Mutable
	@Accessor("modelSet")
	void skaffy$setModelSet(EntityModelSet modelSet);
}
