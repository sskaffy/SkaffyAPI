package me.skaffy.client.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import me.skaffy.client.SkaffySAPIClient;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

final class BlockSelectors {
	private BlockSelectors() {
	}

	static boolean isTag(String selector) {
		return selector.startsWith("#");
	}

	static List<BlockState> resolve(String selector, String what) {
		int open = selector.indexOf('[');
		String name = open < 0 ? selector : selector.substring(0, open);
		Map<String, String> wanted = new LinkedHashMap<>();

		if (open >= 0) {
			for (String pair : selector.substring(open + 1, selector.length() - 1).split(",")) {
				int equals = pair.indexOf('=');
				wanted.put(pair.substring(0, equals), pair.substring(equals + 1));
			}
		}

		List<Block> blocks = new ArrayList<>();
		boolean tag = isTag(name);

		if (tag) {
			Identifier id = Identifier.tryParse(name.substring(1));

			if (id != null) {
				for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, id))) {
					blocks.add(holder.value());
				}
			}
		} else {
			Identifier id = Identifier.tryParse(name);

			if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
				SkaffySAPIClient.LOGGER.warn("Shader {}: no block {}", what, name);
				return List.of();
			}

			blocks.add(BuiltInRegistries.BLOCK.getValue(id));
		}

		List<BlockState> states = new ArrayList<>();

		for (Block block : blocks) {
			List<Map.Entry<Property<?>, Object>> conditions = conditions(block, wanted, tag ? null : selector, what);

			if (conditions == null) {
				continue;
			}

			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				if (conditions.stream().allMatch(condition -> state.getValue(condition.getKey()).equals(condition.getValue()))) {
					states.add(state);
				}
			}
		}

		return states;
	}

	private static List<Map.Entry<Property<?>, Object>> conditions(Block block, Map<String, String> wanted, String logAs, String what) {
		List<Map.Entry<Property<?>, Object>> conditions = new ArrayList<>();

		for (Map.Entry<String, String> entry : wanted.entrySet()) {
			Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
			Optional<?> value = property == null ? Optional.empty() : property.getValue(entry.getValue());

			if (value.isEmpty()) {
				if (logAs != null) {
					SkaffySAPIClient.LOGGER.warn("Shader {}: {} has no state {}={}", what, logAs, entry.getKey(), entry.getValue());
				}

				return null;
			}

			conditions.add(Map.entry(property, value.get()));
		}

		return conditions;
	}

	static int scaledLevel(BlockState state, String property, int level) {
		if (!(state.getBlock().getStateDefinition().getProperty(property) instanceof IntegerProperty number)) {
			return level;
		}

		int value = state.getValue(number);
		int max = Collections.max(number.getPossibleValues());
		return value <= 0 || max <= 0 ? 0 : (int) Math.ceil(level * (double) value / max);
	}
}
