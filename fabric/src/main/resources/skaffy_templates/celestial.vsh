#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>
#include <skaffy:sky_common.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 skUv;
layout(location = 1) out vec3 skDirection;
layout(location = 2) out vec3 skPosition;
layout(location = 3) flat out int skPart;

void main() {
    SkVertex v = skSkyVertex(Position, ColorModulator, UV0);
    skPart = skSkyPart(skSkyDirection(vec3(0.0, Position.y, 0.0)));
    sk_vertexHook(v);
    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);
    skUv = v.sk_uv;
    skDirection = skSkyRelative(v.sk_position);
    skPosition = v.sk_position;
}
