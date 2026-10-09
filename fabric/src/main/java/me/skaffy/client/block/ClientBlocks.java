package me.skaffy.client.block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.pack.PackBuilder;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.blocks.BlockDefinition;
import me.skaffy.protocol.blocks.BlocksCodec;
import me.skaffy.protocol.blocks.BlocksPacket;
import me.skaffy.protocol.blocks.BlocksPacket.DefineBlocks;
import me.skaffy.protocol.blocks.BlocksPacket.SetBlocks;
import me.skaffy.protocol.blocks.BlocksPacket.StopMining;
import me.skaffy.protocol.blocks.Positions;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class ClientBlocks {
	private static final List<BlockDefinition> PENDING = new ArrayList<>();
	private static boolean loaded;

	private ClientBlocks() {
	}

	public static void receive(byte[] data, boolean registering) {
		BlocksPacket packet;

		try {
			packet = BlocksCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed blocks packet: {}", e.getMessage());
			return;
		}

		switch (packet) {
			case DefineBlocks define -> {
				if (registering) {
					synchronized (PENDING) {
						PENDING.addAll(define.blocks());
					}
				}
			}
			case SetBlocks set -> Minecraft.getInstance().execute(() -> {
				if (loaded) {
					WorldBlocks.apply(set);
				}
			});
			case StopMining stop -> Minecraft.getInstance().execute(() -> BlockMining.stop(BlockPos.of(stop.blockPos())));
			default -> {
			}
		}
	}

	public static CompletableFuture<Void> load(PackBuilder pack) {
		List<BlockDefinition> definitions;

		synchronized (PENDING) {
			definitions = List.copyOf(PENDING);
			PENDING.clear();
		}

		if (definitions.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}

		BlockModels models = new BlockModels(pack);
		List<CustomBlockSpec> specs = new ArrayList<>(definitions.size());
		List<String> modelIds = new ArrayList<>(definitions.size());

		for (int i = 0; i < definitions.size(); i++) {
			BlockDefinition definition = definitions.get(i);
			specs.add(new CustomBlockSpec(i + 1, definition, models));
			modelIds.add(models.exportModel(definition.model(), definition.transparency() == BlockDefinition.Transparency.TRANSLUCENT));
		}

		return CompletableFuture.runAsync(() -> {
			List<CustomBlock> blocks = BlockRegistrar.register(specs);

			for (int i = 0; i < blocks.size(); i++) {
				Identifier id = BlockRegistrar.blockId(i + 1);
				models.addFile(id.withPath("blockstates/" + id.getPath() + ".json"), blockstate(specs.get(i).definition(), modelIds.get(i)));
			}

			WorldBlocks.setBlocks(blocks);
		}, Minecraft.getInstance());
	}

	public static void activate() {
		loaded = true;
	}

	public static void unload() {
		synchronized (PENDING) {
			PENDING.clear();
		}

		Minecraft.getInstance().execute(() -> {
			loaded = false;
			WorldBlocks.clear();
			BlockRegistrar.unregister();
		});
	}

	private static String blockstate(BlockDefinition definition, String model) {
		JsonObject variants = new JsonObject();

		if (!definition.rotatable()) {
			JsonObject variant = new JsonObject();
			variant.addProperty("model", model);
			variants.add("", variant);
		} else {
			for (int x = 0; x < 4; x++) {
				for (int y = 0; y < 4; y++) {
					JsonObject variant = new JsonObject();
					variant.addProperty("model", model);

					if (x > 0) {
						variant.addProperty("x", x * 90);
					}

					if (y > 0) {
						variant.addProperty("y", y * 90);
					}

					variants.add("x=" + x + ",y=" + y, variant);
				}
			}
		}

		JsonObject blockstate = new JsonObject();
		blockstate.add("variants", variants);
		return blockstate.toString();
	}

	static long packed(BlockPos pos) {
		return Positions.block(pos.getX(), pos.getY(), pos.getZ());
	}
}
