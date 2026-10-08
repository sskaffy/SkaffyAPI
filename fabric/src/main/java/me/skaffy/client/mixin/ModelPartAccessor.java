package me.skaffy.client.mixin;

import java.util.Map;

import net.minecraft.client.model.geom.ModelPart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("children")
	Map<String, ModelPart> skaffy$children();
}
