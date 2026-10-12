package me.skaffy.client.shader;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import org.jspecify.annotations.Nullable;

final class IdMaterials<T> {
	private final Map<Integer, List<String>> selectors;
	private final ResourceKey<? extends Registry<T>> registry;
	private final Map<Object, Integer> cache = new IdentityHashMap<>();

	IdMaterials(Map<Integer, List<String>> selectors, ResourceKey<? extends Registry<T>> registry) {
		this.selectors = selectors;
		this.registry = registry;
	}

	static <T> @Nullable IdMaterials<T> of(@Nullable Map<Integer, List<String>> selectors, ResourceKey<? extends Registry<T>> registry) {
		return selectors == null || selectors.isEmpty() ? null : new IdMaterials<>(selectors, registry);
	}

	synchronized int of(Holder<T> holder) {
		Object key = holder.value();
		Integer known = cache.get(key);

		if (known != null) {
			return known;
		}

		int found = 0;

		for (boolean tags : new boolean[] {false, true}) {
			for (Map.Entry<Integer, List<String>> entry : selectors.entrySet()) {
				for (String selector : entry.getValue()) {
					if (selector.startsWith("#") == tags && matches(holder, selector)) {
						found = entry.getKey();
						break;
					}
				}

				if (found != 0) {
					break;
				}
			}

			if (found != 0) {
				break;
			}
		}

		cache.put(key, found);
		return found;
	}

	private boolean matches(Holder<T> holder, String selector) {
		Identifier id = Identifier.tryParse(selector.startsWith("#") ? selector.substring(1) : selector);

		if (id == null) {
			return false;
		}

		return selector.startsWith("#") ? holder.is(TagKey.create(registry, id)) : holder.is(id);
	}

	static int dimension(@Nullable Map<Integer, List<String>> selectors, Identifier dimension) {
		if (selectors != null) {
			for (Map.Entry<Integer, List<String>> entry : selectors.entrySet()) {
				if (entry.getValue().contains(dimension.toString())) {
					return entry.getKey();
				}
			}
		}

		return 0;
	}
}
