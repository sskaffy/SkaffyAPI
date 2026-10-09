package me.skaffy.client.nametag;

import me.skaffy.protocol.nametags.NameTag;

import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

public interface NameTagRenderState {
	@Nullable Data skaffy$nameTag();

	void skaffy$setNameTag(@Nullable Data data);

	record Data(NameTag tag, Vec3 attachment, NameTagRenderer.Look look, int light, boolean behindTranslucent) {
	}
}
