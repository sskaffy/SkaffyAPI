package me.skaffy.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.gui.sfy.CompileException;
import me.skaffy.client.gui.sfy.ScriptException;
import me.skaffy.client.gui.sfy.SfyClass;
import me.skaffy.client.gui.sfy.SfyLibrary;
import me.skaffy.client.gui.sfy.SfyMethod;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.client.pack.PackBuilder;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.gui.GuiCodec;
import me.skaffy.protocol.gui.GuiPacket;
import me.skaffy.protocol.gui.GuiPacket.Call;
import me.skaffy.protocol.gui.GuiPacket.CallResult;
import me.skaffy.protocol.gui.GuiPacket.Close;
import me.skaffy.protocol.gui.GuiPacket.CloseReason;
import me.skaffy.protocol.gui.GuiPacket.DefineFonts;
import me.skaffy.protocol.gui.GuiPacket.ErrorKind;
import me.skaffy.protocol.gui.GuiPacket.FileChunk;
import me.skaffy.protocol.gui.GuiPacket.FontDefinition;
import me.skaffy.protocol.gui.GuiPacket.HideHud;
import me.skaffy.protocol.gui.GuiPacket.Open;
import me.skaffy.protocol.gui.GuiPacket.OpenLink;
import me.skaffy.protocol.gui.GuiPacket.RemoveFile;
import me.skaffy.protocol.gui.GuiPacket.ReplaceMethod;
import me.skaffy.protocol.gui.GuiPacket.SetHudPart;
import me.skaffy.protocol.gui.GuiPacket.ShowHud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

public final class ClientGuis {
	private static final Object LOCK = new Object();
	private static final Map<String, Partial> PARTIAL = new HashMap<>();
	private static final Map<String, Integer> SIZES = new HashMap<>();
	private static final Map<String, SfyClass> CLASSES = new ConcurrentHashMap<>();
	private static final List<FontDefinition> PENDING_FONTS = new ArrayList<>();
	private static volatile Map<String, Identifier> fonts = Map.of();
	private static final List<GuiPacket> QUEUED = new ArrayList<>();
	private static volatile boolean ready;
	private static volatile DiskAssetStore assets;
	private static @Nullable SfyLibrary library;
	private static @Nullable GuiSession session;
	private static final Map<String, GuiSession> HUDS = new HashMap<>();
	static @Nullable GuiSession running;

	private record Partial(byte[] data, int received) {
	}

	private ClientGuis() {
	}

	static SfyLibrary library() {
		synchronized (LOCK) {
			if (library == null) {
				library = GuiBindings.create();
			}

			return library;
		}
	}

	static @Nullable GuiSession current() {
		return running != null ? running : session;
	}

	static @Nullable DiskAssetStore assets() {
		return assets;
	}

	static @Nullable Identifier font(String name) {
		return fonts.get(name.toLowerCase(Locale.ROOT));
	}

	public static boolean hidesHud() {
		return session != null && session.isShowing() && session.document().hideHud;
	}

	public static int blur() {
		return session != null && session.isShowing() ? Math.clamp(session.document().blur, 0, 10) : -1;
	}


	public static void receive(byte[] data, boolean registering) {
		GuiPacket packet;

		try {
			packet = GuiCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed gui packet: {}", e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		switch (packet) {
			case FileChunk chunk -> chunk(chunk);
			case RemoveFile remove -> {
				synchronized (LOCK) {
					PARTIAL.remove(remove.className());
					SIZES.remove(remove.className());
				}

				CLASSES.remove(remove.className());
			}
			case DefineFonts define -> {
				if (registering) {
					synchronized (LOCK) {
						for (FontDefinition font : define.fonts()) {
							if (PENDING_FONTS.size() < GuiCodec.MAX_FONTS) {
								PENDING_FONTS.add(font);
							}
						}
					}
				}
			}
			case Open open -> minecraft.execute(() -> open(open));
			case Close ignored -> minecraft.execute(() -> {
				if (session != null) {
					session.close(CloseReason.SERVER);
				}
			});
			case Call call -> minecraft.execute(() -> call(call));
			case ReplaceMethod replace -> minecraft.execute(() -> replace(replace));
			case OpenLink link -> minecraft.execute(() -> GuiScreen.openLink(link.url(), session));
			case ShowHud show -> minecraft.execute(() -> showHud(show));
			case HideHud hide -> minecraft.execute(() -> hideHud(hide.className()));
			case SetHudPart part -> minecraft.execute(() -> HudParts.set(part));
			default -> {
			}
		}
	}

	private static void chunk(FileChunk chunk) {
		byte[] complete = null;

		synchronized (LOCK) {
			Partial partial = PARTIAL.get(chunk.className());

			if (chunk.offset() == 0) {
				partial = new Partial(new byte[chunk.totalSize()], 0);
			} else if (partial == null || partial.data.length != chunk.totalSize() || partial.received != chunk.offset()) {
				SkaffySAPIClient.LOGGER.warn("GUI file {} arrived out of order, dropped", chunk.className());
				PARTIAL.remove(chunk.className());
				return;
			}

			System.arraycopy(chunk.data(), 0, partial.data, chunk.offset(), chunk.data().length);
			partial = new Partial(partial.data, partial.received + chunk.data().length);

			if (partial.received < partial.data.length) {
				PARTIAL.put(chunk.className(), partial);
				return;
			}

			PARTIAL.remove(chunk.className());
			long total = chunk.totalSize();

			for (Map.Entry<String, Integer> entry : SIZES.entrySet()) {
				if (!entry.getKey().equals(chunk.className())) {
					total += entry.getValue();
				}
			}

			if (total > GuiCodec.MAX_TOTAL_SIZE) {
				report(chunk.className(), ErrorKind.COMPILE, "GUI files are over " + GuiCodec.MAX_TOTAL_SIZE / 1024 / 1024 + " MiB together, " + chunk.className() + " is ignored");
				return;
			}

			SIZES.put(chunk.className(), chunk.totalSize());
			complete = partial.data;
		}

		compile(chunk.className(), new String(complete, java.nio.charset.StandardCharsets.UTF_8));
	}

	private static void compile(String className, String source) {
		long start = System.nanoTime();

		try {
			SfyClass compiled = SfyClass.compile(library(), className, source);
			CLASSES.put(className, compiled);
			SkaffySAPIClient.LOGGER.info("Compiled GUI {} in {} ms", className, (System.nanoTime() - start) / 1_000_000);
		} catch (CompileException e) {
			CLASSES.remove(className);
			report(className, ErrorKind.COMPILE, e.describe());
		} catch (RuntimeException | StackOverflowError e) {
			CLASSES.remove(className);
			SkaffySAPIClient.LOGGER.error("Compiling GUI {} crashed", className, e);
			report(className, ErrorKind.COMPILE, "The compiler crashed on this file: " + e);
		}
	}


	public static void load(PackBuilder pack, DiskAssetStore store) {
		assets = store;
		List<FontDefinition> definitions;

		synchronized (LOCK) {
			definitions = List.copyOf(PENDING_FONTS);
			PENDING_FONTS.clear();
		}

		Map<String, Identifier> loaded = new HashMap<>();

		for (FontDefinition font : definitions) {
			byte[] file = pack.readAsset(font.asset());

			if (file == null) {
				SkaffySAPIClient.LOGGER.warn("GUI font {} uses asset {}, which doesn't exist", font.name(), font.asset());
				continue;
			}

			String path = "gui_font_" + font.name();
			pack.add(pack.id("font/" + path + ".ttf"), file);
			pack.add(pack.id("font/" + path + ".json"), String.format(Locale.ROOT,
					"{\"providers\":[{\"type\":\"ttf\",\"file\":\"%s:%s.ttf\",\"size\":%s,\"oversample\":%s,\"shift\":[%s,%s]}]}",
					pack.namespace(), path, font.size(), font.oversample(), font.shiftX(), font.shiftY()));
			loaded.put(font.name(), pack.id(path));
		}

		fonts = Map.copyOf(loaded);
	}

	public static void activate() {
		List<GuiPacket> queued;

		synchronized (LOCK) {
			ready = true;
			queued = List.copyOf(QUEUED);
			QUEUED.clear();
		}

		queued.forEach(ClientGuis::send);
	}

	public static void unload() {
		synchronized (LOCK) {
			ready = false;
			PARTIAL.clear();
			SIZES.clear();
			PENDING_FONTS.clear();
			QUEUED.clear();
		}

		CLASSES.clear();
		fonts = Map.of();
		Minecraft.getInstance().execute(() -> {
			if (session != null) {
				GuiSession ended = session;
				session = null;
				ended.discard();
			}

			List.copyOf(HUDS.values()).forEach(GuiSession::discard);
			HUDS.clear();
			HudParts.reset();
			GuiTextures.clear();
		});
	}


	private static void open(Open open) {
		SfyClass cls = CLASSES.get(open.className());

		if (cls == null) {
			report(open.className(), ErrorKind.REQUEST, "Can't open " + open.className() + ": no such file (not sent, or it has compile errors)");
			return;
		}

		SfyMethod method = cls.findMethod(open.method(), open.args().size());

		if (method == null || !method.isGui() || !method.isPublic()) {
			report(open.className(), ErrorKind.REQUEST, "Can't open " + open.className() + "." + open.method() + " with " + open.args().size() + " values: "
					+ (method == null ? "no such method" : "it must be a public @Gui method"));
			return;
		}

		Object[] args;

		try {
			args = cls.importArgs(method, open.args());
		} catch (IllegalArgumentException e) {
			report(open.className(), ErrorKind.REQUEST, "Can't open " + open.className() + "." + open.method() + ": " + e.getMessage());
			return;
		}

		String blocked = GuiScreen.whyCantOpen();

		if (blocked != null) {
			report(open.className(), ErrorKind.REQUEST, "Can't open " + open.className() + " right now: " + blocked);
			send(new GuiPacket.Closed(open.className(), CloseReason.ERROR));
			return;
		}

		if (session != null) {
			session.close(CloseReason.REPLACED);
		}

		GuiSession created = new GuiSession(cls);
		session = created;

		if (!created.start()) {
			session = null;
			send(new GuiPacket.Closed(open.className(), CloseReason.ERROR));
			return;
		}

		created.show(method, args);
	}

	private static void call(Call call) {
		GuiSession current = session != null && session.className().equals(call.className()) ? session : HUDS.get(call.className());

		if (current == null) {
			answer(call, false, null, "No GUI or HUD of class " + call.className() + " is open");
			return;
		}

		SfyMethod method = current.sfyClass().findMethod(call.method(), call.args().size());

		if (method == null || !method.isPublic()) {
			answer(call, false, null, method == null ? "No method " + call.method() + " with " + call.args().size() + " values" : call.method() + " isn't public");
			return;
		}

		Object[] args;

		try {
			args = current.sfyClass().importArgs(method, call.args());
		} catch (IllegalArgumentException e) {
			answer(call, false, null, e.getMessage());
			return;
		}

		try {
			Object result = current.invoke(method, args);
			Object exported;

			try {
				exported = SfyClass.exportValue(result);
			} catch (IllegalArgumentException e) {
				answer(call, false, null, call.method() + " returned something that can't be sent: " + e.getMessage());
				return;
			}

			answer(call, true, exported, null);
		} catch (ScriptException e) {
			answer(call, false, null, e.describe());
			current.reportRuntime(e);
		}
	}

	private static void answer(Call call, boolean ok, Object value, String error) {
		if (call.callId() != 0) {
			send(new CallResult(call.callId(), ok, value, error));
		}
	}

	private static void replace(ReplaceMethod replace) {
		SfyClass cls = CLASSES.get(replace.className());

		if (cls == null) {
			report(replace.className(), ErrorKind.REQUEST, "Can't replace a method of " + replace.className() + ": no such file");
			return;
		}

		try {
			cls.replaceMethod(replace.source());

			if (session != null && session.className().equals(replace.className()) && session.sfyClass() != cls) {
				session.sfyClass().replaceMethod(replace.source());
			}

			GuiSession layer = HUDS.get(replace.className());

			if (layer != null && layer.sfyClass() != cls) {
				layer.sfyClass().replaceMethod(replace.source());
			}
		} catch (CompileException e) {
			report(replace.className(), ErrorKind.COMPILE, e.describe());
		}
	}

	static void ended(GuiSession ended) {
		if (session == ended) {
			session = null;
		}
	}


	private static void showHud(ShowHud show) {
		SfyClass cls = CLASSES.get(show.className());

		if (cls == null) {
			report(show.className(), ErrorKind.REQUEST, "Can't show " + show.className() + " as a HUD: no such file (not sent, or it has compile errors)");
			return;
		}

		SfyMethod method = cls.findMethod(show.method(), show.args().size());

		if (method == null || !method.isHud() || !method.isPublic()) {
			report(show.className(), ErrorKind.REQUEST, "Can't show " + show.className() + "." + show.method() + " with " + show.args().size() + " values: "
					+ (method == null ? "no such method" : "it must be a public @Hud method"));
			return;
		}

		Object[] args;

		try {
			args = cls.importArgs(method, show.args());
		} catch (IllegalArgumentException e) {
			report(show.className(), ErrorKind.REQUEST, "Can't show " + show.className() + "." + show.method() + ": " + e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level == null || minecraft.player == null) {
			report(show.className(), ErrorKind.REQUEST, "Can't show " + show.className() + " right now: the player isn't in a world");
			send(new GuiPacket.HudHidden(show.className(), CloseReason.ERROR));
			return;
		}

		GuiSession previous = HUDS.get(show.className());

		if (previous != null) {
			previous.close(CloseReason.REPLACED);
		}

		GuiSession layer = new GuiSession(cls, true, show.order());
		HUDS.put(show.className(), layer);

		if (!layer.start()) {
			HUDS.remove(show.className());
			send(new GuiPacket.HudHidden(show.className(), CloseReason.ERROR));
			return;
		}

		layer.showHud(method, args);
	}

	private static void hideHud(String className) {
		for (GuiSession layer : List.copyOf(HUDS.values())) {
			if (className.isEmpty() || layer.className().equals(className)) {
				layer.close(CloseReason.SERVER);
			}
		}
	}

	static void hudEnded(GuiSession ended) {
		HUDS.remove(ended.className(), ended);
	}

	public static void paintHuds(GuiGraphicsExtractor graphics) {
		if (HUDS.isEmpty()) {
			return;
		}

		List<GuiSession> layers = new ArrayList<>(HUDS.values());
		layers.sort(java.util.Comparator.comparingInt(GuiSession::order).thenComparing(GuiSession::className));

		for (GuiSession layer : layers) {
			graphics.nextStratum();
			layer.paintHud(graphics);
		}
	}


	static void report(String className, ErrorKind kind, String message) {
		SkaffySAPIClient.LOGGER.warn("[GUI {}] {} error:\n{}", className, kind.name().toLowerCase(Locale.ROOT), message);
		send(new GuiPacket.Error(className, kind, message));
	}

	static void send(GuiPacket packet) {
		synchronized (LOCK) {
			if (!ready) {
				if (QUEUED.size() < 256) {
					QUEUED.add(packet);
				}

				return;
			}
		}

		byte[] data;

		try {
			data = GuiCodec.encode(packet);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Couldn't send a GUI packet: {}", e.getMessage());
			return;
		}

		if (data.length > me.skaffy.protocol.Protocol.MAX_SERVERBOUND_PAYLOAD) {
			SkaffySAPIClient.LOGGER.warn("A GUI packet is {} bytes, over the {} bytes a client can send; dropped", data.length, me.skaffy.protocol.Protocol.MAX_SERVERBOUND_PAYLOAD);
			return;
		}

		Minecraft.getInstance().execute(() -> SkaffyConnection.sendFeature(GuiCodec.CHANNEL, data));
	}
}
