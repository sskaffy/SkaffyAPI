package me.skaffy.client.gui;

import java.util.function.Consumer;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.gui.element.GuiDocument;
import me.skaffy.client.gui.sfy.Fn;
import me.skaffy.client.gui.sfy.ScriptException;
import me.skaffy.client.gui.sfy.SfyClass;
import me.skaffy.client.gui.sfy.SfyInstance;
import me.skaffy.client.gui.sfy.SfyMethod;
import me.skaffy.protocol.gui.GuiPacket;
import me.skaffy.protocol.gui.GuiPacket.CloseReason;
import me.skaffy.protocol.gui.GuiPacket.ErrorKind;
import me.skaffy.protocol.gui.GuiPacket.LogLevel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class GuiSession {
	private static final int REPORTS_PER_WINDOW = 10;
	private static final long REPORT_WINDOW_NANOS = 10_000_000_000L;
	private static final int LOGS_PER_SECOND = 40;

	private final SfyClass cls;
	private final GuiDocument document;
	private final boolean hud;
	private final GuiPainter hudPainter;
	private int order;
	private SfyInstance instance;
	private GuiScreen screen;
	private String method = "";
	private boolean ended;
	private long reportWindowStart;
	private int reportsInWindow;
	private int hiddenReports;
	private long logSecond;
	private int logsThisSecond;

	GuiSession(SfyClass cls) {
		this(cls, false, 0);
	}

	GuiSession(SfyClass cls, boolean hud, int order) {
		this.cls = cls;
		this.hud = hud;
		this.order = order;
		this.hudPainter = hud ? new GuiPainter() : null;
		this.document = new GuiDocument(McGuiPlatform.INSTANCE);
		this.document.closeRequest = () -> close(CloseReason.ESCAPE);
	}

	boolean isHud() {
		return hud;
	}

	int order() {
		return order;
	}

	boolean start() {
		try {
			instance = cls.newInstance();
			return true;
		} catch (ScriptException e) {
			reportRuntime(e);
			return false;
		} catch (RuntimeException e) {
			internalError(e);
			return false;
		}
	}

	SfyClass sfyClass() {
		return cls;
	}

	String className() {
		return cls.qualifiedName();
	}

	GuiDocument document() {
		return document;
	}

	boolean isShowing() {
		return hud ? !ended : screen != null && screen.isCurrent();
	}

	void show(SfyMethod gui, Object[] args) {
		screen = new GuiScreen(this);
		screen.install();
		switchTo(gui.name());
		run(() -> cls.invoke(instance, gui, args));
	}

	void showHud(SfyMethod method, Object[] args) {
		switchTo(method.name());
		run(() -> cls.invoke(instance, method, args));
	}

	void switchTo(String methodName) {
		method = methodName;
		document.reset();

		if (screen != null) {
			screen.syncSize();
		}

		if (hud) {
			syncHudSize();
		}

		ClientGuis.send(hud ? new GuiPacket.HudShown(className(), methodName) : new GuiPacket.Opened(className(), methodName));
	}

	Object invoke(SfyMethod target, Object[] args) {
		GuiSession previous = ClientGuis.running;
		ClientGuis.running = this;

		try {
			if (target.isGui() || target.isHud()) {
				switchTo(target.name());
			}

			return cls.invoke(instance, target, args);
		} finally {
			ClientGuis.running = previous;
		}
	}

	private void syncHudSize() {
		Minecraft minecraft = Minecraft.getInstance();
		int vanillaScale = Math.max(1, minecraft.getWindow().getGuiScale());
		double factor = document.guiScale > 0 ? document.guiScale / (double) vanillaScale : 1;
		document.resize(minecraft.getWindow().getGuiScaledWidth() / factor, minecraft.getWindow().getGuiScaledHeight() / factor, factor);
	}

	void paintHud(GuiGraphicsExtractor graphics) {
		run(() -> {
			syncHudSize();
			document.update(System.nanoTime());
		});

		if (ended) {
			return;
		}

		try {
			hudPainter.paint(graphics, document, this, false);
		} catch (RuntimeException e) {
			SkaffySAPIClient.LOGGER.error("[GUI {}] Drawing the HUD failed", className(), e);
		}
	}

	String method() {
		return method;
	}

	void run(Runnable code) {
		if (ended) {
			return;
		}

		GuiSession previous = ClientGuis.running;
		ClientGuis.running = this;

		try {
			code.run();
		} catch (ScriptException e) {
			reportRuntime(e);
		} catch (StackOverflowError e) {
			reportRuntime(new ScriptException("Stack overflow"));
		} catch (RuntimeException e) {
			internalError(e);
		} finally {
			ClientGuis.running = previous;
		}
	}

	Consumer<Object> handler(Fn fn) {
		return argument -> run(() -> fn.call(argument));
	}

	void reportRuntime(ScriptException e) {
		if (allowReport()) {
			ClientGuis.report(className(), ErrorKind.RUNTIME, e.describe());
		}
	}

	private void internalError(RuntimeException e) {
		SkaffySAPIClient.LOGGER.error("[GUI {}] Internal error while running the GUI", className(), e);

		if (allowReport()) {
			ClientGuis.send(new GuiPacket.Error(className(), ErrorKind.RUNTIME, "Internal error in Skaffy's API (please report it): " + e));
		}
	}

	private boolean allowReport() {
		long now = System.nanoTime();

		if (now - reportWindowStart > REPORT_WINDOW_NANOS) {
			if (hiddenReports > 0) {
				SkaffySAPIClient.LOGGER.warn("[GUI {}] {} more errors weren't shown", className(), hiddenReports);
			}

			reportWindowStart = now;
			reportsInWindow = 0;
			hiddenReports = 0;
		}

		if (reportsInWindow >= REPORTS_PER_WINDOW) {
			hiddenReports++;
			return false;
		}

		reportsInWindow++;
		return true;
	}

	void log(String level, String message) {
		long second = System.nanoTime() / 1_000_000_000L;

		if (second != logSecond) {
			logSecond = second;
			logsThisSecond = 0;
		}

		if (++logsThisSecond > LOGS_PER_SECOND) {
			return;
		}

		LogLevel logLevel = switch (level) {
			case "warn" -> LogLevel.WARN;
			case "error" -> LogLevel.ERROR;
			default -> LogLevel.INFO;
		};

		switch (logLevel) {
			case WARN -> SkaffySAPIClient.LOGGER.warn("[GUI {}] {}", className(), message);
			case ERROR -> SkaffySAPIClient.LOGGER.error("[GUI {}] {}", className(), message);
			default -> SkaffySAPIClient.LOGGER.info("[GUI {}] {}", className(), message);
		}

		ClientGuis.send(new GuiPacket.Log(className(), logLevel, message));
	}

	void close(CloseReason reason) {
		if (ended) {
			return;
		}

		ended = true;
		document.reset();

		if (hud) {
			ClientGuis.send(new GuiPacket.HudHidden(className(), reason));
			ClientGuis.hudEnded(this);
			return;
		}

		if (screen != null) {
			screen.closeFromSession();
		}

		ClientGuis.send(new GuiPacket.Closed(className(), reason));
		ClientGuis.ended(this);
	}

	void replaced() {
		if (ended) {
			return;
		}

		ended = true;
		document.reset();
		ClientGuis.send(new GuiPacket.Closed(className(), CloseReason.REPLACED));
		ClientGuis.ended(this);
	}

	void discard() {
		if (ended) {
			return;
		}

		ended = true;
		document.reset();

		if (screen != null) {
			screen.closeFromSession();
		}
	}

	boolean ended() {
		return ended;
	}
}
