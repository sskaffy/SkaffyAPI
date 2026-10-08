#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE
#ifdef GLINT
#include <minecraft:globals.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
uniform sampler2D Sampler0;
#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

layout(location = 0) in vec4 skFog;
layout(location = 1) in vec4 skColor;
layout(location = 3) in vec4 skLightmap;
layout(location = 4) in vec4 skOverlay;
layout(location = 5) in vec4 skUv;
layout(location = 6) in vec3 skPosition;
layout(location = 7) in vec3 skNormal;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 skVanilla(vec4 color) {
    color.rgb = mix(skOverlay.rgb, color.rgb, skOverlay.a);
    color *= skLightmap;

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, skUv.zw);
    color.rgb += glintColor.rgb * glintColor.rgb;
    #endif
    return color;
}

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
    vec4 tex = texture(Sampler0, skUv.xy);
    vec4 color = tex;
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    color *= skColor * ColorModulator;

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    SkFragment f;
    f.sk_albedo = color;
    f.sk_color = skColor;
    f.sk_ao = 1.0;
    f.sk_tint = (skColor).rgb;
    f.sk_texture = tex;
    f.sk_lightmap = skLightmap;
    f.sk_blockLight = skFog.z;
    f.sk_skyLight = skFog.w;
    vec3 normal = normalize(skNormal);
    f.sk_normal = gl_FrontFacing ? normal : -normal;
    f.sk_position = skPosition;
    f.sk_worldPos = skPosition + sk_cameraPosition;
    f.sk_uv = skUv.xy;
    f.sk_material = 0;
    #ifndef OIT_ALPHA_ONLY
    f.sk_vanilla = skVanilla(color);
    #else
    f.sk_vanilla = color;
    #endif
    f.sk_isHand = sk_isHand != 0;
    f.sk_direction = normalize(skPosition);
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
    #elif defined(SK_SHADOW_PASS)
    fragColor = result;
    #else
    fragColor = skFinish(result);
    #endif
}
