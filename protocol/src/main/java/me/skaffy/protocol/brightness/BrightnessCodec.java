package me.skaffy.protocol.brightness;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.brightness.BrightnessPacket.AnimateBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.BrightnessInfo;
import me.skaffy.protocol.brightness.BrightnessPacket.Options;
import me.skaffy.protocol.brightness.BrightnessPacket.QueryBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.ResetBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.SetBrightness;
import me.skaffy.protocol.brightness.BrightnessPacket.SetSetting;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class BrightnessCodec {
	public static final String FEATURE = "brightness";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int SET_BRIGHTNESS = 0;
	public static final int ANIMATE_BRIGHTNESS = 1;
	public static final int RESET_BRIGHTNESS = 2;
	public static final int SET_SETTING = 3;
	public static final int QUERY_BRIGHTNESS = 4;

	public static final int BRIGHTNESS_INFO = 0;

	private BrightnessCodec() {
	}

	public static byte[] encode(BrightnessPacket packet) {
		PacketWriter writer = new PacketWriter(32);

		switch (packet) {
			case SetBrightness set -> {
				writer.writeVarInt(SET_BRIGHTNESS).writeFloat(set.brightness());
				writeOptions(writer, set.options());
			}
			case AnimateBrightness animate -> {
				writer.writeVarInt(ANIMATE_BRIGHTNESS).writeBoolean(animate.from() != null);

				if (animate.from() != null) {
					writer.writeFloat(animate.from());
				}

				writer.writeFloat(animate.to()).writeVarInt(animate.durationMillis()).writeByte(animate.easing().ordinal());
				writeOptions(writer, animate.options());
			}
			case ResetBrightness reset -> writer.writeVarInt(RESET_BRIGHTNESS).writeVarInt(reset.durationMillis()).writeByte(reset.easing().ordinal());
			case SetSetting setting -> writer.writeVarInt(SET_SETTING).writeFloat(setting.brightness());
			case QueryBrightness query -> writer.writeVarInt(QUERY_BRIGHTNESS).writeVarInt(query.requestId());
			case BrightnessInfo info -> writer.writeVarInt(BRIGHTNESS_INFO)
					.writeVarInt(info.requestId())
					.writeFloat(info.setting())
					.writeFloat(info.current())
					.writeBoolean(info.serverBrightnessActive());
		}

		return writer.toByteArray();
	}

	public static BrightnessPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		BrightnessPacket packet = switch (id) {
			case SET_BRIGHTNESS -> new SetBrightness(reader.readFloat(), readOptions(reader));
			case ANIMATE_BRIGHTNESS -> {
				Float from = reader.readBoolean() ? reader.readFloat() : null;
				float to = reader.readFloat();
				int duration = reader.readVarInt();
				Easing easing = reader.readEnum(Easing.values(), "easing");
				yield new AnimateBrightness(from, to, duration, easing, readOptions(reader));
			}
			case RESET_BRIGHTNESS -> new ResetBrightness(reader.readVarInt(), reader.readEnum(Easing.values(), "easing"));
			case SET_SETTING -> new SetSetting(reader.readFloat());
			case QUERY_BRIGHTNESS -> new QueryBrightness(reader.readVarInt());
			default -> throw new ProtocolException("Unknown clientbound brightness packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static BrightnessPacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();

		if (id != BRIGHTNESS_INFO) {
			throw new ProtocolException("Unknown serverbound brightness packet " + id);
		}

		BrightnessInfo info = new BrightnessInfo(reader.readVarInt(), reader.readFloat(), reader.readFloat(), reader.readBoolean());
		reader.expectEnd();
		return info;
	}

	private static void writeOptions(PacketWriter writer, Options options) {
		writer.writeBoolean(options.darknessEffect()).writeBoolean(options.nightVision());
	}

	private static Options readOptions(PacketReader reader) {
		return new Options(reader.readBoolean(), reader.readBoolean());
	}
}
