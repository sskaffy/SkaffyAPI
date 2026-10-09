package me.skaffy.api.animation;

import java.util.Set;

import me.skaffy.api.Easing;
import me.skaffy.api.model.CustomEntityModel;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public interface AnimationEntity {
	String getId();

	CustomEntityModel getModel();

	Player getViewer();

	Location getLocation();

	Set<Player> getSeenBy();


	void teleport(Location location);

	void moveTo(Location location, int millis, Easing easing);

	void setRoll(float roll, int millis, Easing easing);

	void setScale(float scale, int millis, Easing easing);

	void followPath(AnimationPath path);

	void attachTo(Entity entity, Vector offset, float yaw, float pitch, float roll, boolean turnWithBody);

	void attachTo(AnimationEntity parent, String bone, Vector offset, float yaw, float pitch, float roll);

	void detach();


	default void play(String animation) {
		play(animation, PlayOptions.DEFAULT);
	}

	void play(String animation, PlayOptions options);

	void stop(String animation, float fadeOut);

	void stopAll(float fadeOut);

	Set<String> getPlaying();

	void setVariable(String name, float value);


	void setBone(String bone, BoneSettings settings, int millis, Easing easing);

	void resetBone(String bone);

	void lookAt(String bone, Entity target, float maxYaw, float maxPitch, float speed);

	void lookAtCamera(String bone, float maxYaw, float maxPitch, float speed);

	void lookAt(String bone, Location point, float maxYaw, float maxPitch, float speed);

	void stopLooking(String bone);

	void setItem(String slot, String bone, ItemStack item, String context, ItemTransform transform);

	void setBlock(String slot, String bone, BlockData block, ItemTransform transform);

	void removeItem(String slot);


	void setHitbox(float width, float height);

	void setFullBright(boolean fullBright);

	void setGlowColor(Color color);

	void setShadowRadius(float radius);

	void setViewDistance(float blocks);

	void setShowInFirstPerson(boolean show);

	void setTrackingRange(double blocks);

	void remove();

	boolean isRemoved();
}
