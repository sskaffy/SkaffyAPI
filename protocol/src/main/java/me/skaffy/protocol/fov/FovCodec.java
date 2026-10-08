package me.skaffy.protocol.fov;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.fov.FovPacket.AnimateFov;
import me.skaffy.protocol.fov.FovPacket.FovInfo;
import me.skaffy.protocol.fov.FovPacket.Options;
import me.skaffy.protocol.fov.FovPacket.QueryFov;
import me.skaffy.protocol.fov.FovPacket.ResetFov;
import me.skaffy.protocol.fov.FovPacket.SetFov;
import me.skaffy.protocol.fov.FovPacket.SetSettings;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class FovCodec {
	public static final String FEATURE = "fov";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int SET_FOV = 0;
	public static final int ANIMATE_FOV = 1;
	public static final int RESET_FOV = 2;
	public static final int SET_SETTINGS = 3;
	public static final int QUERY_FOV = 4;

	public static final int FOV_INFO = 0;

	private FovCodec() {
	}

	public static byte[] encode(FovPacket packet) {
		PacketWriter writer = new PacketWriter(32);

		switch (packet) {
			case SetFov set -> {
				writer.writeVarInt(SET_FOV);
				set.fov().write(writer);
				writeOptions(writer, set.options());
			}
			case AnimateFov animate -> {
				writer.writeVarInt(ANIMATE_FOV);
				writer.writeBoolean(animate.from() != null);

				if (animate.from() != null) {
					animate.from().write(writer);
				}

				animate.to().write(writer);
				writer.writeVarInt(animate.durationMillis());
				writer.writeByte(animate.easing().ordinal());
				writeOptions(writer, animate.options());
			}
			case ResetFov reset -> writer.writeVarInt(RESET_FOV).writeVarInt(reset.durationMillis()).writeByte(reset.easing().ordinal());
			case SetSettings settings -> {
				writer.writeVarInt(SET_SETTINGS).writeBoolean(settings.fov() != null);

				if (settings.fov() != null) {
					writer.writeVarInt(settings.fov());
				}

				writer.writeBoolean(settings.effectScale() != null);

				if (settings.effectScale() != null) {
					writer.writeFloat(settings.effectScale());
				}
			}
			case QueryFov query -> writer.writeVarInt(QUERY_FOV).writeVarInt(query.requestId());
			case FovInfo info -> writer.writeVarInt(FOV_INFO)
					.writeVarInt(info.requestId())
					.writeVarInt(info.fovSetting())
					.writeFloat(info.effectScale())
					.writeFloat(info.currentFov())
					.writeBoolean(info.serverFovActive());
		}

		return writer.toByteArray();
	}

	public static FovPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		FovPacket packet = switch (id) {
			case SET_FOV -> new SetFov(FovValue.read(reader), readOptions(reader));
			case ANIMATE_FOV -> {
				FovValue from = reader.readBoolean() ? FovValue.read(reader) : null;
				FovValue to = FovValue.read(reader);
				int duration = reader.readVarInt();
				FovEasing easing = reader.readEnum(FovEasing.values(), "easing");
				yield new AnimateFov(from, to, duration, easing, readOptions(reader));
			}
			case RESET_FOV -> new ResetFov(reader.readVarInt(), reader.readEnum(FovEasing.values(), "easing"));
			case SET_SETTINGS -> new SetSettings(reader.readBoolean() ? reader.readVarInt() : null, reader.readBoolean() ? reader.readFloat() : null);
			case QUERY_FOV -> new QueryFov(reader.readVarInt());
			default -> throw new ProtocolException("Unknown clientbound fov packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static FovPacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		if (id != FOV_INFO) {
			throw new ProtocolException("Unknown serverbound fov packet " + id);
		}

		FovInfo info = new FovInfo(reader.readVarInt(), reader.readVarInt(), reader.readFloat(), reader.readFloat(), reader.readBoolean());
		reader.expectEnd();
		return info;
	}

	private static void writeOptions(PacketWriter writer, Options options) {
		writer.writeBoolean(options.vanillaEffects()).writeBoolean(options.zoomHands()).writeBoolean(options.scaleSensitivity());
	}

	private static Options readOptions(PacketReader reader) {
		return new Options(reader.readBoolean(), reader.readBoolean(), reader.readBoolean());
	}
}
