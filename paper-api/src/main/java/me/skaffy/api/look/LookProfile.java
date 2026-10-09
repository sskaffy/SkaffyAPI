package me.skaffy.api.look;

import java.util.UUID;

public record LookProfile(UUID id, String name, String value, String signature) {
	public static final int MAX_VALUE_LENGTH = 4096;

	public LookProfile {
		if (id == null && name == null && value == null) {
			throw new IllegalArgumentException("A profile needs a UUID, a name or a skin value");
		}

		if (name != null && (name.isEmpty() || name.length() > 16)) {
			throw new IllegalArgumentException("Player names are 1 to 16 characters, got " + name);
		}

		if (value != null && (value.isEmpty() || value.length() > MAX_VALUE_LENGTH)) {
			throw new IllegalArgumentException("Skin values are 1 to " + MAX_VALUE_LENGTH + " characters");
		}

		if (signature != null && (value == null || signature.isEmpty() || signature.length() > MAX_VALUE_LENGTH)) {
			throw new IllegalArgumentException("A signature needs a skin value and is 1 to " + MAX_VALUE_LENGTH + " characters");
		}
	}

	public static LookProfile name(String name) {
		return new LookProfile(null, name, null, null);
	}

	public static LookProfile uuid(UUID id) {
		return new LookProfile(id, null, null, null);
	}

	public static LookProfile texture(String value) {
		return new LookProfile(null, null, value, null);
	}

	public static LookProfile texture(String value, String signature) {
		return new LookProfile(null, null, value, signature);
	}
}
