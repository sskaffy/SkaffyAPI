#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE
#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec4 skFog;
layout(location = 1) in vec2 skUv;
layout(location = 2) in vec3 skPosition;
layout(location = 3) in vec4 skColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, skUv);
    vec4 albedo = tex * ColorModulator * skColor;
    if (albedo.a < 0.1) {
        discard;
    }
    vec4 color = vec4(albedo.rgb * GlintAlpha, albedo.a);
    SkFragment f;
    f.sk_albedo = albedo;
    f.sk_color = skColor;
    f.sk_ao = 1.0;
    f.sk_tint = (skColor).rgb;
    f.sk_texture = tex;
    f.sk_lightmap = vec4(1.0);
    f.sk_blockLight = 1.0;
    f.sk_skyLight = 1.0;
    f.sk_normal = skSurfaceNormal(skPosition);
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
    f.sk_entity = 0;
    f.sk_item = 0;
    f.sk_emissive = false;
    vec4 result = sk_fragmentHook(f);

    #if SK_FOG
    float fade = 1.0f - total_fog_value(skFog.x, skFog.y, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    result.rgb *= fade;
    #endif
    fragColor = result;
}
