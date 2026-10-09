package me.skaffy.protocol.animations;

import java.util.List;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.entitymodels.EntityModelsPacket;
import me.skaffy.protocol.entitymodels.EntityModelsPacket.PlayAnimation.Mode;

public sealed interface AnimationsPacket {
	int MAX_ID = 128;
	int MAX_BONE = 64;
	int MAX_DIMENSION = 128;
	int MAX_DURATION = 600_000;
	int MAX_PATH_POINTS = 4096;
	int MAX_SLOT = 64;
	int MAX_ITEM = 32767;
	int MAX_BLOCK = 1024;
	int MAX_DISPLAY = 32;
	int MAX_ENTITIES = 8192;
	int MAX_PLAYING = 16;

	record Placement(String dimension, double x, double y, double z, float yaw, float pitch, float roll, float scaleX, float scaleY, float scaleZ) {
		public Placement {
			checkDimension(dimension);
			finite(x, y, z, yaw, pitch, roll, scaleX, scaleY, scaleZ);

			if (Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7 || Math.abs(y) > 1.0E5) {
				throw new ProtocolException("Animation entities must be inside the world");
			}
		}
	}

	record Settings(float hitboxWidth, float hitboxHeight, boolean fullBright, int glowColor, float shadowRadius, float viewDistance, boolean showInFirstPerson) {
		public static final Settings DEFAULT = new Settings(0, 0, false, 0, 0, 0, false);

		public Settings {
			finite(hitboxWidth, hitboxHeight, shadowRadius, viewDistance);

			if (hitboxWidth < 0 || hitboxHeight < 0 || hitboxWidth > 256 || hitboxHeight > 256) {
				throw new ProtocolException("Hitboxes are 0 to 256 blocks");
			}

			if (shadowRadius < 0 || shadowRadius > 32 || viewDistance < 0) {
				throw new ProtocolException("Shadow radius is 0 to 32 blocks, view distance at least 0");
			}
		}
	}

	record PathPoint(int time, double x, double y, double z, float yaw, float pitch, float roll) {
		public PathPoint {
			finite(x, y, z, yaw, pitch, roll);

			if (time < 0) {
				throw new ProtocolException("Path times must be 0 or more");
			}
		}
	}

	sealed interface Attachment {
		record None() implements Attachment {
		}

		record ToEntity(int entity, float x, float y, float z, float yaw, float pitch, float roll, boolean turn) implements Attachment {
			public ToEntity {
				finite(x, y, z, yaw, pitch, roll);
			}
		}

		record ToBone(String parent, String bone, float x, float y, float z, float yaw, float pitch, float roll) implements Attachment {
			public ToBone {
				checkId(parent);
				checkBone(bone);
				finite(x, y, z, yaw, pitch, roll);
			}
		}
	}

	sealed interface Target {
		record None() implements Target {
		}

		record AtEntity(int entity) implements Target {
		}

		record AtCamera() implements Target {
		}

		record AtPoint(double x, double y, double z) implements Target {
			public AtPoint {
				finite(x, y, z);
			}
		}
	}

	sealed interface Display {
		record None() implements Display {
		}

		record Item(String item, String context) implements Display {
			public Item {
				if (item.isEmpty() || item.length() > MAX_ITEM || context.isEmpty() || context.length() > MAX_DISPLAY) {
					throw new ProtocolException("Invalid item display");
				}
			}
		}

		record Block(String state) implements Display {
			public Block {
				if (state.isEmpty() || state.length() > MAX_BLOCK) {
					throw new ProtocolException("Invalid block display");
				}
			}
		}
	}

	record Spawn(String id, int model, Placement placement, Settings settings) implements AnimationsPacket {
		public Spawn {
			checkId(id);

			if (model < 1) {
				throw new ProtocolException("Invalid model " + model);
			}
		}
	}

	record Remove(String id) implements AnimationsPacket {
		public Remove {
			checkId(id);
		}
	}

	record Clear(String prefix) implements AnimationsPacket {
		public Clear {
			if (prefix.length() > MAX_ID) {
				throw new ProtocolException("Prefix too long");
			}
		}
	}

	record Move(String id, Placement placement, int duration, Easing easing) implements AnimationsPacket {
		public Move {
			checkId(id);
			checkDuration(duration);
		}
	}

	record FollowPath(String id, List<PathPoint> points, boolean smooth, boolean loop, boolean faceAlong, int start) implements AnimationsPacket {
		public FollowPath {
			checkId(id);
			points = List.copyOf(points);

			if (points.isEmpty() || points.size() > MAX_PATH_POINTS) {
				throw new ProtocolException("Paths have 1 to " + MAX_PATH_POINTS + " points");
			}

			for (int i = 1; i < points.size(); i++) {
				if (points.get(i).time() < points.get(i - 1).time()) {
					throw new ProtocolException("Path times must not go down");
				}
			}

			if (start < 0) {
				throw new ProtocolException("Path start must be 0 or more");
			}
		}
	}

	record Attach(String id, Attachment attachment) implements AnimationsPacket {
		public Attach {
			checkId(id);

			if (attachment == null) {
				throw new ProtocolException("Missing attachment");
			}
		}
	}

	record Play(String id, int animation, Mode mode, float speed, float start, float fadeIn, boolean removeWhenDone) implements AnimationsPacket {
		public Play {
			checkId(id);
			new EntityModelsPacket.PlayAnimation(0, animation, mode, speed, start, fadeIn);
		}
	}

	record Stop(String id, int animation, float fadeOut) implements AnimationsPacket {
		public Stop {
			checkId(id);
			new EntityModelsPacket.StopAnimation(0, animation, fadeOut);
		}
	}

	record SetVariables(String id, List<EntityModelsPacket.Variable> variables) implements AnimationsPacket {
		public SetVariables {
			checkId(id);
			variables = new EntityModelsPacket.SetVariables(0, variables).variables();
		}
	}

	record SetBone(String id, String bone, boolean hidden, int tint, float rotationX, float rotationY, float rotationZ, float positionX, float positionY, float positionZ,
			float scaleX, float scaleY, float scaleZ, int duration, Easing easing) implements AnimationsPacket {
		public SetBone {
			checkId(id);
			checkBone(bone);
			finite(rotationX, rotationY, rotationZ, positionX, positionY, positionZ, scaleX, scaleY, scaleZ);
			checkDuration(duration);
		}
	}

	record LookAt(String id, String bone, Target target, float maxYaw, float maxPitch, float speed) implements AnimationsPacket {
		public LookAt {
			checkId(id);
			checkBone(bone);
			finite(maxYaw, maxPitch, speed);

			if (bone.isEmpty() || target == null || maxYaw < 0 || maxYaw > 180 || maxPitch < 0 || maxPitch > 90 || speed < 0) {
				throw new ProtocolException("Look at needs a bone, a target, yaw 0 to 180, pitch 0 to 90 and a speed of 0 or more");
			}
		}
	}

	record SetItem(String id, String slot, String bone, Display display, float x, float y, float z, float rotationX, float rotationY, float rotationZ, float scale)
			implements AnimationsPacket {
		public SetItem {
			checkId(id);
			checkBone(bone);
			finite(x, y, z, rotationX, rotationY, rotationZ, scale);

			if (slot.isEmpty() || slot.length() > MAX_SLOT || display == null) {
				throw new ProtocolException("Item slots are 1 to " + MAX_SLOT + " characters");
			}
		}
	}

	record SetSettings(String id, Settings settings) implements AnimationsPacket {
		public SetSettings {
			checkId(id);
		}
	}

	enum Button {
		ATTACK,
		USE
	}

	record Click(String id, Button button, boolean offHand, float x, float y, float z, boolean sneaking) implements AnimationsPacket {
		public Click {
			checkId(id);
			finite(x, y, z);
		}
	}

	static void checkId(String id) {
		if (id.isEmpty() || id.length() > MAX_ID) {
			throw new ProtocolException("Animation entity ids are 1 to " + MAX_ID + " characters");
		}
	}

	static void checkBone(String bone) {
		if (bone.length() > MAX_BONE) {
			throw new ProtocolException("Bone names are at most " + MAX_BONE + " characters");
		}
	}

	static void checkDimension(String dimension) {
		if (dimension.isEmpty() || dimension.length() > MAX_DIMENSION || !dimension.contains(":")) {
			throw new ProtocolException("Invalid dimension " + dimension + " (like minecraft:overworld)");
		}
	}

	static void checkDuration(int duration) {
		if (duration < 0 || duration > MAX_DURATION) {
			throw new ProtocolException("Durations are 0 to " + MAX_DURATION + " ms");
		}
	}

	static void finite(double... values) {
		for (double value : values) {
			if (!Double.isFinite(value)) {
				throw new ProtocolException("Values must be finite numbers");
			}
		}
	}
}
