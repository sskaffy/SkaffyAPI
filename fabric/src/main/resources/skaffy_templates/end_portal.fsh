#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE
#include <minecraft:fog.glsl>
#include <minecraft:matrix.glsl>
#include <minecraft:globals.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec4 skFog;
layout(location = 1) in vec3 skPosition;
layout(location = 2) in vec4 skColor;
layout(location = 3) in vec4 skTexProj;

const vec3[] COLORS = vec3[](
    vec3(0.022087, 0.098399, 0.110818),
    vec3(0.011892, 0.095924, 0.089485),
    vec3(0.027636, 0.101689, 0.100326),
    vec3(0.046564, 0.109883, 0.114838),
    vec3(0.064901, 0.117696, 0.097189),
    vec3(0.063761, 0.086895, 0.123646),
    vec3(0.084817, 0.111994, 0.166380),
    vec3(0.097489, 0.154120, 0.091064),
    vec3(0.106152, 0.131144, 0.195191),
    vec3(0.097721, 0.110188, 0.187229),
    vec3(0.133516, 0.138278, 0.148582),
    vec3(0.070006, 0.243332, 0.235792),
    vec3(0.196766, 0.142899, 0.214696),
    vec3(0.047281, 0.315338, 0.321970),
    vec3(0.204675, 0.390010, 0.302066),
    vec3(0.080955, 0.314821, 0.661491)
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 end_portal_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );
    mat2 rotate = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));
    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);
    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 texProj0 = skTexProj;
    vec3 portal = textureProj(Sampler0, texProj0).rgb * COLORS[0];
    for (int i = 0; i < PORTAL_LAYERS; i++) {
        portal += textureProj(Sampler1, texProj0 * end_portal_layer(float(i + 1))).rgb * COLORS[i];
    }
    vec4 color = vec4(portal, 1.0) * skColor;
    SkFragment f;
    f.sk_albedo = color;
    f.sk_color = skColor;
    f.sk_ao = 1.0;
    f.sk_tint = (skColor).rgb;
    f.sk_texture = vec4(portal, 1.0);
    f.sk_lightmap = vec4(1.0);
    f.sk_blockLight = 1.0;
    f.sk_skyLight = 1.0;
    f.sk_normal = skSurfaceNormal(skPosition);
    f.sk_position = skPosition;
    f.sk_worldPos = skPosition + sk_cameraPosition;
    f.sk_uv = texProj0.xy / texProj0.w;
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
