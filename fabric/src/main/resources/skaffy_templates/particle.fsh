#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
uniform sampler2D Sampler0;
#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec4 skFog;
layout(location = 1) in vec4 skColor;
layout(location = 2) in vec4 skLightmap;

layout(location = 3) in vec2 skUv;
layout(location = 4) in vec3 skPosition;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 skFinish(vec4 color) {

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    #if SK_FOG
    return apply_fog(color, skFog.x, skFog.y, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #else
    return color;
    #endif

}

void main() {

    vec4 tex = texture(Sampler0, skUv);
    vec4 albedo = tex * skColor * ColorModulator;
    vec4 color = albedo * skLightmap;
    if (color.a < 0.1) {
        discard;
    }

    SkFragment f;
    f.sk_albedo = albedo;
    f.sk_color = skColor;
    f.sk_ao = 1.0;
    f.sk_tint = (skColor).rgb;
    f.sk_texture = tex;
    f.sk_lightmap = skLightmap;
    f.sk_blockLight = skFog.z;
    f.sk_skyLight = skFog.w;
    f.sk_normal = -normalize(skPosition + vec3(0.0, 0.0, 1e-6));
    f.sk_position = skPosition;
    f.sk_worldPos = skPosition + sk_cameraPosition;
    f.sk_uv = skUv;
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
    vec4 result = sk_fragmentHook(f);

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, result.a);
    #else
    fragColor = skFinish(result);
    #endif

}
