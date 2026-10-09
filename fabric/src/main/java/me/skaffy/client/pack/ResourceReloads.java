package me.skaffy.client.pack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;

public final class ResourceReloads {
	private static CompletableFuture<Void> last = CompletableFuture.completedFuture(null);

	private ResourceReloads() {
	}

	public static synchronized CompletableFuture<Void> reload() {
		last = last.handle((result, error) -> null).thenCompose(ignored -> start());
		return last;
	}

	private static CompletableFuture<Void> start() {
		Minecraft minecraft = Minecraft.getInstance();
		CompletableFuture<Void> finished = new CompletableFuture<>();

		minecraft.execute(() -> {
			if (minecraft.gui.overlay() instanceof LoadingOverlay) {
				CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() -> start().whenComplete((result, error) -> complete(finished, error)));
				return;
			}

			try {
				List<PackResources> packs = minecraft.getResourcePackRepository().openAllSelected();
				ReloadableResourceManager resources = (ReloadableResourceManager) minecraft.getResourceManager();
				resources.createReload(Util.backgroundExecutor().forName("skaffyResourceLoad"), minecraft, CompletableFuture.completedFuture(Unit.INSTANCE), packs)
						.done()
						.whenCompleteAsync((result, error) -> {
							minecraft.levelExtractor.allChanged();
							complete(finished, error);
						}, minecraft);
			} catch (RuntimeException e) {
				finished.completeExceptionally(e);
			}
		});

		return finished;
	}

	private static void complete(CompletableFuture<Void> future, Throwable error) {
		if (error != null) {
			future.completeExceptionally(error);
		} else {
			future.complete(null);
		}
	}
}
