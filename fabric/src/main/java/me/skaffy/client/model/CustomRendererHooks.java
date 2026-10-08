package me.skaffy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;

import me.skaffy.client.mixin.LivingEntityRendererAccessor;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import org.jspecify.annotations.Nullable;

public final class CustomRendererHooks {
	private CustomRendererHooks() {
	}

	public static void setContext(EntityRendererProvider.Context context) {
		CustomRenderers.setContext(context);
	}

	public static @Nullable EntityRenderer<?, ?> rendererOf(EntityRenderState state) {
		ModelRenderData data = ((SkaffyRenderState) state).skaffy$modelData();
		return data == null ? null : data.renderer();
	}

	public static void forceModel(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state) {
		EntityModel<?> model = CustomRenderers.forcedModel(renderer, state.isBaby);

		if (model != null) {
			((LivingEntityRendererAccessor) renderer).skaffy$setModel(model);
		}
	}

	public static void scale(EntityRenderState state, PoseStack poseStack) {
		ModelRenderData data = ((SkaffyRenderState) state).skaffy$modelData();

		if (data == null) {
			return;
		}

		float scale = data.model().definition().scale();

		if (state instanceof LivingEntityRenderState living && living.isBaby && CustomRenderers.shrinksBabies(data.renderer())) {
			scale *= 0.5f;
		}

		if (scale != 1) {
			poseStack.scale(scale, scale, scale);
		}
	}

	public static @Nullable RenderType renderType(EntityRenderer<?, ?> renderer, EntityModel<?> model, EntityRenderState state) {
		ModelRenderData data = ((SkaffyRenderState) state).skaffy$modelData();

		if (data == null || data.renderer() != renderer) {
			return null;
		}

		ClientModel clientModel = data.model();
		net.minecraft.resources.Identifier texture = CustomRenderers.texture(clientModel);

		if (texture == null) {
			return null;
		}

		return clientModel.definition().translucent() ? RenderTypes.entityTranslucent(texture) : model.renderType(texture);
	}
}
