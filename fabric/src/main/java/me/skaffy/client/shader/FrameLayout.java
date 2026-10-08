package me.skaffy.client.shader;

import java.util.List;
import java.util.Map;

import com.mojang.renderpearl.api.GpuFormat;

public record FrameLayout(GpuFormat main, List<Slot> outputs) {
	public static final FrameLayout VANILLA = new FrameLayout(GpuFormat.RGBA8_UNORM, List.of());
	public static final int MAX_OUTPUTS = 7;

	public record Slot(String className, String sampler, GpuFormat format) {
	}

	public boolean hdr() {
		return main != GpuFormat.RGBA8_UNORM;
	}

	public boolean hasOutputs() {
		return !outputs.isEmpty();
	}

	public List<GpuFormat> formats() {
		List<GpuFormat> formats = new java.util.ArrayList<>();
		formats.add(main);
		outputs.forEach(slot -> formats.add(slot.format()));
		return formats;
	}

	public Map<String, Integer> locations(String className) {
		Map<String, Integer> locations = new java.util.HashMap<>();

		for (int i = 0; i < outputs.size(); i++) {
			if (outputs.get(i).className().equals(className)) {
				locations.put(outputs.get(i).sampler(), i + 1);
			}
		}

		return locations;
	}
}
