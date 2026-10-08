package me.skaffy.client.model;

import java.util.List;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public record AnimationClip(String name, float length, Loop loop, boolean overridePrevious, List<BoneTrack> bones, List<MorphTrack> morphs, List<Effect> effects,
		List<Marker> markers) {
	public enum Loop {
		ONCE,
		LOOP,
		HOLD
	}

	public enum Kind {
		ROTATION_ADD,
		POSITION_ADD,
		SCALE_MULTIPLY,
		ROTATION_SET,
		POSITION_SET,
		SCALE_SET
	}

	public record BoneTrack(String bone, @Nullable Channel rotation, @Nullable Channel position, @Nullable Channel scale) {
	}

	public record MorphTrack(int mesh, SampledChannel weights) {
	}

	public enum EffectKind {
		SOUND,
		PARTICLE
	}

	public record Effect(float time, EffectKind kind, String effect, String locator) {
	}

	public record Marker(float time, String text) {
	}

	public sealed interface Channel permits MolangChannel, SampledChannel {
		Kind kind();

		void sample(float time, Molang.Scope scope, float[] out);

		float lastTime();
	}

	public enum Lerp {
		LINEAR,
		CATMULLROM,
		STEP,
		BEZIER
	}

	public record Keyframe(float time, Molang.Expression[] pre, Molang.Expression[] post, Lerp lerp, float @Nullable [] leftTime, float @Nullable [] leftValue,
			float @Nullable [] rightTime, float @Nullable [] rightValue) {
		public Keyframe(float time, Molang.Expression[] pre, Molang.Expression[] post, Lerp lerp) {
			this(time, pre, post, lerp, null, null, null, null);
		}
	}

	public record MolangChannel(Kind kind, List<Keyframe> keyframes) implements Channel {
		@Override
		public float lastTime() {
			return keyframes.getLast().time();
		}

		@Override
		public void sample(float time, Molang.Scope scope, float[] out) {
			List<Keyframe> frames = keyframes;

			if (frames.size() == 1) {
				evaluate(frames.getFirst().post(), scope, out);
				return;
			}

			if (time <= frames.getFirst().time()) {
				evaluate(time < frames.getFirst().time() ? frames.getFirst().pre() : frames.getFirst().post(), scope, out);
				return;
			}

			if (time >= frames.getLast().time()) {
				evaluate(frames.getLast().post(), scope, out);
				return;
			}

			int next = 1;

			while (frames.get(next).time() <= time) {
				next++;
			}

			Keyframe from = frames.get(next - 1);
			Keyframe to = frames.get(next);

			if (from.lerp() == Lerp.STEP) {
				evaluate(from.post(), scope, out);
				return;
			}

			float progress = (time - from.time()) / (to.time() - from.time());
			float[] a = new float[3];
			float[] b = new float[3];
			evaluate(from.post(), scope, a);
			evaluate(to.pre(), scope, b);

			if (from.lerp() == Lerp.LINEAR && (to.lerp() == Lerp.LINEAR || to.lerp() == Lerp.STEP)) {
				for (int i = 0; i < 3; i++) {
					out[i] = a[i] + (b[i] - a[i]) * progress;
				}
			} else if (from.lerp() == Lerp.CATMULLROM || to.lerp() == Lerp.CATMULLROM) {
				float[] before = new float[3];
				float[] after = new float[3];
				evaluate(next >= 2 ? frames.get(next - 2).post() : from.post(), scope, before);
				evaluate(next + 1 < frames.size() ? frames.get(next + 1).pre() : to.pre(), scope, after);

				for (int i = 0; i < 3; i++) {
					out[i] = catmullRom(progress, before[i], a[i], b[i], after[i]);
				}
			} else {
				float gap = to.time() - from.time();

				for (int i = 0; i < 3; i++) {
					float rightTime = from.rightTime() == null ? 0.1f : from.rightTime()[i];
					float rightValue = from.rightValue() == null ? 0 : from.rightValue()[i];
					float leftTime = to.leftTime() == null ? -0.1f : to.leftTime()[i];
					float leftValue = to.leftValue() == null ? 0 : to.leftValue()[i];
					out[i] = bezier(time, from.time(), a[i], from.time() + Math.clamp(rightTime, 0, gap), a[i] + rightValue,
							to.time() + Math.clamp(leftTime, -gap, 0), b[i] + leftValue, to.time(), b[i]);
				}
			}
		}

		private static void evaluate(Molang.Expression[] values, Molang.Scope scope, float[] out) {
			for (int i = 0; i < 3; i++) {
				out[i] = values[i].evaluate(scope);
			}
		}

		private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
			return 0.5f * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t);
		}

		static float bezier(float time, float t0, float v0, float t1, float v1, float t2, float v2, float t3, float v3) {
			float low = 0;
			float high = 1;
			float s = 0.5f;

			for (int i = 0; i < 24; i++) {
				s = (low + high) / 2;
				float x = cubic(s, t0, t1, t2, t3);

				if (x < time) {
					low = s;
				} else {
					high = s;
				}
			}

			return cubic(s, v0, v1, v2, v3);
		}

		private static float cubic(float s, float p0, float p1, float p2, float p3) {
			float r = 1 - s;
			return r * r * r * p0 + 3 * r * r * s * p1 + 3 * r * s * s * p2 + s * s * s * p3;
		}
	}

	public enum Interpolation {
		LINEAR,
		STEP,
		CUBICSPLINE
	}

	public record SampledChannel(Kind kind, float[] times, float[] values, int components, Interpolation interpolation) implements Channel {
		@Override
		public float lastTime() {
			return times.length == 0 ? 0 : times[times.length - 1];
		}

		@Override
		public void sample(float time, Molang.Scope scope, float[] out) {
			int count = times.length;

			if (count == 0) {
				return;
			}

			if (time <= times[0] || count == 1) {
				value(0, out);
				return;
			}

			if (time >= times[count - 1]) {
				value(count - 1, out);
				return;
			}

			int next = 1;

			while (times[next] <= time) {
				next++;
			}

			int previous = next - 1;
			float gap = times[next] - times[previous];
			float t = gap <= 0 ? 0 : (time - times[previous]) / gap;

			switch (interpolation) {
				case STEP -> value(previous, out);
				case LINEAR -> {
					if (kind == Kind.ROTATION_SET && components == 4) {
						Quaternionf a = new Quaternionf(values[previous * 4], values[previous * 4 + 1], values[previous * 4 + 2], values[previous * 4 + 3]);
						Quaternionf b = new Quaternionf(values[next * 4], values[next * 4 + 1], values[next * 4 + 2], values[next * 4 + 3]);
						a.slerp(b, t);
						out[0] = a.x;
						out[1] = a.y;
						out[2] = a.z;
						out[3] = a.w;
					} else {
						for (int i = 0; i < components; i++) {
							float a = values[previous * components + i];
							out[i] = a + (values[next * components + i] - a) * t;
						}
					}
				}
				case CUBICSPLINE -> {
					float t2 = t * t;
					float t3 = t2 * t;
					float h00 = 2 * t3 - 3 * t2 + 1;
					float h10 = t3 - 2 * t2 + t;
					float h01 = -2 * t3 + 3 * t2;
					float h11 = t3 - t2;
					int stride = components * 3;

					for (int i = 0; i < components; i++) {
						float p0 = values[previous * stride + components + i];
						float m0 = values[previous * stride + components * 2 + i] * gap;
						float p1 = values[next * stride + components + i];
						float m1 = values[next * stride + i] * gap;
						out[i] = h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1;
					}

					if (kind == Kind.ROTATION_SET && components == 4) {
						float length = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2] + out[3] * out[3]);

						if (length > 0) {
							for (int i = 0; i < 4; i++) {
								out[i] /= length;
							}
						}
					}
				}
			}
		}

		private void value(int key, float[] out) {
			int offset = interpolation == Interpolation.CUBICSPLINE ? key * components * 3 + components : key * components;
			System.arraycopy(values, offset, out, 0, components);
		}
	}

	public @Nullable BoneTrack track(String bone) {
		for (BoneTrack track : bones) {
			if (track.bone().equals(bone)) {
				return track;
			}
		}

		return null;
	}

	public static AnimationClip empty(String name) {
		return new AnimationClip(name, 0, Loop.ONCE, false, List.of(), List.of(), List.of(), List.of());
	}
}
