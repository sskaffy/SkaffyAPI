#version 330
#extension GL_ARB_separate_shader_objects : require

#define SK_FRAGMENT_STAGE

uniform sampler2D Sampler0;

#include <skaffy:hooks_fragment.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec4 skColor;

layout(location = 1) in vec4 skUvLight;
layout(location = 2) in vec3 skPosition;

layout(location = 3) in vec3 skNormal;
layout(location = 4) flat in int skMaterial;
layout(location = 5) in vec4 skTintAo;


layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv = skUvLight.xy;
    vec4 tex = texture(Sampler0, uv);
    vec4 albedo = tex * skColor;


    SkFragment f;
    f.sk_albedo = albedo;
    f.sk_color = skColor;
    f.sk_ao = skTintAo.a;
    f.sk_tint = skTintAo.rgb;
    f.sk_texture = tex;
    f.sk_lightmap = vec4(1.0);
    f.sk_blockLight = skUvLight.z;
    f.sk_skyLight = skUvLight.w;
    f.sk_normal = skNormal;
    f.sk_position = skPosition;
    f.sk_worldPos = skPosition + sk_cameraPosition;
    f.sk_uv = uv;
    f.sk_material = skMaterial;
    f.sk_vanilla = albedo;
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
    #ifdef ALPHA_CUTOUT
    if (albedo.a < ALPHA_CUTOUT && result.a >= 0.999) {
        discard;
    }
    #endif
    #if defined(SK_SHADOW_SEE_THROUGH)
    if (result.a >= 0.999 || result.a <= 0.0) {
        discard;
    }
    #elif defined(SK_SHADOW_TRANSLUCENT)
    if (result.a <= 0.0) {
        discard;
    }
    #elif defined(ALPHA_CUTOUT)
    if (result.a < 0.999) {
        discard;
    }
    #endif
    fragColor = result;
}
