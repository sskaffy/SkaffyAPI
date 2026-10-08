package me.skaffy.client.nametag;

import com.mojang.renderpearl.api.commands.RenderPass;

import me.skaffy.client.mixin.PreparedFrameAccessor;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;

public final class LateNameTags {
	private LateNameTags() {
	}

	public interface Storage {
		TranslucentFeatureRenderPhase skaffy$lateNameTags();
	}

	public static void execute(FeatureRenderDispatcher.PreparedFrame frame, RenderPass renderPass) {
		PreparedFrameAccessor accessor = (PreparedFrameAccessor) frame;
		SubmitNodeStorage storage = accessor.skaffy$submitNodeStorage();
		FeatureFrameContext context = accessor.skaffy$context();

		if (storage != null && context != null) {
			accessor.skaffy$executePhase(((Storage) storage).skaffy$lateNameTags(), context, renderPass);
		}
	}
}
