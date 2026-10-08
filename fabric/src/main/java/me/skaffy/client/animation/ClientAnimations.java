package me.skaffy.client.animation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.model.AnimationClip;
import me.skaffy.client.model.AnimationPlayer;
import me.skaffy.client.model.ClientEntityModels;
import me.skaffy.client.model.ClientModel;
import me.skaffy.client.model.ModelAnimator;
import me.skaffy.client.model.ModelData;
import me.skaffy.client.model.ModelEffects;
import me.skaffy.client.model.gpu.GpuModels;
import me.skaffy.client.model.gpu.Skeleton;
import me.skaffy.client.network.SkaffyConnection;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.animations.AnimationsCodec;
import me.skaffy.protocol.animations.AnimationsPacket;
import me.skaffy.protocol.animations.AnimationsPacket.Attach;
import me.skaffy.protocol.animations.AnimationsPacket.Attachment;
import me.skaffy.protocol.animations.AnimationsPacket.Clear;
import me.skaffy.protocol.animations.AnimationsPacket.Click;
import me.skaffy.protocol.animations.AnimationsPacket.FollowPath;
import me.skaffy.protocol.animations.AnimationsPacket.LookAt;
import me.skaffy.protocol.animations.AnimationsPacket.Move;
import me.skaffy.protocol.animations.AnimationsPacket.Play;
import me.skaffy.protocol.animations.AnimationsPacket.Remove;
import me.skaffy.protocol.animations.AnimationsPacket.SetBone;
import me.skaffy.protocol.animations.AnimationsPacket.SetItem;
import me.skaffy.protocol.animations.AnimationsPacket.SetSettings;
import me.skaffy.protocol.animations.AnimationsPacket.SetVariables;
import me.skaffy.protocol.animations.AnimationsPacket.Spawn;
import me.skaffy.protocol.animations.AnimationsPacket.Stop;
import me.skaffy.protocol.animations.AnimationsPacket.Target;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class ClientAnimations {
	private static final int FIRST_ENTITY_ID = -2_000_000;
	private static final int MAX_ATTACH_DEPTH = 8;
	private static final Map<String, AnimatedObject> OBJECTS = new LinkedHashMap<>();
	private static int nextEntityId = FIRST_ENTITY_ID;
	private static @Nullable ClientLevel materializedIn;
	private static @Nullable AnimationEntityRenderer renderer;
	private static EntityRendererProvider.@Nullable Context context;
	private static long frame;
	private static double frameNow = AnimationPlayer.now();
	private static int attachDepth;

	private ClientAnimations() {
	}

	public record Placement(double x, double y, double z, Matrix3f rotation, float scaleX, float scaleY, float scaleZ) {
		Matrix4f local(float modelScale) {
			return new Matrix4f().set(rotation).scale(scaleX * modelScale, scaleY * modelScale, scaleZ * modelScale).translate(0, 1.501f, 0).scale(1, -1, -1);
		}
	}

	public record BoneFrame(Vec3 origin, Matrix3f axes) {
		public Vec3 toWorld(double x, double y, double z) {
			Vector3f turned = axes.transform(new Vector3f((float) x, (float) y, (float) z));
			return new Vec3(turned.x, turned.y, turned.z);
		}

		public Vec3 forward() {
			return toWorld(0, 0, 1).normalize();
		}

		public Vec3 up() {
			return toWorld(0, 1, 0).normalize();
		}
	}

	public static void receive(byte[] data) {
		AnimationsPacket packet;

		try {
			packet = AnimationsCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			SkaffySAPIClient.LOGGER.warn("Dropped malformed animations packet: {}", e.getMessage());
			return;
		}

		Minecraft.getInstance().execute(() -> handle(packet));
	}

	private static void handle(AnimationsPacket packet) {
		double now = AnimationPlayer.now();

		switch (packet) {
			case Spawn spawn -> spawn(spawn);
			case Remove remove -> remove(remove.id());
			case Clear clear -> {
				List<String> ids = new ArrayList<>();
				OBJECTS.keySet().stream().filter(id -> id.startsWith(clear.prefix())).forEach(ids::add);
				ids.forEach(ClientAnimations::remove);
			}
			case Move move -> with(move.id(), object -> {
				detach(object, now);

				if (!object.dimension.equals(move.placement().dimension())) {
					object.dimension = move.placement().dimension();
					object.motion.set(pose(move.placement()));
					materialize(object);
				} else {
					object.motion.moveTo(pose(move.placement()), now, move.duration(), move.easing());
				}
			});
			case FollowPath path -> with(path.id(), object -> {
				detach(object, now);
				object.motion.follow(path.points(), path.smooth(), path.loop(), path.faceAlong(), path.start(), now);
			});
			case Attach attach -> with(attach.id(), object -> {
				detach(object, now);
				object.attachment = attach.attachment();
			});
			case Play play -> with(play.id(), object -> object.player.play(play.animation(), play.mode(), play.speed(), play.start(), play.fadeIn(), play.removeWhenDone(), now));
			case Stop stop -> with(stop.id(), object -> object.player.stop(stop.animation(), stop.fadeOut(), now));
			case SetVariables set -> with(set.id(), object -> set.variables().forEach(variable -> object.player.setVariable(variable.name(), variable.value())));
			case SetBone set -> with(set.id(), object -> setBone(object, set, now));
			case LookAt look -> with(look.id(), object -> {
				AnimatedObject.Look entry = object.looks.computeIfAbsent(look.bone(), bone -> new AnimatedObject.Look());
				entry.target = look.target();
				entry.maxYaw = look.maxYaw();
				entry.maxPitch = look.maxPitch();
				entry.speed = look.speed();
			});
			case SetItem item -> with(item.id(), object -> {
				if (item.display() instanceof AnimationsPacket.Display.None) {
					object.items.remove(item.slot());
					return;
				}

				AnimatedObject.Item entry = object.items.computeIfAbsent(item.slot(), slot -> new AnimatedObject.Item());
				entry.bone = item.bone();
				entry.display = item.display();
				entry.parsed = false;
				entry.stack = null;
				entry.block = null;
				float[] transform = {item.x(), item.y(), item.z(), item.rotationX(), item.rotationY(), item.rotationZ(), item.scale()};
				System.arraycopy(transform, 0, entry.transform, 0, transform.length);
			});
			case SetSettings set -> with(set.id(), object -> {
				object.settings = set.settings();

				if (object.entity != null) {
					object.entity.refreshHitbox();
				}
			});
			case Click ignored -> {
			}
		}
	}

	private static void with(String id, java.util.function.Consumer<AnimatedObject> action) {
		AnimatedObject object = OBJECTS.get(id);

		if (object != null) {
			action.accept(object);
		}
	}

	private static void spawn(Spawn spawn) {
		ClientModel model = ClientEntityModels.model(spawn.model());

		if (model == null) {
			SkaffySAPIClient.LOGGER.warn("Animation entity {} uses model {}, which the server didn't define", spawn.id(), spawn.model());
			return;
		}

		AnimatedObject previous = OBJECTS.remove(spawn.id());

		if (previous != null) {
			dematerialize(previous);
			previous.holder.close();
		}

		if (OBJECTS.size() >= AnimationsPacket.MAX_ENTITIES) {
			SkaffySAPIClient.LOGGER.warn("Too many animation entities ({}), {} left out", AnimationsPacket.MAX_ENTITIES, spawn.id());
			return;
		}

		AnimatedObject object = new AnimatedObject(spawn.id(), model, spawn.placement().dimension(), pose(spawn.placement()), spawn.settings());
		OBJECTS.put(spawn.id(), object);
		materialize(object);
		model.data();
	}

	private static void remove(String id) {
		AnimatedObject object = OBJECTS.remove(id);

		if (object != null) {
			dematerialize(object);
			object.holder.close();
		}
	}

	private static void detach(AnimatedObject object, double now) {
		if (object.attachment instanceof Attachment.None) {
			return;
		}

		Placement placed = placement(object, 1);
		Vector3f euler = new Vector3f();
		eulerOf(placed.rotation(), euler);
		object.motion.set(new Motion.Pose(placed.x(), placed.y(), placed.z(), euler.x, euler.y, euler.z, placed.scaleX(), placed.scaleY(), placed.scaleZ()));
		object.attachment = new Attachment.None();
		object.lastAttached = null;
	}

	private static void setBone(AnimatedObject object, SetBone set, double now) {
		AnimatedObject.BoneSetting setting = object.bones.computeIfAbsent(set.bone(), bone -> new AnimatedObject.BoneSetting());
		float[] current = setting.at(now).clone();
		System.arraycopy(current, 0, setting.from, 0, 9);
		float[] target = {set.rotationX(), set.rotationY(), set.rotationZ(), set.positionX(), set.positionY(), set.positionZ(), set.scaleX(), set.scaleY(), set.scaleZ()};
		System.arraycopy(target, 0, setting.to, 0, 9);
		setting.hidden = set.hidden();
		setting.tint = set.tint();
		setting.start = now;
		setting.duration = set.duration() / 1000.0;
		setting.easing = set.easing();
	}

	private static Motion.Pose pose(AnimationsPacket.Placement placement) {
		return new Motion.Pose(placement.x(), placement.y(), placement.z(), placement.yaw(), placement.pitch(), placement.roll(), placement.scaleX(), placement.scaleY(),
				placement.scaleZ());
	}

	public static void beginFrame() {
		frame++;
		frameNow = AnimationPlayer.now();
	}

	static long frame() {
		return frame;
	}

	static double frameNow() {
		return frameNow;
	}

	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;

		if (level != materializedIn) {
			materializedIn = level;
			nextEntityId = FIRST_ENTITY_ID;

			for (AnimatedObject object : OBJECTS.values()) {
				object.entity = null;
				materialize(object);
			}
		}

		if (OBJECTS.isEmpty()) {
			return;
		}

		double now = AnimationPlayer.now();
		List<String> finished = new ArrayList<>();

		for (AnimatedObject object : OBJECTS.values()) {
			if (object.player.tick(null, now, effect -> playEffect(object, effect))) {
				finished.add(object.id);
			}

			if (object.entity != null) {
				Placement placed = placementAt(object, 1, now);
				object.entity.setOldPosAndRot();
				object.entity.setPos(placed.x(), placed.y(), placed.z());
			}
		}

		finished.forEach(ClientAnimations::remove);
	}

	public static void unload() {
		Minecraft.getInstance().execute(() -> {
			OBJECTS.values().forEach(object -> {
				dematerialize(object);
				object.holder.close();
			});
			OBJECTS.clear();
			renderer = null;
		});
	}

	private static void materialize(AnimatedObject object) {
		ClientLevel level = Minecraft.getInstance().level;

		if (object.entity != null && (level == null || object.entity.level() != level || !level.dimension().identifier().toString().equals(object.dimension))) {
			dematerialize(object);
		}

		if (level == null || object.entity != null || !level.dimension().identifier().toString().equals(object.dimension)) {
			return;
		}

		AnimationEntity entity = new AnimationEntity(level, object);
		entity.setId(nextEntityId--);
		Placement placed = placementAt(object, 1, AnimationPlayer.now());
		entity.setPos(placed.x(), placed.y(), placed.z());
		entity.setOldPosAndRot();
		entity.refreshHitbox();
		object.entity = entity;
		level.addEntity(entity);
	}

	private static void dematerialize(AnimatedObject object) {
		AnimationEntity entity = object.entity;
		object.entity = null;

		if (entity != null && entity.level() instanceof ClientLevel level) {
			level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
		}
	}


	static Placement placement(AnimatedObject object, float partialTicks) {
		if (object.placedFrame == frame && object.placed != null) {
			return object.placed;
		}

		Placement placed = placementAt(object, partialTicks, frameNow);
		object.placed = placed;
		object.placedFrame = frame;
		return placed;
	}

	private static Placement placementAt(AnimatedObject object, float partialTicks, double now) {
		Motion.Pose pose = object.motion.at(now);

		switch (object.attachment) {
			case Attachment.ToEntity attached -> {
				ClientLevel level = Minecraft.getInstance().level;
				Entity entity = level == null ? null : level.getEntity(attached.entity());

				if (entity != null) {
					Vec3 position = entity.getPosition(partialTicks);
					float yaw = !attached.turn() ? 0 : entity instanceof LivingEntity living ? Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot) : entity.getYRot(partialTicks);
					Vector3f offset = new Vector3f(attached.x(), attached.y(), attached.z()).rotateY((float) Math.toRadians(-yaw));
					Matrix3f rotation = new Matrix3f().rotationY((float) Math.toRadians(-yaw)).mul(rotation(attached.yaw(), attached.pitch(), attached.roll()));
					return object.lastAttached = new Placement(position.x + offset.x, position.y + offset.y, position.z + offset.z, rotation, pose.scaleX(), pose.scaleY(),
							pose.scaleZ());
				}

				if (object.lastAttached != null) {
					return object.lastAttached;
				}
			}
			case Attachment.ToBone attached -> {
				BoneFrame parent = attachDepth < MAX_ATTACH_DEPTH ? boneFrameNested(attached.parent(), attached.bone(), partialTicks) : null;

				if (parent != null) {
					Vec3 position = parent.origin().add(parent.toWorld(attached.x(), attached.y(), attached.z()));
					Matrix3f rotation = new Matrix3f(parent.axes()).mul(rotation(attached.yaw(), attached.pitch(), attached.roll()));
					return object.lastAttached = new Placement(position.x, position.y, position.z, rotation, pose.scaleX(), pose.scaleY(), pose.scaleZ());
				}

				if (object.lastAttached != null) {
					return object.lastAttached;
				}
			}
			default -> {
			}
		}

		return new Placement(pose.x(), pose.y(), pose.z(), rotation(pose.yaw(), pose.pitch(), pose.roll()), pose.scaleX(), pose.scaleY(), pose.scaleZ());
	}

	static Matrix3f rotation(float yaw, float pitch, float roll) {
		return new Matrix3f().rotationY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.toRadians(roll));
	}

	private static void eulerOf(Matrix3f rotation, Vector3f out) {
		Vector3f forward = rotation.transform(new Vector3f(0, 0, 1));
		Vector3f up = rotation.transform(new Vector3f(0, 1, 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
		float pitch = (float) Math.toDegrees(Math.asin(Math.clamp(-forward.y, -1, 1)));
		Matrix3f withoutRoll = rotation(yaw, pitch, 0);
		Vector3f levelUp = withoutRoll.transform(new Vector3f(0, 1, 0));
		Vector3f right = withoutRoll.transform(new Vector3f(-1, 0, 0));
		float roll = (float) Math.toDegrees(Math.atan2(up.dot(right), up.dot(levelUp)));
		out.set(yaw, pitch, roll);
	}

	private static @Nullable BoneFrame boneFrameNested(String id, String bone, float partialTicks) {
		attachDepth++;

		try {
			return boneFrame(id, bone, partialTicks);
		} finally {
			attachDepth--;
		}
	}

	public static @Nullable BoneFrame boneFrame(String id, String bone, float partialTicks) {
		AnimatedObject object = OBJECTS.get(id);
		ClientLevel level = Minecraft.getInstance().level;

		if (object == null || level == null || !level.dimension().identifier().toString().equals(object.dimension)) {
			return null;
		}

		Placement placed = placement(object, partialTicks);
		Matrix4f local = placed.local(object.model.definition().scale());
		Matrix4f matrix = new Matrix4f().translation((float) 0, 0, 0).mul(local);

		if (!bone.isEmpty()) {
			Matrix4f boneMatrix = boneMatrix(object, bone, placed);

			if (boneMatrix == null) {
				return null;
			}

			matrix.mul(boneMatrix);
		}

		matrix.scale(1, -1, -1);
		Vector3f origin = matrix.transformPosition(new Vector3f());
		Matrix3f axes = matrix.get3x3(new Matrix3f());
		normalizeColumns(axes);
		return new BoneFrame(new Vec3(placed.x() + origin.x, placed.y() + origin.y, placed.z() + origin.z), axes);
	}

	private static @Nullable Matrix4f boneMatrix(AnimatedObject object, String name, Placement placed) {
		ModelData data = object.model.data();

		if (data == null) {
			return null;
		}

		ModelData.Locator locator = data.locator(name);
		int bone = locator != null ? locator.bone() : data.boneIndex(name);

		if (bone < 0) {
			return null;
		}

		Matrix4f[] posed = posed(object, placed);

		if (posed == null) {
			return null;
		}

		Matrix4f matrix = new Matrix4f(posed[bone]);

		if (locator != null) {
			matrix.translate(locator.x() / 16, locator.y() / 16, locator.z() / 16).rotateZYX(locator.zRot(), locator.yRot(), locator.xRot());
		}

		return matrix;
	}

	private static Matrix4f @Nullable [] posed(AnimatedObject object, Placement placed) {
		if (object.posedFrame == frame && object.posed != null) {
			return object.posed;
		}

		GpuModels.get(object.model);
		Skeleton skeleton = GpuModels.skeleton(object.model);

		if (skeleton == null) {
			return null;
		}

		skeleton.reset();
		ModelAnimator.apply(skeleton.lookup(), object.player.active(frameNow), queries(object));
		poseBones(object, skeleton, placed);
		Matrix4f[] computed = skeleton.compute();
		Matrix4f[] copy = new Matrix4f[computed.length];

		for (int i = 0; i < copy.length; i++) {
			copy[i] = new Matrix4f(computed[i]);
		}

		object.posed = copy;
		object.posedFrame = frame;
		return copy;
	}

	static void rememberPosed(AnimatedObject object, Matrix4f[] modelPartBones) {
		object.posed = modelPartBones;
		object.posedFrame = frame;
	}

	static ModelAnimator.Queries queries(AnimatedObject object) {
		return new ModelAnimator.Queries() {
			@Override
			public float lifeTime() {
				return object.entity == null ? 0 : object.entity.tickCount / 20f;
			}

			@Override
			public float groundSpeed() {
				if (object.entity == null) {
					return 0;
				}

				double dx = object.entity.getX() - object.entity.xo;
				double dz = object.entity.getZ() - object.entity.zo;
				return (float) Math.sqrt(dx * dx + dz * dz) * 20;
			}

			@Override
			public float headXRotation() {
				return 0;
			}

			@Override
			public float headYRotation() {
				return 0;
			}

			@Override
			public float distanceMoved() {
				return 0;
			}

			@Override
			public Map<String, Float> variables() {
				return object.player.variables();
			}
		};
	}

	static void poseBones(AnimatedObject object, Skeleton skeleton, Placement placed) {
		for (Map.Entry<String, AnimatedObject.BoneSetting> entry : object.bones.entrySet()) {
			ModelPart part = skeleton.lookup().apply(entry.getKey());

			if (part == null) {
				continue;
			}

			AnimatedObject.BoneSetting setting = entry.getValue();

			if (setting.hidden) {
				part.visible = false;
			}

			float[] value = setting.at(frameNow);
			part.xRot += (float) Math.toRadians(-value[0]);
			part.yRot += (float) Math.toRadians(-value[1]);
			part.zRot += (float) Math.toRadians(value[2]);
			part.x -= value[3];
			part.y -= value[4];
			part.z += value[5];
			part.xScale *= value[6];
			part.yScale *= value[7];
			part.zScale *= value[8];
		}

		if (!object.looks.isEmpty()) {
			look(object, skeleton, placed);
		}
	}

	private static void look(AnimatedObject object, Skeleton skeleton, Placement placed) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		Matrix4f local = placed.local(object.model.definition().scale());
		Iterator<Map.Entry<String, AnimatedObject.Look>> iterator = object.looks.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<String, AnimatedObject.Look> entry = iterator.next();
			ModelData data = object.model.data();
			int bone = data == null ? -1 : data.boneIndex(entry.getKey());
			ModelPart part = skeleton.part(bone);

			if (part == null) {
				continue;
			}

			AnimatedObject.Look look = entry.getValue();
			Vec3 target = switch (look.target) {
				case Target.AtEntity at -> {
					Entity entity = level == null ? null : level.getEntity(at.entity());
					yield entity == null ? null : entity.getEyePosition(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
				}
				case Target.AtCamera ignored -> minecraft.gameRenderer.mainCamera().position();
				case Target.AtPoint point -> new Vec3(point.x(), point.y(), point.z());
				case Target.None ignored -> null;
			};
			float wantedYaw = 0;
			float wantedPitch = 0;

			if (target != null) {
				Matrix4f world = new Matrix4f(local).mul(skeleton.compute()[bone]);
				Vector3f origin = world.transformPosition(new Vector3f());
				Vector3f direction = new Vector3f((float) (target.x - placed.x() - origin.x), (float) (target.y - placed.y() - origin.y), (float) (target.z - placed.z() - origin.z));
				Matrix3f rotation = world.get3x3(new Matrix3f());
				normalizeColumns(rotation);
				rotation.transpose().transform(direction);

				if (direction.lengthSquared() > 1.0E-8f) {
					direction.normalize();
					wantedYaw = Math.clamp((float) Math.toDegrees(Math.atan2(-direction.x, -direction.z)), -look.maxYaw, look.maxYaw);
					wantedPitch = Math.clamp((float) Math.toDegrees(Math.asin(Math.clamp(direction.y, -1, 1))), -look.maxPitch, look.maxPitch);
				}
			}

			double elapsed = Double.isNaN(look.lastTime) ? 0 : Math.max(0, frameNow - look.lastTime);
			look.lastTime = frameNow;

			if (look.speed <= 0) {
				look.yaw = wantedYaw;
				look.pitch = wantedPitch;
			} else {
				float step = (float) (look.speed * elapsed);
				look.yaw += Math.clamp(wantedYaw - look.yaw, -step, step);
				look.pitch += Math.clamp(wantedPitch - look.pitch, -step, step);
			}

			part.yRot += (float) Math.toRadians(look.yaw);
			part.xRot += (float) Math.toRadians(look.pitch);

			if (target == null && Math.abs(look.yaw) < 0.01f && Math.abs(look.pitch) < 0.01f) {
				iterator.remove();
			}
		}
	}

	private static void normalizeColumns(Matrix3f matrix) {
		Vector3f column = new Vector3f();

		for (int i = 0; i < 3; i++) {
			matrix.getColumn(i, column);
			float length = column.length();

			if (length > 1.0E-8f) {
				matrix.setColumn(i, column.div(length));
			}
		}
	}

	private static void playEffect(AnimatedObject object, AnimationClip.Effect effect) {
		BoneFrame frame = boneFrame(object.id, effect.locator(), 1);

		if (frame != null) {
			ModelEffects.play(effect, frame.origin().x, frame.origin().y, frame.origin().z);
		} else if (object.entity != null) {
			ModelEffects.play(effect, object.entity.getX(), object.entity.getY(), object.entity.getZ());
		}
	}


	public static void setContext(EntityRendererProvider.Context newContext) {
		context = newContext;
		renderer = null;
	}

	public static @Nullable AnimationEntityRenderer renderer() {
		if (renderer == null && context != null) {
			renderer = new AnimationEntityRenderer(context);
		}

		return renderer;
	}

	public static boolean click(Entity entity, boolean attack, InteractionHand hand, Vec3 hit, boolean sneaking) {
		if (!(entity instanceof AnimationEntity animation)) {
			return false;
		}

		Vec3 relative = hit.subtract(entity.position());
		SkaffyConnection.sendFeature(AnimationsCodec.CHANNEL, AnimationsCodec.encode(new Click(animation.object.id, attack ? AnimationsPacket.Button.ATTACK : AnimationsPacket.Button.USE,
				hand == InteractionHand.OFF_HAND, (float) relative.x, (float) relative.y, (float) relative.z, sneaking)));
		return true;
	}
}
