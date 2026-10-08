#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <skaffys-api:model.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;

#if !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler2;
#endif

layout(location = 0) out vec4 skFog;
layout(location = 1) out vec4 skColorBack;
layout(location = 2) out vec4 skColorFront;
layout(location = 3) out vec4 skLightmap;
layout(location = 4) out vec4 skOverlay;
layout(location = 5) out vec4 skUv;
layout(location = 6) out vec3 skPosition;
layout(location = 7) out vec3 skNormal;

void main() {
    vec3 position = (SkModelMatrix * vec4(Position, 1.0)).xyz;
    vec3 normal = normalize(mat3(SkNormalMatrix) * Normal);
    bool unlit = SkParams.z > 0.5;

    SkVertex v;
    v.sk_position = position;
    v.sk_color = Color * SkTint;
    v.sk_uv = UV0 * SkUvTransform.xy + SkUvTransform.zw;
    v.sk_worldPos = position + sk_cameraPosition;
    v.sk_normal = normal;
    v.sk_material = 0;
    v.sk_blockLight = unlit ? 1.0 : float(SkLight.x) / 240.0;
    v.sk_skyLight = unlit ? 1.0 : float(SkLight.y) / 240.0;
    v.sk_top = false;
    v.sk_isHand = false;
    sk_vertexHook(v);

#ifdef SK_SHADOW_PASS
    gl_Position = skShadowClip(v.sk_position);
#else
    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);
#endif
    skFog = vec4(fog_spherical_distance(v.sk_position), fog_cylindrical_distance(v.sk_position), v.sk_blockLight, v.sk_skyLight);

    if (unlit) {
        skColorBack = v.sk_color;
        skColorFront = v.sk_color;
    } else {
        vec2 cardinal = minecraft_compute_light(Light0_Direction, Light1_Direction, normal);
        skColorBack = minecraft_mix_light_separate(-cardinal, v.sk_color);
        skColorFront = minecraft_mix_light_separate(cardinal, v.sk_color);
    }

#if !defined(OIT_ALPHA_ONLY)
    skLightmap = unlit ? vec4(1.0) : sample_lightmap(Sampler2, SkLight.xy);
    skOverlay = SkOverlay;
#else
    skLightmap = vec4(1.0);
    skOverlay = vec4(1.0);
#endif

    skUv = vec4(v.sk_uv, 0.0, 0.0);
    skPosition = v.sk_position;
    skNormal = normal;
}
