package me.skaffy.client.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

final class EffectShaderSource implements ShaderSource {
	private static final Map<String, String> TEMPLATES = new ConcurrentHashMap<>();

	private final Map<Identifier, CachedIncludeSource> includes;
	private final boolean ownsIncludes;
	private final Map<Identifier, String> vertex = new HashMap<>();
	private final Map<Identifier, String> fragment = new HashMap<>();
	private final Map<Identifier, CachedIncludeSource> own = new HashMap<>();

	EffectShaderSource(Map<Identifier, CachedIncludeSource> includes, boolean ownsIncludes) {
		this.includes = includes;
		this.ownsIncludes = ownsIncludes;
	}

	void shader(Identifier id, String vertexSource, String fragmentSource) {
		vertex.put(id, vertexSource);
		fragment.put(id, fragmentSource);
	}

	void include(Identifier id, String source) {
		CachedIncludeSource previous = own.put(id, CachedIncludeSource.create(id, source));

		if (previous != null) {
			previous.close();
		}
	}

	static String template(String name) {
		return TEMPLATES.computeIfAbsent(name, key -> {
			try (InputStream stream = EffectShaderSource.class.getResourceAsStream("/skaffy_templates/" + key)) {
				if (stream == null) {
					throw new IllegalStateException("Missing shader template " + key);
				}

				return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new IllegalStateException("Can't read shader template " + key, e);
			}
		});
	}

	@Override
	public @Nullable String getShader(Identifier id, ShaderType type) {
		return type == ShaderType.VERTEX ? vertex.get(id) : fragment.get(id);
	}

	@Override
	public @Nullable CachedIncludeSource getInclude(Identifier id) {
		CachedIncludeSource mine = own.get(id);
		return mine != null ? mine : includes.get(id);
	}

	@Override
	public void close() {
		own.values().forEach(CachedIncludeSource::close);
		own.clear();

		if (ownsIncludes) {
			includes.values().forEach(CachedIncludeSource::close);
		}
	}
}
