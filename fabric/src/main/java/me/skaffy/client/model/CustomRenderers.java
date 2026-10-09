package me.skaffy.client.model;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.mixin.AgeableMobRendererAccessor;
import me.skaffy.client.mixin.EntityModelSetAccessor;
import me.skaffy.client.mixin.EntityRendererContextAccessor;
import me.skaffy.client.mixin.EntityRenderersAccessor;
import me.skaffy.client.mixin.LivingEntityRendererAccessor;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CarriedBlockLayer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.DolphinCarryingItemLayer;
import net.minecraft.client.renderer.entity.layers.FoxHeldItemLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.PandaHoldsItemLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import org.jspecify.annotations.Nullable;

final class CustomRenderers {
	private static EntityRendererProvider.@Nullable Context context;
	private static final Map<Key, EntityRenderer<?, ?>> RENDERERS = new HashMap<>();
	private static final Set<EntityRenderer<?, ?>> CUSTOM = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<EntityRenderer<?, ?>, MobModels> MOB_MODELS = new IdentityHashMap<>();
	private static final Map<ClientModel, ModelDataBaker.Atlas> ATLASES = new IdentityHashMap<>();
	private static final Map<ClientModel, ModelData> DATA = new IdentityHashMap<>();

	private CustomRenderers() {
	}

	static void setContext(EntityRendererProvider.Context newContext) {
		context = newContext;
		clear();
	}

	static void clear() {
		RENDERERS.clear();
		CUSTOM.clear();
		MOB_MODELS.clear();
		ATLASES.keySet().forEach(model -> net.minecraft.client.Minecraft.getInstance().getTextureManager().release(ATLASES.get(model).texture()));
		ATLASES.clear();
		DATA.clear();
	}

	static net.minecraft.resources.@Nullable Identifier texture(ClientModel model) {
		if (model.format() == ClientModel.Format.BEDROCK) {
			return model.texture();
		}

		ModelDataBaker.Atlas atlas = ATLASES.get(model);
		return atlas == null ? null : atlas.texture();
	}

	static boolean isCustom(EntityRenderer<?, ?> renderer) {
		return CUSTOM.contains(renderer);
	}

	static @Nullable EntityRenderer<?, ?> get(ClientModel model, EntityType<?> type) {
		if (context == null) {
			return null;
		}

		Key key = new Key(model.number(), type);
		EntityRenderer<?, ?> renderer = RENDERERS.get(key);

		if (renderer == null) {
			EntityRendererProvider<?> provider = provider(type);

			if (provider != null && model.format() != ClientModel.Format.BEDROCK && !DATA.containsKey(model)) {
				ModelData data = model.data();

				if (data == null) {
					return null;
				}

				DATA.put(model, data);
				ATLASES.put(model, ModelDataBaker.atlas(data, model.definition().name()));
			}

			renderer = build(model, type, context);
			RENDERERS.put(key, renderer);
			CUSTOM.add(renderer);
		}

		return renderer;
	}

	private static @Nullable EntityRendererProvider<?> provider(EntityType<?> type) {
		return type == EntityTypes.PLAYER
				? (EntityRendererProvider<AbstractClientPlayer>) ctx -> new AvatarRenderer<>(ctx, false)
				: EntityRenderersAccessor.skaffy$providers().get(type);
	}

	private static EntityRenderer<?, ?> build(ClientModel model, EntityType<?> type, EntityRendererProvider.Context context) {
		EntityRendererProvider<?> provider = provider(type);

		if (provider != null && (model.format() == ClientModel.Format.BEDROCK || DATA.containsKey(model))) {
			try {
				EntityRenderer<?, ?> living = buildMob(model, provider, context);

				if (living != null) {
					return living;
				}
			} catch (RuntimeException | LinkageError e) {
				SkaffySAPIClient.LOGGER.warn("Could not give {} the model {} with vanilla animations, using custom animations only", type, model.definition().name(), e);
			}
		}

		return new GenericModelRenderer(context, model);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static @Nullable EntityRenderer<?, ?> buildMob(ClientModel model, EntityRendererProvider<?> provider, EntityRendererProvider.Context context) {
		EntityRendererContextAccessor access = (EntityRendererContextAccessor) context;
		EntityModelSet vanilla = access.skaffy$modelSet();
		Map<ModelLayerLocation, LayerDefinition> roots = ((EntityModelSetAccessor) vanilla).skaffy$roots();

		Recording recording = new Recording(roots);

		if (!(create(provider, context, recording) instanceof LivingEntityRenderer<?, ?, ?> probe)) {
			return null;
		}

		ModelLayerLocation adult = recording.layerOf(probe.getModel().root());
		ModelLayerLocation baby = probe instanceof AgeableMobRendererAccessor ageable ? recording.layerOf(ageable.skaffy$babyModel().root()) : null;

		if (adult == null) {
			return null;
		}

		EntityRenderer<?, ?> renderer = create(provider, context, new Replacing(roots, model, adult, baby));
		List<RenderLayer<?, ?>> layers = ((LivingEntityRendererAccessor) renderer).skaffy$layers();
		layers.removeIf(layer -> !keepsLayer(layer));

		if (model.format() == ClientModel.Format.BEDROCK && model.emissiveTexture() != null) {
			layers.add(new EmissiveLayer((LivingEntityRenderer) renderer, model.emissiveTexture()));
		}

		EntityModel<?> adultModel = ((LivingEntityRenderer<?, ?, ?>) renderer).getModel();
		EntityModel<?> babyModel = baby != null && renderer instanceof AgeableMobRendererAccessor ageable
				? ageable.skaffy$babyModel()
				: babyCopy(adultModel, model, roots.get(adult));
		MOB_MODELS.put(renderer, new MobModels(adultModel, babyModel));
		return renderer;
	}

	static @Nullable EntityModel<?> forcedModel(EntityRenderer<?, ?> renderer, boolean baby) {
		MobModels models = MOB_MODELS.get(renderer);

		if (models == null) {
			return null;
		}

		return baby && models.baby() != null ? models.baby() : models.adult();
	}

	static boolean shrinksBabies(EntityRenderer<?, ?> renderer) {
		MobModels models = MOB_MODELS.get(renderer);
		return models != null && models.baby() == null;
	}

	private static @Nullable EntityModel<?> babyCopy(EntityModel<?> adult, ClientModel model, @Nullable LayerDefinition vanilla) {
		try {
			ModelPart vanillaRoot = vanilla == null ? null : vanilla.bakeRoot();
			ModelPart root = bakeModel(model, true, vanillaRoot);
			Constructor<? extends EntityModel> constructor = adult.getClass().getDeclaredConstructor(ModelPart.class);
			constructor.setAccessible(true);
			return constructor.newInstance(root);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static EntityRenderer<?, ?> create(EntityRendererProvider<?> provider, EntityRendererProvider.Context context, EntityModelSet models) {
		EntityRendererContextAccessor access = (EntityRendererContextAccessor) context;
		EntityModelSet vanilla = access.skaffy$modelSet();
		access.skaffy$setModelSet(models);

		try {
			return provider.create(context);
		} finally {
			access.skaffy$setModelSet(vanilla);
		}
	}

	private static boolean keepsLayer(RenderLayer<?, ?> layer) {
		return layer instanceof ItemInHandLayer
				|| layer instanceof CrossedArmsItemLayer
				|| layer instanceof FoxHeldItemLayer
				|| layer instanceof DolphinCarryingItemLayer
				|| layer instanceof PandaHoldsItemLayer
				|| layer instanceof CarriedBlockLayer
				|| layer instanceof HumanoidArmorLayer
				|| layer instanceof CustomHeadLayer
				|| layer instanceof WingsLayer;
	}

	private static ModelPart bakeModel(ClientModel model, boolean baby, @Nullable ModelPart vanilla) {
		if (model.format() != ClientModel.Format.BEDROCK) {
			return ModelDataBaker.bake(DATA.get(model), ATLASES.get(model), baby ? 0.5f : 1, vanilla);
		}

		if (baby) {
			return model.babyGeometry() != null ? ModelBaker.bake(model.babyGeometry(), 1, vanilla) : ModelBaker.bake(model.geometry(), 0.5f, vanilla);
		}

		return ModelBaker.bake(model.geometry(), 1, vanilla);
	}

	private record Key(int model, EntityType<?> type) {
	}

	private record MobModels(EntityModel<?> adult, @Nullable EntityModel<?> baby) {
	}

	private static final class Recording extends EntityModelSet {
		private final Map<ModelPart, ModelLayerLocation> baked = new IdentityHashMap<>();

		Recording(Map<ModelLayerLocation, LayerDefinition> roots) {
			super(roots);
		}

		@Override
		public ModelPart bakeLayer(ModelLayerLocation id) {
			ModelPart part = super.bakeLayer(id);
			baked.put(part, id);
			return part;
		}

		@Nullable ModelLayerLocation layerOf(ModelPart root) {
			return baked.get(root);
		}
	}

	private static final class Replacing extends EntityModelSet {
		private final ClientModel model;
		private final ModelLayerLocation adult;
		private final @Nullable ModelLayerLocation baby;

		Replacing(Map<ModelLayerLocation, LayerDefinition> roots, ClientModel model, ModelLayerLocation adult, @Nullable ModelLayerLocation baby) {
			super(roots);
			this.model = model;
			this.adult = adult;
			this.baby = baby;
		}

		@Override
		public ModelPart bakeLayer(ModelLayerLocation id) {
			if (id.equals(adult)) {
				return bakeModel(model, false, super.bakeLayer(id));
			}

			if (id.equals(baby)) {
				return bakeModel(model, true, super.bakeLayer(id));
			}

			return super.bakeLayer(id);
		}
	}
}
