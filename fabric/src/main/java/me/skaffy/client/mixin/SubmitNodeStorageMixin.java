package me.skaffy.client.mixin;

import java.util.function.Consumer;

import me.skaffy.client.nametag.LateNameTags;
import me.skaffy.client.shape.ClientShapes;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeStorage.class)
public abstract class SubmitNodeStorageMixin implements LateNameTags.Storage, ClientShapes.Storage {
	@Unique
	private final TranslucentFeatureRenderPhase skaffy$lateNameTags = new TranslucentFeatureRenderPhase();

	@Unique
	private final SimpleFeatureRenderPhase skaffy$lateShapes = new SimpleFeatureRenderPhase();

	@Override
	public TranslucentFeatureRenderPhase skaffy$lateNameTags() {
		return skaffy$lateNameTags;
	}

	@Override
	public SimpleFeatureRenderPhase skaffy$lateShapes() {
		return skaffy$lateShapes;
	}

	@Inject(method = "drainPhases", at = @At("TAIL"))
	private void skaffy$drainLateNameTags(Consumer<FeatureRenderPhase<?>> consumer, CallbackInfo ci) {
		if (!skaffy$lateNameTags.isEmpty()) {
			consumer.accept(skaffy$lateNameTags);
		}

		if (!skaffy$lateShapes.isEmpty()) {
			consumer.accept(skaffy$lateShapes);
		}
	}
}
