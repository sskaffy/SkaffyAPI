package me.skaffy.client.model;

import org.jspecify.annotations.Nullable;

public interface SkaffyRenderState {
	@Nullable ModelRenderData skaffy$modelData();

	void skaffy$setModelData(@Nullable ModelRenderData data);
}
