#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) out vec4 skFog;
layout(location = 1) out vec4 skColor;
layout(location = 2) out vec4 skLightmap;
layout(location = 3) out vec4 skUvLight;
layout(location = 4) out vec3 skPosition;
layout(location = 5) out vec3 skNormal;
layout(location = 6) out vec4 skTintAo;

void main() {
    vec3 pos = Position + vec3(ChunkPosition - CameraBlockPos) + CameraOffset;
    ivec2 light = UV2 & ivec2(0xFF);
    int material = ((UV2.x >> 8) & 0xFF) | (((UV2.y >> 8) & 0x0F) << 8);
    bool top = ((UV2.y >> 12) & 1) == 1;
    int face = (UV2.y >> 13) & 7;
    vec4 color = Color;
    vec4 tintAo = vec4(Color.rgb, 1.0);
    int packedAo = int(Color.a * 255.0 + 0.5);
    if (packedAo < 192) {
        tintAo.a = float(packedAo & 31) / 31.0;
        color = vec4(Color.rgb * skShade(packedAo >> 5) * tintAo.a, 1.0);
    }

    SkVertex v;
    v.sk_position = pos;
    v.sk_color = color;
    v.sk_uv = UV0;
    v.sk_worldPos = pos + sk_cameraPosition;
    v.sk_normal = skFaceNormal(face);
    v.sk_material = material;
    v.sk_blockLight = float(light.x) / 240.0;
    v.sk_skyLight = float(light.y) / 240.0;
    v.sk_top = top;
    v.sk_isHand = false;
    sk_vertexHook(v);

    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);

    const float chunkFullyVisibleRange = 16.0;
    float dist = length(v.sk_position);
    float visibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
    skFog = vec4(fog_spherical_distance(v.sk_position), fog_cylindrical_distance(v.sk_position), visibility, float(material));
    skColor = v.sk_color;
    #ifndef OIT_ALPHA_ONLY
    skLightmap = sample_lightmap(Sampler2, light);
    #else
    skLightmap = vec4(1.0);
    #endif
    skUvLight = vec4(v.sk_uv, v.sk_blockLight, v.sk_skyLight);
    skPosition = v.sk_position;
    skNormal = v.sk_normal;
    skTintAo = tintAo;
}
