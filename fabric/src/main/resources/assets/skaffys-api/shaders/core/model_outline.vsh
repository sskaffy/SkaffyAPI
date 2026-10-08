#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <skaffys-api:model.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out float alpha;

void main() {
    gl_Position = ProjMat * ModelViewMat * (SkModelMatrix * vec4(Position, 1.0));
    texCoord0 = UV0 * SkUvTransform.xy + SkUvTransform.zw;
    alpha = Color.a;
}
