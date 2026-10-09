package me.skaffy.protocol.entitymodels;

import java.util.List;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public record ModelDefinition(
		String name,
		String geometry,
		String babyGeometry,
		String texture,
		String emissiveTexture,
		boolean translucent,
		float scale,
		List<AnimationRef> animations,
		int idle,
		int walk,
		int attack,
		int hurt,
		int death) {
	public static final int MAX_ANIMATIONS = 256;
	public static final int MAX_ANIMATION_NAME_LENGTH = 128;

	public record AnimationRef(String asset, String name, boolean replacesVanilla) {
		public AnimationRef {
			Protocol.requireAssetId(asset);

			if (name.isEmpty() || name.length() > MAX_ANIMATION_NAME_LENGTH) {
				throw new ProtocolException("Animation name must be 1 to " + MAX_ANIMATION_NAME_LENGTH + " characters");
			}
		}

		static AnimationRef read(PacketReader reader) {
			return new AnimationRef(reader.readString(Protocol.MAX_ASSET_ID_LENGTH), reader.readString(MAX_ANIMATION_NAME_LENGTH), reader.readBoolean());
		}

		void write(PacketWriter writer) {
			writer.writeString(asset, Protocol.MAX_ASSET_ID_LENGTH);
			writer.writeString(name, MAX_ANIMATION_NAME_LENGTH);
			writer.writeBoolean(replacesVanilla);
		}
	}

	public ModelDefinition {
		if (name.isEmpty() || name.length() > 64) {
			throw new ProtocolException("Model name must be 1 to 64 characters");
		}

		Protocol.requireAssetId(geometry);

		if (texture != null) {
			Protocol.requireAssetId(texture);
		}

		if (babyGeometry != null) {
			Protocol.requireAssetId(babyGeometry);
		}

		if (emissiveTexture != null) {
			Protocol.requireAssetId(emissiveTexture);
		}

		if (!(scale > 0) || !Float.isFinite(scale)) {
			throw new ProtocolException("Model scale must be above 0, got " + scale);
		}

		animations = List.copyOf(animations);

		if (animations.size() > MAX_ANIMATIONS) {
			throw new ProtocolException("A model has at most " + MAX_ANIMATIONS + " animations");
		}

		for (int automatic : new int[] {idle, walk, attack, hurt, death}) {
			if (automatic < 0 || automatic > animations.size()) {
				throw new ProtocolException("Automatic animation " + automatic + " isn't one of the model's " + animations.size() + " animations");
			}
		}
	}

	public static ModelDefinition read(PacketReader reader) {
		String name = reader.readString(64);
		String geometry = reader.readString(Protocol.MAX_ASSET_ID_LENGTH);
		String babyGeometry = readOptional(reader);
		String texture = readOptional(reader);
		String emissiveTexture = readOptional(reader);
		boolean translucent = reader.readBoolean();
		float scale = reader.readFloat();
		List<AnimationRef> animations = reader.readList(MAX_ANIMATIONS, AnimationRef::read);
		int idle = reader.readVarInt();
		int walk = reader.readVarInt();
		int attack = reader.readVarInt();
		int hurt = reader.readVarInt();
		int death = reader.readVarInt();
		return new ModelDefinition(name, geometry, babyGeometry, texture, emissiveTexture, translucent, scale, animations, idle, walk, attack, hurt, death);
	}

	public void write(PacketWriter writer) {
		writer.writeString(name, 64);
		writer.writeString(geometry, Protocol.MAX_ASSET_ID_LENGTH);
		writeOptional(writer, babyGeometry);
		writeOptional(writer, texture);
		writeOptional(writer, emissiveTexture);
		writer.writeBoolean(translucent);
		writer.writeFloat(scale);
		writer.writeList(animations, (w, animation) -> animation.write(w));
		writer.writeVarInt(idle);
		writer.writeVarInt(walk);
		writer.writeVarInt(attack);
		writer.writeVarInt(hurt);
		writer.writeVarInt(death);
	}

	private static String readOptional(PacketReader reader) {
		return reader.readBoolean() ? reader.readString(Protocol.MAX_ASSET_ID_LENGTH) : null;
	}

	private static void writeOptional(PacketWriter writer, String value) {
		writer.writeBoolean(value != null);

		if (value != null) {
			writer.writeString(value, Protocol.MAX_ASSET_ID_LENGTH);
		}
	}
}
