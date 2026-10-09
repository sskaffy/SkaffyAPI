#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec4 skFog;
layout(location = 1) in vec3 skDirection;
layout(location = 2) in vec3 skPosition;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 direction = normalize(skDirection);
    vec4 vanilla = apply_fog(ColorModulator, skFog.x, skFog.y, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);

    SkFragment f;
    f.sk_albedo = ColorModulator;
    f.sk_color = ColorModulator;
    f.sk_ao = 1.0;
    f.sk_tint = (ColorModulator).rgb;
    f.sk_texture = vec4(1.0);
    f.sk_lightmap = vec4(1.0);
    f.sk_blockLight = 0.0;
    f.sk_skyLight = 1.0;
    f.sk_normal = -direction;
    f.sk_position = skPosition;
    f.sk_worldPos = sk_cameraPosition + direction * sk_far;
    f.sk_uv = vec2(0.0);
    f.sk_material = 0;
    f.sk_vanilla = vanilla;
    f.sk_isHand = false;
    f.sk_direction = direction;
    f.sk_part = 0;
    f.sk_mappedNormal = f.sk_normal;
    f.sk_roughness = 1.0;
    f.sk_metallic = 0.0;
    f.sk_specular = vec3(0.04);
    f.sk_emission = vec3(0.0);
    f.sk_hasMaterial = false;
    f.sk_unlit = false;
    vec4 result = sk_fragmentHook(f);

    #if SK_FOG
    if (sk_hasFragmentHook) {
        result = apply_fog(result, skFog.x, skFog.y, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);
    }
    #endif
    fragColor = result;
}
