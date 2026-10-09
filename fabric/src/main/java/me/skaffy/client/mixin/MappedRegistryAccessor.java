package me.skaffy.client.mixin;

import java.util.Map;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor<T> {
	@Accessor("frozen")
	void skaffy$setFrozen(boolean frozen);

	@Accessor("unregisteredIntrusiveHolders")
	void skaffy$setUnregisteredIntrusiveHolders(Map<T, Holder.Reference<T>> holders);

	@Accessor("byId")
	ObjectList<Holder.Reference<T>> skaffy$byId();

	@Accessor("toId")
	Reference2IntMap<T> skaffy$toId();

	@Accessor("byLocation")
	Map<Identifier, Holder.Reference<T>> skaffy$byLocation();

	@Accessor("byKey")
	Map<ResourceKey<T>, Holder.Reference<T>> skaffy$byKey();

	@Accessor("byValue")
	Map<T, Holder.Reference<T>> skaffy$byValue();

	@Accessor("registrationInfos")
	Map<ResourceKey<T>, RegistrationInfo> skaffy$registrationInfos();
}
