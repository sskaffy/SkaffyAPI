package me.skaffy.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SkaffyClient {
	enum State {
		REGISTERING,
		LOADING,
		READY,
		FAILED
	}

	UUID getPlayerId();

	String getPlayerName();

	String getModVersion();

	Map<String, Integer> getFeatures();

	default boolean hasFeature(String featureId) {
		return getFeatures().containsKey(featureId);
	}

	State getState();

	default boolean isReady() {
		return getState() == State.READY;
	}

	Optional<String> getFailureReason();
}
