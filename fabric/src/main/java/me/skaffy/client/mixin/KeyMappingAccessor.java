package me.skaffy.client.mixin;

import java.util.Map;

import net.minecraft.client.KeyMapping;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("ALL")
	static Map<String, KeyMapping> skaffy$all() {
		throw new AssertionError();
	}

	@Accessor("MAP")
	static Map<com.mojang.blaze3d.platform.InputConstants.Key, java.util.List<KeyMapping>> skaffy$map() {
		throw new AssertionError();
	}

	@Invoker("release")
	void skaffy$release();
}
