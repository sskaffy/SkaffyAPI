package me.skaffy.client.perspective;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.protocol.Easing;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.perspective.PerspectiveCodec;
import me.skaffy.protocol.perspective.PerspectivePacket;
import me.skaffy.protocol.perspective.PerspectivePacket.Camera;
import me.skaffy.protocol.perspective.PerspectivePacket.Look;
import me.skaffy.protocol.perspective.PerspectivePacket.Perspective;
import me.skaffy.protocol.perspective.PerspectivePacket.PerspectiveChanged;
import me.skaffy.protocol.perspective.PerspectivePacket.PerspectiveInfo;
import me.skaffy.protocol.perspective.PerspectivePacket.QueryPerspective;
import me.skaffy.protocol.perspective.PerspectivePacket.SetAllowed;
import me.skaffy.protocol.perspective.PerspectivePacket.SetCamera;
import me.skaffy.protocol.perspective.PerspectivePacket.SetPerspective;
import me.skaffy.protocol.perspective.PerspectivePacket.Turn;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

public final class ClientPerspective {
	private static final double DEGREES = Math.PI / 180;
	private static final double HEAD_RADIUS = 0.5;
	private static final CameraType[] VANILLA = {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT};

	private static int allowed = 7;
	private static @Nullable Camera custom;
	private static CameraType beforeCustom = CameraType.FIRST_PERSON;
	private static @Nullable Transition transition;
	private static @Nullable Pose last;
	private static float appliedRoll;
	private static float lookYaw;
	private static float lookPitch;

	private ClientPerspective() {
	}

	private record Pose(Vec3 offset, float yaw, float pitch, float roll) {
		Pose towards(Pose to, double t) {
			return new Pose(offset.lerp(to.offset, t), yaw + Mth.wrapDegrees(to.yaw - yaw) * (float) t,
					(float) Mth.lerp(t, pitch, to.pitch), (float) Mth.lerp(t, roll, to.roll));
		}
	}

	private record Transition(Pose from, @Nullable CameraType target, long start, int durationMillis, Easing easing) {
		double progress(long now) {
			return durationMillis == 0 ? 1 : (now - start) / 1_000_000.0 / durationMillis;
		}
	}

	public static void receive(byte[] data) {
		PerspectivePacket packet;

		try {
			packet = PerspectiveCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed perspective packet: {}", e.getMessage());
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> handle(packet, minecraft));
	}

	private static void handle(PerspectivePacket packet, Minecraft minecraft) {
		long now = System.nanoTime();

		switch (packet) {
			case SetPerspective set -> {
				CameraType target = vanilla(set.perspective());
				boolean already = custom == null && transition == null && minecraft.options.getCameraType() == target;
				custom = null;

				if (set.durationMillis() == 0 || last == null || already) {
					transition = null;
					setType(target);
				} else {
					transition = new Transition(last, target, now, set.durationMillis(), set.easing());
					setType(target.isFirstPerson() ? CameraType.THIRD_PERSON_BACK : target);
				}
			}
			case SetCamera set -> {
				if (custom == null && (transition == null || transition.target() != null)) {
					beforeCustom = transition != null ? transition.target() : minecraft.options.getCameraType();
				}

				custom = set.camera();
				transition = set.durationMillis() == 0 || last == null ? null : new Transition(last, null, now, set.durationMillis(), set.easing());
				setType(CameraType.THIRD_PERSON_BACK);
			}
			case SetAllowed set -> {
				allowed = set.mask();

				if (custom == null && transition == null && !isAllowed(minecraft.options.getCameraType())) {
					setType(nextAllowed(minecraft.options.getCameraType()));
				}
			}
			case QueryPerspective query -> SkaffyConnection.sendFeature(PerspectiveCodec.CHANNEL, PerspectiveCodec.encode(new PerspectiveInfo(query.requestId(), current())));
			default -> {
			}
		}
	}

	public static void unload() {
		Minecraft.getInstance().execute(() -> {
			allowed = 7;

			if (transition != null && transition.target() != null) {
				setType(transition.target());
			} else if (custom != null || transition != null) {
				setType(beforeCustom);
			}

			custom = null;
			transition = null;
			last = null;
		});
	}

	private static Perspective current() {
		if (transition != null) {
			return transition.target() == null ? Perspective.CUSTOM : of(transition.target());
		}

		return custom != null ? Perspective.CUSTOM : of(Minecraft.getInstance().options.getCameraType());
	}

	public static CameraType cycle(CameraType type) {
		if (custom != null || transition != null && transition.target() == null) {
			return type;
		}

		CameraType from = type;

		if (transition != null) {
			from = transition.target();
			transition = null;
		}

		CameraType next = nextAllowed(from);

		if (next != from) {
			SkaffyConnection.sendFeature(PerspectiveCodec.CHANNEL, PerspectiveCodec.encode(new PerspectiveChanged(of(from), of(next))));
		}

		return next;
	}

	public static void adjustCamera(net.minecraft.client.Camera camera, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null || camera.isPanoramicMode()) {
			return;
		}

		SkaffyCamera bridge = (SkaffyCamera) camera;
		Vec3 anchor = player.getPosition(partialTicks);
		appliedRoll = 0;

		if (transition != null) {
			Transition running = transition;
			Pose target = running.target() == null ? customPose(bridge, player, anchor, partialTicks) : vanillaPose(running.target(), camera, bridge, player, anchor, partialTicks);
			double progress = running.progress(System.nanoTime());

			if (progress >= 1) {
				transition = null;

				if (running.target() != null) {
					setType(running.target());
					bridge.skaffy$setDetached(!running.target().isFirstPerson());
				}

				apply(bridge, anchor, target);
			} else {
				apply(bridge, anchor, running.from().towards(target, running.easing().apply(progress)));
			}
		} else if (custom != null) {
			apply(bridge, anchor, customPose(bridge, player, anchor, partialTicks));
		}

		if ((transition != null || custom != null) && camera.position().distanceToSqr(anchor.add(0, bridge.skaffy$eyeHeight(partialTicks), 0)) < HEAD_RADIUS * HEAD_RADIUS) {
			bridge.skaffy$setDetached(false);
		}

		last = new Pose(camera.position().subtract(anchor), camera.yRot(), camera.xRot(), appliedRoll);
	}

	private static void apply(SkaffyCamera camera, Vec3 anchor, Pose pose) {
		camera.skaffy$setPose(anchor.add(pose.offset()), pose.yaw(), pose.pitch(), pose.roll());
		appliedRoll = pose.roll();
	}

	private static Pose vanillaPose(CameraType type, net.minecraft.client.Camera camera, SkaffyCamera bridge, LocalPlayer player, Vec3 anchor, float partialTicks) {
		if (type.isFirstPerson()) {
			return new Pose(new Vec3(0, bridge.skaffy$eyeHeight(partialTicks), 0), player.getViewYRot(partialTicks), player.getViewXRot(partialTicks), 0);
		}

		return new Pose(camera.position().subtract(anchor), camera.yRot(), camera.xRot(), 0);
	}

	private static Pose customPose(SkaffyCamera bridge, LocalPlayer player, Vec3 anchor, float partialTicks) {
		Camera camera = custom;

		if (camera == null) {
			return new Pose(new Vec3(0, bridge.skaffy$eyeHeight(partialTicks), 0), player.getViewYRot(partialTicks), player.getViewXRot(partialTicks), 0);
		}

		if (camera.anchor() instanceof PerspectivePacket.Anchor.Bone bone) {
			return bonePose(camera, bone, bridge, player, anchor, partialTicks);
		}

		float playerYaw = player.getViewYRot(partialTicks);
		float playerPitch = player.getViewXRot(partialTicks);
		Turn turn = camera.turn();
		double eyeHeight = bridge.skaffy$eyeHeight(partialTicks);
		Vec3 eyes = anchor.add(0, eyeHeight, 0);
		Vec3 position = anchor.add(placed(new Vec3(camera.x(), camera.y(), camera.z()), turn, playerYaw, playerPitch, eyeHeight));

		if (camera.pullInFrontOfWalls()) {
			position = pull(player.level(), eyes, position, player);
		}

		switch (camera.look()) {
			case Look.Angles angles -> {
				lookYaw = angles.yaw() + (turn != Turn.NONE ? playerYaw : 0);
				lookPitch = Mth.clamp(angles.pitch() + (turn == Turn.YAW_AND_PITCH ? playerPitch : 0), -90, 90);
			}
			case Look.Point point -> lookAt(position, anchor.add(placed(new Vec3(point.x(), point.y(), point.z()), turn, playerYaw, playerPitch, eyeHeight)));
			case Look.AtPlayer ignored -> lookAt(position, eyes);
			case Look.AtEntity target -> {
				Entity entity = player.level().getEntity(target.entityId());

				if (entity != null) {
					lookAt(position, entity.getEyePosition(partialTicks));
				}
			}
			case Look.PlayerView ignored -> {
				lookYaw = playerYaw;
				lookPitch = player.getViewXRot(partialTicks);
			}
			case Look.AnchorFacing ignored -> {
				lookYaw = playerYaw;
				lookPitch = player.getViewXRot(partialTicks);
			}
		}

		return new Pose(position.subtract(anchor), lookYaw, lookPitch, camera.roll());
	}

	private static Pose bonePose(Camera camera, PerspectivePacket.Anchor.Bone bone, SkaffyCamera bridge, LocalPlayer player, Vec3 anchor, float partialTicks) {
		me.skaffy.client.animation.ClientAnimations.BoneFrame frame = me.skaffy.client.animation.ClientAnimations.boneFrame(bone.entity(), bone.bone(), partialTicks);

		if (frame == null) {
			return last != null ? last : new Pose(new Vec3(0, bridge.skaffy$eyeHeight(partialTicks), 0), player.getViewYRot(partialTicks), player.getViewXRot(partialTicks), 0);
		}

		Vec3 origin = frame.origin();
		Vec3 position = origin.add(frame.toWorld(camera.x(), camera.y(), camera.z()));

		if (camera.pullInFrontOfWalls()) {
			position = pull(player.level(), origin, position, player);
		}

		float roll = camera.roll();

		switch (camera.look()) {
			case Look.Angles angles -> {
				lookYaw = angles.yaw();
				lookPitch = Mth.clamp(angles.pitch(), -90, 90);
			}
			case Look.Point point -> lookAt(position, origin.add(frame.toWorld(point.x(), point.y(), point.z())));
			case Look.AtPlayer ignored -> lookAt(position, player.getEyePosition(partialTicks));
			case Look.AtEntity target -> {
				Entity entity = player.level().getEntity(target.entityId());

				if (entity != null) {
					lookAt(position, entity.getEyePosition(partialTicks));
				}
			}
			case Look.PlayerView ignored -> {
				lookYaw = player.getViewYRot(partialTicks);
				lookPitch = player.getViewXRot(partialTicks);
			}
			case Look.AnchorFacing ignored -> {
				Vec3 forward = frame.forward();
				Vec3 up = frame.up();
				lookAt(position, position.add(forward));
				Vec3 right = forward.cross(new Vec3(0, 1, 0));

				if (right.lengthSqr() > 1.0E-8) {
					right = right.normalize();
					Vec3 levelUp = right.cross(forward).normalize();
					roll += (float) (Math.atan2(up.dot(right), up.dot(levelUp)) / DEGREES);
				}
			}
		}

		return new Pose(position.subtract(anchor), lookYaw, lookPitch, roll);
	}

	private static Vec3 placed(Vec3 offset, Turn turn, float yaw, float pitch, double eyeHeight) {
		return switch (turn) {
			case NONE -> offset;
			case YAW -> offset.yRot((float) (-yaw * DEGREES));
			case YAW_AND_PITCH -> offset.subtract(0, eyeHeight, 0).xRot((float) (-pitch * DEGREES)).yRot((float) (-yaw * DEGREES)).add(0, eyeHeight, 0);
		};
	}

	private static void lookAt(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		if (horizontal < 1.0E-6 && Math.abs(dy) < 1.0E-6) {
			return;
		}

		lookYaw = (float) (Mth.atan2(dz, dx) / DEGREES) - 90;
		lookPitch = (float) -(Mth.atan2(dy, horizontal) / DEGREES);
	}

	private static Vec3 pull(Level level, Vec3 from, Vec3 to, Entity entity) {
		Vec3 delta = to.subtract(from);
		double distance = delta.length();

		if (distance < 1.0E-4) {
			return to;
		}

		Vec3 direction = delta.scale(1 / distance);
		double reach = distance;

		for (int i = 0; i < 8; i++) {
			Vec3 start = from.add(((i & 1) * 2 - 1) * 0.1, ((i >> 1 & 1) * 2 - 1) * 0.1, ((i >> 2 & 1) * 2 - 1) * 0.1);
			HitResult hit = level.clip(new ClipContext(start, start.add(direction.scale(distance)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));

			if (hit.getType() != HitResult.Type.MISS) {
				reach = Math.min(reach, hit.getLocation().distanceTo(from));
			}
		}

		return from.add(direction.scale(reach));
	}

	private static void setType(CameraType type) {
		Minecraft minecraft = Minecraft.getInstance();
		CameraType previous = minecraft.options.getCameraType();

		if (previous == type) {
			return;
		}

		minecraft.options.setCameraType(type);

		if (previous.isFirstPerson() != type.isFirstPerson()) {
			minecraft.gameRenderer.checkEntityPostEffect(type.isFirstPerson() ? minecraft.getCameraEntity() : null);
		}
	}

	private static boolean isAllowed(CameraType type) {
		return (allowed & 1 << type.ordinal()) != 0;
	}

	private static CameraType nextAllowed(CameraType type) {
		for (int step = 1; step <= VANILLA.length; step++) {
			CameraType candidate = VANILLA[(type.ordinal() + step) % VANILLA.length];

			if (isAllowed(candidate)) {
				return candidate;
			}
		}

		return type;
	}

	private static CameraType vanilla(Perspective perspective) {
		return switch (perspective) {
			case FIRST_PERSON, CUSTOM -> CameraType.FIRST_PERSON;
			case THIRD_PERSON_BACK -> CameraType.THIRD_PERSON_BACK;
			case THIRD_PERSON_FRONT -> CameraType.THIRD_PERSON_FRONT;
		};
	}

	private static Perspective of(CameraType type) {
		return switch (type) {
			case FIRST_PERSON -> Perspective.FIRST_PERSON;
			case THIRD_PERSON_BACK -> Perspective.THIRD_PERSON_BACK;
			case THIRD_PERSON_FRONT -> Perspective.THIRD_PERSON_FRONT;
		};
	}
}
