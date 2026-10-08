package me.skaffy.paper.particle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import me.skaffy.api.particle.CustomParticleType;
import me.skaffy.api.particle.CustomParticles;
import me.skaffy.api.particle.ParticleColorCurve;
import me.skaffy.api.particle.ParticleCurve;
import me.skaffy.api.particle.ParticleSpawn;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.io.PacketWriter;
import me.skaffy.protocol.particles.ColorCurve;
import me.skaffy.protocol.particles.Curve;
import me.skaffy.protocol.particles.Easing;
import me.skaffy.protocol.particles.ParticleDefinition;
import me.skaffy.protocol.particles.ParticleMotion;
import me.skaffy.protocol.particles.ParticlesCodec;
import me.skaffy.protocol.particles.ParticlesPacket.DefineParticles;
import me.skaffy.protocol.particles.ParticlesPacket.Rotation;
import me.skaffy.protocol.particles.ParticlesPacket.SpawnParticles;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class PaperCustomParticles implements CustomParticles {
	private static final int DEFINITIONS_BUDGET = 512 * 1024;
	private static final double RANGE = 32;
	private static final double FORCE_RANGE = 512;

	private final Plugin plugin;
	private final Function<UUID, ParticleSession> sessions;
	private final Map<String, CustomParticleType> types = new ConcurrentHashMap<>();

	public PaperCustomParticles(Plugin plugin, Function<UUID, ParticleSession> sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
	}

	@Override
	public void register(CustomParticleType type) {
		try {
			definition(type);
		} catch (ProtocolException e) {
			throw new IllegalArgumentException("Particle " + type.getName() + ": " + e.getMessage(), e);
		}

		types.put(type.getName(), type);
	}

	@Override
	public boolean unregister(String name) {
		return types.remove(name) != null;
	}

	@Override
	public Optional<CustomParticleType> getType(String name) {
		return Optional.ofNullable(types.get(name));
	}

	@Override
	public Collection<CustomParticleType> getTypes() {
		return List.copyOf(types.values());
	}

	@Override
	public void spawn(ParticleSpawn spawn) {
		Location location = spawn.getLocation();
		double range = spawn.isForce() ? FORCE_RANGE : RANGE;

		for (Player player : location.getWorld().getPlayers()) {
			if (player.getLocation().distanceSquared(location) <= range * range) {
				send(player, spawn);
			}
		}
	}

	@Override
	public void spawn(Collection<? extends Player> players, ParticleSpawn spawn) {
		for (Player player : players) {
			if (player.getWorld().equals(spawn.getLocation().getWorld())) {
				send(player, spawn);
			}
		}
	}

	public void register(ParticleSession session) {
		List<CustomParticleType> snapshot = List.copyOf(types.values());
		session.setParticleTypes(snapshot);
		List<ParticleDefinition> part = new ArrayList<>();
		int partSize = 0;

		for (CustomParticleType type : snapshot) {
			ParticleDefinition definition = definition(type);
			PacketWriter measure = new PacketWriter();
			definition.write(measure);

			if (!part.isEmpty() && partSize + measure.size() > DEFINITIONS_BUDGET) {
				session.sendParticleDefinition(ParticlesCodec.encode(new DefineParticles(part)));
				part = new ArrayList<>();
				partSize = 0;
			}

			part.add(definition);
			partSize += measure.size();
		}

		if (!part.isEmpty()) {
			session.sendParticleDefinition(ParticlesCodec.encode(new DefineParticles(part)));
		}
	}

	private void send(Player player, ParticleSpawn spawn) {
		ParticleSession session = sessions.apply(player.getUniqueId());
		int number = session == null ? 0 : session.particleNumber(spawn.getType());

		if (number == 0) {
			return;
		}

		Location location = spawn.getLocation();
		Vector spread = spawn.getSpread();
		Vector velocity = spawn.getVelocity();
		SpawnParticles packet = new SpawnParticles(
				number,
				location.getX(),
				location.getY(),
				location.getZ(),
				spawn.getCount(),
				(float) spread.getX(),
				(float) spread.getY(),
				(float) spread.getZ(),
				SpawnParticles.Spread.valueOf(spawn.getSpreadShape().name()),
				(float) velocity.getX(),
				(float) velocity.getY(),
				(float) velocity.getZ(),
				spawn.isForce(),
				spawn.getColor() == null ? null : spawn.getColor().asRGB(),
				spawn.getSize(),
				spawn.getLifetime(),
				spawn.getYaw() == null ? null : new Rotation(spawn.getYaw(), spawn.getPitch()));
		player.sendPluginMessage(plugin, ParticlesCodec.CHANNEL, ParticlesCodec.encode(packet));
	}

	private static ParticleDefinition definition(CustomParticleType type) {
		ParticleMotion motion = new ParticleMotion(
				type.getGravity(),
				type.getFriction(),
				type.collides(),
				type.diesOnGround(),
				type.getVelocityRandomness(),
				type.getWanderChance(),
				type.getWanderSpeed(),
				type.getSway(),
				type.getSwayPeriod(),
				type.getConverge() != null,
				type.getConverge() == null ? Easing.LINEAR : easing(type.getConverge()));
		return new ParticleDefinition(
				type.getName(),
				type.getTextures(),
				ParticleDefinition.FrameMode.valueOf(type.getFrameMode().name()),
				ParticleDefinition.RenderMode.valueOf(type.getRender().name()),
				ParticleDefinition.Facing.valueOf(type.getFacing().name()),
				type.getYaw(),
				type.getPitch(),
				type.getLight(),
				type.getMinSize(),
				type.getMaxSize(),
				type.getMinLifetime(),
				type.getMaxLifetime(),
				type.getColorVariation(),
				type.getMinSpin(),
				type.getMaxSpin(),
				type.hasRandomAngle(),
				curve(type.getSizeOverLife()),
				curve(type.getAlphaOverLife()),
				colorCurve(type.getColorOverLife()),
				curve(type.getSpinOverLife()),
				motion);
	}

	private static Curve curve(ParticleCurve curve) {
		if (curve == null) {
			return Curve.NONE;
		}

		List<ParticleCurve.Point> points = curve.getPoints();
		List<Curve.Point> converted = new ArrayList<>(points.size());

		for (int i = 0; i < points.size(); i++) {
			Easing next = i + 1 < points.size() ? easing(points.get(i + 1).easing()) : Easing.LINEAR;
			converted.add(new Curve.Point(points.get(i).time(), points.get(i).value(), next));
		}

		return new Curve(converted);
	}

	private static ColorCurve colorCurve(ParticleColorCurve curve) {
		if (curve == null) {
			return ColorCurve.NONE;
		}

		List<ParticleColorCurve.Point> points = curve.getPoints();
		List<ColorCurve.Point> converted = new ArrayList<>(points.size());

		for (int i = 0; i < points.size(); i++) {
			Easing next = i + 1 < points.size() ? easing(points.get(i + 1).easing()) : Easing.LINEAR;
			converted.add(new ColorCurve.Point(points.get(i).time(), points.get(i).color().asRGB(), next));
		}

		return new ColorCurve(converted);
	}

	private static Easing easing(me.skaffy.api.particle.Easing easing) {
		return Easing.valueOf(easing.name());
	}
}
