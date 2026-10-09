package me.skaffy.client.mixin;

import java.util.Collection;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Holder.Reference.class)
public interface HolderReferenceInvoker<T> {
	@Invoker("bindValue")
	void skaffy$bindValue(T value);

	@Invoker("bindTags")
	void skaffy$bindTags(Collection<TagKey<T>> tags);
}
