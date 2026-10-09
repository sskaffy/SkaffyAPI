package me.skaffy.api.look;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import me.skaffy.api.model.CustomEntityModel;
import org.bukkit.entity.EntityType;

public final class PlayerLook {
	public static final float MAX_SIZE = 256;
	public static final int MAX_DATA_LENGTH = 16384;

	public enum Kind {
		PLAYER,
		ENTITY,
		MODEL
	}

	private final Kind kind;
	private final LookSkin skin;
	private final ArmType arms;
	private final LookCape cape;
	private final String elytra;
	private final Map<SkinLayer, Boolean> layers;
	private final Set<BodyPart> hidden;
	private final String entityType;
	private final String entityData;
	private final String model;
	private final float scale;
	private final float hitboxWidth;
	private final float hitboxHeight;
	private final Float eyeHeight;
	private final boolean ownHitbox;
	private final boolean showToSelf;

	private PlayerLook(Builder builder) {
		this.kind = builder.kind;
		this.skin = builder.skin;
		this.arms = builder.arms;
		this.cape = builder.cape;
		this.elytra = builder.elytra;
		this.layers = Collections.unmodifiableMap(new EnumMap<>(builder.layers));
		this.hidden = Collections.unmodifiableSet(EnumSet.copyOf(builder.hidden));
		this.entityType = builder.entityType;
		this.entityData = builder.entityData;
		this.model = builder.model;
		this.scale = builder.scale;
		this.hitboxWidth = builder.hitboxWidth;
		this.hitboxHeight = builder.hitboxHeight;
		this.eyeHeight = builder.eyeHeight;
		this.ownHitbox = builder.ownHitbox;
		this.showToSelf = builder.showToSelf;
	}

	public static Builder player() {
		return new Builder(Kind.PLAYER, null, null);
	}

	public static Builder entity(EntityType type) {
		Objects.requireNonNull(type, "type");

		if (type == EntityType.PLAYER || type == EntityType.UNKNOWN) {
			throw new IllegalArgumentException("Use PlayerLook.player() for the player model");
		}

		return entity(type.getKey().toString());
	}

	public static Builder entity(String type) {
		if (type == null || type.isEmpty() || type.length() > 128) {
			throw new IllegalArgumentException("Entity type ids are 1 to 128 characters");
		}

		if (type.equals("player") || type.equals("minecraft:player")) {
			throw new IllegalArgumentException("Use PlayerLook.player() for the player model");
		}

		return new Builder(Kind.ENTITY, type, null);
	}

	public static Builder model(CustomEntityModel model) {
		return model(Objects.requireNonNull(model, "model").getName());
	}

	public static Builder model(String name) {
		return new Builder(Kind.MODEL, null, Objects.requireNonNull(name, "name"));
	}

	public Kind getKind() {
		return kind;
	}

	public LookSkin getSkin() {
		return skin;
	}

	public ArmType getArms() {
		return arms;
	}

	public LookCape getCape() {
		return cape;
	}

	public String getElytra() {
		return elytra;
	}

	public Map<SkinLayer, Boolean> getLayers() {
		return layers;
	}

	public Set<BodyPart> getHiddenParts() {
		return hidden;
	}

	public String getEntityType() {
		return entityType;
	}

	public String getEntityData() {
		return entityData;
	}

	public String getModel() {
		return model;
	}

	public float getScale() {
		return scale;
	}

	public boolean hasCustomHitbox() {
		return hitboxWidth > 0;
	}

	public float getHitboxWidth() {
		return hitboxWidth;
	}

	public float getHitboxHeight() {
		return hitboxHeight;
	}

	public Float getEyeHeight() {
		return eyeHeight;
	}

	public boolean hasOwnHitbox() {
		return ownHitbox;
	}

	public boolean showsToSelf() {
		return showToSelf;
	}

	public static final class Builder {
		private final Kind kind;
		private final String entityType;
		private final String model;
		private LookSkin skin = LookSkin.own();
		private ArmType arms = ArmType.FROM_SKIN;
		private LookCape cape = LookCape.own();
		private String elytra;
		private final Map<SkinLayer, Boolean> layers = new EnumMap<>(SkinLayer.class);
		private final Set<BodyPart> hidden = EnumSet.noneOf(BodyPart.class);
		private String entityData = "";
		private float scale = 1;
		private float hitboxWidth;
		private float hitboxHeight;
		private Float eyeHeight;
		private boolean ownHitbox;
		private boolean showToSelf = true;

		private Builder(Kind kind, String entityType, String model) {
			this.kind = kind;
			this.entityType = entityType;
			this.model = model;
		}

		public Builder skin(LookSkin skin) {
			requirePlayer("skin");
			this.skin = Objects.requireNonNull(skin, "skin");
			return this;
		}

		public Builder arms(ArmType arms) {
			requirePlayer("arms");
			this.arms = Objects.requireNonNull(arms, "arms");
			return this;
		}

		public Builder cape(LookCape cape) {
			requirePlayer("cape");
			this.cape = Objects.requireNonNull(cape, "cape");
			return this;
		}

		public Builder elytra(String asset) {
			requirePlayer("elytra");
			this.elytra = asset;
			return this;
		}

		public Builder layer(SkinLayer layer, boolean shown) {
			requirePlayer("layers");
			layers.put(Objects.requireNonNull(layer, "layer"), shown);
			return this;
		}

		public Builder hide(BodyPart... parts) {
			requirePlayer("hidden parts");

			for (BodyPart part : parts) {
				hidden.add(Objects.requireNonNull(part, "part"));
			}

			return this;
		}

		public Builder data(String snbt) {
			if (kind != Kind.ENTITY) {
				throw new IllegalStateException("Only entity looks have data");
			}

			if (snbt == null || snbt.length() > MAX_DATA_LENGTH) {
				throw new IllegalArgumentException("Entity data must be at most " + MAX_DATA_LENGTH + " characters");
			}

			this.entityData = snbt;
			return this;
		}

		public Builder scale(float scale) {
			this.scale = scale;
			return this;
		}

		public Builder hitbox(float width, float height) {
			this.hitboxWidth = width;
			this.hitboxHeight = height;
			this.eyeHeight = null;
			return this;
		}

		public Builder hitbox(float width, float height, float eyeHeight) {
			this.hitboxWidth = width;
			this.hitboxHeight = height;
			this.eyeHeight = eyeHeight;
			return this;
		}

		public Builder ownHitbox(boolean ownHitbox) {
			this.ownHitbox = ownHitbox;
			return this;
		}

		public Builder showToSelf(boolean showToSelf) {
			this.showToSelf = showToSelf;
			return this;
		}

		public PlayerLook build() {
			checkSize("Scale", scale);

			if (hitboxWidth != 0 || hitboxHeight != 0) {
				checkSize("Hitbox width", hitboxWidth);
				checkSize("Hitbox height", hitboxHeight);

				if (eyeHeight != null && !(eyeHeight >= 0 && eyeHeight <= hitboxHeight)) {
					throw new IllegalArgumentException("Eye height must be 0 to the hitbox height, got " + eyeHeight);
				}
			}

			return new PlayerLook(this);
		}

		private void requirePlayer(String what) {
			if (kind != Kind.PLAYER) {
				throw new IllegalStateException("Only player model looks have " + what);
			}
		}

		private static void checkSize(String what, float value) {
			if (!(value > 0 && value <= MAX_SIZE)) {
				throw new IllegalArgumentException(what + " must be above 0 and at most " + MAX_SIZE + ", got " + value);
			}
		}
	}
}
