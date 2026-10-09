package me.skaffy.paper.look;

import java.util.Map;

import me.skaffy.api.look.ArmType;
import me.skaffy.api.look.BodyPart;
import me.skaffy.api.look.LookCape;
import me.skaffy.api.look.LookProfile;
import me.skaffy.api.look.LookSkin;
import me.skaffy.api.look.PlayerLook;
import me.skaffy.api.look.SkinLayer;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

final class LookConversion {
	private static final int SAVE_VERSION = 1;
	private static final int MAX_MODEL_NAME_LENGTH = 64;

	private LookConversion() {
	}

	static me.skaffy.protocol.playerlooks.PlayerLook toProtocol(PlayerLook look, int modelNumber) {
		me.skaffy.protocol.playerlooks.PlayerLook.Form form = switch (look.getKind()) {
			case PLAYER -> {
				int forced = 0;
				int shown = 0;

				for (Map.Entry<SkinLayer, Boolean> layer : look.getLayers().entrySet()) {
					forced |= 1 << layer.getKey().ordinal();

					if (layer.getValue()) {
						shown |= 1 << layer.getKey().ordinal();
					}
				}

				int hidden = 0;

				for (BodyPart part : look.getHiddenParts()) {
					hidden |= 1 << part.ordinal();
				}

				yield new me.skaffy.protocol.playerlooks.PlayerLook.PlayerModel(skin(look.getSkin()),
						me.skaffy.protocol.playerlooks.PlayerLook.Arms.valueOf(look.getArms().name()), cape(look.getCape()), look.getElytra(), forced, shown, hidden);
			}
			case ENTITY -> new me.skaffy.protocol.playerlooks.PlayerLook.EntityForm(look.getEntityType(), look.getEntityData());
			case MODEL -> new me.skaffy.protocol.playerlooks.PlayerLook.CustomModel(modelNumber);
		};

		me.skaffy.protocol.playerlooks.PlayerLook.Hitbox hitbox = look.hasCustomHitbox()
				? new me.skaffy.protocol.playerlooks.PlayerLook.Hitbox(look.getHitboxWidth(), look.getHitboxHeight(), look.getEyeHeight())
				: null;
		return new me.skaffy.protocol.playerlooks.PlayerLook(form, look.getScale(), hitbox, look.hasOwnHitbox(), look.showsToSelf());
	}

	private static me.skaffy.protocol.playerlooks.PlayerLook.Skin skin(LookSkin skin) {
		return switch (skin.source()) {
			case OWN -> new me.skaffy.protocol.playerlooks.PlayerLook.OwnSkin();
			case ASSET -> new me.skaffy.protocol.playerlooks.PlayerLook.AssetSkin(skin.asset());
			case PROFILE -> new me.skaffy.protocol.playerlooks.PlayerLook.ProfileSkin(profile(skin.profile()));
		};
	}

	private static me.skaffy.protocol.playerlooks.PlayerLook.Cape cape(LookCape cape) {
		return switch (cape.source()) {
			case OWN -> new me.skaffy.protocol.playerlooks.PlayerLook.OwnCape();
			case NONE -> new me.skaffy.protocol.playerlooks.PlayerLook.NoCape();
			case ASSET -> new me.skaffy.protocol.playerlooks.PlayerLook.AssetCape(cape.asset());
			case PROFILE -> new me.skaffy.protocol.playerlooks.PlayerLook.ProfileCape(profile(cape.profile()));
		};
	}

	private static me.skaffy.protocol.playerlooks.PlayerLook.Profile profile(LookProfile profile) {
		return new me.skaffy.protocol.playerlooks.PlayerLook.Profile(profile.id(), profile.name(), profile.value(), profile.signature());
	}

	static byte[] save(PlayerLook look) {
		PacketWriter writer = new PacketWriter(64);
		writer.writeVarInt(SAVE_VERSION).writeString(look.getModel() == null ? "" : look.getModel(), MAX_MODEL_NAME_LENGTH);
		toProtocol(look, 1).write(writer);
		return writer.toByteArray();
	}

	static PlayerLook load(byte[] data) {
		try {
			PacketReader reader = new PacketReader(data);

			if (reader.readVarInt() != SAVE_VERSION) {
				return null;
			}

			String model = reader.readString(MAX_MODEL_NAME_LENGTH);
			me.skaffy.protocol.playerlooks.PlayerLook look = me.skaffy.protocol.playerlooks.PlayerLook.read(reader);
			reader.expectEnd();
			return fromProtocol(look, model);
		} catch (ProtocolException | IllegalArgumentException | IllegalStateException e) {
			return null;
		}
	}

	private static PlayerLook fromProtocol(me.skaffy.protocol.playerlooks.PlayerLook look, String model) {
		PlayerLook.Builder builder = switch (look.form()) {
			case me.skaffy.protocol.playerlooks.PlayerLook.PlayerModel player -> {
				PlayerLook.Builder b = PlayerLook.player().skin(skin(player.skin())).arms(ArmType.valueOf(player.arms().name())).cape(cape(player.cape()));

				if (player.elytra() != null) {
					b.elytra(player.elytra());
				}

				for (SkinLayer layer : SkinLayer.values()) {
					int bit = 1 << layer.ordinal();

					if ((player.forcedLayers() & bit) != 0) {
						b.layer(layer, (player.shownLayers() & bit) != 0);
					}
				}

				for (BodyPart part : BodyPart.values()) {
					if ((player.hiddenParts() & 1 << part.ordinal()) != 0) {
						b.hide(part);
					}
				}

				yield b;
			}
			case me.skaffy.protocol.playerlooks.PlayerLook.EntityForm entity -> PlayerLook.entity(entity.type()).data(entity.data());
			case me.skaffy.protocol.playerlooks.PlayerLook.CustomModel ignored -> PlayerLook.model(model);
		};

		if (look.hitbox() != null) {
			if (look.hitbox().eyeHeight() != null) {
				builder.hitbox(look.hitbox().width(), look.hitbox().height(), look.hitbox().eyeHeight());
			} else {
				builder.hitbox(look.hitbox().width(), look.hitbox().height());
			}
		}

		return builder.scale(look.scale()).ownHitbox(look.ownHitbox()).showToSelf(look.showToSelf()).build();
	}

	private static LookSkin skin(me.skaffy.protocol.playerlooks.PlayerLook.Skin skin) {
		return switch (skin) {
			case me.skaffy.protocol.playerlooks.PlayerLook.OwnSkin ignored -> LookSkin.own();
			case me.skaffy.protocol.playerlooks.PlayerLook.AssetSkin asset -> LookSkin.asset(asset.asset());
			case me.skaffy.protocol.playerlooks.PlayerLook.ProfileSkin profile -> LookSkin.of(profile(profile.profile()));
		};
	}

	private static LookCape cape(me.skaffy.protocol.playerlooks.PlayerLook.Cape cape) {
		return switch (cape) {
			case me.skaffy.protocol.playerlooks.PlayerLook.OwnCape ignored -> LookCape.own();
			case me.skaffy.protocol.playerlooks.PlayerLook.NoCape ignored -> LookCape.none();
			case me.skaffy.protocol.playerlooks.PlayerLook.AssetCape asset -> LookCape.asset(asset.asset());
			case me.skaffy.protocol.playerlooks.PlayerLook.ProfileCape profile -> LookCape.of(profile(profile.profile()));
		};
	}

	private static LookProfile profile(me.skaffy.protocol.playerlooks.PlayerLook.Profile profile) {
		return new LookProfile(profile.id(), profile.name(), profile.value(), profile.signature());
	}
}
