package me.skaffy.paper.model;

import java.util.ArrayList;
import java.util.List;

import me.skaffy.api.model.AnimationMode;

public final class AnimationTimers {
	public interface Listener {
		void marker(AnimationInfo.Marker marker);

		void end();
	}

	private static final class Timer {
		final Object owner;
		final String animation;
		final AnimationInfo.Clip clip;
		final AnimationInfo.Loop loop;
		final float speed;
		final long start;
		final Listener listener;
		float upTo = -1;
		boolean done;

		Timer(Object owner, String animation, AnimationInfo.Clip clip, AnimationInfo.Loop loop, float speed, long start, Listener listener) {
			this.owner = owner;
			this.animation = animation;
			this.clip = clip;
			this.loop = loop;
			this.speed = speed;
			this.start = start;
			this.listener = listener;
		}
	}

	private final List<Timer> timers = new ArrayList<>();

	public synchronized void start(Object owner, String animation, AnimationInfo.Clip clip, AnimationMode mode, float speed, float startSeconds, Listener listener) {
		stop(owner, animation);
		AnimationInfo.Loop loop = switch (mode) {
			case DEFAULT -> clip.loop();
			case ONCE -> AnimationInfo.Loop.ONCE;
			case LOOP -> AnimationInfo.Loop.LOOP;
			case HOLD -> AnimationInfo.Loop.HOLD;
		};

		if (clip.markers().isEmpty() && loop != AnimationInfo.Loop.ONCE) {
			return;
		}

		Timer timer = new Timer(owner, animation, clip, loop, speed, System.nanoTime() - (long) (startSeconds / speed * 1.0E9), listener);
		timer.upTo = startSeconds - 1.0E-4f;
		timers.add(timer);
	}

	public synchronized void stop(Object owner, String animation) {
		timers.removeIf(timer -> timer.owner.equals(owner) && timer.animation.equals(animation));
	}

	public synchronized void stopAll(Object owner) {
		timers.removeIf(timer -> timer.owner.equals(owner));
	}

	public void tick() {
		List<Runnable> due = new ArrayList<>();

		synchronized (this) {
			long now = System.nanoTime();

			for (Timer timer : timers) {
				float seconds = (float) ((now - timer.start) / 1.0E9 * timer.speed);
				float length = timer.clip.length();

				if (timer.loop == AnimationInfo.Loop.LOOP && length > 0) {
					float previous = timer.upTo;
					float time = seconds % length;

					if (previous >= 0 && time < previous) {
						collect(timer, previous, length, due);
						collect(timer, -1, time, due);
					} else {
						collect(timer, previous, time, due);
					}

					timer.upTo = time;
				} else {
					float time = Math.min(seconds, length);
					collect(timer, timer.upTo, time, due);
					timer.upTo = time;

					if (seconds >= length) {
						timer.done = true;

						if (timer.loop == AnimationInfo.Loop.ONCE) {
							Listener listener = timer.listener;
							due.add(listener::end);
						}
					}
				}
			}

			timers.removeIf(timer -> timer.done);
		}

		due.forEach(Runnable::run);
	}

	private static void collect(Timer timer, float after, float upTo, List<Runnable> due) {
		for (AnimationInfo.Marker marker : timer.clip.markers()) {
			if (marker.time() > after && marker.time() <= upTo) {
				Listener listener = timer.listener;
				due.add(() -> listener.marker(marker));
			}
		}
	}
}
