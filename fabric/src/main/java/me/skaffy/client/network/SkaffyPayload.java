package me.skaffy.client.network;

import java.util.List;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.blockshapes.BlockShapesCodec;
import me.skaffy.protocol.brightness.BrightnessCodec;
import me.skaffy.protocol.entitymodels.EntityModelsCodec;
import me.skaffy.protocol.fov.FovCodec;
import me.skaffy.protocol.gui.GuiCodec;
import me.skaffy.protocol.keybinds.KeybindsCodec;
import me.skaffy.protocol.nametags.NameTagsCodec;
import me.skaffy.protocol.particles.ParticlesCodec;
import me.skaffy.protocol.perspective.PerspectiveCodec;
import me.skaffy.protocol.playerlooks.PlayerLooksCodec;
import me.skaffy.protocol.shaders.ShadersCodec;
import me.skaffy.protocol.shapes.ShapesCodec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SkaffyPayload(Type<SkaffyPayload> type, byte[] data) implements CustomPacketPayload {
	public static final Type<SkaffyPayload> CORE = new Type<>(Identifier.parse(Protocol.CORE_CHANNEL));
	public static final Type<SkaffyPayload> BLOCKS = new Type<>(Identifier.parse(BlocksCodec.CHANNEL));
	public static final Type<SkaffyPayload> PARTICLES = new Type<>(Identifier.parse(ParticlesCodec.CHANNEL));
	public static final Type<SkaffyPayload> KEYBINDS = new Type<>(Identifier.parse(KeybindsCodec.CHANNEL));
	public static final Type<SkaffyPayload> ENTITY_MODELS = new Type<>(Identifier.parse(EntityModelsCodec.CHANNEL));
	public static final Type<SkaffyPayload> FOV = new Type<>(Identifier.parse(FovCodec.CHANNEL));
	public static final Type<SkaffyPayload> NAME_TAGS = new Type<>(Identifier.parse(NameTagsCodec.CHANNEL));
	public static final Type<SkaffyPayload> GUI = new Type<>(Identifier.parse(GuiCodec.CHANNEL));
	public static final Type<SkaffyPayload> PERSPECTIVE = new Type<>(Identifier.parse(PerspectiveCodec.CHANNEL));
	public static final Type<SkaffyPayload> BRIGHTNESS = new Type<>(Identifier.parse(BrightnessCodec.CHANNEL));
	public static final Type<SkaffyPayload> PLAYER_LOOKS = new Type<>(Identifier.parse(PlayerLooksCodec.CHANNEL));
	public static final Type<SkaffyPayload> SHADERS = new Type<>(Identifier.parse(ShadersCodec.CHANNEL));
	public static final Type<SkaffyPayload> BLOCK_SHAPES = new Type<>(Identifier.parse(BlockShapesCodec.CHANNEL));
	public static final Type<SkaffyPayload> SHAPES = new Type<>(Identifier.parse(ShapesCodec.CHANNEL));
	public static final Type<SkaffyPayload> ANIMATIONS = new Type<>(Identifier.parse(me.skaffy.protocol.animations.AnimationsCodec.CHANNEL));
	public static final List<Type<SkaffyPayload>> FEATURES = List.of(BLOCKS, PARTICLES, KEYBINDS, ENTITY_MODELS, FOV, NAME_TAGS, GUI, PERSPECTIVE, BRIGHTNESS, PLAYER_LOOKS, SHADERS, BLOCK_SHAPES, SHAPES, ANIMATIONS);

	public static StreamCodec<FriendlyByteBuf, SkaffyPayload> codec(Type<SkaffyPayload> type) {
		return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
			byte[] data = new byte[buf.readableBytes()];
			buf.readBytes(data);
			return new SkaffyPayload(type, data);
		});
	}
}
