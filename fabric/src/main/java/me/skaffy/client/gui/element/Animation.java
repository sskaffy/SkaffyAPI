package me.skaffy.client.gui.element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Animation implements PropTarget {
	private final Element element;
	private final List<Step> steps = new ArrayList<>();
	private Step building;
	private int cycles = 1;
	private boolean pingPong;
	private Consumer<Object> onDone;

	private boolean started;
	private boolean finished;
	private int stepIndex;
	private long stepStart;
	private int cycle;
	private final Object[] stepFrom = new Object[Prop.COUNT];

	private static final class Step {
		final long duration;
		long delay;
		Easing easing = Easing.EASE_OUT_QUAD;
		final PropSet targets = new PropSet();
		final Object[] origin = new Object[Prop.COUNT];

		Step(long duration) {
			this.duration = Math.max(0, duration);
		}
	}

	Animation(Element element, long durationMillis) {
		this.element = element;
		building = new Step(durationMillis);
		steps.add(building);
	}

	@Override
	public void set(Prop prop, Object value) {
		building.targets.put(prop, value);
	}

	public Animation ease(Easing easing) {
		building.easing = easing;
		return this;
	}

	public Animation delay(long millis) {
		building.delay = Math.max(0, millis);
		return this;
	}

	public Animation then(long durationMillis) {
		building = new Step(durationMillis);
		steps.add(building);
		return this;
	}

	public Animation loop() {
		cycles = 0;
		return this;
	}

	public Animation repeat(int times) {
		cycles = Math.max(1, times);
		return this;
	}

	public Animation pingPong() {
		pingPong = true;

		if (cycles == 1) {
			cycles = 2;
		}

		return this;
	}

	public Animation onDone(Consumer<Object> handler) {
		this.onDone = handler;
		return this;
	}

	public void cancel() {
		if (finished) {
			return;
		}

		finished = true;

		for (Prop prop : Prop.ALL) {
			if (started && current().targets.has(prop)) {
				element.base[prop.ordinal()] = element.shown[prop.ordinal()];
			}
		}
	}

	public boolean isFinished() {
		return finished;
	}

	private Step current() {
		boolean backwards = pingPong && cycle % 2 == 1;
		return steps.get(backwards ? steps.size() - 1 - stepIndex : stepIndex);
	}

	private boolean backwards() {
		return pingPong && cycle % 2 == 1;
	}

	void update(long now) {
		if (finished) {
			return;
		}

		if (!started) {
			started = true;
			beginStep(now);
		}

		int guard = 0;

		while (!finished && guard++ < 1000) {
			Step step = current();
			double elapsed = (now - stepStart) / 1_000_000.0 - step.delay;

			if (elapsed < 0) {
				apply(step, 0);
				return;
			}

			if (elapsed < step.duration) {
				apply(step, step.easing.apply(elapsed / step.duration));
				return;
			}

			apply(step, 1);
			commit(step);
			stepStart += (long) ((step.delay + step.duration) * 1_000_000L);

			if (step.delay + step.duration == 0) {
				stepStart = now;
			}

			stepIndex++;

			if (stepIndex >= steps.size()) {
				stepIndex = 0;
				cycle++;

				if (cycles != 0 && cycle >= cycles) {
					finished = true;

					if (onDone != null) {
						onDone.accept(null);
					}

					return;
				}

				if (cycles == 0 && totalDuration() == 0) {
					finished = true;
					return;
				}
			}

			beginStep(stepStart);
		}
	}

	private long totalDuration() {
		long total = 0;

		for (Step step : steps) {
			total += step.delay + step.duration;
		}

		return total;
	}

	private void beginStep(long now) {
		stepStart = now;
		Step step = current();

		for (Prop prop : Prop.ALL) {
			if (step.targets.has(prop)) {
				Object from = element.currentValue(prop);
				stepFrom[prop.ordinal()] = from;

				if (!backwards() && cycle == 0) {
					step.origin[prop.ordinal()] = from;
				}
			}
		}
	}

	private Object target(Step step, Prop prop) {
		return backwards() ? step.origin[prop.ordinal()] : step.targets.get(prop);
	}

	private void apply(Step step, double t) {
		for (Prop prop : Prop.ALL) {
			if (step.targets.has(prop)) {
				Object to = element.resolveAuto(prop, target(step, prop));
				Object from = element.resolveAuto(prop, stepFrom[prop.ordinal()]);
				element.shown[prop.ordinal()] = t >= 1 ? target(step, prop) : prop.lerp(from, to, t);
				element.animated[prop.ordinal()] = true;
			}
		}
	}

	private void commit(Step step) {
		for (Prop prop : Prop.ALL) {
			if (step.targets.has(prop)) {
				Object value = target(step, prop);
				element.base[prop.ordinal()] = value;
				element.lastTarget[prop.ordinal()] = element.targetValue(prop);
			}
		}
	}
}
