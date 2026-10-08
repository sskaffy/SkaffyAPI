package me.skaffy.client.mixin;

import me.skaffy.client.look.LookRenderState;
import me.skaffy.client.model.ModelRenderData;
import me.skaffy.client.model.SkaffyRenderState;
import me.skaffy.client.nametag.NameTagRenderState;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements SkaffyRenderState, NameTagRenderState, LookRenderState {
	@Unique
	private @Nullable ModelRenderData skaffy$modelData;
	@Unique
	private NameTagRenderState.@Nullable Data skaffy$nameTag;
	@Unique
	private LookRenderState.@Nullable Data skaffy$look;

	@Override
	public @Nullable ModelRenderData skaffy$modelData() {
		return skaffy$modelData;
	}

	@Override
	public void skaffy$setModelData(@Nullable ModelRenderData data) {
		skaffy$modelData = data;
	}

	@Override
	public NameTagRenderState.@Nullable Data skaffy$nameTag() {
		return skaffy$nameTag;
	}

	@Override
	public void skaffy$setNameTag(NameTagRenderState.@Nullable Data data) {
		skaffy$nameTag = data;
	}

	@Override
	public LookRenderState.@Nullable Data skaffy$look() {
		return skaffy$look;
	}

	@Override
	public void skaffy$setLook(LookRenderState.@Nullable Data data) {
		skaffy$look = data;
	}
}
