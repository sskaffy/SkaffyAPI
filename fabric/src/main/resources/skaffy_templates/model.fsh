#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <skaffys-api:model.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform sampler2D Sampler5;
#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec4 skFog;
layout(location = 1) in vec4 skColorBack;
layout(location = 2) in vec4 skColorFront;
layout(location = 3) in vec4 skLightmap;
layout(location = 4) in vec4 skOverlay;
layout(location = 5) in vec4 skUv;
layout(location = 6) in vec3 skPosition;
layout(location = 7) in vec3 skNormal;

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

vec3 skModelMappedNormal(vec3 normal, vec2 uv, vec2 encoded) {
    vec3 dp1 = dFdx(skPosition);
    vec3 dp2 = dFdy(skPosition);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);
    vec3 dp2perp = cross(dp2, normal);
    vec3 dp1perp = cross(normal, dp1);
    vec3 tangent = dp2perp * duv1.x + dp1perp * duv2.x;
    vec3 bitangent = dp2perp * duv1.y + dp1perp * duv2.y;
    float scale = inversesqrt(max(dot(tangent, tangent), dot(bitangent, bitangent)) + 1e-20);
    vec3 mapped;
    mapped.xy = encoded * 2.0 - 1.0;
    mapped.z = sqrt(max(0.0, 1.0 - dot(mapped.xy, mapped.xy)));
    mapped.xy *= SkParams.w;

    vec3 result = normalize(tangent * scale * mapped.x - bitangent * scale * mapped.y + normal * mapped.z);
    return any(isnan(result)) ? normal : result;
}

void main() {
    vec4 tex = texture(Sampler0, skUv.xy);
    vec4 color = tex;
    vec4 faceVertexColor = gl_FrontFacing ? skColorFront : skColorBack;

    if (SkParams.x < 0.0) {
        color.a = 1.0;
        faceVertexColor.a = 1.0;
    } else if (color.a * faceVertexColor.a < SkParams.x) {
        discard;
    }

    #ifdef OIT_ADDITIVE
    color.a = min(0.99, color.a);
    #endif

    color *= faceVertexColor * ColorModulator;
    vec3 emission = SkEmissive.w > 0.5 ? texture(Sampler5, skUv.xy).rgb * SkEmissive.rgb : SkEmissive.rgb;

    SkFragment f;
    f.sk_albedo = color;
    f.sk_color = faceVertexColor;
    f.sk_ao = 1.0;
    f.sk_tint = faceVertexColor.rgb;
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
    vec4 vanilla = color;
    #ifndef OIT_ALPHA_ONLY
    vanilla.rgb = mix(skOverlay.rgb, vanilla.rgb, skOverlay.a) * skLightmap.rgb + emission;
    #endif
    f.sk_vanilla = vanilla;
    f.sk_isHand = false;
    f.sk_direction = normalize(skPosition);
    f.sk_part = 0;
    bool hasMap = SkMaterial.w > 0.5;
    vec4 map = hasMap ? texture(Sampler3, skUv.xy) : vec4(0.5, 0.5, SkMaterial.z, SkMaterial.y);
    f.sk_mappedNormal = hasMap ? skModelMappedNormal(f.sk_normal, skUv.xy, map.xy) : f.sk_normal;
    f.sk_roughness = map.b;

    if (SkMaterial.x > 0.5) {
        f.sk_metallic = 0.0;
        f.sk_specular = map.a * SkSpecular.rgb;
    } else {
        f.sk_metallic = map.a;
        f.sk_specular = mix(vec3(0.04), color.rgb, map.a);
    }

    f.sk_emission = emission;
    f.sk_hasMaterial = hasMap;
    f.sk_unlit = SkParams.z > 0.5;

    vec4 result = sk_fragmentHook(f);

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, result.a);
    #elif defined(SK_SHADOW_PASS)
    fragColor = result;
    #else
    fragColor = skFinish(result);
    #endif
}
