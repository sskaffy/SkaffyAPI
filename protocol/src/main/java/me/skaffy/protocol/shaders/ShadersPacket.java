package me.skaffy.protocol.shaders;

import java.util.Map;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.ProtocolException;

public sealed interface ShadersPacket {
	int MAX_DURATION = 600_000;

	record FileChunk(String className, int totalSize, int offset, byte[] data) implements ShadersPacket {
		public FileChunk {
			ShadersCodec.checkClassName(className);

			if (totalSize < 0 || totalSize > ShadersCodec.MAX_FILE_SIZE) {
				throw new ProtocolException("File size " + totalSize + " is over " + ShadersCodec.MAX_FILE_SIZE);
			}

			if (offset < 0 || offset + data.length > totalSize) {
				throw new ProtocolException("Chunk " + offset + "+" + data.length + " outside of " + totalSize);
			}
		}
	}

	record RemoveFile(String className) implements ShadersPacket {
		public RemoveFile {
			ShadersCodec.checkClassName(className);
		}
	}

	record Enable(String className, int order, Map<String, Object> uniforms) implements ShadersPacket {
		public Enable {
			ShadersCodec.checkClassName(className);
			ShadersCodec.checkUniforms(uniforms);
		}
	}

	record Disable(String className) implements ShadersPacket {
		public Disable {
			ShadersCodec.checkClassName(className);
		}
	}

	record DisableAll() implements ShadersPacket {
	}

	record SetUniforms(String className, Map<String, Object> values, int durationMillis, Easing easing) implements ShadersPacket {
		public SetUniforms {
			ShadersCodec.checkClassName(className);
			ShadersCodec.checkUniforms(values);

			if (durationMillis < 0 || durationMillis > MAX_DURATION) {
				throw new ProtocolException("Duration must be 0 to " + MAX_DURATION + " ms, got " + durationMillis);
			}
		}
	}

	record SetVista(Boolean enabled, boolean locked) implements ShadersPacket {
	}

	record SetShaderDistance(int chunks, boolean locked) implements ShadersPacket {
		public SetShaderDistance {
			if (chunks != 0 && (chunks < ShadersCodec.MIN_DISTANCE || chunks > ShadersCodec.MAX_DISTANCE)) {
				throw new ProtocolException("Shader Distance must be 0 or " + ShadersCodec.MIN_DISTANCE + " to " + ShadersCodec.MAX_DISTANCE + " chunks, got " + chunks);
			}
		}
	}

	enum ErrorKind {
		COMPILE,
		RUNTIME,
		REQUEST
	}

	record Error(String className, ErrorKind kind, String message) implements ShadersPacket {
	}

	record State(String className, boolean running) implements ShadersPacket {
	}

	record VistaState(boolean enabled, int chunks, boolean byPlayer) implements ShadersPacket {
	}
}
