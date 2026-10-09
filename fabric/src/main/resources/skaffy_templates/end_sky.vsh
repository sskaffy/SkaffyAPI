#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>
#include <skaffy:sky_common.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec4 skColor;
layout(location = 1) out vec3 skDirection;
layout(location = 2) out vec3 skPosition;
layout(location = 3) out vec2 skUv;

void main() {
    SkVertex v = skSkyVertex(Position, Color, UV0);
    sk_vertexHook(v);
    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);
    skColor = v.sk_color;
    skDirection = skSkyRelative(v.sk_position);
    skPosition = v.sk_position;
    skUv = v.sk_uv;
}
