package me.skaffy.client.particle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.pack.PackBuilder;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.particles.ParticleDefinition;
import me.skaffy.protocol.particles.ParticlesCodec;
import me.skaffy.protocol.particles.ParticlesPacket;
import me.skaffy.protocol.particles.ParticlesPacket.DefineParticles;
import me.skaffy.protocol.particles.ParticlesPacket.SpawnParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class ClientParticles {
	private static final double VANILLA_RANGE_SQUARED = 32 * 32;
	private static final List<ParticleDefinition> PENDING = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static volatile List<Type> prepared = List.of();
	private static volatile List<Type> types = List.of();

	private ClientParticles() {
	}

	public static void receive(byte[] data, boolean registering) {
		ParticlesPacket packet;

		try {
			packet = ParticlesCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed particles packet: {}", e.getMessage());
			return;
		}

		switch (packet) {
			case DefineParticles define -> {
				if (registering) {
					synchronized (PENDING) {
						PENDING.addAll(define.particles());
					}
				}
			}
			case SpawnParticles spawn -> Minecraft.getInstance().execute(() -> spawn(spawn));
		}
	}

	public static void load(PackBuilder pack) {
		List<ParticleDefinition> definitions;

		synchronized (PENDING) {
			definitions = List.copyOf(PENDING);
			PENDING.clear();
		}

		Map<String, Identifier> exported = new HashMap<>();
		List<Type> loaded = new ArrayList<>(definitions.size());

		for (ParticleDefinition definition : definitions) {
			List<Identifier> sprites = definition.textures().stream().map(texture -> exported.computeIfAbsent(texture, reference -> exportTexture(pack, reference))).toList();
			loaded.add(new Type(definition, sprites));
		}

		prepared = loaded;
	}

	public static void activate() {
		types = prepared;
		prepared = List.of();
	}

	public static void unload() {
		synchronized (PENDING) {
			PENDING.clear();
		}

		prepared = List.of();
		types = List.of();
	}

	public static boolean spawnNamed(String name, double x, double y, double z) {
		List<Type> current = types;

		for (int i = 0; i < current.size(); i++) {
			if (current.get(i).definition().name().equals(name)) {
				spawn(new SpawnParticles(i + 1, x, y, z, 1, 0, 0, 0, SpawnParticles.Spread.GAUSSIAN, 0, 0, 0, false, null, null, null, null));
				return true;
			}
		}

		return false;
	}

	private static void spawn(SpawnParticles spawn) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		List<Type> current = types;

		if (level == null || spawn.particle() > current.size()) {
			return;
		}

		ParticleStatus setting = minecraft.options.particles().get();

		if (!spawn.force() && setting == ParticleStatus.MINIMAL) {
			return;
		}

		Type type = current.get(spawn.particle() - 1);
		TextureAtlas atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
		TextureAtlasSprite[] sprites = type.sprites().stream().map(atlas::getSprite).toArray(TextureAtlasSprite[]::new);
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();

		for (int i = 0; i < spawn.count(); i++) {
			double x = spawn.x() + offset(spawn.spread(), spawn.spreadX());
			double y = spawn.y() + offset(spawn.spread(), spawn.spreadY());
			double z = spawn.z() + offset(spawn.spread(), spawn.spreadZ());

			if (!spawn.force() && (camera.distanceToSqr(x, y, z) > VANILLA_RANGE_SQUARED || setting == ParticleStatus.DECREASED && RANDOM.nextInt(3) == 0)) {
				continue;
			}

			minecraft.particleEngine.add(new CustomParticle(level, type.definition(), sprites, spawn, x, y, z));
		}
	}

	private static double offset(SpawnParticles.Spread spread, float distance) {
		if (distance == 0) {
			return 0;
		}

		return switch (spread) {
			case GAUSSIAN -> RANDOM.nextGaussian() * distance;
			case BOX -> (RANDOM.nextDouble() * 2 - 1) * distance;
		};
	}

	private static Identifier exportTexture(PackBuilder pack, String reference) {
		boolean isAsset = pack.hasAsset(reference);
		Identifier vanillaId = PackBuilder.vanillaId(reference);
		String path = isAsset ? "a/" + reference : "v/" + vanillaId.getNamespace() + "/" + vanillaId.getPath();
		byte[] image = isAsset ? pack.readAsset(reference) : pack.readVanilla("textures/particle", reference, ".png");
		byte[] animation = isAsset ? pack.readAsset(reference + ".mcmeta") : pack.readVanilla("textures/particle", reference, ".png.mcmeta");

		if (image == null) {
			SkaffySAPIClient.LOGGER.warn("Server particle texture {} not found", reference);
		} else {
			pack.add(pack.id("textures/particle/" + path + ".png"), image);

			if (animation != null) {
				pack.add(pack.id("textures/particle/" + path + ".png.mcmeta"), animation);
			}
		}

		return pack.id(path);
	}

	private record Type(ParticleDefinition definition, List<Identifier> sprites) {
	}
}
