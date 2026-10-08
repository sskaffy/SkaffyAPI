package me.skaffy.protocol.entitymodels;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.DefineModels;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.PlayAnimation;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.SetEntityModel;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.SetVariables;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.StopAnimation;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class EntityModelsCodec {
	public static final String FEATURE = "entity_models";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);
	public static final int MAX_MODELS = 4096;

	public static final int DEFINE_MODELS = 0;
	public static final int SET_ENTITY_MODEL = 1;
	public static final int PLAY_ANIMATION = 2;
	public static final int STOP_ANIMATION = 3;
	public static final int SET_VARIABLES = 4;

	private EntityModelsCodec() {
	}

	public static byte[] encode(EntityModelsPacket packet) {
		PacketWriter writer = new PacketWriter(64);

		switch (packet) {
			case DefineModels define -> writer.writeVarInt(DEFINE_MODELS).writeList(define.models(), (w, model) -> model.write(w));
			case SetEntityModel set -> writer.writeVarInt(SET_ENTITY_MODEL).writeVarInt(set.entity()).writeVarInt(set.model());
			case PlayAnimation play -> writer.writeVarInt(PLAY_ANIMATION)
					.writeVarInt(play.entity())
					.writeVarInt(play.animation())
					.writeByte(play.mode().ordinal())
					.writeFloat(play.speed())
					.writeFloat(play.start())
					.writeFloat(play.fadeIn());
			case StopAnimation stop -> writer.writeVarInt(STOP_ANIMATION).writeVarInt(stop.entity()).writeVarInt(stop.animation()).writeFloat(stop.fadeOut());
			case SetVariables set -> {
				writer.writeVarInt(SET_VARIABLES).writeVarInt(set.entity());
				writeVariables(writer, set.variables());
			}
		}

		if (writer.size() > Protocol.MAX_CLIENTBOUND_PAYLOAD) {
			throw new ProtocolException("Packet is " + writer.size() + " bytes, max is " + Protocol.MAX_CLIENTBOUND_PAYLOAD);
		}

		return writer.toByteArray();
	}

	public static EntityModelsPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		EntityModelsPacket packet = switch (id) {
			case DEFINE_MODELS -> new DefineModels(reader.readList(MAX_MODELS, ModelDefinition::read));
			case SET_ENTITY_MODEL -> new SetEntityModel(reader.readVarInt(), reader.readVarInt());
			case PLAY_ANIMATION -> new PlayAnimation(reader.readVarInt(), reader.readVarInt(), reader.readEnum(PlayAnimation.Mode.values(), "animation mode"), reader.readFloat(), reader.readFloat(),
					reader.readFloat());
			case STOP_ANIMATION -> new StopAnimation(reader.readVarInt(), reader.readVarInt(), reader.readFloat());
			case SET_VARIABLES -> new SetVariables(reader.readVarInt(), readVariables(reader));
			default -> throw new ProtocolException("Unknown clientbound entity models packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static void writeVariables(PacketWriter writer, java.util.List<EntityModelsPacket.Variable> variables) {
		writer.writeList(variables, (w, variable) -> w.writeString(variable.name(), EntityModelsPacket.Variable.MAX_NAME).writeFloat(variable.value()));
	}

	public static java.util.List<EntityModelsPacket.Variable> readVariables(PacketReader reader) {
		return reader.readList(SetVariables.MAX_VARIABLES, r -> new EntityModelsPacket.Variable(r.readString(EntityModelsPacket.Variable.MAX_NAME), r.readFloat()));
	}
}
