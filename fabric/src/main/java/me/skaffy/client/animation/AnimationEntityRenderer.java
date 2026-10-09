package me.skaffy.client.animation;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.StringReader;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.model.ModelData;
import me.skaffy.client.model.gpu.GpuModel;
import me.skaffy.client.model.gpu.GpuModels;
import me.skaffy.client.model.gpu.ModelDrawer;
import me.skaffy.protocol.animations.AnimationsPacket.Attachment;
import me.skaffy.protocol.animations.AnimationsPacket.Display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class AnimationEntityRenderer extends EntityRenderer<AnimationEntity, AnimationEntityRenderer.State> {
	private final ItemModelResolver items;
	private final BlockModelResolver blocks;
	private @Nullable Frustum frustum;

	AnimationEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemModelResolver();
		this.blocks = context.getBlockModelResolver();
	}

	@Override
	public boolean shouldRender(AnimationEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		frustum = culler;
		AnimatedObject object = entity.object;
		ClientAnimations.Placement placed = ClientAnimations.placement(object, partialTicks);

		if (entity.radius < 0) {
			GpuModel model = GpuModels.get(object.model);

			if (model == null) {
				return false;
			}

			entity.radius = radius(model, placed.local(object.model.definition().scale()));
		}

		double dx = placed.x() - camX;
		double dy = placed.y() - camY;
		double dz = placed.z() - camZ;
		double distance = Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - entity.radius);
		return entity.shouldRenderAtSqrDistance(distance * distance);
	}

	@Override
	protected AABB getBoundingBoxForCulling(AnimationEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(Math.max(0, entity.radius));
	}

	private static float radius(GpuModel model, Matrix4f local) {
		float radius = 0;
		Vector3f corner = new Vector3f();

		for (int i = 0; i < 8; i++) {
			corner.set((i & 1) == 0 ? model.min().x : model.max().x, (i & 2) == 0 ? model.min().y : model.max().y, (i & 4) == 0 ? model.min().z : model.max().z);
			radius = Math.max(radius, local.transformPosition(corner).length());
		}

		return radius;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AnimationEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		AnimatedObject object = entity.object;
		state.frame = null;
		state.items.clear();
		state.shadow = object.settings.shadowRadius();

		if (object.settings.glowColor() != 0) {
			state.outlineColor = ARGB.opaque(object.settings.glowColor());
		}

		if (hiddenInFirstPerson(object)) {
			state.shadow = 0;
			return;
		}

		ClientAnimations.Placement placed = ClientAnimations.placement(object, partialTicks);
		state.x = placed.x();
		state.y = placed.y();
		state.z = placed.z();
		float modelScale = object.model.definition().scale();
		Matrix4f local = placed.local(modelScale);
		boolean fullBright = object.settings.fullBright();

		if (fullBright) {
			state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
		}

		ModelDrawer.Frame frame = ModelDrawer.extract(object.model, object.holder, object.player.active(ClientAnimations.frameNow()), ClientAnimations.queries(object),
				skeleton -> ClientAnimations.poseBones(object, skeleton, placed), bone -> tint(object, bone), local, placed.x(), placed.y(), placed.z(), frustum,
				fullBright ? LightCoordsUtil.FULL_BRIGHT : -1, -1, entity.level(), entity.tickCount);

		if (frame == null) {
			return;
		}

		state.frame = frame;
		ClientAnimations.rememberPosed(object, frame.modelBones());
		GpuModel model = frame.model();
		entity.radius = radius(model, local);

		if (!object.items.isEmpty()) {
			extractItems(entity, object, frame, state);
		}
	}

	private static boolean hiddenInFirstPerson(AnimatedObject object) {
		if (object.settings.showInFirstPerson() || !(object.attachment instanceof Attachment.ToEntity attached)) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.getCameraEntity() != null && minecraft.getCameraEntity().getId() == attached.entity() && minecraft.options.getCameraType().isFirstPerson();
	}

	private static int tint(AnimatedObject object, int bone) {
		ModelData data = object.model.data();

		if (data == null || object.bones.isEmpty()) {
			return -1;
		}

		AnimatedObject.BoneSetting setting = object.bones.get(data.bones().get(bone).name());
		return setting == null ? -1 : setting.tint;
	}

	private void extractItems(AnimationEntity entity, AnimatedObject object, ModelDrawer.Frame frame, State state) {
		ModelData data = frame.model().data();

		for (AnimatedObject.Item item : object.items.values()) {
			parse(entity, item);
			Matrix4f matrix = new Matrix4f(frame.entityLocal());

			if (!item.bone.isEmpty()) {
				ModelData.Locator locator = data.locator(item.bone);
				int bone = locator != null ? locator.bone() : data.boneIndex(item.bone);

				if (bone < 0) {
					continue;
				}

				matrix = new Matrix4f(frame.bones()[bone]);

				if (locator != null) {
					matrix.translate(locator.x() / 16, locator.y() / 16, locator.z() / 16).rotateZYX(locator.zRot(), locator.yRot(), locator.xRot());
				}
			}

			float[] t = item.transform;
			matrix.scale(1, -1, -1).translate(t[0], t[1], t[2]).rotateZYX((float) Math.toRadians(t[5]), (float) Math.toRadians(t[4]), (float) Math.toRadians(t[3])).scale(t[6]);

			if (item.stack != null && item.display instanceof Display.Item display) {
				ItemStackRenderState render = new ItemStackRenderState();
				items.updateForNonLiving(render, item.stack, context(display.context()), entity);
				state.items.add(new Shown(matrix, render, null));
			} else if (item.block != null) {
				BlockModelRenderState render = new BlockModelRenderState();
				blocks.update(render, item.block, DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
				state.items.add(new Shown(matrix, null, render));
			}
		}
	}

	private static void parse(AnimationEntity entity, AnimatedObject.Item item) {
		if (item.parsed) {
			return;
		}

		item.parsed = true;
		var registries = entity.level().registryAccess();

		try {
			switch (item.display) {
				case Display.Item display -> item.stack = new ItemParser(registries).parse(new StringReader(display.item())).createItemStack(1);
				case Display.Block block -> item.block = BlockStateParser.parseForBlock(registries.lookupOrThrow(Registries.BLOCK), block.state(), false).blockState();
				case Display.None ignored -> {
				}
			}
		} catch (Exception e) {
			SkaffySAPIClient.LOGGER.warn("Animation entity {} shows something that can't be read: {}", entity.object.id, e.getMessage());
		}
	}

	private static ItemDisplayContext context(String name) {
		for (ItemDisplayContext context : ItemDisplayContext.values()) {
			if (context.getSerializedName().equals(name)) {
				return context;
			}
		}

		return ItemDisplayContext.FIXED;
	}

	@Override
	protected float getShadowRadius(State state) {
		return state.shadow;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (state.frame != null) {
			ModelDrawer.submit(state.frame, poseStack, submitNodeCollector, OverlayTexture.NO_OVERLAY, state.outlineColor);
		}

		for (Shown shown : state.items) {
			poseStack.pushPose();
			poseStack.mulPose(shown.matrix);

			if (shown.item != null) {
				shown.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			} else if (shown.block != null) {
				shown.block.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			}

			poseStack.popPose();
		}

		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	private record Shown(Matrix4f matrix, @Nullable ItemStackRenderState item, @Nullable BlockModelRenderState block) {
	}

	public static final class State extends EntityRenderState {
		ModelDrawer.@Nullable Frame frame;
		final List<Shown> items = new ArrayList<>();
		float shadow;
	}
}
