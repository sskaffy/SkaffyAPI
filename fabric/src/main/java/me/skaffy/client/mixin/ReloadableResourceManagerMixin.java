package me.skaffy.client.mixin;

import java.util.ArrayList;
import java.util.List;

import me.skaffy.client.pack.HiddenPack;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@Shadow
	@Final
	private PackType type;

	@ModifyVariable(method = "createReload", at = @At("HEAD"), argsOnly = true)
	private List<PackResources> skaffy$addHiddenPack(List<PackResources> packs) {
		HiddenPack pack = HiddenPack.active();

		if (type != PackType.CLIENT_RESOURCES || pack == null) {
			return packs;
		}

		List<PackResources> withPack = new ArrayList<>(packs);
		withPack.add(pack);
		return withPack;
	}
}
