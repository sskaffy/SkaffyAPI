package me.skaffy.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.skaffy.protocol.entitymodels.EntityModelsPacket.PlayAnimation.Mode;

import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.jspecify.annotations.Nullable;

public final class AnimationPlayer {
	public static final int MAX_LAYERS = 16;
	private static final float WALK_SPEED = 0.05f;

	public interface Effects {
		void play(AnimationClip.Effect effect);
	}

	public record Active(AnimationClip clip, boolean replacesVanilla, float time, float weight) {
	}

	private final ClientModel model;
	private final List<Layer> layers = new ArrayList<>();
	private final Map<String, Float> variables = new HashMap<>();
	private @Nullable Layer base;
	private @Nullable Layer automatic;
	private int baseNumber;
	private int lastHurtTime;
	private boolean wasSwinging;
	private boolean dead;

	public AnimationPlayer(ClientModel model) {
		this.model = model;
	}

	public ClientModel model() {
		return model;
	}

	public static double now() {
		return Util.getNanos() / 1.0E9;
	}

	public Map<String, Float> variables() {
		return variables;
	}

	public void setVariable(String name, float value) {
		variables.put(name, value);
	}

	public void play(int number, Mode mode, float speed, float start, float fadeIn, boolean removeWhenDone, double now) {
		if (number < 1 || number > model.animationCount()) {
			return;
		}

		layers.removeIf(layer -> layer.number == number);
		layers.add(new Layer(number, mode, speed, now - start / speed, fadeIn, removeWhenDone));

		while (layers.size() > MAX_LAYERS) {
			layers.removeFirst();
		}
	}

	public void stop(int number, float fadeOut, double now) {
		for (Layer layer : layers) {
			if ((number == 0 || layer.number == number) && Double.isNaN(layer.fadeOutStart)) {
				layer.fadeOutStart = now;
				layer.fadeOut = fadeOut;
			}
		}

		layers.removeIf(layer -> (number == 0 || layer.number == number) && fadeOut <= 0);
	}

	public void clear() {
		layers.clear();
		base = null;
		automatic = null;
		baseNumber = 0;
	}

	public boolean tick(@Nullable Entity entity, double now, @Nullable Effects effects) {
		if (entity != null && model.hasAutomaticAnimations()) {
			tickAutomatic(entity, now);
		}

		boolean remove = false;

		for (Layer layer : allLayers()) {
			AnimationClip clip = model.clip(layer.number);

			if (clip == null) {
				continue;
			}

			if (effects != null && !clip.effects().isEmpty()) {
				layer.fireEffects(clip, now, effects);
			}

			if (layer.finished(clip, now)) {
				layer.done = true;
				remove |= layer.removeWhenDone && layer.loop(clip) != AnimationClip.Loop.LOOP;
			}
		}

		layers.removeIf(layer -> layer.done);

		if (automatic != null && automatic.done) {
			automatic = null;
		}

		return remove;
	}

	private List<Layer> allLayers() {
		List<Layer> all = new ArrayList<>(layers.size() + 2);

		if (base != null) {
			all.add(base);
		}

		if (automatic != null) {
			all.add(automatic);
		}

		all.addAll(layers);
		return all;
	}

	private void tickAutomatic(Entity entity, double now) {
		boolean moving;
		var definition = model.definition();

		if (entity instanceof LivingEntity living) {
			if (living.deathTime > 0 && !dead) {
				dead = true;
				base = null;
				baseNumber = 0;
				automatic = automatic(definition.death(), Mode.HOLD, now);
			}

			if (dead) {
				return;
			}

			boolean swinging = living.getCurrentSwing() != null;

			if (living.hurtTime > lastHurtTime && definition.hurt() > 0) {
				automatic = automatic(definition.hurt(), Mode.ONCE, now);
			} else if (swinging && !wasSwinging && definition.attack() > 0) {
				automatic = automatic(definition.attack(), Mode.ONCE, now);
			}

			lastHurtTime = living.hurtTime;
			wasSwinging = swinging;
			moving = living.walkAnimation.speed() > WALK_SPEED;
		} else {
			double dx = entity.getX() - entity.xo;
			double dz = entity.getZ() - entity.zo;
			moving = dx * dx + dz * dz > 1.0E-4;
		}

		int wanted = moving && definition.walk() > 0 ? definition.walk() : definition.idle();

		if (wanted != baseNumber) {
			baseNumber = wanted;
			base = automatic(wanted, Mode.LOOP, now);
		}
	}

	private @Nullable Layer automatic(int number, Mode mode, double now) {
		return number < 1 || number > model.animationCount() ? null : new Layer(number, mode, 1, now, 0, false);
	}

	public List<Active> active(double now) {
		List<Active> active = new ArrayList<>(layers.size() + 2);

		for (Layer layer : allLayers()) {
			AnimationClip clip = model.clip(layer.number);

			if (clip != null) {
				layer.addTo(active, clip, model.replacesVanilla(layer.number), now);
			}
		}

		return active;
	}

	public boolean isEmpty() {
		return layers.isEmpty() && base == null && automatic == null;
	}

	private static final class Layer {
		final int number;
		final Mode mode;
		final float speed;
		final double start;
		final float fadeIn;
		final boolean removeWhenDone;
		double fadeOutStart = Double.NaN;
		float fadeOut;
		float effectsUpTo = -1;
		boolean done;

		Layer(int number, Mode mode, float speed, double start, float fadeIn, boolean removeWhenDone) {
			this.number = number;
			this.mode = mode;
			this.speed = speed;
			this.start = start;
			this.fadeIn = fadeIn;
			this.removeWhenDone = removeWhenDone;
		}

		AnimationClip.Loop loop(AnimationClip clip) {
			return switch (mode) {
				case DEFAULT -> clip.loop();
				case ONCE -> AnimationClip.Loop.ONCE;
				case LOOP -> AnimationClip.Loop.LOOP;
				case HOLD -> AnimationClip.Loop.HOLD;
			};
		}

		float seconds(double now) {
			return (float) ((now - start) * speed);
		}

		float weight(double now) {
			float weight = 1;

			if (fadeIn > 0) {
				weight = (float) Math.min(1, (now - start) / fadeIn);
			}

			if (!Double.isNaN(fadeOutStart)) {
				weight = fadeOut <= 0 ? 0 : (float) Math.min(weight, 1 - (now - fadeOutStart) / fadeOut);
			}

			return Math.clamp(weight, 0, 1);
		}

		boolean finished(AnimationClip clip, double now) {
			if (!Double.isNaN(fadeOutStart) && (fadeOut <= 0 || now - fadeOutStart >= fadeOut)) {
				return true;
			}

			return loop(clip) == AnimationClip.Loop.ONCE && seconds(now) > clip.length();
		}

		void addTo(List<Active> active, AnimationClip clip, boolean replacesVanilla, double now) {
			float time = Math.max(0, seconds(now));
			float length = clip.length();
			AnimationClip.Loop loop = loop(clip);

			if (loop == AnimationClip.Loop.LOOP && length > 0) {
				time %= length;
			} else if (time > length) {
				if (loop == AnimationClip.Loop.ONCE) {
					return;
				}

				time = length;
			}

			float weight = weight(now);

			if (weight > 0) {
				active.add(new Active(clip, replacesVanilla || clip.overridePrevious(), time, weight));
			}
		}

		void fireEffects(AnimationClip clip, double now, Effects effects) {
			float length = clip.length();
			float raw = Math.max(0, seconds(now));
			AnimationClip.Loop loop = loop(clip);

			if (loop != AnimationClip.Loop.LOOP || length <= 0) {
				float time = Math.min(raw, length);
				fire(clip, effectsUpTo, time, effects);
				effectsUpTo = time;
				return;
			}

			float time = raw % length;
			float previousLoopTime = effectsUpTo < 0 ? -1 : effectsUpTo;

			if (effectsUpTo >= 0 && time < previousLoopTime) {
				fire(clip, previousLoopTime, length, effects);
				fire(clip, -1, time, effects);
			} else {
				fire(clip, previousLoopTime, time, effects);
			}

			effectsUpTo = time;
		}

		private static void fire(AnimationClip clip, float after, float upTo, Effects effects) {
			for (AnimationClip.Effect effect : clip.effects()) {
				if (effect.time() > after && effect.time() <= upTo) {
					effects.play(effect);
				}
			}
		}
	}
}
