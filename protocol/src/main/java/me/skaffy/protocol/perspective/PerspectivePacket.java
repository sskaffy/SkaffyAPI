package me.skaffy.protocol.perspective;

import me.skaffy.protocol.Easing;
import me.skaffy.protocol.ProtocolException;

public sealed interface PerspectivePacket {
	int MAX_DURATION = 600_000;
	float MAX_OFFSET = 4096;

	enum Perspective {
		FIRST_PERSON,
		THIRD_PERSON_BACK,
		THIRD_PERSON_FRONT,
		CUSTOM
	}

	enum Turn {
		NONE,
		YAW,
		YAW_AND_PITCH
	}

	sealed interface Look {
		record Angles(float yaw, float pitch) implements Look {
		}

		record Point(float x, float y, float z) implements Look {
		}

		record AtPlayer() implements Look {
		}

		record AtEntity(int entityId) implements Look {
		}

		record PlayerView() implements Look {
		}

		record AnchorFacing() implements Look {
		}
	}

	sealed interface Anchor {
		int MAX_ID = 128;
		int MAX_BONE = 64;

		record Player() implements Anchor {
		}

		record Bone(String entity, String bone) implements Anchor {
			public Bone {
				if (entity.isEmpty() || entity.length() > MAX_ID || bone.length() > MAX_BONE) {
					throw new ProtocolException("Camera anchor ids are 1 to " + MAX_ID + " characters, bones at most " + MAX_BONE);
				}
			}
		}
	}

	record Camera(float x, float y, float z, Turn turn, Look look, float roll, boolean pullInFrontOfWalls, Anchor anchor) {
		public Camera(float x, float y, float z, Turn turn, Look look, float roll, boolean pullInFrontOfWalls) {
			this(x, y, z, turn, look, roll, pullInFrontOfWalls, new Anchor.Player());
		}

		public Camera {
			if (turn == null || look == null || anchor == null) {
				throw new ProtocolException("A camera needs a turn mode, a look and an anchor");
			}

			checkOffset(x, y, z);

			if (look instanceof Look.Point point) {
				checkOffset(point.x(), point.y(), point.z());
			}

			if (look instanceof Look.Angles angles && (!Float.isFinite(angles.yaw()) || !Float.isFinite(angles.pitch()))) {
				throw new ProtocolException("Camera angles must be finite");
			}

			if (!Float.isFinite(roll)) {
				throw new ProtocolException("Camera roll must be finite");
			}
		}

		private static void checkOffset(float x, float y, float z) {
			if (!(Math.abs(x) <= MAX_OFFSET && Math.abs(y) <= MAX_OFFSET && Math.abs(z) <= MAX_OFFSET)) {
				throw new ProtocolException("Camera offsets must be within " + MAX_OFFSET + " blocks, got " + x + ", " + y + ", " + z);
			}
		}
	}

	record SetPerspective(Perspective perspective, int durationMillis, Easing easing) implements PerspectivePacket {
		public SetPerspective {
			if (perspective == Perspective.CUSTOM) {
				throw new ProtocolException("SetPerspective takes a vanilla perspective; use SetCamera for a custom one");
			}

			checkDuration(durationMillis);
		}
	}

	record SetCamera(Camera camera, int durationMillis, Easing easing) implements PerspectivePacket {
		public SetCamera {
			checkDuration(durationMillis);
		}
	}

	record SetAllowed(int mask) implements PerspectivePacket {
		public SetAllowed {
			if (mask < 1 || mask > 7) {
				throw new ProtocolException("Allowed perspectives must be 1 to 7, got " + mask);
			}
		}
	}

	record QueryPerspective(int requestId) implements PerspectivePacket {
	}

	record PerspectiveInfo(int requestId, Perspective perspective) implements PerspectivePacket {
	}

	record PerspectiveChanged(Perspective from, Perspective to) implements PerspectivePacket {
	}

	private static void checkDuration(int durationMillis) {
		if (durationMillis < 0 || durationMillis > MAX_DURATION) {
			throw new ProtocolException("Duration must be 0 to " + MAX_DURATION + " ms, got " + durationMillis);
		}
	}
}
