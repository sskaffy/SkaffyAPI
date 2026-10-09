package me.skaffy.api.look;

import java.util.Objects;
import java.util.UUID;

public record LookSkin(Source source, String asset, LookProfile profile) {
	public enum Source {
		OWN,
		ASSET,
		PROFILE
	}

	private static final LookSkin OWN = new LookSkin(Source.OWN, null, null);

	public LookSkin {
		Objects.requireNonNull(source, "source");

		if (source == Source.ASSET && asset == null || source == Source.PROFILE && profile == null) {
			throw new IllegalArgumentException("A " + source + " skin needs its " + (source == Source.ASSET ? "asset" : "profile"));
		}
	}

	public static LookSkin own() {
		return OWN;
	}

	public static LookSkin asset(String asset) {
		return new LookSkin(Source.ASSET, asset, null);
	}

	public static LookSkin player(String name) {
		return new LookSkin(Source.PROFILE, null, LookProfile.name(name));
	}

	public static LookSkin player(UUID id) {
		return new LookSkin(Source.PROFILE, null, LookProfile.uuid(id));
	}

	public static LookSkin texture(String value, String signature) {
		return new LookSkin(Source.PROFILE, null, LookProfile.texture(value, signature));
	}

	public static LookSkin of(LookProfile profile) {
		return new LookSkin(Source.PROFILE, null, profile);
	}
}
