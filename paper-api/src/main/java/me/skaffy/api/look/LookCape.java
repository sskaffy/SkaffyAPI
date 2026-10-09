package me.skaffy.api.look;

import java.util.Objects;
import java.util.UUID;

public record LookCape(Source source, String asset, LookProfile profile) {
	public enum Source {
		OWN,
		NONE,
		ASSET,
		PROFILE
	}

	private static final LookCape OWN = new LookCape(Source.OWN, null, null);
	private static final LookCape NONE = new LookCape(Source.NONE, null, null);

	public LookCape {
		Objects.requireNonNull(source, "source");

		if (source == Source.ASSET && asset == null || source == Source.PROFILE && profile == null) {
			throw new IllegalArgumentException("A " + source + " cape needs its " + (source == Source.ASSET ? "asset" : "profile"));
		}
	}

	public static LookCape own() {
		return OWN;
	}

	public static LookCape none() {
		return NONE;
	}

	public static LookCape asset(String asset) {
		return new LookCape(Source.ASSET, asset, null);
	}

	public static LookCape player(String name) {
		return new LookCape(Source.PROFILE, null, LookProfile.name(name));
	}

	public static LookCape player(UUID id) {
		return new LookCape(Source.PROFILE, null, LookProfile.uuid(id));
	}

	public static LookCape of(LookProfile profile) {
		return new LookCape(Source.PROFILE, null, profile);
	}
}
