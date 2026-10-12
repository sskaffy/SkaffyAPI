package me.skaffy.client.mixin;

import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import me.skaffy.client.shader.ShaderOptions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
	@Unique
	private int skaffy$shownVersion = -1;

	private VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	@ModifyReturnValue(method = "displayOptions", at = @At("RETURN"))
	private static OptionInstance<?>[] skaffy$shaderRow(OptionInstance<?>[] options) {
		List<OptionInstance<?>> list = new ArrayList<>(List.of(options));
		int api = list.indexOf(Minecraft.getInstance().options.preferredGraphicsBackend());
		list.addAll(api >= 0 ? api + 1 : list.size(), List.of(ShaderOptions.SHADERS));
		return list.toArray(OptionInstance[]::new);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void skaffy$followServer(CallbackInfo ci) {
		if (this.list == null) {
			return;
		}

		boolean changed = skaffy$shownVersion != ShaderOptions.version();
		skaffy$shownVersion = ShaderOptions.version();
		skaffy$follow(ShaderOptions.SHADERS, !ShaderOptions.shadersUsable(), changed, ShaderOptions.shadersTooltip());
	}

	@Unique
	private void skaffy$follow(OptionInstance<?> option, boolean locked, boolean changed, Tooltip tooltip) {
		AbstractWidget widget = this.list.findOption(option);

		if (widget == null) {
			return;
		}

		if (changed) {
			widget.setTooltip(tooltip);
		}

		widget.active = !locked;
	}
}
