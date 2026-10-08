#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>
#include <skaffy:sky_common.glsl>

layout(location = 0) in vec3 Position;

layout(location = 0) out vec4 skFog;
layout(location = 1) out vec3 skDirection;
layout(location = 2) out vec3 skPosition;

void main() {
    SkVertex v = skSkyVertex(Position, ColorModulator, vec2(0.0));
    sk_vertexHook(v);
    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);
    skFog = vec4(fog_spherical_distance(v.sk_position), fog_cylindrical_distance(v.sk_position), 0.0, 0.0);
    skDirection = skSkyRelative(v.sk_position);
    skPosition = v.sk_position;
}
