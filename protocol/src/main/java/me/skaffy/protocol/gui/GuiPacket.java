package me.skaffy.protocol.gui;

import java.util.List;

import me.skaffy.protocol.ProtocolException;

public sealed interface GuiPacket {
	record FileChunk(String className, int totalSize, int offset, byte[] data) implements GuiPacket {
		public FileChunk {
			GuiCodec.checkClassName(className);

			if (totalSize < 0 || totalSize > GuiCodec.MAX_FILE_SIZE) {
				throw new ProtocolException("File size " + totalSize + " is over " + GuiCodec.MAX_FILE_SIZE);
			}

			if (offset < 0 || offset + data.length > totalSize) {
				throw new ProtocolException("Chunk " + offset + "+" + data.length + " outside of " + totalSize);
			}
		}
	}

	record RemoveFile(String className) implements GuiPacket {
		public RemoveFile {
			GuiCodec.checkClassName(className);
		}
	}

	record DefineFonts(List<FontDefinition> fonts) implements GuiPacket {
	}

	record FontDefinition(String name, String asset, float size, float oversample, float shiftX, float shiftY) {
		public FontDefinition {
			if (!name.matches("[a-z0-9_]{1,32}")) {
				throw new ProtocolException("Invalid font name " + name);
			}

			if (!(size > 0 && size <= 256) || !(oversample > 0 && oversample <= 16)) {
				throw new ProtocolException("Invalid font size or oversample for " + name);
			}
		}
	}

	record Open(String className, String method, List<Object> args) implements GuiPacket {
		public Open {
			GuiCodec.checkClassName(className);
			GuiCodec.checkMethodName(method);
		}
	}

	record Close() implements GuiPacket {
	}

	record Call(int callId, String className, String method, List<Object> args) implements GuiPacket {
		public Call {
			GuiCodec.checkClassName(className);
			GuiCodec.checkMethodName(method);
		}
	}

	record ReplaceMethod(String className, String source) implements GuiPacket {
		public ReplaceMethod {
			GuiCodec.checkClassName(className);
		}
	}

	record OpenLink(String url) implements GuiPacket {
	}

	record ShowHud(String className, String method, List<Object> args, int order) implements GuiPacket {
		public ShowHud {
			GuiCodec.checkClassName(className);
			GuiCodec.checkMethodName(method);
		}
	}

	record HideHud(String className) implements GuiPacket {
		public HideHud {
			if (!className.isEmpty()) {
				GuiCodec.checkClassName(className);
			}
		}
	}

	enum HudPart {
		CROSSHAIR,
		HOTBAR,
		HEALTH,
		ARMOR,
		FOOD,
		AIR,
		EXPERIENCE,
		MOUNT_HEALTH,
		HELD_ITEM_NAME,
		EFFECTS,
		BOSS_BARS,
		SCOREBOARD,
		ACTION_BAR,
		TITLE,
		CHAT,
		PLAYER_LIST,
		SUBTITLES,
		VIGNETTE,
		HELMET_OVERLAYS,
		SLEEP_FADE
	}

	record SetHudPart(HudPart part, boolean visible, float x, float y, float scale) implements GuiPacket {
		public SetHudPart {
			if (!Float.isFinite(x) || !Float.isFinite(y) || !(scale > 0 && scale <= 16)) {
				throw new me.skaffy.protocol.ProtocolException("HUD part offsets must be finite and scale 0 to 16");
			}
		}
	}

	record HudShown(String className, String method) implements GuiPacket {
	}

	record HudHidden(String className, CloseReason reason) implements GuiPacket {
	}

	record Opened(String className, String method) implements GuiPacket {
	}

	enum CloseReason {
		ESCAPE,
		SERVER,
		CODE,
		REPLACED,
		ERROR
	}

	record Closed(String className, CloseReason reason) implements GuiPacket {
	}

	record Message(String className, String name, List<Object> values) implements GuiPacket {
	}

	record CallResult(int callId, boolean ok, Object value, String error) implements GuiPacket {
	}

	enum ErrorKind {
		COMPILE,
		RUNTIME,
		REQUEST
	}

	record Error(String className, ErrorKind kind, String message) implements GuiPacket {
	}

	enum LogLevel {
		INFO,
		WARN,
		ERROR
	}

	record Log(String className, LogLevel level, String message) implements GuiPacket {
	}
}
