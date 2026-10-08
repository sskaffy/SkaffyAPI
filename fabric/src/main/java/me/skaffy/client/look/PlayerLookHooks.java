package me.skaffy.client.look;

import com.mojang.blaze3d.vertex.PoseStack;

import me.skaffy.client.look.ClientPlayerLooks.ClientLook;
import me.skaffy.client.nametag.NameTagRenderState;
import me.skaffy.protocol.playerlooks.PlayerLook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import org.jspecify.annotations.Nullable;

public final class PlayerLookHooks {
	private PlayerLookHooks() {
	}

	public static void patchAvatar(Avatar entity, AvatarRenderState state) {
		ClientLook look = ClientPlayerLooks.shown(entity);

		if (look == null) {
			((LookRenderState) state).skaffy$setLook(null);
			return;
		}

		PlayerLook.PlayerModel model = look.look.form() instanceof PlayerLook.PlayerModel player ? player : null;
		((LookRenderState) state).skaffy$setLook(new LookRenderState.Data(look.look.scale(), model == null ? 0 : model.hiddenParts()));

		if (model == null) {
			return;
		}

		PlayerSkin own = state.skin;
		ClientAsset.Texture body = own.body();
		PlayerModelType type = own.model();

		switch (model.skin()) {
			case PlayerLook.OwnSkin ignored -> {
			}
			case PlayerLook.AssetSkin asset -> {
				ClientAsset.Texture texture = ClientPlayerLooks.texture(asset.asset());
				body = texture != null ? texture : body;
				type = PlayerModelType.WIDE;
			}
			case PlayerLook.ProfileSkin profile -> {
				PlayerSkin skin = ClientPlayerLooks.profile(profile.profile()).playerSkin();
				body = skin.body();
				type = skin.model();
			}
		}

		type = switch (model.arms()) {
			case FROM_SKIN -> type;
			case WIDE -> PlayerModelType.WIDE;
			case SLIM -> PlayerModelType.SLIM;
		};

		ClientAsset.Texture cape = own.cape();
		ClientAsset.Texture elytra = own.elytra();

		switch (model.cape()) {
			case PlayerLook.OwnCape ignored -> {
			}
			case PlayerLook.NoCape ignored -> {
				cape = null;
				elytra = null;
				state.showCape = false;
			}
			case PlayerLook.AssetCape asset -> {
				cape = ClientPlayerLooks.texture(asset.asset());
				elytra = null;
				state.showCape = true;
			}
			case PlayerLook.ProfileCape profile -> {
				PlayerSkin skin = ClientPlayerLooks.profile(profile.profile()).playerSkin();
				cape = skin.cape();
				elytra = skin.elytra();
				state.showCape = true;
			}
		}

		if (model.elytra() != null) {
			ClientAsset.Texture texture = ClientPlayerLooks.texture(model.elytra());
			elytra = texture != null ? texture : elytra;
		}

		state.skin = new PlayerSkin(body, cape, elytra, type, own.secure());
		int forced = model.forcedLayers();
		int shown = model.shownLayers();
		state.showHat = layer(forced, shown, PlayerLook.LAYER_HAT, state.showHat);
		state.showJacket = layer(forced, shown, PlayerLook.LAYER_JACKET, state.showJacket);
		state.showLeftSleeve = layer(forced, shown, PlayerLook.LAYER_LEFT_SLEEVE, state.showLeftSleeve);
		state.showRightSleeve = layer(forced, shown, PlayerLook.LAYER_RIGHT_SLEEVE, state.showRightSleeve);
		state.showLeftPants = layer(forced, shown, PlayerLook.LAYER_LEFT_PANTS, state.showLeftPants);
		state.showRightPants = layer(forced, shown, PlayerLook.LAYER_RIGHT_PANTS, state.showRightPants);
	}

	private static boolean layer(int forced, int shown, int bit, boolean own) {
		return (forced & bit) != 0 ? (shown & bit) != 0 : own;
	}

	public static void afterSetupAnim(Object model, Object state) {
		if (!(model instanceof PlayerModel player) || !(state instanceof AvatarRenderState)) {
			return;
		}

		LookRenderState.Data data = ((LookRenderState) state).skaffy$look();
		int hidden = data == null ? 0 : data.hiddenParts();
		player.head.visible = (hidden & PlayerLook.PART_HEAD) == 0;

		if (hidden != 0) {
			player.body.visible &= (hidden & PlayerLook.PART_BODY) == 0;
			player.rightArm.visible &= (hidden & PlayerLook.PART_RIGHT_ARM) == 0;
			player.leftArm.visible &= (hidden & PlayerLook.PART_LEFT_ARM) == 0;
			player.rightLeg.visible &= (hidden & PlayerLook.PART_RIGHT_LEG) == 0;
			player.leftLeg.visible &= (hidden & PlayerLook.PART_LEFT_LEG) == 0;
		}
	}

	public static boolean hidesArm(AvatarRenderState state, boolean right) {
		LookRenderState.Data data = ((LookRenderState) state).skaffy$look();
		return data != null && (data.hiddenParts() & (right ? PlayerLook.PART_RIGHT_ARM : PlayerLook.PART_LEFT_ARM)) != 0;
	}

	public static void scale(EntityRenderState state, PoseStack poseStack) {
		LookRenderState.Data data = ((LookRenderState) state).skaffy$look();

		if (data != null && data.scale() != 1) {
			poseStack.scale(data.scale(), data.scale(), data.scale());
		}
	}

	public static float nonLivingScale(EntityRenderState state) {
		LookRenderState.Data data = ((LookRenderState) state).skaffy$look();
		return data == null || state instanceof LivingEntityRenderState ? 1 : data.scale();
	}

	public static @Nullable Entity dummyFor(Entity entity) {
		ClientLook look = ClientPlayerLooks.shown(entity);

		if (look == null || !(look.look.form() instanceof PlayerLook.EntityForm)) {
			return null;
		}

		LookDummy dummy = look.dummy();

		if (dummy == null) {
			return null;
		}

		dummy.sync((AbstractClientPlayer) entity);
		return dummy.entity;
	}

	public static EntityRenderState disguise(Entity entity, EntityRenderState own, float partialTicks) {
		Entity dummy = dummyFor(entity);

		if (dummy == null) {
			return own;
		}

		EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(dummy, partialTicks);
		state.nameTag = own.nameTag;
		state.nameTagAttachment = own.nameTagAttachment;
		state.outlineColor = own.outlineColor;
		((NameTagRenderState) state).skaffy$setNameTag(((NameTagRenderState) own).skaffy$nameTag());

		if (own instanceof LivingEntityRenderState ownLiving && state instanceof LivingEntityRenderState living) {
			living.isInvisibleToPlayer = ownLiving.isInvisibleToPlayer;
		}

		ClientLook look = ClientPlayerLooks.shown(entity);
		((LookRenderState) state).skaffy$setLook(new LookRenderState.Data(look == null ? 1 : look.look.scale(), 0));
		return state;
	}

	public static @Nullable EntityRenderState previewState(Entity entity) {
		Entity dummy = dummyFor(entity);

		if (dummy == null) {
			return null;
		}

		EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(dummy, 1);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		return state;
	}

	public static void previewSize(EntityRenderState state) {
		LookRenderState.Data data = ((LookRenderState) state).skaffy$look();

		if (data != null && data.scale() != 1) {
			((LookRenderState) state).skaffy$setLook(new LookRenderState.Data(1, data.hiddenParts()));
		}
	}
}
