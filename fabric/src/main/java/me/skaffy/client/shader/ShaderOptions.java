package me.skaffy.client.shader;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;

import com.mojang.serialization.Codec;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.shader.pack.ShaderMenu;
import me.skaffy.protocol.shaders.ShadersPacket.ClientShaderMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class ShaderOptions {
	private static final Component FORCED_OFF = Component.translatable("skaffys-api.options.shaders.forcedOff");

	private static ClientShaderMode mode = ClientShaderMode.ALLOWED;
	private static boolean serverOff;
	private static int version;

	public static final OptionInstance<Boolean> SHADERS = new OptionInstance<>("skaffys-api.options.shaders", value -> shadersTooltip(), (caption, value) -> caption,
			new OptionInstance.ValueSet<>() {
				@Override
				public Function<OptionInstance<Boolean>, AbstractWidget> createButton(OptionInstance.TooltipSupplier<Boolean> tooltip, Options options, int x, int y,
						int width, OptionInstance.ValueUpdateListener<? super Boolean> onValueChanged) {
					return option -> {
						Button button = Button.builder(Component.translatable("skaffys-api.options.shaders.button"), pressed -> openMenu())
								.bounds(x, y, width, 20)
								.tooltip(shadersTooltip())
								.build();
						button.active = shadersUsable();
						return button;
					};
				}

				@Override
				public Optional<Boolean> validateValue(Boolean value) {
					return Optional.of(value);
				}

				@Override
				public Codec<Boolean> codec() {
					return Codec.BOOL;
				}
			}, true, value -> {
			});

	private ShaderOptions() {
	}


	public static boolean clientShaderAllowed() {
		return !serverOff;
	}

	public static boolean shadersUsable() {
		return mode != ClientShaderMode.FORCED_OFF;
	}

	public static int version() {
		return version;
	}

	public static Tooltip shadersTooltip() {
		return Tooltip.create(shadersUsable() ? Component.translatable("skaffys-api.options.shaders.tooltip") : FORCED_OFF);
	}

	private static void openMenu() {
		if (!shadersUsable()) {
			return;
		}

		ShaderMenu.open(Minecraft.getInstance().gui.screen());
	}


	public static void processOptions(Object access) {
		try {
			Method strings = process(access, String.class);
			String selected = ShaderPacks.selectedForOptions();
			String read = (String) strings.invoke(access, "skaffy_shader", selected == null ? ShaderPacks.UNSET : selected);

			if (read.equals(ShaderPacks.UNSET)) {
				boolean vista = (boolean) process(access, boolean.class).invoke(access, "skaffy_vista", false);
				read = vista ? ShaderPacks.VISTA_ID : "";
			}

			ShaderPacks.loadSelected(read);
		} catch (ReflectiveOperationException | RuntimeException e) {
			SkaffySAPIClient.LOGGER.error("Couldn't load or save the shader settings", e);
		}
	}

	private static Method process(Object access, Class<?> type) throws NoSuchMethodException {
		for (Class<?> face : access.getClass().getInterfaces()) {
			try {
				Method method = face.getMethod("process", String.class, type);
				method.setAccessible(true);
				return method;
			} catch (NoSuchMethodException ignored) {
			}
		}

		throw new NoSuchMethodException("process(String, " + type + ") on " + access.getClass());
	}

	static void playerPicked() {
		serverOff = false;
	}

	static void sendState(boolean byPlayer) {
		ClientShaders.sendClientState(ShaderPacks.activeId() != null, byPlayer);
	}


	static void serverMode(ClientShaderMode value) {
		mode = value;
		serverOff = value != ClientShaderMode.ALLOWED;
		if (value == ClientShaderMode.FORCED_OFF) {
			ShaderMenu.close();
		}

		version++;
		ShaderPacks.update();
		sendState(false);
	}

	static void sessionStarted() {
		sendState(false);
	}

	static void sessionEnded() {
		mode = ClientShaderMode.ALLOWED;
		serverOff = false;
		version++;
	}
}
