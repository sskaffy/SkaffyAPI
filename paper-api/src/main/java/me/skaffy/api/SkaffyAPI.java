package me.skaffy.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import me.skaffy.api.animation.SkaffyAnimations;
import me.skaffy.api.block.CustomBlocks;
import me.skaffy.api.blockshape.BlockShapes;
import me.skaffy.api.brightness.PlayerBrightness;
import me.skaffy.api.fov.PlayerFov;
import me.skaffy.api.gui.SkaffyGuis;
import me.skaffy.api.keybind.CustomKeybinds;
import me.skaffy.api.look.PlayerLooks;
import me.skaffy.api.model.CustomEntityModels;
import me.skaffy.api.nametag.NameTags;
import me.skaffy.api.particle.CustomParticles;
import me.skaffy.api.perspective.PlayerPerspective;
import me.skaffy.api.shader.SkaffyShaders;
import me.skaffy.api.shape.SkaffyShapes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public interface SkaffyAPI {
	static SkaffyAPI get() {
		SkaffyAPI api = Bukkit.getServicesManager().load(SkaffyAPI.class);

		if (api == null) {
			throw new IllegalStateException("SkaffysAPI is not enabled");
		}

		return api;
	}

	Optional<SkaffyClient> getClient(UUID playerId);

	default Optional<SkaffyClient> getClient(Player player) {
		return getClient(player.getUniqueId());
	}

	default boolean isReady(Player player) {
		return getClient(player).map(SkaffyClient::isReady).orElse(false);
	}

	Collection<SkaffyClient> getClients();

	AssetRegistry getAssets();

	CustomBlocks getBlocks();

	CustomParticles getParticles();

	CustomKeybinds getKeybinds();

	CustomEntityModels getEntityModels();

	PlayerFov getFov();

	NameTags getNameTags();

	SkaffyGuis getGuis();

	PlayerPerspective getPerspective();

	PlayerBrightness getBrightness();

	PlayerLooks getPlayerLooks();

	SkaffyShaders getShaders();

	BlockShapes getBlockShapes();

	SkaffyShapes getShapes();

	SkaffyAnimations getAnimations();
}
