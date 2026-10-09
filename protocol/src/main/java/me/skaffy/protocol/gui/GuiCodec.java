package me.skaffy.protocol.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.gui.GuiPacket.Call;
import me.skaffy.protocol.gui.GuiPacket.CallResult;
import me.skaffy.protocol.gui.GuiPacket.Close;
import me.skaffy.protocol.gui.GuiPacket.CloseReason;
import me.skaffy.protocol.gui.GuiPacket.Closed;
import me.skaffy.protocol.gui.GuiPacket.DefineFonts;
import me.skaffy.protocol.gui.GuiPacket.Error;
import me.skaffy.protocol.gui.GuiPacket.ErrorKind;
import me.skaffy.protocol.gui.GuiPacket.FileChunk;
import me.skaffy.protocol.gui.GuiPacket.FontDefinition;
import me.skaffy.protocol.gui.GuiPacket.HideHud;
import me.skaffy.protocol.gui.GuiPacket.HudHidden;
import me.skaffy.protocol.gui.GuiPacket.HudPart;
import me.skaffy.protocol.gui.GuiPacket.HudShown;
import me.skaffy.protocol.gui.GuiPacket.Log;
import me.skaffy.protocol.gui.GuiPacket.LogLevel;
import me.skaffy.protocol.gui.GuiPacket.Message;
import me.skaffy.protocol.gui.GuiPacket.Open;
import me.skaffy.protocol.gui.GuiPacket.OpenLink;
import me.skaffy.protocol.gui.GuiPacket.Opened;
import me.skaffy.protocol.gui.GuiPacket.RemoveFile;
import me.skaffy.protocol.gui.GuiPacket.ReplaceMethod;
import me.skaffy.protocol.gui.GuiPacket.SetHudPart;
import me.skaffy.protocol.gui.GuiPacket.ShowHud;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class GuiCodec {
	public static final String FEATURE = "gui";
	public static final int VERSION = 1;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int FILE_CHUNK = 0;
	public static final int REMOVE_FILE = 1;
	public static final int DEFINE_FONTS = 2;
	public static final int OPEN = 3;
	public static final int CLOSE = 4;
	public static final int CALL = 5;
	public static final int REPLACE_METHOD = 6;
	public static final int OPEN_LINK = 7;
	public static final int SHOW_HUD = 8;
	public static final int HIDE_HUD = 9;
	public static final int SET_HUD_PART = 10;

	public static final int OPENED = 0;
	public static final int CLOSED = 1;
	public static final int MESSAGE = 2;
	public static final int CALL_RESULT = 3;
	public static final int ERROR = 4;
	public static final int LOG = 5;
	public static final int HUD_SHOWN = 6;
	public static final int HUD_HIDDEN = 7;

	public static final int MAX_FILE_SIZE = 2 * 1024 * 1024;
	public static final long MAX_TOTAL_SIZE = 32L * 1024 * 1024;
	public static final int MAX_CHUNK_SIZE = 256 * 1024;
	public static final int MAX_CLASS_NAME = 128;
	public static final int MAX_METHOD_NAME = 64;
	public static final int MAX_SOURCE_LENGTH = 131_072;
	public static final int MAX_URL_LENGTH = 2048;
	public static final int MAX_FONTS = 64;
	public static final int MAX_VALUE_DEPTH = 32;
	public static final int MAX_VALUES = 100_000;
	public static final int MAX_STRING = 32767;
	public static final int MAX_ERROR_LENGTH = 8192;
	public static final int MAX_LOG_LENGTH = 4096;

	private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
	private static final Pattern METHOD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	private static final int NULL = 0;
	private static final int FALSE = 1;
	private static final int TRUE = 2;
	private static final int INT = 3;
	private static final int LONG = 4;
	private static final int DOUBLE = 5;
	private static final int STRING = 6;
	private static final int LIST = 7;
	private static final int MAP = 8;

	private GuiCodec() {
	}

	public static boolean isValidClassName(String name) {
		return name != null && name.length() <= MAX_CLASS_NAME && CLASS_NAME.matcher(name).matches();
	}

	static void checkClassName(String name) {
		if (!isValidClassName(name)) {
			throw new ProtocolException("Invalid class name " + name + " (it needs a package, like shop.Shop)");
		}
	}

	static void checkMethodName(String name) {
		if (name == null || name.length() > MAX_METHOD_NAME || !METHOD_NAME.matcher(name).matches()) {
			throw new ProtocolException("Invalid method name " + name);
		}
	}

	public static byte[] encode(GuiPacket packet) {
		PacketWriter writer = new PacketWriter(64);

		switch (packet) {
			case FileChunk chunk -> writer.writeVarInt(FILE_CHUNK)
					.writeString(chunk.className(), MAX_CLASS_NAME)
					.writeVarInt(chunk.totalSize())
					.writeVarInt(chunk.offset())
					.writeBytes(chunk.data());
			case RemoveFile remove -> writer.writeVarInt(REMOVE_FILE).writeString(remove.className(), MAX_CLASS_NAME);
			case DefineFonts define -> {
				writer.writeVarInt(DEFINE_FONTS);
				writer.writeList(define.fonts(), (out, font) -> out.writeString(font.name(), 32)
						.writeString(font.asset(), Protocol.MAX_ASSET_ID_LENGTH)
						.writeFloat(font.size())
						.writeFloat(font.oversample())
						.writeFloat(font.shiftX())
						.writeFloat(font.shiftY()));
			}
			case Open open -> {
				writer.writeVarInt(OPEN).writeString(open.className(), MAX_CLASS_NAME).writeString(open.method(), MAX_METHOD_NAME);
				writeValues(writer, open.args());
			}
			case Close ignored -> writer.writeVarInt(CLOSE);
			case Call call -> {
				writer.writeVarInt(CALL).writeVarInt(call.callId()).writeString(call.className(), MAX_CLASS_NAME).writeString(call.method(), MAX_METHOD_NAME);
				writeValues(writer, call.args());
			}
			case ReplaceMethod replace -> writer.writeVarInt(REPLACE_METHOD).writeString(replace.className(), MAX_CLASS_NAME).writeString(replace.source(), MAX_SOURCE_LENGTH);
			case OpenLink link -> writer.writeVarInt(OPEN_LINK).writeString(link.url(), MAX_URL_LENGTH);
			case ShowHud show -> {
				writer.writeVarInt(SHOW_HUD).writeString(show.className(), MAX_CLASS_NAME).writeString(show.method(), MAX_METHOD_NAME);
				writeValues(writer, show.args());
				writer.writeInt(show.order());
			}
			case HideHud hide -> writer.writeVarInt(HIDE_HUD).writeString(hide.className(), MAX_CLASS_NAME);
			case SetHudPart part -> writer.writeVarInt(SET_HUD_PART).writeByte(part.part().ordinal()).writeBoolean(part.visible())
					.writeFloat(part.x()).writeFloat(part.y()).writeFloat(part.scale());
			case HudShown shown -> writer.writeVarInt(HUD_SHOWN).writeString(shown.className(), MAX_CLASS_NAME).writeString(shown.method(), MAX_METHOD_NAME);
			case HudHidden hidden -> writer.writeVarInt(HUD_HIDDEN).writeString(hidden.className(), MAX_CLASS_NAME).writeByte(hidden.reason().ordinal());
			case Opened opened -> writer.writeVarInt(OPENED).writeString(opened.className(), MAX_CLASS_NAME).writeString(opened.method(), MAX_METHOD_NAME);
			case Closed closed -> writer.writeVarInt(CLOSED).writeString(closed.className(), MAX_CLASS_NAME).writeByte(closed.reason().ordinal());
			case Message message -> {
				writer.writeVarInt(MESSAGE).writeString(message.className(), MAX_CLASS_NAME).writeString(message.name(), MAX_METHOD_NAME);
				writeValues(writer, message.values());
			}
			case CallResult result -> {
				writer.writeVarInt(CALL_RESULT).writeVarInt(result.callId()).writeBoolean(result.ok());

				if (result.ok()) {
					writeValue(writer, result.value(), 0);
				} else {
					writer.writeString(truncate(result.error(), MAX_ERROR_LENGTH), MAX_ERROR_LENGTH);
				}
			}
			case Error error -> writer.writeVarInt(ERROR)
					.writeString(error.className(), MAX_CLASS_NAME)
					.writeByte(error.kind().ordinal())
					.writeString(truncate(error.message(), MAX_ERROR_LENGTH), MAX_ERROR_LENGTH);
			case Log log -> writer.writeVarInt(LOG)
					.writeString(log.className(), MAX_CLASS_NAME)
					.writeByte(log.level().ordinal())
					.writeString(truncate(log.message(), MAX_LOG_LENGTH), MAX_LOG_LENGTH);
		}

		return writer.toByteArray();
	}

	public static String truncate(String text, int maxLength) {
		if (text == null) {
			return "";
		}

		if (text.length() <= maxLength) {
			return text;
		}

		int end = maxLength - 3;

		if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
			end--;
		}

		return text.substring(0, Math.max(0, end)) + "...";
	}

	public static GuiPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();
		GuiPacket packet = switch (id) {
			case FILE_CHUNK -> new FileChunk(reader.readString(MAX_CLASS_NAME), reader.readVarInt(), reader.readVarInt(), reader.readBytes(MAX_CHUNK_SIZE));
			case REMOVE_FILE -> new RemoveFile(reader.readString(MAX_CLASS_NAME));
			case DEFINE_FONTS -> new DefineFonts(reader.readList(MAX_FONTS, in -> new FontDefinition(
					in.readString(32), Protocol.requireAssetId(in.readString(Protocol.MAX_ASSET_ID_LENGTH)),
					in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat())));
			case OPEN -> new Open(reader.readString(MAX_CLASS_NAME), reader.readString(MAX_METHOD_NAME), readValues(reader));
			case CLOSE -> new Close();
			case CALL -> new Call(reader.readVarInt(), reader.readString(MAX_CLASS_NAME), reader.readString(MAX_METHOD_NAME), readValues(reader));
			case REPLACE_METHOD -> new ReplaceMethod(reader.readString(MAX_CLASS_NAME), reader.readString(MAX_SOURCE_LENGTH));
			case OPEN_LINK -> new OpenLink(reader.readString(MAX_URL_LENGTH));
			case SHOW_HUD -> new ShowHud(reader.readString(MAX_CLASS_NAME), reader.readString(MAX_METHOD_NAME), readValues(reader), reader.readInt());
			case HIDE_HUD -> new HideHud(reader.readString(MAX_CLASS_NAME));
			case SET_HUD_PART -> new SetHudPart(reader.readEnum(HudPart.values(), "HUD part"), reader.readBoolean(), reader.readFloat(), reader.readFloat(), reader.readFloat());
			default -> throw new ProtocolException("Unknown clientbound gui packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static GuiPacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();
		GuiPacket packet = switch (id) {
			case OPENED -> new Opened(reader.readString(MAX_CLASS_NAME), reader.readString(MAX_METHOD_NAME));
			case CLOSED -> new Closed(reader.readString(MAX_CLASS_NAME), reader.readEnum(CloseReason.values(), "close reason"));
			case MESSAGE -> new Message(reader.readString(MAX_CLASS_NAME), reader.readString(MAX_METHOD_NAME), readValues(reader));
			case CALL_RESULT -> {
				int callId = reader.readVarInt();
				boolean ok = reader.readBoolean();
				yield ok ? new CallResult(callId, true, readValue(reader, 0, new int[1]), null)
						: new CallResult(callId, false, null, reader.readString(MAX_ERROR_LENGTH));
			}
			case ERROR -> new Error(reader.readString(MAX_CLASS_NAME), reader.readEnum(ErrorKind.values(), "error kind"), reader.readString(MAX_ERROR_LENGTH));
			case LOG -> new Log(reader.readString(MAX_CLASS_NAME), reader.readEnum(LogLevel.values(), "log level"), reader.readString(MAX_LOG_LENGTH));
			case HUD_SHOWN -> new HudShown(reader.readString(MAX_CLASS_NAME), reader.readString(MAX_METHOD_NAME));
			case HUD_HIDDEN -> new HudHidden(reader.readString(MAX_CLASS_NAME), reader.readEnum(CloseReason.values(), "close reason"));
			default -> throw new ProtocolException("Unknown serverbound gui packet " + id);
		};

		reader.expectEnd();
		return packet;
	}


	public static void writeValues(PacketWriter writer, List<?> values) {
		writer.writeVarInt(values.size());

		for (Object value : values) {
			writeValue(writer, value, 0);
		}
	}

	public static void writeValue(PacketWriter writer, Object value, int depth) {
		if (depth > MAX_VALUE_DEPTH) {
			throw new ProtocolException("Values are nested more than " + MAX_VALUE_DEPTH + " deep");
		}

		switch (value) {
			case null -> writer.writeByte(NULL);
			case Boolean bool -> writer.writeByte(bool ? TRUE : FALSE);
			case Integer number -> writer.writeByte(INT).writeInt(number);
			case Short number -> writer.writeByte(INT).writeInt(number);
			case Byte number -> writer.writeByte(INT).writeInt(number);
			case Long number -> writer.writeByte(LONG).writeLong(number);
			case Number number -> writer.writeByte(DOUBLE).writeDouble(number.doubleValue());
			case Character character -> writer.writeByte(STRING).writeString(String.valueOf(character), MAX_STRING);
			case String text -> writer.writeByte(STRING).writeString(text, MAX_STRING);
			case List<?> list -> {
				writer.writeByte(LIST).writeVarInt(list.size());

				for (Object element : list) {
					writeValue(writer, element, depth + 1);
				}
			}
			case Map<?, ?> map -> {
				writer.writeByte(MAP).writeVarInt(map.size());

				for (Map.Entry<?, ?> entry : map.entrySet()) {
					writeValue(writer, entry.getKey(), depth + 1);
					writeValue(writer, entry.getValue(), depth + 1);
				}
			}
			case Enum<?> constant -> writer.writeByte(STRING).writeString(constant.name(), MAX_STRING);
			default -> throw new ProtocolException("Can't send a " + value.getClass().getSimpleName() + " (only null, booleans, numbers, strings, lists and maps)");
		}
	}

	public static List<Object> readValues(PacketReader reader) {
		int[] budget = new int[1];
		int count = reader.readVarInt(0, MAX_VALUES);
		List<Object> values = new ArrayList<>(Math.min(count, 256));

		for (int i = 0; i < count; i++) {
			values.add(readValue(reader, 0, budget));
		}

		return Collections.unmodifiableList(values);
	}

	public static Object readValue(PacketReader reader) {
		return readValue(reader, 0, new int[1]);
	}

	private static Object readValue(PacketReader reader, int depth, int[] budget) {
		if (depth > MAX_VALUE_DEPTH) {
			throw new ProtocolException("Values are nested more than " + MAX_VALUE_DEPTH + " deep");
		}

		if (++budget[0] > MAX_VALUES) {
			throw new ProtocolException("More than " + MAX_VALUES + " values");
		}

		int tag = reader.readUnsignedByte();
		return switch (tag) {
			case NULL -> null;
			case FALSE -> false;
			case TRUE -> true;
			case INT -> reader.readInt();
			case LONG -> reader.readLong();
			case DOUBLE -> reader.readDouble();
			case STRING -> reader.readString(MAX_STRING);
			case LIST -> {
				int count = reader.readVarInt(0, MAX_VALUES);
				List<Object> list = new ArrayList<>(Math.min(count, 256));

				for (int i = 0; i < count; i++) {
					list.add(readValue(reader, depth + 1, budget));
				}

				yield list;
			}
			case MAP -> {
				int count = reader.readVarInt(0, MAX_VALUES);
				Map<Object, Object> map = new LinkedHashMap<>();

				for (int i = 0; i < count; i++) {
					Object key = readValue(reader, depth + 1, budget);
					map.put(key, readValue(reader, depth + 1, budget));
				}

				yield map;
			}
			default -> throw new ProtocolException("Unknown value tag " + tag);
		};
	}
}
