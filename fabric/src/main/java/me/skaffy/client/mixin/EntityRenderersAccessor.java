package me.skaffy.client.mixin;

import java.util.Map;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderers.class)
public interface EntityRenderersAccessor {
	@Accessor("PROVIDERS")
	static Map<EntityType<?>, EntityRendererProvider<?>> skaffy$providers() {
		throw new AssertionError();
	}
}
