#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <skaffys-api:model.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in float alpha;

layout(location = 0) out vec4 fragColor;

void main() {

    if (SkParams.x >= 0.0 && texture(Sampler0, texCoord0).a * alpha < max(SkParams.x, 0.004)) {
        discard;
    }

    fragColor = vec4(ColorModulator.rgb * SkTint.rgb, ColorModulator.a);
}
