package me.skaffy.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CustomEntityModel {
	public record ModelAnimation(String asset, String name, boolean replacesVanilla) {
		public ModelAnimation {
			Objects.requireNonNull(asset, "asset");
			Objects.requireNonNull(name, "name");
		}
	}

	private final String name;
	private final String geometry;
	private final String babyGeometry;
	private final String texture;
	private final String emissiveTexture;
	private final boolean translucent;
	private final float scale;
	private final List<ModelAnimation> animations;
	private final String idle;
	private final String walk;
	private final String attack;
	private final String hurt;
	private final String death;

	private CustomEntityModel(Builder builder) {
		this.name = builder.name;
		this.geometry = builder.geometry;
		this.babyGeometry = builder.babyGeometry;
		this.texture = builder.texture;
		this.emissiveTexture = builder.emissiveTexture;
		this.translucent = builder.translucent;
		this.scale = builder.scale;
		this.animations = List.copyOf(builder.animations);
		this.idle = builder.idle;
		this.walk = builder.walk;
		this.attack = builder.attack;
		this.hurt = builder.hurt;
		this.death = builder.death;
	}

	public static Builder builder(String name, String geometry, String texture) {
		return new Builder(name, geometry, texture);
	}

	public static Builder builder(String name, String geometry) {
		return new Builder(name, geometry, null);
	}

	public String getName() {
		return name;
	}

	public String getGeometry() {
		return geometry;
	}

	public String getBabyGeometry() {
		return babyGeometry;
	}

	public String getTexture() {
		return texture;
	}

	public String getEmissiveTexture() {
		return emissiveTexture;
	}

	public boolean isTranslucent() {
		return translucent;
	}

	public float getScale() {
		return scale;
	}

	public List<ModelAnimation> getAnimations() {
		return animations;
	}

	public Optional<ModelAnimation> getAnimation(String name) {
		return animations.stream().filter(animation -> animation.name().equals(name)).findFirst();
	}

	public String getIdle() {
		return idle;
	}

	public String getWalk() {
		return walk;
	}

	public String getAttack() {
		return attack;
	}

	public String getHurt() {
		return hurt;
	}

	public String getDeath() {
		return death;
	}

	@Override
	public String toString() {
		return "CustomEntityModel[" + name + "]";
	}

	public static final class Builder {
		private final String name;
		private final String geometry;
		private String texture;
		private String babyGeometry;
		private String emissiveTexture;
		private boolean translucent;
		private float scale = 1;
		private final List<ModelAnimation> animations = new ArrayList<>();
		private String idle;
		private String walk;
		private String attack;
		private String hurt;
		private String death;

		private Builder(String name, String geometry, String texture) {
			this.name = Objects.requireNonNull(name, "name");
			this.geometry = Objects.requireNonNull(geometry, "geometry");
			this.texture = texture;
		}

		public Builder texture(String texture) {
			this.texture = texture;
			return this;
		}

		public List<ModelAnimation> animations() {
			return List.copyOf(animations);
		}

		public Builder babyGeometry(String geometry) {
			this.babyGeometry = geometry;
			return this;
		}

		public Builder emissiveTexture(String texture) {
			this.emissiveTexture = texture;
			return this;
		}

		public Builder translucent(boolean translucent) {
			this.translucent = translucent;
			return this;
		}

		public Builder scale(float scale) {
			this.scale = scale;
			return this;
		}

		public Builder animation(String asset, String name) {
			return animation(asset, name, false);
		}

		public Builder animation(String asset, String name, boolean replacesVanilla) {
			animations.add(new ModelAnimation(asset, name, replacesVanilla));
			return this;
		}

		public Builder idle(String animation) {
			this.idle = animation;
			return this;
		}

		public Builder walk(String animation) {
			this.walk = animation;
			return this;
		}

		public Builder attack(String animation) {
			this.attack = animation;
			return this;
		}

		public Builder hurt(String animation) {
			this.hurt = animation;
			return this;
		}

		public Builder death(String animation) {
			this.death = animation;
			return this;
		}

		public CustomEntityModel build() {
			if (name.isEmpty() || name.length() > 64) {
				throw new IllegalArgumentException("Model name must be 1 to 64 characters: " + name);
			}

			if (!(scale > 0) || !Float.isFinite(scale)) {
				throw new IllegalArgumentException("Scale must be above 0, got " + scale);
			}

			if (animations.size() > 256) {
				throw new IllegalArgumentException("A model has at most 256 animations");
			}

			for (String automatic : new String[] {idle, walk, attack, hurt, death}) {
				if (automatic != null && animations.stream().noneMatch(animation -> animation.name().equals(automatic))) {
					throw new IllegalArgumentException("Model " + name + " uses animation " + automatic + " automatically but doesn't have it (add it with animation())");
				}
			}

			return new CustomEntityModel(this);
		}
	}
}
