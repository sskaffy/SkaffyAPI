package me.skaffy.client.model;

import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.entity.EntityRenderer;

public record ModelRenderData(
		ClientModel model,
		EntityRenderer<?, ?> renderer,
		List<AnimationPlayer.Active> animations,
		float lifeTime,
		float groundSpeed,
		float headXRotation,
		float headYRotation,
		float distanceMoved,
		Map<String, Float> variables) implements ModelAnimator.Queries {
}
