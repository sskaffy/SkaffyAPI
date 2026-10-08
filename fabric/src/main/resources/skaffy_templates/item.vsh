#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5) in vec2 UV3;
#endif
layout(location = 6) in vec3 Normal;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
#endif

layout(location = 0) out vec4 skFog;
layout(location = 1) out vec4 skColor;
layout(location = 3) out vec4 skLightmap;
layout(location = 4) out vec4 skOverlay;
layout(location = 5) out vec4 skUv;
layout(location = 6) out vec3 skPosition;
layout(location = 7) out vec3 skNormal;

void main() {
    ivec2 light = UV2 & ivec2(0xFF);

    SkVertex v;
    v.sk_position = Position;
    v.sk_color = Color;
    v.sk_uv = UV0;
    v.sk_worldPos = Position + sk_cameraPosition;
    v.sk_normal = Normal;
    v.sk_material = 0;
    v.sk_blockLight = float(light.x) / 240.0;
    v.sk_skyLight = float(light.y) / 240.0;
    v.sk_top = false;
    v.sk_isHand = sk_isHand != 0;
    sk_vertexHook(v);

#ifdef SK_SHADOW_PASS
    gl_Position = skShadowClip(v.sk_position);
#else
    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);
#endif
    skFog = vec4(fog_spherical_distance(v.sk_position), fog_cylindrical_distance(v.sk_position), v.sk_blockLight, v.sk_skyLight);
    skColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, v.sk_color);

    #ifndef OIT_ALPHA_ONLY
    skLightmap = sample_lightmap(Sampler2, light);
    skOverlay = texelFetch(Sampler1, UV1, 0);
    #else
    skLightmap = vec4(1.0);
    skOverlay = vec4(1.0);
    #endif

    vec2 glintCoord = vec2(0.0);
    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    glintCoord = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    glintCoord = (TextureMat * vec4(v.sk_uv, 0.0, 1.0)).xy;
    #endif
    #endif
    skUv = vec4(v.sk_uv, glintCoord);
    skPosition = v.sk_position;
    skNormal = Normal;
}
