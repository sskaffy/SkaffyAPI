package me.skaffy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import me.skaffy.client.model.ClientEntityModels;
import me.skaffy.client.nametag.ClientNameTags;
import me.skaffy.client.nametag.NameTagRenderState;
import me.skaffy.client.nametag.NameTagRenderer;
import me.skaffy.client.shader.ShaderRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
	private void skaffy$attachModelData(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		ClientEntityModels.attach((EntityRenderer<?, ?>) (Object) this, entity, cir.getReturnValue(), partialTicks);
		ClientNameTags.extract(entity, cir.getReturnValue(), partialTicks);
	}

	@Inject(
			method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
			at = @At("HEAD"),
			cancellable = true)
	private void skaffy$customNameTag(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, int offset, CallbackInfo ci) {
		NameTagRenderState.Data data = ((NameTagRenderState) state).skaffy$nameTag();

		if (data != null) {
			NameTagRenderer.submit(data, poseStack, submitNodeCollector, camera, offset);
			ci.cancel();
		}
	}

	@Inject(method = "extractShadow", at = @At("TAIL"))
	private void skaffy$noBlobShadow(EntityRenderState state, Minecraft minecraft, Level level, CallbackInfo ci) {
		if (ShaderRenderer.hidesBlobShadows()) {
			state.shadowPieces.clear();
		}
	}
}
