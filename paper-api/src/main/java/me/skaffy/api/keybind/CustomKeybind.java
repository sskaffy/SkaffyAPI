package me.skaffy.api.keybind;

import java.util.Objects;

public final class CustomKeybind {
	public static final String UNBOUND = "key.keyboard.unknown";
	public static final int MAX_LENGTH = 64;

	private final String id;
	private final String name;
	private final String defaultKey;
	private final boolean active;
	private final boolean winsOverVanilla;

	private CustomKeybind(Builder builder) {
		this.id = builder.id;
		this.name = builder.name;
		this.defaultKey = builder.defaultKey;
		this.active = builder.active;
		this.winsOverVanilla = builder.winsOverVanilla;
	}

	public static Builder builder(String id, String name) {
		return new Builder(id, name);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDefaultKey() {
		return defaultKey;
	}

	public boolean isActiveAtStart() {
		return active;
	}

	public boolean winsOverVanillaAtStart() {
		return winsOverVanilla;
	}

	@Override
	public String toString() {
		return "CustomKeybind[" + id + "]";
	}

	static void checkLength(String what, String value) {
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException(what + " must be 1 to " + MAX_LENGTH + " characters: " + value);
		}
	}

	public static final class Builder {
		private final String id;
		private final String name;
		private String defaultKey = UNBOUND;
		private boolean active = true;
		private boolean winsOverVanilla;

		private Builder(String id, String name) {
			this.id = Objects.requireNonNull(id, "id");
			this.name = Objects.requireNonNull(name, "name");
		}

		public Builder defaultKey(String key) {
			this.defaultKey = Objects.requireNonNull(key, "key");
			return this;
		}

		public Builder active(boolean active) {
			this.active = active;
			return this;
		}

		public Builder winsOverVanilla(boolean wins) {
			this.winsOverVanilla = wins;
			return this;
		}

		public CustomKeybind build() {
			checkLength("Keybind id", id);
			checkLength("Keybind name", name);
			checkLength("Key name", defaultKey);
			return new CustomKeybind(this);
		}
	}
}
