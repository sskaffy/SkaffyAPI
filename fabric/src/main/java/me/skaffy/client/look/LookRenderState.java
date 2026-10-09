package me.skaffy.client.look;

import org.jspecify.annotations.Nullable;

public interface LookRenderState {
	@Nullable Data skaffy$look();

	void skaffy$setLook(@Nullable Data data);

	record Data(float scale, int hiddenParts) {
	}
}
