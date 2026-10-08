package me.skaffy.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import me.skaffy.client.asset.DiskAssetStore;
import me.skaffy.client.block.ClientBlocks;
import me.skaffy.client.blockshape.ClientBlockShapes;
import me.skaffy.client.brightness.ClientBrightness;
import me.skaffy.client.fov.ClientFov;
import me.skaffy.client.gui.ClientGuis;
import me.skaffy.client.keybind.ClientKeybinds;
import me.skaffy.client.look.ClientPlayerLooks;
import me.skaffy.client.model.ClientEntityModels;
import me.skaffy.client.nametag.ClientNameTags;
import me.skaffy.client.pack.HiddenPack;
import me.skaffy.client.pack.PackBuilder;
import me.skaffy.client.pack.ResourceReloads;
import me.skaffy.client.particle.ClientParticles;
import me.skaffy.client.perspective.ClientPerspective;
import me.skaffy.client.shader.ClientShaders;
import me.skaffy.client.shape.ClientShapes;

import net.minecraft.client.Minecraft;

public final class ClientContent {
	private static final AtomicInteger GENERATION = new AtomicInteger();

	private ClientContent() {
	}

	public static CompletableFuture<Void> load(DiskAssetStore assets) {
		int generation = GENERATION.get();
		Minecraft minecraft = Minecraft.getInstance();
		PackBuilder pack = new PackBuilder(assets, minecraft.getVanillaPackResources().fullResources());
		ClientParticles.load(pack);
		ClientEntityModels.load(pack, assets);
		ClientNameTags.load(pack);
		ClientGuis.load(pack, assets);
		ClientPlayerLooks.load(assets);
		ClientShaders.load(assets);
		ClientShapes.load(assets);

		return ClientBlocks.load(pack).thenComposeAsync(ignored -> {
			if (pack.files().isEmpty() || GENERATION.get() != generation) {
				return CompletableFuture.completedFuture(null);
			}

			HiddenPack.setActive(new HiddenPack(pack.files()));
			return ResourceReloads.reload();
		}, minecraft).thenRunAsync(() -> {
			if (GENERATION.get() == generation) {
				ClientBlocks.activate();
				ClientParticles.activate();
				ClientKeybinds.activate();
				ClientEntityModels.activate();
				ClientNameTags.activate();
				ClientGuis.activate();
				ClientShaders.activate();
			}
		}, minecraft);
	}

	public static void unload() {
		GENERATION.incrementAndGet();
		ClientBlocks.unload();
		ClientParticles.unload();
		ClientKeybinds.unload();
		ClientEntityModels.unload();
		ClientFov.unload();
		ClientNameTags.unload();
		ClientGuis.unload();
		ClientPerspective.unload();
		ClientBrightness.unload();
		ClientPlayerLooks.unload();
		ClientShaders.unload();
		ClientBlockShapes.unload();
		ClientShapes.unload();
		me.skaffy.client.animation.ClientAnimations.unload();

		Minecraft.getInstance().execute(() -> {
			if (HiddenPack.active() != null) {
				HiddenPack.setActive(null);
				ResourceReloads.reload();
			}
		});
	}
}
