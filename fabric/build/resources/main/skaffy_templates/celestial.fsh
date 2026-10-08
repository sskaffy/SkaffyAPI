#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec2 skUv;
layout(location = 1) in vec3 skDirection;
layout(location = 2) in vec3 skPosition;
layout(location = 3) flat in int skPart;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, skUv);
    if (tex.a == 0.0) {
        discard;
    }
    vec3 direction = normalize(skDirection);

    SkFragment f;
    f.sk_albedo = tex;
    f.sk_color = ColorModulator;
    f.sk_ao = 1.0;
    f.sk_tint = (ColorModulator).rgb;
    f.sk_texture = tex;
    f.sk_lightmap = vec4(1.0);
    f.sk_blockLight = 0.0;
    f.sk_skyLight = 1.0;
    f.sk_normal = -direction;
    f.sk_position = skPosition;
    f.sk_worldPos = sk_cameraPosition + direction * sk_far;
    f.sk_uv = skUv;
    f.sk_material = 0;
    f.sk_vanilla = tex * ColorModulator;
    f.sk_isHand = false;
    f.sk_direction = direction;
    f.sk_part = skPart;
    f.sk_mappedNormal = f.sk_normal;
    f.sk_roughness = 1.0;
    f.sk_metallic = 0.0;
    f.sk_specular = vec3(0.04);
    f.sk_emission = vec3(0.0);
    f.sk_hasMaterial = false;
    f.sk_unlit = false;
    fragColor = sk_fragmentHook(f);
}
