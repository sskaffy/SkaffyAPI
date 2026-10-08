package me.skaffy.client.mixin;

import me.skaffy.client.block.WorldBlocks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Shadow
	private ClientLevel level;

	@Inject(
			method = "handleLevelChunkWithLight",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;replaceWithPacketData(IILnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;)Lnet/minecraft/world/level/chunk/LevelChunk;"))
	private void skaffy$chunkReplaced(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		WorldBlocks.chunkReplaced(level, packet.x(), packet.z());
	}

	@Inject(method = "handleForgetLevelChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;drop(Lnet/minecraft/world/level/ChunkPos;)V"))
	private void skaffy$chunkForgotten(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
		WorldBlocks.chunkReplaced(level, packet.pos().x(), packet.pos().z());
	}

	@Inject(method = "applyLightData", at = @At("TAIL"))
	private void skaffy$lightReplaced(int x, int z, ClientboundLightUpdatePacketData lightData, boolean scheduleRebuild, CallbackInfo ci) {
		WorldBlocks.lightReplaced(level, x, z);
	}
}
