package me.skaffy.paper.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.skaffy.api.Easing;
import me.skaffy.api.animation.AnimationEntity;
import me.skaffy.api.animation.AnimationPath;
import me.skaffy.api.animation.BoneSettings;
import me.skaffy.api.animation.ItemTransform;
import me.skaffy.api.animation.PlayOptions;
import me.skaffy.api.model.CustomEntityModel;
import me.skaffy.protocol.animations.AnimationsPacket;
import me.skaffy.protocol.animations.AnimationsPacket.Attachment;
import me.skaffy.protocol.animations.AnimationsPacket.PathPoint;
import me.skaffy.protocol.animations.AnimationsPacket.Settings;
import me.skaffy.protocol.entitymodels.EntityModelsPacket;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

final class PaperAnimationEntity implements AnimationEntity {
	record Played(String animation, PlayOptions options, long start) {
	}

	final PaperAnimations owner;
	final String id;
	final String wireId;
	final CustomEntityModel model;
	final UUID viewer;
	final ServerMotion motion;
	final Set<UUID> seenBy = ConcurrentHashMap.newKeySet();
	final Map<String, Played> played = Collections.synchronizedMap(new LinkedHashMap<>());
	final Map<String, Float> variables = new ConcurrentHashMap<>();
	final Map<String, AnimationsPacket.SetBone> bones = new ConcurrentHashMap<>();
	final Map<String, AnimationsPacket.LookAt> looks = new ConcurrentHashMap<>();
	final Map<String, AnimationsPacket.SetItem> items = new ConcurrentHashMap<>();
	volatile World world;
	volatile Attachment attachment = new Attachment.None();
	volatile UUID attachedEntity;
	volatile PaperAnimationEntity attachedParent;
	volatile Settings settings = Settings.DEFAULT;
	volatile double trackingRange;
	volatile boolean removed;

	PaperAnimationEntity(PaperAnimations owner, String id, String wireId, CustomEntityModel model, UUID viewer, Location location) {
		this.owner = owner;
		this.id = id;
		this.wireId = wireId;
		this.model = model;
		this.viewer = viewer;
		this.world = location.getWorld();
		this.motion = new ServerMotion(new ServerMotion.Pose(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), 0, 1));
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public CustomEntityModel getModel() {
		return model;
	}

	@Override
	public Player getViewer() {
		return viewer == null ? null : Bukkit.getPlayer(viewer);
	}

	@Override
	public Location getLocation() {
		switch (attachment) {
			case Attachment.ToEntity attached -> {
				Entity entity = attachedEntity == null ? null : Bukkit.getEntity(attachedEntity);

				if (entity != null) {
					Location base = entity.getLocation();
					float yaw = !attached.turn() ? 0 : entity instanceof LivingEntity living ? living.getBodyYaw() : base.getYaw();
					Vector offset = new Vector(attached.x(), attached.y(), attached.z()).rotateAroundY(Math.toRadians(-yaw));
					Location location = base.add(offset);
					location.setYaw(yaw + attached.yaw());
					location.setPitch(attached.pitch());
					return location;
				}
			}
			case Attachment.ToBone ignored -> {
				PaperAnimationEntity parent = attachedParent;

				if (parent != null && !parent.removed) {
					return parent.getLocation();
				}
			}
			default -> {
			}
		}

		ServerMotion.Pose pose = motion.at(System.nanoTime());
		return new Location(world, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
	}

	@Override
	public Set<Player> getSeenBy() {
		Set<Player> players = new java.util.HashSet<>();

		for (UUID id : seenBy) {
			Player player = Bukkit.getPlayer(id);

			if (player != null) {
				players.add(player);
			}
		}

		return players;
	}


	@Override
	public void teleport(Location location) {
		check();
		detachQuietly();
		ServerMotion.Pose current = motion.at(System.nanoTime());
		ServerMotion.Pose target = new ServerMotion.Pose(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), current.roll(), current.scale());
		boolean changedWorld = location.getWorld() != world;
		world = location.getWorld();
		motion.set(target);

		if (changedWorld && viewer == null) {
			owner.sendRemove(this);
		} else {
			owner.send(this, new AnimationsPacket.Move(wireId, placement(target), 0, me.skaffy.protocol.Easing.LINEAR));
		}
	}

	@Override
	public void moveTo(Location location, int millis, Easing easing) {
		check();

		if (location.getWorld() != world) {
			teleport(location);
			return;
		}

		detachQuietly();
		ServerMotion.Pose current = motion.at(System.nanoTime());
		ServerMotion.Pose target = new ServerMotion.Pose(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), current.roll(), current.scale());
		motion.moveTo(target, millis, easing(easing));
		owner.send(this, new AnimationsPacket.Move(wireId, placement(target), millis, easing(easing)));
	}

	@Override
	public void setRoll(float roll, int millis, Easing easing) {
		check();
		ServerMotion.Pose current = finalPose();
		changePose(new ServerMotion.Pose(current.x(), current.y(), current.z(), current.yaw(), current.pitch(), roll, current.scale()), millis, easing);
	}

	@Override
	public void setScale(float scale, int millis, Easing easing) {
		check();

		if (!(scale > 0) || !Float.isFinite(scale)) {
			throw new IllegalArgumentException("Scale must be above 0");
		}

		ServerMotion.Pose current = finalPose();
		changePose(new ServerMotion.Pose(current.x(), current.y(), current.z(), current.yaw(), current.pitch(), current.roll(), scale), millis, easing);
	}

	private ServerMotion.Pose finalPose() {
		ServerMotion.Move move = motion.move();
		return move != null ? move.to() : motion.at(System.nanoTime());
	}

	private void changePose(ServerMotion.Pose target, int millis, Easing easing) {
		ServerMotion.Move move = motion.move();

		if (motion.path() != null || !(attachment instanceof Attachment.None)) {
			motion.set(target);
		} else {
			int remaining = move == null ? millis : (int) Math.max(millis, (move.start() + move.duration() - System.nanoTime()) / 1_000_000);
			motion.moveTo(target, remaining, easing(easing));
			millis = remaining;
		}

		owner.send(this, new AnimationsPacket.Move(wireId, placement(target), millis, easing(easing)));
	}

	@Override
	public void followPath(AnimationPath path) {
		check();

		if (path.getPoints().getFirst().location().getWorld() != world) {
			teleport(path.getPoints().getFirst().location());
		}

		detachQuietly();
		List<PathPoint> points = points(path);
		motion.follow(points, path.isSmooth(), path.isLoop(), path.isFaceAlong(), path.getStartAt());
		owner.send(this, new AnimationsPacket.FollowPath(wireId, points, path.isSmooth(), path.isLoop(), path.isFaceAlong(), path.getStartAt()));
	}

	static List<PathPoint> points(AnimationPath path) {
		List<PathPoint> points = new ArrayList<>();

		for (AnimationPath.Point point : path.getPoints()) {
			Location location = point.location();
			points.add(new PathPoint((int) point.millis(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), point.roll()));
		}

		return points;
	}

	@Override
	public void attachTo(Entity entity, Vector offset, float yaw, float pitch, float roll, boolean turnWithBody) {
		check();
		attachedEntity = entity.getUniqueId();
		attachedParent = null;
		attachment = new Attachment.ToEntity(entity.getEntityId(), (float) offset.getX(), (float) offset.getY(), (float) offset.getZ(), yaw, pitch, roll, turnWithBody);

		if (entity.getWorld() != world) {
			world = entity.getWorld();
		}

		owner.send(this, new AnimationsPacket.Attach(wireId, attachment));
	}

	@Override
	public void attachTo(AnimationEntity parent, String bone, Vector offset, float yaw, float pitch, float roll) {
		check();

		if (!(parent instanceof PaperAnimationEntity paperParent) || paperParent.removed) {
			throw new IllegalArgumentException("The parent must be an animation entity that still exists");
		}

		if (paperParent == this || (viewer == null) != (paperParent.viewer == null) || viewer != null && !viewer.equals(paperParent.viewer)) {
			throw new IllegalArgumentException("An animation entity can only attach to another one seen by the same players");
		}

		attachedEntity = null;
		attachedParent = paperParent;
		attachment = new Attachment.ToBone(paperParent.wireId, bone, (float) offset.getX(), (float) offset.getY(), (float) offset.getZ(), yaw, pitch, roll);
		world = paperParent.world;
		owner.send(this, new AnimationsPacket.Attach(wireId, attachment));
	}

	@Override
	public void detach() {
		check();

		if (attachment instanceof Attachment.None) {
			return;
		}

		detachQuietly();
		owner.send(this, new AnimationsPacket.Attach(wireId, new Attachment.None()));
	}

	private void detachQuietly() {
		if (attachment instanceof Attachment.None) {
			return;
		}

		Location location = getLocation();
		attachment = new Attachment.None();
		attachedEntity = null;
		attachedParent = null;
		ServerMotion.Pose current = motion.at(System.nanoTime());
		motion.set(new ServerMotion.Pose(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), current.roll(), current.scale()));
	}


	@Override
	public void play(String animation, PlayOptions options) {
		check();
		int number = PaperAnimations.animationNumber(model, animation);

		if (number == 0) {
			throw new IllegalArgumentException("Model " + model.getName() + " has no animation " + animation);
		}

		played.remove(animation);
		played.put(animation, new Played(animation, options, System.nanoTime()));

		synchronized (played) {
			while (played.size() > 16) {
				played.remove(played.keySet().iterator().next());
			}
		}

		owner.startTimer(this, animation, options);
		owner.send(this, new AnimationsPacket.Play(wireId, number, mode(options), options.speed(), options.startAt(), options.fadeIn(), options.removeWhenDone()));
	}

	@Override
	public void stop(String animation, float fadeOut) {
		check();
		int number = PaperAnimations.animationNumber(model, animation);

		if (number == 0 || played.remove(animation) == null) {
			return;
		}

		owner.stopTimer(this, animation);
		owner.send(this, new AnimationsPacket.Stop(wireId, number, fadeOut));
	}

	@Override
	public void stopAll(float fadeOut) {
		check();
		played.clear();
		owner.stopTimers(this);
		owner.send(this, new AnimationsPacket.Stop(wireId, 0, fadeOut));
	}

	@Override
	public Set<String> getPlaying() {
		synchronized (played) {
			return Set.copyOf(new ArrayList<>(played.keySet()));
		}
	}

	@Override
	public void setVariable(String name, float value) {
		check();
		EntityModelsPacket.Variable variable = new EntityModelsPacket.Variable(name, value);
		variables.put(name, value);
		owner.send(this, new AnimationsPacket.SetVariables(wireId, List.of(variable)));
	}


	@Override
	public void setBone(String bone, BoneSettings settings, int millis, Easing easing) {
		check();
		Vector r = settings.rotation();
		Vector p = settings.position();
		Vector s = settings.scale();
		int tint = settings.tint() == null ? -1 : settings.tint().asARGB();
		AnimationsPacket.SetBone packet = new AnimationsPacket.SetBone(wireId, bone, settings.hidden(), tint, (float) r.getX(), (float) r.getY(), (float) r.getZ(),
				(float) p.getX(), (float) p.getY(), (float) p.getZ(), (float) s.getX(), (float) s.getY(), (float) s.getZ(), millis, easing(easing));
		bones.put(bone, new AnimationsPacket.SetBone(wireId, bone, settings.hidden(), tint, (float) r.getX(), (float) r.getY(), (float) r.getZ(),
				(float) p.getX(), (float) p.getY(), (float) p.getZ(), (float) s.getX(), (float) s.getY(), (float) s.getZ(), 0, me.skaffy.protocol.Easing.LINEAR));
		owner.send(this, packet);
	}

	@Override
	public void resetBone(String bone) {
		check();
		bones.remove(bone);
		owner.send(this, new AnimationsPacket.SetBone(wireId, bone, false, -1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, me.skaffy.protocol.Easing.LINEAR));
	}

	@Override
	public void lookAt(String bone, Entity target, float maxYaw, float maxPitch, float speed) {
		look(new AnimationsPacket.LookAt(wireId, bone, new AnimationsPacket.Target.AtEntity(target.getEntityId()), maxYaw, maxPitch, speed));
	}

	@Override
	public void lookAtCamera(String bone, float maxYaw, float maxPitch, float speed) {
		look(new AnimationsPacket.LookAt(wireId, bone, new AnimationsPacket.Target.AtCamera(), maxYaw, maxPitch, speed));
	}

	@Override
	public void lookAt(String bone, Location point, float maxYaw, float maxPitch, float speed) {
		look(new AnimationsPacket.LookAt(wireId, bone, new AnimationsPacket.Target.AtPoint(point.getX(), point.getY(), point.getZ()), maxYaw, maxPitch, speed));
	}

	@Override
	public void stopLooking(String bone) {
		check();
		AnimationsPacket.LookAt previous = looks.remove(bone);

		if (previous != null) {
			owner.send(this, new AnimationsPacket.LookAt(wireId, bone, new AnimationsPacket.Target.None(), previous.maxYaw(), previous.maxPitch(), previous.speed()));
		}
	}

	private void look(AnimationsPacket.LookAt packet) {
		check();
		looks.put(packet.bone(), packet);
		owner.send(this, packet);
	}

	@Override
	public void setItem(String slot, String bone, ItemStack item, String context, ItemTransform transform) {
		check();
		ItemMeta meta = item.getItemMeta();
		String components = meta == null ? "" : meta.getAsComponentString();
		String text = item.getType().getKey() + (components == null || components.equals("[]") ? "" : components);
		item(new AnimationsPacket.SetItem(wireId, slot, bone, new AnimationsPacket.Display.Item(text, context), (float) transform.offset().getX(),
				(float) transform.offset().getY(), (float) transform.offset().getZ(), (float) transform.rotation().getX(), (float) transform.rotation().getY(),
				(float) transform.rotation().getZ(), transform.scale()));
	}

	@Override
	public void setBlock(String slot, String bone, BlockData block, ItemTransform transform) {
		check();
		item(new AnimationsPacket.SetItem(wireId, slot, bone, new AnimationsPacket.Display.Block(block.getAsString()), (float) transform.offset().getX(),
				(float) transform.offset().getY(), (float) transform.offset().getZ(), (float) transform.rotation().getX(), (float) transform.rotation().getY(),
				(float) transform.rotation().getZ(), transform.scale()));
	}

	private void item(AnimationsPacket.SetItem packet) {
		items.put(packet.slot(), packet);
		owner.send(this, packet);
	}

	@Override
	public void removeItem(String slot) {
		check();

		if (items.remove(slot) != null) {
			owner.send(this, new AnimationsPacket.SetItem(wireId, slot, "", new AnimationsPacket.Display.None(), 0, 0, 0, 0, 0, 0, 1));
		}
	}


	@Override
	public void setHitbox(float width, float height) {
		Settings s = settings;
		settings(new Settings(width, height, s.fullBright(), s.glowColor(), s.shadowRadius(), s.viewDistance(), s.showInFirstPerson()));
	}

	@Override
	public void setFullBright(boolean fullBright) {
		Settings s = settings;
		settings(new Settings(s.hitboxWidth(), s.hitboxHeight(), fullBright, s.glowColor(), s.shadowRadius(), s.viewDistance(), s.showInFirstPerson()));
	}

	@Override
	public void setGlowColor(Color color) {
		Settings s = settings;
		settings(new Settings(s.hitboxWidth(), s.hitboxHeight(), s.fullBright(), color == null ? 0 : color.asRGB() | 0xFF000000, s.shadowRadius(), s.viewDistance(),
				s.showInFirstPerson()));
	}

	@Override
	public void setShadowRadius(float radius) {
		Settings s = settings;
		settings(new Settings(s.hitboxWidth(), s.hitboxHeight(), s.fullBright(), s.glowColor(), radius, s.viewDistance(), s.showInFirstPerson()));
	}

	@Override
	public void setViewDistance(float blocks) {
		Settings s = settings;
		settings(new Settings(s.hitboxWidth(), s.hitboxHeight(), s.fullBright(), s.glowColor(), s.shadowRadius(), blocks, s.showInFirstPerson()));
	}

	@Override
	public void setShowInFirstPerson(boolean show) {
		Settings s = settings;
		settings(new Settings(s.hitboxWidth(), s.hitboxHeight(), s.fullBright(), s.glowColor(), s.shadowRadius(), s.viewDistance(), show));
	}

	private void settings(Settings settings) {
		check();
		this.settings = settings;
		owner.send(this, new AnimationsPacket.SetSettings(wireId, settings));
	}

	@Override
	public void setTrackingRange(double blocks) {
		if (!(blocks >= 0) || !Double.isFinite(blocks)) {
			throw new IllegalArgumentException("Tracking range must be 0 or more");
		}

		trackingRange = blocks;
	}

	@Override
	public void remove() {
		if (!removed) {
			owner.remove(this);
		}
	}

	@Override
	public boolean isRemoved() {
		return removed;
	}

	private void check() {
		if (removed) {
			throw new IllegalStateException("Animation entity " + id + " was removed");
		}
	}


	AnimationsPacket.Placement placement(ServerMotion.Pose pose) {
		return new AnimationsPacket.Placement(world.getKey().toString(), pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.roll(), pose.scale(), pose.scale(),
				pose.scale());
	}

	List<AnimationsPacket> state() {
		List<AnimationsPacket> packets = new ArrayList<>();
		long now = System.nanoTime();
		ServerMotion.Move move = motion.move();
		ServerMotion.Path path = motion.path();

		if (move != null) {
			int remaining = (int) Math.max(0, (move.start() + move.duration() - now) / 1_000_000);
			packets.add(new AnimationsPacket.Move(wireId, placement(move.to()), remaining, me.skaffy.protocol.Easing.LINEAR));
		} else if (path != null) {
			packets.add(new AnimationsPacket.FollowPath(wireId, path.points(), path.smooth(), path.loop(), path.faceAlong(), motion.pathElapsed(now)));
		}

		if (!(attachment instanceof Attachment.None)) {
			packets.add(new AnimationsPacket.Attach(wireId, attachment));
		}

		synchronized (played) {
			for (Played entry : played.values()) {
				float seconds = (now - entry.start()) / 1.0E9f;
				PlayOptions options = entry.options();
				int number = PaperAnimations.animationNumber(model, entry.animation());

				if (number > 0) {
					packets.add(new AnimationsPacket.Play(wireId, number, mode(options), options.speed(), options.startAt() + seconds * options.speed(),
							Math.max(0, options.fadeIn() - seconds), options.removeWhenDone()));
				}
			}
		}

		if (!variables.isEmpty()) {
			List<EntityModelsPacket.Variable> list = new ArrayList<>();
			variables.forEach((name, value) -> list.add(new EntityModelsPacket.Variable(name, value)));

			for (int i = 0; i < list.size(); i += EntityModelsPacket.SetVariables.MAX_VARIABLES) {
				packets.add(new AnimationsPacket.SetVariables(wireId, list.subList(i, Math.min(list.size(), i + EntityModelsPacket.SetVariables.MAX_VARIABLES))));
			}
		}

		packets.addAll(bones.values());
		packets.addAll(looks.values());
		packets.addAll(items.values());
		return packets;
	}

	static me.skaffy.protocol.Easing easing(Easing easing) {
		return me.skaffy.protocol.Easing.valueOf(easing.name());
	}

	static EntityModelsPacket.PlayAnimation.Mode mode(PlayOptions options) {
		return EntityModelsPacket.PlayAnimation.Mode.valueOf(options.mode().name());
	}
}
