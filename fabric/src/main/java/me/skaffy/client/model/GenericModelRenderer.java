package me.skaffy.client.model;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import me.skaffy.client.model.gpu.ModelDrawer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

final class GenericModelRenderer extends EntityRenderer<Entity, GenericModelRenderer.State> {
	private static final Int2ObjectMap<ModelDrawer.Holder> HOLDERS = new Int2ObjectOpenHashMap<>();
	private static @Nullable Frustum frustum;
	private final ClientModel clientModel;

	GenericModelRenderer(EntityRendererProvider.Context context, ClientModel clientModel) {
		super(context);
		this.clientModel = clientModel;
	}

	static void forget(int entityId) {
		ModelDrawer.Holder holder = HOLDERS.remove(entityId);

		if (holder != null) {
			holder.close();
		}
	}

	static void clear() {
		HOLDERS.values().forEach(ModelDrawer.Holder::close);
		HOLDERS.clear();
	}

	@Override
	public boolean shouldRender(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		frustum = culler;
		return entity.shouldRender(camX, camY, camZ);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Entity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.frame = null;
		AnimationPlayer player = ClientEntityModels.animations(entity);
		List<AnimationPlayer.Active> animations = player == null ? List.of() : player.active(AnimationPlayer.now());
		ModelAnimator.Queries queries = ClientEntityModels.renderData(player != null ? player : new AnimationPlayer(clientModel), this, entity, partialTicks);
		float scale = clientModel.definition().scale();
		Matrix4f local = new Matrix4f().rotateY((float) Math.toRadians(-entity.getYRot(partialTicks))).scale(scale).translate(0, 1.501f, 0).scale(1, -1, -1);
		ModelDrawer.Holder holder = HOLDERS.computeIfAbsent(entity.getId(), id -> new ModelDrawer.Holder());
		state.frame = ModelDrawer.extract(clientModel, holder, animations, queries, null, null, local, state.x, state.y, state.z, frustum, -1, -1, entity.level(), entity.tickCount);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (state.frame != null) {
			ModelDrawer.submit(state.frame, poseStack, submitNodeCollector, OverlayTexture.NO_OVERLAY, state.outlineColor);
		}

		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	static final class State extends EntityRenderState {
		ModelDrawer.@Nullable Frame frame;
	}
}
