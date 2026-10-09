package me.skaffy.client.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.skaffy.client.shader.lang.ShType;
import me.skaffy.client.shader.lang.ShaderModule;
import me.skaffy.protocol.Easing;

final class UniformValues {
	private final Map<String, double[]> values = new HashMap<>();
	private final Map<String, Animation> animations = new HashMap<>();
	private ShaderModule module;
	private boolean dirty = true;

	private record Animation(double[] from, double[] to, long start, int duration, Easing easing) {
	}

	UniformValues(ShaderModule module) {
		use(module);
	}

	void use(ShaderModule module) {
		this.module = module;
		Map<String, double[]> kept = new HashMap<>();

		for (ShaderModule.Uniform uniform : module.uniforms()) {
			double[] old = values.get(uniform.name());
			kept.put(uniform.name(), old != null && old.length == uniform.components() ? old : uniform.defaults().clone());
		}

		values.clear();
		values.putAll(kept);
		animations.keySet().retainAll(values.keySet());
		dirty = true;
	}

	void reset() {
		values.clear();
		animations.clear();
		use(module);
	}

	double value(String name, double fallback) {
		double[] value = values.get(name);
		return value == null || value.length == 0 ? fallback : value[0];
	}

	double[] values(String name) {
		return values.get(name);
	}

	boolean consumeDirty() {
		boolean was = dirty;
		dirty = false;
		return was;
	}

	List<String> set(Map<String, Object> incoming, int duration, Easing easing, long now) {
		List<String> problems = new java.util.ArrayList<>();

		for (Map.Entry<String, Object> entry : incoming.entrySet()) {
			ShaderModule.Uniform uniform = find(entry.getKey());

			if (uniform == null) {
				problems.add(module.className() + " has no @Uniform " + entry.getKey());
				continue;
			}

			double[] target;

			try {
				target = convert(uniform, entry.getValue());
			} catch (IllegalArgumentException e) {
				problems.add(e.getMessage());
				continue;
			}

			dirty = true;

			if (duration <= 0) {
				animations.remove(uniform.name());
				values.put(uniform.name(), target);
			} else {
				animations.put(uniform.name(), new Animation(values.get(uniform.name()).clone(), target, now, duration, easing));
			}
		}

		return problems;
	}

	private ShaderModule.Uniform find(String name) {
		for (ShaderModule.Uniform uniform : module.uniforms()) {
			if (uniform.name().equals(name)) {
				return uniform;
			}
		}

		return null;
	}

	void tick(long now) {
		if (!animations.isEmpty()) {
			dirty = true;
		}

		animations.entrySet().removeIf(entry -> {
			Animation animation = entry.getValue();
			double t = Math.min(1, (now - animation.start) / (double) animation.duration);
			double eased = animation.easing.apply(t);
			ShaderModule.Uniform uniform = find(entry.getKey());
			double[] current = new double[animation.to.length];

			for (int i = 0; i < current.length; i++) {
				double value = animation.from[i] + (animation.to[i] - animation.from[i]) * eased;
				current[i] = switch (uniform == null ? ShType.Base.FLOAT : uniform.base()) {
					case FLOAT -> value;
					case INT -> Math.round(value);
					case BOOL -> t >= 1 ? animation.to[i] : animation.from[i];
				};
			}

			values.put(entry.getKey(), current);
			return t >= 1;
		});
	}

	void write(ByteBuffer buffer) {
		buffer.order(ByteOrder.nativeOrder());

		for (ShaderModule.Uniform uniform : module.uniforms()) {
			double[] value = values.get(uniform.name());

			if (uniform.matrix()) {
				for (int column = 0; column < uniform.size(); column++) {
					for (int row = 0; row < uniform.size(); row++) {
						buffer.putFloat(uniform.offset() + column * 16 + row * 4, (float) value[column * uniform.size() + row]);
					}
				}

				continue;
			}

			for (int i = 0; i < uniform.size(); i++) {
				int at = uniform.offset() + i * 4;

				if (uniform.base() == ShType.Base.FLOAT) {
					buffer.putFloat(at, (float) value[i]);
				} else {
					buffer.putInt(at, (int) value[i]);
				}
			}
		}
	}

	static double[] convert(ShaderModule.Uniform uniform, Object value) {
		int components = uniform.components();
		String what = "@Uniform " + uniform.name() + " is a " + uniform.typeName();

		switch (value) {
			case Boolean bool -> {
				if (components == 1) {
					return new double[] {bool ? 1 : 0};
				}
			}
			case Number number -> {
				if (components == 1) {
					double v = number.doubleValue();
					return new double[] {uniform.base() == ShType.Base.INT ? Math.round(v) : uniform.base() == ShType.Base.BOOL ? (v != 0 ? 1 : 0) : v};
				}

				if (number instanceof Integer argb && !uniform.matrix() && uniform.base() == ShType.Base.FLOAT && (components == 3 || components == 4)) {
					return color(argb, components);
				}
			}
			case String text -> {
				if (text.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})") && !uniform.matrix() && uniform.base() == ShType.Base.FLOAT && (components == 3 || components == 4)) {
					int argb = (int) Long.parseLong(text.substring(1), 16);
					return color(text.length() == 7 ? 0xFF000000 | argb : argb, components);
				}
			}
			case List<?> list -> {
				if (list.size() == components) {
					double[] values = new double[components];

					for (int i = 0; i < components; i++) {
						Object element = list.get(i);

						if (element instanceof Number number) {
							values[i] = uniform.base() == ShType.Base.INT ? Math.round(number.doubleValue()) : number.doubleValue();
						} else if (element instanceof Boolean bool) {
							values[i] = bool ? 1 : 0;
						} else {
							throw new IllegalArgumentException(what + "; its values are numbers");
						}

						if (uniform.base() == ShType.Base.BOOL) {
							values[i] = values[i] != 0 ? 1 : 0;
						}
					}

					return values;
				}
			}
			default -> {
			}
		}

		String expected = components == 1 ? "one value" : components + " numbers" + (uniform.matrix() ? " (column by column)" : "")
				+ (!uniform.matrix() && uniform.base() == ShType.Base.FLOAT && components >= 3 ? " or a color like #FF8800" : "");
		throw new IllegalArgumentException(what + ", send " + expected);
	}

	private static double[] color(int argb, int components) {
		double[] rgba = {(argb >> 16 & 0xFF) / 255.0, (argb >> 8 & 0xFF) / 255.0, (argb & 0xFF) / 255.0, (argb >>> 24) / 255.0};
		return components == 3 ? new double[] {rgba[0], rgba[1], rgba[2]} : rgba;
	}
}
