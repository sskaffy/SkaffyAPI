package me.skaffy.client.mixin;

import java.util.Map;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityModelSet.class)
public interface EntityModelSetAccessor {
	@Accessor("roots")
	Map<ModelLayerLocation, LayerDefinition> skaffy$roots();
}
