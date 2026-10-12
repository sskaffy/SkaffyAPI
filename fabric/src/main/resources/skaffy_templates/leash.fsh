#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec4 skFog;
layout(location = 1) flat in vec4 skColor;
layout(location = 2) flat in vec4 skLightmap;
layout(location = 3) in vec3 skPosition;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 albedo = skColor * ColorModulator;
    vec4 color = albedo * skLightmap;
    SkFragment f;
    f.sk_albedo = albedo;
    f.sk_color = skColor;
    f.sk_ao = 1.0;
    f.sk_tint = (skColor).rgb;
    f.sk_texture = vec4(1.0);
    f.sk_lightmap = skLightmap;
    f.sk_blockLight = skFog.z;
    f.sk_skyLight = skFog.w;
    f.sk_normal = skSurfaceNormal(skPosition);
    f.sk_position = skPosition;
    f.sk_worldPos = skPosition + sk_cameraPosition;
    f.sk_uv = vec2(0.0);
    f.sk_material = 0;
    f.sk_vanilla = color;
    f.sk_isHand = false;
    f.sk_direction = normalize(skPosition + vec3(0.0, 0.0, 1e-6));
    f.sk_part = 0;
    f.sk_mappedNormal = f.sk_normal;
    f.sk_roughness = 1.0;
    f.sk_metallic = 0.0;
    f.sk_specular = vec3(0.04);
    f.sk_emission = vec3(0.0);
    f.sk_hasMaterial = false;
    f.sk_unlit = false;
    f.sk_entity = 0;
    f.sk_item = 0;
    f.sk_emissive = false;
    vec4 result = sk_fragmentHook(f);

    #if SK_FOG
    fragColor = apply_fog(result, skFog.x, skFog.y, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    #else
    fragColor = result;
    #endif
}
