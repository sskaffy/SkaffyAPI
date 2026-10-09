package me.skaffy.client;

import java.util.stream.Stream;

import me.skaffy.client.keybind.ClientKeybinds;
import me.skaffy.client.model.ClientEntityModels;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.client.network.SkaffyPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkaffySAPIClient implements ClientModInitializer {
	public static final String MOD_ID = "skaffys-api";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		me.skaffy.client.model.gpu.ModelPipelines.init();

		for (CustomPacketPayload.Type<SkaffyPayload> type : Stream.concat(Stream.of(SkaffyPayload.CORE), SkaffyPayload.FEATURES.stream()).toList()) {
			PayloadTypeRegistry.clientboundConfiguration().register(type, SkaffyPayload.codec(type));
			PayloadTypeRegistry.serverboundConfiguration().register(type, SkaffyPayload.codec(type));
			PayloadTypeRegistry.clientboundPlay().register(type, SkaffyPayload.codec(type));
			PayloadTypeRegistry.serverboundPlay().register(type, SkaffyPayload.codec(type));
		}

		ClientConfigurationNetworking.registerGlobalReceiver(SkaffyPayload.CORE, (payload, context) -> SkaffyConnection.receive(payload.data()));
		ClientPlayNetworking.registerGlobalReceiver(SkaffyPayload.CORE, (payload, context) -> SkaffyConnection.receive(payload.data()));

		for (CustomPacketPayload.Type<SkaffyPayload> type : SkaffyPayload.FEATURES) {
			String channel = type.id().toString();
			ClientConfigurationNetworking.registerGlobalReceiver(type, (payload, context) -> SkaffyConnection.receiveFeature(channel, payload.data()));
			ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> SkaffyConnection.receiveFeature(channel, payload.data()));
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientKeybinds.tick();
			ClientEntityModels.tick();
			me.skaffy.client.animation.ClientAnimations.tick();
		});

		ClientConfigurationConnectionEvents.START.register((listener, client) -> SkaffyConnection.startConfiguration(client));
		ClientConfigurationConnectionEvents.COMPLETE.register((listener, client) -> SkaffyConnection.completeConfiguration());
		ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> SkaffyConnection.disconnect());
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> SkaffyConnection.startPlay(client, sender));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> SkaffyConnection.disconnect());
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
