package me.skaffy.client.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.particle.ClientParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class ModelEffects {
	private static final Map<String, Optional<ParticleOptions>> PARTICLES = new HashMap<>();

	private ModelEffects() {
	}

	public static void play(AnimationClip.Effect effect, double x, double y, double z) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null) {
			return;
		}

		switch (effect.kind()) {
			case SOUND -> {
				Identifier id = Identifier.tryParse(effect.effect().trim());

				if (id != null) {
					level.playLocalSound(x, y, z, SoundEvent.createVariableRangeEvent(id), SoundSource.NEUTRAL, 1, 1, false);
				}
			}
			case PARTICLE -> {
				if (ClientParticles.spawnNamed(effect.effect(), x, y, z)) {
					return;
				}

				PARTICLES.computeIfAbsent(effect.effect(), text -> {
					try {
						return Optional.of(ParticleArgument.readParticle(new StringReader(text), level.registryAccess()));
					} catch (CommandSyntaxException | RuntimeException e) {
						SkaffySAPIClient.LOGGER.warn("Animation particle {} isn't a server or vanilla particle", text);
						return Optional.empty();
					}
				}).ifPresent(options -> level.addParticle(options, x, y, z, 0, 0, 0));
			}
		}
	}

	public static void clear() {
		PARTICLES.clear();
	}
}
