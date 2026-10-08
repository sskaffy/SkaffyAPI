package me.skaffy.paper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import me.skaffy.api.AssetRegistry;
import me.skaffy.api.SkaffyAPI;
import me.skaffy.api.SkaffyClient;
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

final class PaperSkaffyAPI implements SkaffyAPI {
	private final ClientManager clients;
	private final PaperAssetRegistry assets;
	private final CustomBlocks blocks;
	private final CustomParticles particles;
	private final CustomKeybinds keybinds;
	private final CustomEntityModels entityModels;
	private final PlayerFov fov;
	private final NameTags nameTags;
	private final SkaffyGuis guis;
	private final PlayerPerspective perspective;
	private final PlayerBrightness brightness;
	private final PlayerLooks playerLooks;
	private final SkaffyShaders shaders;
	private final BlockShapes blockShapes;
	private final SkaffyShapes shapes;
	private final me.skaffy.api.animation.SkaffyAnimations animations;

	PaperSkaffyAPI(ClientManager clients, PaperAssetRegistry assets, CustomBlocks blocks, CustomParticles particles, CustomKeybinds keybinds, CustomEntityModels entityModels,
			PlayerFov fov, NameTags nameTags, SkaffyGuis guis, PlayerPerspective perspective, PlayerBrightness brightness, PlayerLooks playerLooks, SkaffyShaders shaders,
			BlockShapes blockShapes, SkaffyShapes shapes, me.skaffy.api.animation.SkaffyAnimations animations) {
		this.clients = clients;
		this.assets = assets;
		this.blocks = blocks;
		this.particles = particles;
		this.keybinds = keybinds;
		this.entityModels = entityModels;
		this.fov = fov;
		this.nameTags = nameTags;
		this.guis = guis;
		this.perspective = perspective;
		this.brightness = brightness;
		this.playerLooks = playerLooks;
		this.shaders = shaders;
		this.blockShapes = blockShapes;
		this.shapes = shapes;
		this.animations = animations;
	}

	@Override
	public Optional<SkaffyClient> getClient(UUID playerId) {
		return clients.get(playerId).map(client -> client);
	}

	@Override
	public Collection<SkaffyClient> getClients() {
		return List.copyOf(clients.all());
	}

	@Override
	public AssetRegistry getAssets() {
		return assets;
	}

	@Override
	public CustomBlocks getBlocks() {
		return blocks;
	}

	@Override
	public CustomParticles getParticles() {
		return particles;
	}

	@Override
	public CustomKeybinds getKeybinds() {
		return keybinds;
	}

	@Override
	public CustomEntityModels getEntityModels() {
		return entityModels;
	}

	@Override
	public PlayerFov getFov() {
		return fov;
	}

	@Override
	public NameTags getNameTags() {
		return nameTags;
	}

	@Override
	public SkaffyGuis getGuis() {
		return guis;
	}

	@Override
	public PlayerPerspective getPerspective() {
		return perspective;
	}

	@Override
	public PlayerBrightness getBrightness() {
		return brightness;
	}

	@Override
	public PlayerLooks getPlayerLooks() {
		return playerLooks;
	}

	@Override
	public SkaffyShaders getShaders() {
		return shaders;
	}

	@Override
	public BlockShapes getBlockShapes() {
		return blockShapes;
	}

	@Override
	public SkaffyShapes getShapes() {
		return shapes;
	}

	@Override
	public me.skaffy.api.animation.SkaffyAnimations getAnimations() {
		return animations;
	}
}
