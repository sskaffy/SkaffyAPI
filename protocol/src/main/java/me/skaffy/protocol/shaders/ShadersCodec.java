package me.skaffy.protocol.shaders;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.gui.GuiCodec;
import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.shaders.ShadersPacket.ClientShaderMode;
import me.skaffy.protocol.shaders.ShadersPacket.ClientShaderState;
import me.skaffy.protocol.shaders.ShadersPacket.Disable;
import me.skaffy.protocol.shaders.ShadersPacket.DisableAll;
import me.skaffy.protocol.shaders.ShadersPacket.Enable;
import me.skaffy.protocol.shaders.ShadersPacket.Error;
import me.skaffy.protocol.shaders.ShadersPacket.ErrorKind;
import me.skaffy.protocol.shaders.ShadersPacket.FileChunk;
import me.skaffy.protocol.shaders.ShadersPacket.RemoveFile;
import me.skaffy.protocol.shaders.ShadersPacket.SetClientShaders;
import me.skaffy.protocol.shaders.ShadersPacket.SetUniforms;
import me.skaffy.protocol.shaders.ShadersPacket.State;

public final class ShadersCodec {
	public static final String FEATURE = "shaders";
	public static final int VERSION = 2;
	public static final String CHANNEL = Protocol.featureChannel(FEATURE);

	public static final int FILE_CHUNK = 0;
	public static final int REMOVE_FILE = 1;
	public static final int ENABLE = 2;
	public static final int DISABLE = 3;
	public static final int DISABLE_ALL = 4;
	public static final int SET_UNIFORMS = 5;
	public static final int SET_CLIENT_SHADERS = 6;

	public static final int ERROR = 0;
	public static final int STATE = 1;
	public static final int CLIENT_SHADER_STATE = 2;

	public static final int MAX_FILE_SIZE = 512 * 1024;
	public static final long MAX_TOTAL_SIZE = 8L * 1024 * 1024;
	public static final int MAX_CHUNK_SIZE = 256 * 1024;
	public static final int MAX_CLASS_NAME = 128;
	public static final int MAX_UNIFORM_NAME = 64;
	public static final int MAX_UNIFORMS = 256;
	public static final int MAX_ERROR_LENGTH = 8192;

	private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
	private static final Pattern UNIFORM_NAME = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*\\.)?[A-Za-z_][A-Za-z0-9_]*");

	private ShadersCodec() {
	}

	public static boolean isValidClassName(String name) {
		return name != null && name.length() <= MAX_CLASS_NAME && CLASS_NAME.matcher(name).matches();
	}

	public static boolean isReserved(String className) {
		return className.startsWith("skaffy.");
	}

	static void checkClassName(String name) {
		if (!isValidClassName(name)) {
			throw new ProtocolException("Invalid class name " + name + " (it needs a package, like effects.Dream)");
		}
	}

	static void checkUniforms(Map<String, Object> values) {
		if (values.size() > MAX_UNIFORMS) {
			throw new ProtocolException("At most " + MAX_UNIFORMS + " uniforms at once");
		}

		for (String name : values.keySet()) {
			if (name == null || name.length() > MAX_UNIFORM_NAME || !UNIFORM_NAME.matcher(name).matches()) {
				throw new ProtocolException("Invalid uniform name " + name);
			}
		}
	}

	public static byte[] encode(ShadersPacket packet) {
		PacketWriter writer = new PacketWriter(64);

		switch (packet) {
			case FileChunk chunk -> writer.writeVarInt(FILE_CHUNK)
					.writeString(chunk.className(), MAX_CLASS_NAME)
					.writeVarInt(chunk.totalSize())
					.writeVarInt(chunk.offset())
					.writeBytes(chunk.data());
			case RemoveFile remove -> writer.writeVarInt(REMOVE_FILE).writeString(remove.className(), MAX_CLASS_NAME);
			case Enable enable -> {
				writer.writeVarInt(ENABLE).writeString(enable.className(), MAX_CLASS_NAME).writeInt(enable.order());
				writeUniforms(writer, enable.uniforms());
			}
			case Disable disable -> writer.writeVarInt(DISABLE).writeString(disable.className(), MAX_CLASS_NAME);
			case DisableAll ignored -> writer.writeVarInt(DISABLE_ALL);
			case SetUniforms set -> {
				writer.writeVarInt(SET_UNIFORMS).writeString(set.className(), MAX_CLASS_NAME);
				writeUniforms(writer, set.values());
				writer.writeVarInt(set.durationMillis()).writeByte(set.easing().ordinal());
			}
			case Error error -> writer.writeVarInt(ERROR)
					.writeString(error.className(), MAX_CLASS_NAME)
					.writeByte(error.kind().ordinal())
					.writeString(GuiCodec.truncate(error.message(), MAX_ERROR_LENGTH), MAX_ERROR_LENGTH);
			case State state -> writer.writeVarInt(STATE).writeString(state.className(), MAX_CLASS_NAME).writeBoolean(state.running());
			case SetClientShaders shaders -> writer.writeVarInt(SET_CLIENT_SHADERS).writeByte(shaders.mode().ordinal());
			case ClientShaderState state -> writer.writeVarInt(CLIENT_SHADER_STATE).writeBoolean(state.shaderOn()).writeBoolean(state.byPlayer());
		}

		return writer.toByteArray();
	}

	private static void writeUniforms(PacketWriter writer, Map<String, Object> values) {
		writer.writeVarInt(values.size());

		for (Map.Entry<String, Object> entry : values.entrySet()) {
			writer.writeString(entry.getKey(), MAX_UNIFORM_NAME);
			GuiCodec.writeValue(writer, entry.getValue(), 0);
		}
	}

	private static Map<String, Object> readUniforms(PacketReader reader) {
		int count = reader.readVarInt(0, MAX_UNIFORMS);
		Map<String, Object> values = new LinkedHashMap<>();

		for (int i = 0; i < count; i++) {
			String name = reader.readString(MAX_UNIFORM_NAME);
			values.put(name, GuiCodec.readValue(reader));
		}

		return Collections.unmodifiableMap(values);
	}

	public static ShadersPacket decodeClientbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();
		ShadersPacket packet = switch (id) {
			case FILE_CHUNK -> new FileChunk(reader.readString(MAX_CLASS_NAME), reader.readVarInt(), reader.readVarInt(), reader.readBytes(MAX_CHUNK_SIZE));
			case REMOVE_FILE -> new RemoveFile(reader.readString(MAX_CLASS_NAME));
			case ENABLE -> new Enable(reader.readString(MAX_CLASS_NAME), reader.readInt(), readUniforms(reader));
			case DISABLE -> new Disable(reader.readString(MAX_CLASS_NAME));
			case DISABLE_ALL -> new DisableAll();
			case SET_UNIFORMS -> new SetUniforms(reader.readString(MAX_CLASS_NAME), readUniforms(reader), reader.readVarInt(), reader.readEnum(Easing.values(), "easing"));
			case SET_CLIENT_SHADERS -> new SetClientShaders(reader.readEnum(ClientShaderMode.values(), "client shader mode"));
			default -> throw new ProtocolException("Unknown clientbound shaders packet " + id);
		};

		reader.expectEnd();
		return packet;
	}

	public static ShadersPacket decodeServerbound(byte[] data) {
		PacketReader reader = new PacketReader(data);
		int id = reader.readVarInt();
		ShadersPacket packet = switch (id) {
			case ERROR -> new Error(reader.readString(MAX_CLASS_NAME), reader.readEnum(ErrorKind.values(), "error kind"), reader.readString(MAX_ERROR_LENGTH));
			case STATE -> new State(reader.readString(MAX_CLASS_NAME), reader.readBoolean());
			case CLIENT_SHADER_STATE -> new ClientShaderState(reader.readBoolean(), reader.readBoolean());
			default -> throw new ProtocolException("Unknown serverbound shaders packet " + id);
		};

		reader.expectEnd();
		return packet;
	}
}
