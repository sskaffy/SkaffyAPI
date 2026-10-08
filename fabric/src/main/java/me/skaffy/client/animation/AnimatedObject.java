package me.skaffy.client.animation;

import java.util.LinkedHashMap;
import java.util.Map;

import me.skaffy.client.model.AnimationPlayer;
import me.skaffy.client.model.ClientModel;
import me.skaffy.client.model.gpu.ModelDrawer;
import me.skaffy.protocol.Easing;
import me.skaffy.protocol.animations.AnimationsPacket.Attachment;
import me.skaffy.protocol.animations.AnimationsPacket.Display;
import me.skaffy.protocol.animations.AnimationsPacket.Settings;
import me.skaffy.protocol.animations.AnimationsPacket.Target;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

final class AnimatedObject {
	final String id;
	final ClientModel model;
	final AnimationPlayer player;
	final Motion motion;
	final Map<String, BoneSetting> bones = new LinkedHashMap<>();
	final Map<String, Look> looks = new LinkedHashMap<>();
	final Map<String, Item> items = new LinkedHashMap<>();
	final ModelDrawer.Holder holder = new ModelDrawer.Holder();
	String dimension;
	Settings settings;
	Attachment attachment = new Attachment.None();
	ClientAnimations.@Nullable Placement lastAttached;
	@Nullable AnimationEntity entity;
	Matrix4f @Nullable [] posed;
	long posedFrame = -1;
	ClientAnimations.@Nullable Placement placed;
	long placedFrame = -1;

	AnimatedObject(String id, ClientModel model, String dimension, Motion.Pose pose, Settings settings) {
		this.id = id;
		this.model = model;
		this.player = new AnimationPlayer(model);
		this.motion = new Motion(pose);
		this.dimension = dimension;
		this.settings = settings;
	}

	static final class BoneSetting {
		boolean hidden;
		int tint = -1;
		final float[] from = {0, 0, 0, 0, 0, 0, 1, 1, 1};
		final float[] to = {0, 0, 0, 0, 0, 0, 1, 1, 1};
		double start;
		double duration;
		Easing easing = Easing.LINEAR;

		float[] at(double now) {
			double progress = duration <= 0 ? 1 : (now - start) / duration;

			if (progress >= 1) {
				return to;
			}

			double eased = easing.apply(Math.max(0, progress));
			float[] value = new float[9];

			for (int i = 0; i < 9; i++) {
				value[i] = (float) (from[i] + (to[i] - from[i]) * eased);
			}

			return value;
		}
	}

	static final class Look {
		Target target = new Target.None();
		float maxYaw;
		float maxPitch;
		float speed;
		float yaw;
		float pitch;
		double lastTime = Double.NaN;
	}

	static final class Item {
		String bone = "";
		Display display = new Display.None();
		final float[] transform = {0, 0, 0, 0, 0, 0, 1};
		@Nullable ItemStack stack;
		@Nullable BlockState block;
		boolean parsed;
	}
}
