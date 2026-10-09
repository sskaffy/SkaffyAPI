package me.skaffy.client.gui;

import java.net.URI;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.gui.element.GuiDocument;
import me.skaffy.client.gui.element.GuiEvents.KeyInput;
import me.skaffy.protocol.gui.GuiPacket.ErrorKind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLKeyboard;

final class GuiScreen extends Screen {
	static volatile long lastInput;

	private final GuiSession session;
	private boolean closing;
	private boolean linkPrompt;
	private final GuiPainter painter = new GuiPainter();

	GuiScreen(GuiSession session) {
		super(Component.empty());
		this.session = session;
	}

	static @Nullable String whyCantOpen() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level == null || minecraft.player == null) {
			return "the player isn't in a world";
		}

		if (minecraft.player.isDeadOrDying() || minecraft.gui.screen() instanceof DeathScreen) {
			return "the player is dead";
		}

		if (minecraft.gui.screen() instanceof LevelLoadingScreen) {
			return "the world is loading";
		}

		return null;
	}

	void install() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.gui.screen() instanceof AbstractContainerScreen<?> && minecraft.player != null) {
			minecraft.player.closeContainer();
		}

		minecraft.gui.setScreen(this);
	}

	boolean isCurrent() {
		return Minecraft.getInstance().gui.screen() == this;
	}

	void closeFromSession() {
		closing = true;

		if (isCurrent()) {
			Minecraft.getInstance().gui.setScreen(null);
		}
	}

	private GuiDocument document() {
		return session.document();
	}

	void syncSize() {
		Minecraft minecraft = Minecraft.getInstance();
		int vanillaScale = Math.max(1, minecraft.getWindow().getGuiScale());
		int forced = document().guiScale;
		double factor = forced > 0 ? forced / (double) vanillaScale : 1;
		double guiWidth = minecraft.getWindow().getGuiScaledWidth();
		double guiHeight = minecraft.getWindow().getGuiScaledHeight();
		document().resize(guiWidth / factor, guiHeight / factor, factor);
	}

	private double factor() {
		return document().scaleFactor <= 0 ? 1 : document().scaleFactor;
	}

	@Override
	protected void init() {
		syncSize();
	}

	@Override
	protected void repositionElements() {
		session.run(this::syncSize);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
		session.close(me.skaffy.protocol.gui.GuiPacket.CloseReason.ESCAPE);
	}

	@Override
	public void removed() {
		Minecraft.getInstance().textInputManager().stopTextInput();

		if (!closing && !linkPrompt) {
			session.replaced();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		GuiDocument document = document();

		if (document.menuBackground) {
			Screen.extractMenuBackgroundTexture(graphics, INWORLD_MENU_BACKGROUND, 0, 0, 0, 0, width, height);
		}

		if (document.blur >= 1) {
			graphics.blurBeforeThisStratum();
		}

		if (document.tintTop != 0 || document.tintBottom != 0) {
			graphics.fillGradient(0, 0, width, height, document.tintTop, document.tintBottom);
		}
	}

	private static final net.minecraft.resources.Identifier INWORLD_MENU_BACKGROUND = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/inworld_menu_background.png");

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		session.run(() -> {
			syncSize();

			if (document().mouseX < -1e8) {
				Minecraft minecraft = Minecraft.getInstance();
				double x = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
				double y = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
				document().mouseX = x / factor();
				document().mouseY = y / factor();
			}

			document().update(System.nanoTime());
		});

		try {
			painter.paint(graphics, document(), session);
		} catch (RuntimeException e) {
			SkaffySAPIClient.LOGGER.error("[GUI {}] Drawing failed", session.className(), e);
		}
	}


	@Override
	public void mouseMoved(double x, double y) {
		session.run(() -> document().mouseMoved(x / factor(), y / factor()));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		lastInput = System.nanoTime();
		session.run(() -> document().mousePressed(event.x() / factor(), event.y() / factor(), event.button(), event.modifiers(), doubleClick));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		session.run(() -> document().mouseReleased(event.x() / factor(), event.y() / factor(), event.button(), event.modifiers()));
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0) {
			int modifiers = SDLKeyboard.SDL_GetModState() & 0xFFFF;
			session.run(() -> document().mouseScrolled(x / factor(), y / factor(), scrollY, modifiers));
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		lastInput = System.nanoTime();
		KeyInput key = new KeyInput(InputConstants.getKey(event).getName(), event.keycode(), event.modifiers());
		session.run(() -> document().keyPressed(key));
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		KeyInput key = new KeyInput(InputConstants.getKey(event).getName(), event.keycode(), event.modifiers());
		session.run(() -> document().keyReleased(key));
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event.isAllowedChatCharacter()) {
			session.run(() -> document().charTyped(event.codepointAsString()));
		}

		return true;
	}


	static void openLink(String url, @Nullable GuiSession session) {
		URI uri;

		try {
			uri = Util.parseAndValidateUntrustedUri(url);
		} catch (Exception e) {
			String owner = session != null ? session.className() : "gui";
			ClientGuis.report(owner, ErrorKind.REQUEST, "Can't open link " + url + ": " + e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Screen previous = minecraft.gui.screen();

		if (previous instanceof GuiScreen gui) {
			gui.linkPrompt = true;
		}

		minecraft.gui.setScreen(new ConfirmLinkScreen(accepted -> {
			if (accepted) {
				Blaze3D.openUri(uri);
			}

			if (previous instanceof GuiScreen gui) {
				gui.linkPrompt = false;
				minecraft.gui.setScreen(gui.session.ended() ? null : gui);
			} else {
				minecraft.gui.setScreen(previous);
			}
		}, uri, false));
	}
}
