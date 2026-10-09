package me.skaffy.client.look;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.mixin.EntityAccessor;
import me.skaffy.client.mixin.LivingEntityAccessor;
import me.skaffy.protocol.playerlooks.PlayerLook;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;

import org.jspecify.annotations.Nullable;

final class LookDummy {
	private static int nextId = -1_000_000;

	final Entity entity;
	private @Nullable AbstractClientPlayer boundTo;

	private LookDummy(Entity entity) {
		this.entity = entity;
	}

	static @Nullable LookDummy create(PlayerLook.EntityForm form, Level level) {
		Identifier id = Identifier.tryParse(form.type());
		EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);

		if (type == null) {
			SkaffySAPIClient.LOGGER.warn("A player look uses the entity type {}, which doesn't exist", form.type());
			return null;
		}

		Entity entity;

		if (form.data().isEmpty()) {
			entity = type.create(level, EntitySpawnReason.LOAD);
		} else {
			CompoundTag data;

			try {
				data = TagParser.parseCompoundFully(form.data());
			} catch (CommandSyntaxException e) {
				SkaffySAPIClient.LOGGER.warn("A player look's {} data isn't valid SNBT: {}", form.type(), e.getMessage());
				data = new CompoundTag();
			}

			ProblemReporter.Collector problems = new ProblemReporter.Collector();
			entity = EntityType.create(type, TagValueInput.create(problems, level.registryAccess(), data), level, EntitySpawnReason.LOAD).orElse(null);

			if (!problems.isEmpty()) {
				SkaffySAPIClient.LOGGER.warn("A player look's {} data has parts Minecraft can't read (they're left out):{}", form.type(), problems.getReport());
			}
		}

		if (entity == null) {
			SkaffySAPIClient.LOGGER.warn("A player look's entity {} can't be made on the client", form.type());
			return null;
		}

		entity.setId(nextId--);
		return new LookDummy(entity);
	}

	void sync(AbstractClientPlayer player) {
		Entity dummy = entity;
		dummy.setPos(player.getX(), player.getY(), player.getZ());
		dummy.xo = player.xo;
		dummy.yo = player.yo;
		dummy.zo = player.zo;
		dummy.xOld = player.xOld;
		dummy.yOld = player.yOld;
		dummy.zOld = player.zOld;
		dummy.tickCount = player.tickCount;
		dummy.setOnGround(player.onGround());
		dummy.getEntityData().set(EntityAccessor.skaffy$sharedFlags(), player.getEntityData().get(EntityAccessor.skaffy$sharedFlags()));

		if (dummy.getPose() != player.getPose()) {
			dummy.setPose(player.getPose());
		}

		if (!(dummy instanceof LivingEntity living)) {
			dummy.setYRot(player.yBodyRot);
			dummy.yRotO = player.yBodyRotO;
			return;
		}

		if (boundTo != player) {
			LivingEntityAccessor access = (LivingEntityAccessor) living;
			access.skaffy$setWalkAnimation(player.walkAnimation);
			access.skaffy$setSwingState(((LivingEntityAccessor) player).skaffy$swingState());
			boundTo = player;
		}

		living.setYRot(player.getYRot());
		living.yRotO = player.yRotO;
		living.setXRot(player.getXRot());
		living.xRotO = player.xRotO;
		living.yBodyRot = player.yBodyRot;
		living.yBodyRotO = player.yBodyRotO;
		living.yHeadRot = player.yHeadRot;
		living.yHeadRotO = player.yHeadRotO;
		living.hurtTime = player.hurtTime;
		living.hurtDuration = player.hurtDuration;
		living.deathTime = player.deathTime;

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);

			if (living.getItemBySlot(slot) != stack) {
				living.setItemSlot(slot, stack);
			}
		}

		BlockPos bed = player.getSleepingPos().orElse(null);

		if (bed == null) {
			living.clearSleepingPos();
		} else {
			living.setSleepingPos(bed);
		}
	}
}
