package me.skaffy.client.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import me.skaffy.client.SkaffySAPIClient;
import me.skaffy.client.mixin.HolderReferenceInvoker;
import me.skaffy.client.mixin.IdMapperAccessor;
import me.skaffy.client.mixin.MappedRegistryAccessor;
import me.skaffy.protocol.blocks.BlockDefinition;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

final class BlockRegistrar {
	private static final List<CustomBlock> BLOCKS = new ArrayList<>();
	private static int blockStart = -1;
	private static int stateStart = -1;
	private static int generation;

	private BlockRegistrar() {
	}

	static Identifier blockId(int number) {
		return Identifier.fromNamespaceAndPath("skaffy", generation == 0 ? "custom_" + number : "custom_" + generation + "_" + number);
	}

	static List<CustomBlock> register(List<CustomBlockSpec> specs) {
		unregister();

		MappedRegistry<Block> registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
		MappedRegistryAccessor<Block> access = registryAccess();
		blockStart = access.skaffy$byId().size();
		stateStart = stateAccess().skaffy$idToT().size();
		access.skaffy$setFrozen(false);
		access.skaffy$setUnregisteredIntrusiveHolders(new IdentityHashMap<>());

		try {
			for (CustomBlockSpec spec : specs) {
				ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, blockId(spec.number()));
				BlockBehaviour.Properties properties = properties(spec.definition()).setId(key);
				CustomBlock block = spec.definition().rotatable() ? new RotatableCustomBlock(properties, spec) : new CustomBlock(properties, spec);
				Holder.Reference<Block> holder = registry.register(key, block, RegistrationInfo.BUILT_IN);
				HolderReferenceInvoker<Block> binder = invoker(holder);
				binder.skaffy$bindValue(block);
				binder.skaffy$bindTags(List.of());
				BLOCKS.add(block);
			}
		} finally {
			access.skaffy$setUnregisteredIntrusiveHolders(null);
			access.skaffy$setFrozen(true);
		}

		for (CustomBlock block : BLOCKS) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				if (Block.BLOCK_STATE_REGISTRY.getId(state) == -1) {
					Block.BLOCK_STATE_REGISTRY.add(state);
				}

				state.initCache();
			}
		}

		return List.copyOf(BLOCKS);
	}

	static void unregister() {
		if (BLOCKS.isEmpty()) {
			return;
		}

		MappedRegistryAccessor<Block> access = registryAccess();
		List<Holder.Reference<Block>> byId = access.skaffy$byId();
		IdMapperAccessor<BlockState> stateAccess = stateAccess();
		List<BlockState> stateIds = stateAccess.skaffy$idToT();

		Set<Block> ourBlocks = Collections.newSetFromMap(new IdentityHashMap<>());
		ourBlocks.addAll(BLOCKS);
		boolean onlyOursAtTheEnd = byId.size() == blockStart + BLOCKS.size()
				&& byId.subList(blockStart, byId.size()).stream().allMatch(holder -> ourBlocks.contains(holder.value()))
				&& stateIds.size() >= stateStart
				&& stateIds.subList(stateStart, stateIds.size()).stream().allMatch(state -> state != null && ourBlocks.contains(state.getBlock()));

		if (!onlyOursAtTheEnd) {
			SkaffySAPIClient.LOGGER.warn("Other blocks were registered after Skaffy's API blocks, so they stay registered until the game restarts");
			generation++;
			BLOCKS.clear();
			return;
		}

		for (CustomBlock block : BLOCKS) {
			Holder.Reference<Block> holder = access.skaffy$byValue().remove(block);
			access.skaffy$toId().removeInt(block);
			access.skaffy$byKey().remove(holder.key());
			access.skaffy$byLocation().remove(holder.key().identifier());
			access.skaffy$registrationInfos().remove(holder.key());
		}

		byId.subList(blockStart, byId.size()).clear();

		for (BlockState state : stateIds.subList(stateStart, stateIds.size())) {
			stateAccess.skaffy$tToId().removeInt(state);
		}

		stateIds.subList(stateStart, stateIds.size()).clear();
		stateAccess.skaffy$setNextId(stateIds.size());
		BLOCKS.clear();
	}

	private static BlockBehaviour.Properties properties(BlockDefinition definition) {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
				.strength(definition.unbreakable() ? -1 : definition.hardness(), 3_600_000)
				.lightLevel(state -> definition.light())
				.sound(SoundType.STONE)
				.noLootTable();

		if (definition.collision() == null) {
			properties.noCollision();
		}

		if (definition.transparency() != BlockDefinition.Transparency.SOLID) {
			properties.noOcclusion();
		}

		return properties;
	}

	@SuppressWarnings("unchecked")
	private static MappedRegistryAccessor<Block> registryAccess() {
		return (MappedRegistryAccessor<Block>) BuiltInRegistries.BLOCK;
	}

	@SuppressWarnings("unchecked")
	private static IdMapperAccessor<BlockState> stateAccess() {
		return (IdMapperAccessor<BlockState>) (Object) Block.BLOCK_STATE_REGISTRY;
	}

	@SuppressWarnings("unchecked")
	private static HolderReferenceInvoker<Block> invoker(Holder.Reference<Block> holder) {
		return (HolderReferenceInvoker<Block>) (Object) holder;
	}
}
