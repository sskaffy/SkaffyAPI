package me.skaffy.client.mixin;

import java.util.List;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;

import net.minecraft.core.IdMapper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IdMapper.class)
public interface IdMapperAccessor<T> {
	@Accessor("nextId")
	void skaffy$setNextId(int nextId);

	@Accessor("tToId")
	Reference2IntMap<T> skaffy$tToId();

	@Accessor("idToT")
	List<T> skaffy$idToT();
}
