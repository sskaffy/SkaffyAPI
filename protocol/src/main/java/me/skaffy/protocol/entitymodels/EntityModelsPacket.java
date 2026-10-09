package me.skaffy.protocol.entitymodels;

import java.util.List;

import me.skaffy.protocol.ProtocolException;

public sealed interface EntityModelsPacket {
	record DefineModels(List<ModelDefinition> models) implements EntityModelsPacket {
		public DefineModels {
			models = List.copyOf(models);
		}
	}

	record SetEntityModel(int entity, int model) implements EntityModelsPacket {
		public SetEntityModel {
			if (model < 0) {
				throw new ProtocolException("Invalid model " + model);
			}
		}
	}

	record PlayAnimation(int entity, int animation, Mode mode, float speed, float start, float fadeIn) implements EntityModelsPacket {
		public enum Mode {
			DEFAULT,
			ONCE,
			LOOP,
			HOLD
		}

		public PlayAnimation {
			if (animation < 1) {
				throw new ProtocolException("Invalid animation " + animation);
			}

			if (!(speed > 0) || !Float.isFinite(speed)) {
				throw new ProtocolException("Animation speed must be above 0, got " + speed);
			}

			if (!(start >= 0) || !Float.isFinite(start)) {
				throw new ProtocolException("Animation start must be 0 or more, got " + start);
			}

			checkFade(fadeIn);
		}
	}

	record StopAnimation(int entity, int animation, float fadeOut) implements EntityModelsPacket {
		public StopAnimation {
			if (animation < 0) {
				throw new ProtocolException("Invalid animation " + animation);
			}

			checkFade(fadeOut);
		}
	}

	record Variable(String name, float value) {
		public static final int MAX_NAME = 64;

		public Variable {
			if (name.isEmpty() || name.length() > MAX_NAME || !name.matches("[a-z_][a-z0-9_]*")) {
				throw new ProtocolException("Variable names are 1 to " + MAX_NAME + " characters of a-z 0-9 _ (not starting with a digit): " + name);
			}

			if (!Float.isFinite(value)) {
				throw new ProtocolException("Variable values must be finite");
			}
		}
	}

	record SetVariables(int entity, List<Variable> variables) implements EntityModelsPacket {
		public static final int MAX_VARIABLES = 256;

		public SetVariables {
			variables = List.copyOf(variables);

			if (variables.size() > MAX_VARIABLES) {
				throw new ProtocolException("At most " + MAX_VARIABLES + " variables at once");
			}
		}
	}

	float MAX_FADE = 600;

	private static void checkFade(float seconds) {
		if (!(seconds >= 0) || seconds > MAX_FADE) {
			throw new ProtocolException("Fades must be 0 to " + MAX_FADE + " seconds, got " + seconds);
		}
	}
}
