package me.skaffy.client.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ConnectScreen.class)
public interface ConnectScreenInvoker {
	@Invoker("updateStatus")
	void skaffy$updateStatus(Component status);
}
