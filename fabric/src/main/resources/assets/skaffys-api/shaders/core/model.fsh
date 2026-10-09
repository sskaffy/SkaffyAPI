#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <skaffys-api:model.glsl>

uniform sampler2D Sampler0;
#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler5;
#endif

layout(location = 0) in vec2 fogDistance;
layout(location = 1) in vec4 colorBack;
layout(location = 2) in vec4 colorFront;
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
layout(location = 5) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    vec4 face = gl_FrontFacing ? colorFront : colorBack;

    if (SkParams.x < 0.0) {
        color.a = 1.0;
        face.a = 1.0;
    } else if (color.a * face.a < SkParams.x) {
        discard;
    }

    #ifdef OIT_ADDITIVE
    color.a = min(0.99, color.a);
    #endif

    color *= face * ColorModulator;

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color.rgb *= lightMapColor.rgb;
    vec3 emissive = SkEmissive.w > 0.5 ? texture(Sampler5, texCoord0).rgb * SkEmissive.rgb : SkEmissive.rgb;
    color.rgb += emissive;

        #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
        #else
    vec4 fogColor = FogColor;
        #endif

    fragColor = apply_fog(color, fogDistance.x, fogDistance.y, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif
}
