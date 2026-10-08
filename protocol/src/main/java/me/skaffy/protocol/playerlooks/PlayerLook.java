package me.skaffy.protocol.playerlooks;

import java.util.UUID;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record PlayerLook(Form form, float scale, Hitbox hitbox, boolean ownHitbox, boolean showToSelf) {
	public static final float MAX_SIZE = 256;
	public static final int MAX_TYPE_LENGTH = 128;
	public static final int MAX_DATA_LENGTH = 16384;
	public static final int MAX_PROFILE_NAME_LENGTH = 16;
	public static final int MAX_SKIN_LENGTH = 4096;

	public static final int LAYER_HAT = 1;
	public static final int LAYER_JACKET = 2;
	public static final int LAYER_LEFT_SLEEVE = 4;
	public static final int LAYER_RIGHT_SLEEVE = 8;
	public static final int LAYER_LEFT_PANTS = 16;
	public static final int LAYER_RIGHT_PANTS = 32;

	public static final int PART_HEAD = 1;
	public static final int PART_BODY = 2;
	public static final int PART_RIGHT_ARM = 4;
	public static final int PART_LEFT_ARM = 8;
	public static final int PART_RIGHT_LEG = 16;
	public static final int PART_LEFT_LEG = 32;

	private static final int FORM_PLAYER = 0;
	private static final int FORM_ENTITY = 1;
	private static final int FORM_MODEL = 2;

	public PlayerLook {
		if (form == null) {
			throw new ProtocolException("A look needs a form");
		}

		checkSize("Scale", scale);
	}

	public sealed interface Form permits PlayerModel, EntityForm, CustomModel {
	}

	public record PlayerModel(Skin skin, Arms arms, Cape cape, String elytra, int forcedLayers, int shownLayers, int hiddenParts) implements Form {
		public PlayerModel {
			if (skin == null || arms == null || cape == null) {
				throw new ProtocolException("A player model needs a skin, arms and a cape");
			}

			if (elytra != null) {
				Protocol.requireAssetId(elytra);
			}

			if ((forcedLayers & ~63) != 0 || (shownLayers & ~forcedLayers) != 0 || (hiddenParts & ~63) != 0) {
				throw new ProtocolException("Bad skin layer or body part bits");
			}
		}
	}

	public record EntityForm(String type, String data) implements Form {
		public EntityForm {
			if (type == null || type.isEmpty() || type.length() > MAX_TYPE_LENGTH) {
				throw new ProtocolException("Entity type must be 1 to " + MAX_TYPE_LENGTH + " characters");
			}

			if (data == null || data.length() > MAX_DATA_LENGTH) {
				throw new ProtocolException("Entity data must be at most " + MAX_DATA_LENGTH + " characters");
			}
		}
	}

	public record CustomModel(int model) implements Form {
		public CustomModel {
			if (model < 1) {
				throw new ProtocolException("Custom model numbers start at 1, got " + model);
			}
		}
	}

	public sealed interface Skin permits OwnSkin, AssetSkin, ProfileSkin {
	}

	public record OwnSkin() implements Skin {
	}

	public record AssetSkin(String asset) implements Skin {
		public AssetSkin {
			Protocol.requireAssetId(asset);
		}
	}

	public record ProfileSkin(Profile profile) implements Skin {
	}

	public sealed interface Cape permits OwnCape, NoCape, AssetCape, ProfileCape {
	}

	public record OwnCape() implements Cape {
	}

	public record NoCape() implements Cape {
	}

	public record AssetCape(String asset) implements Cape {
		public AssetCape {
			Protocol.requireAssetId(asset);
		}
	}

	public record ProfileCape(Profile profile) implements Cape {
	}

	public record Profile(UUID id, String name, String value, String signature) {
		public Profile {
			if (id == null && name == null && value == null) {
				throw new ProtocolException("A profile needs a UUID, a name or a skin value");
			}

			if (name != null && (name.isEmpty() || name.length() > MAX_PROFILE_NAME_LENGTH)) {
				throw new ProtocolException("Profile name must be 1 to " + MAX_PROFILE_NAME_LENGTH + " characters");
			}

			if (value != null && (value.isEmpty() || value.length() > MAX_SKIN_LENGTH)) {
				throw new ProtocolException("Skin value must be 1 to " + MAX_SKIN_LENGTH + " characters");
			}

			if (signature != null && (value == null || signature.isEmpty() || signature.length() > MAX_SKIN_LENGTH)) {
				throw new ProtocolException("Skin signature must be 1 to " + MAX_SKIN_LENGTH + " characters, and only with a value");
			}
		}

		static Profile read(PacketReader reader) {
			UUID id = reader.readBoolean() ? reader.readUuid() : null;
			String name = reader.readBoolean() ? reader.readString(MAX_PROFILE_NAME_LENGTH) : null;
			String value = null;
			String signature = null;

			if (reader.readBoolean()) {
				value = reader.readString(MAX_SKIN_LENGTH);
				signature = reader.readBoolean() ? reader.readString(MAX_SKIN_LENGTH) : null;
			}

			return new Profile(id, name, value, signature);
		}

		void write(PacketWriter writer) {
			writer.writeBoolean(id != null);

			if (id != null) {
				writer.writeUuid(id);
			}

			writer.writeBoolean(name != null);

			if (name != null) {
				writer.writeString(name, MAX_PROFILE_NAME_LENGTH);
			}

			writer.writeBoolean(value != null);

			if (value != null) {
				writer.writeString(value, MAX_SKIN_LENGTH);
				writer.writeBoolean(signature != null);

				if (signature != null) {
					writer.writeString(signature, MAX_SKIN_LENGTH);
				}
			}
		}
	}

	public enum Arms {
		FROM_SKIN,
		WIDE,
		SLIM
	}

	public record Hitbox(float width, float height, Float eyeHeight) {
		public Hitbox {
			checkSize("Hitbox width", width);
			checkSize("Hitbox height", height);

			if (eyeHeight != null && !(eyeHeight >= 0 && eyeHeight <= height)) {
				throw new ProtocolException("Eye height must be 0 to the hitbox height, got " + eyeHeight);
			}
		}
	}

	private static void checkSize(String what, float value) {
		if (!(value > 0 && value <= MAX_SIZE)) {
			throw new ProtocolException(what + " must be above 0 and at most " + MAX_SIZE + ", got " + value);
		}
	}

	public static PlayerLook read(PacketReader reader) {
		int kind = reader.readUnsignedByte();

		Form form = switch (kind) {
			case FORM_PLAYER -> new PlayerModel(readSkin(reader), reader.readEnum(Arms.values(), "arms"), readCape(reader),
					reader.readBoolean() ? reader.readString(Protocol.MAX_ASSET_ID_LENGTH) : null,
					reader.readUnsignedByte(), reader.readUnsignedByte(), reader.readUnsignedByte());
			case FORM_ENTITY -> new EntityForm(reader.readString(MAX_TYPE_LENGTH), reader.readString(MAX_DATA_LENGTH));
			case FORM_MODEL -> new CustomModel(reader.readVarInt());
			default -> throw new ProtocolException("Unknown look form " + kind);
		};

		float scale = reader.readFloat();
		Hitbox hitbox = null;

		if (reader.readBoolean()) {
			float width = reader.readFloat();
			float height = reader.readFloat();
			hitbox = new Hitbox(width, height, reader.readBoolean() ? reader.readFloat() : null);
		}

		return new PlayerLook(form, scale, hitbox, reader.readBoolean(), reader.readBoolean());
	}

	public void write(PacketWriter writer) {
		switch (form) {
			case PlayerModel model -> {
				writer.writeByte(FORM_PLAYER);
				writeSkin(writer, model.skin());
				writer.writeByte(model.arms().ordinal());
				writeCape(writer, model.cape());
				writer.writeBoolean(model.elytra() != null);

				if (model.elytra() != null) {
					writer.writeString(model.elytra(), Protocol.MAX_ASSET_ID_LENGTH);
				}

				writer.writeByte(model.forcedLayers()).writeByte(model.shownLayers()).writeByte(model.hiddenParts());
			}
			case EntityForm entity -> writer.writeByte(FORM_ENTITY).writeString(entity.type(), MAX_TYPE_LENGTH).writeString(entity.data(), MAX_DATA_LENGTH);
			case CustomModel model -> writer.writeByte(FORM_MODEL).writeVarInt(model.model());
		}

		writer.writeFloat(scale).writeBoolean(hitbox != null);

		if (hitbox != null) {
			writer.writeFloat(hitbox.width()).writeFloat(hitbox.height()).writeBoolean(hitbox.eyeHeight() != null);

			if (hitbox.eyeHeight() != null) {
				writer.writeFloat(hitbox.eyeHeight());
			}
		}

		writer.writeBoolean(ownHitbox).writeBoolean(showToSelf);
	}

	private static Skin readSkin(PacketReader reader) {
		int source = reader.readUnsignedByte();

		return switch (source) {
			case 0 -> new OwnSkin();
			case 1 -> new AssetSkin(reader.readString(Protocol.MAX_ASSET_ID_LENGTH));
			case 2 -> new ProfileSkin(Profile.read(reader));
			default -> throw new ProtocolException("Unknown skin source " + source);
		};
	}

	private static void writeSkin(PacketWriter writer, Skin skin) {
		switch (skin) {
			case OwnSkin ignored -> writer.writeByte(0);
			case AssetSkin asset -> writer.writeByte(1).writeString(asset.asset(), Protocol.MAX_ASSET_ID_LENGTH);
			case ProfileSkin profile -> {
				writer.writeByte(2);
				profile.profile().write(writer);
			}
		}
	}

	private static Cape readCape(PacketReader reader) {
		int source = reader.readUnsignedByte();

		return switch (source) {
			case 0 -> new OwnCape();
			case 1 -> new NoCape();
			case 2 -> new AssetCape(reader.readString(Protocol.MAX_ASSET_ID_LENGTH));
			case 3 -> new ProfileCape(Profile.read(reader));
			default -> throw new ProtocolException("Unknown cape source " + source);
		};
	}

	private static void writeCape(PacketWriter writer, Cape cape) {
		switch (cape) {
			case OwnCape ignored -> writer.writeByte(0);
			case NoCape ignored -> writer.writeByte(1);
			case AssetCape asset -> writer.writeByte(2).writeString(asset.asset(), Protocol.MAX_ASSET_ID_LENGTH);
			case ProfileCape profile -> {
				writer.writeByte(3);
				profile.profile().write(writer);
			}
		}
	}
}
