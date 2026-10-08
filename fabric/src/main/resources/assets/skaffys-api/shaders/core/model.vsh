#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <skaffys-api:model.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;

#if !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler2;
#endif

layout(location = 0) out vec2 fogDistance;
layout(location = 1) out vec4 colorBack;
layout(location = 2) out vec4 colorFront;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
layout(location = 5) out vec2 texCoord0;

void main() {
    vec3 position = (SkModelMatrix * vec4(Position, 1.0)).xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    texCoord0 = UV0 * SkUvTransform.xy + SkUvTransform.zw;
    vec4 color = Color * SkTint;

    fogDistance = vec2(fog_spherical_distance(position), fog_cylindrical_distance(position));

    if (SkParams.z > 0.5) {
        colorBack = color;
        colorFront = color;
    } else {
        vec3 normal = normalize(mat3(SkNormalMatrix) * Normal);
        vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, normal);
        colorFront = minecraft_mix_light_separate(light, color);
        colorBack = minecraft_mix_light_separate(-light, color);
    }

    #if !defined(OIT_ALPHA_ONLY)
    lightMapColor = SkParams.z > 0.5 ? vec4(1.0) : sample_lightmap(Sampler2, SkLight.xy);
    overlayColor = SkOverlay;
    #else
    lightMapColor = vec4(1.0);
    overlayColor = vec4(1.0);
    #endif
}
