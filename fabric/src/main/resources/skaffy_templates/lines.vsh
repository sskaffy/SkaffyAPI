#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;
layout(location = 3) in float LineWidth;

layout(location = 0) out vec4 skFog;
layout(location = 1) out vec4 skColor;
layout(location = 2) out vec3 skPosition;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);

void main() {
    SkVertex v;
    v.sk_position = Position;
    v.sk_color = Color;
    v.sk_uv = vec2(0.0);
    v.sk_worldPos = Position + sk_cameraPosition;
    v.sk_normal = Normal;
    v.sk_material = 0;
    v.sk_blockLight = 1.0;
    v.sk_skyLight = 1.0;
    v.sk_top = false;
    v.sk_isHand = false;
    v.sk_entity = 0;
    v.sk_item = 0;
    sk_vertexHook(v);

    vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(v.sk_position, 1.0);
    vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(v.sk_position + Normal, 1.0);
    vec3 ndc1 = linePosStart.xyz / linePosStart.w;
    vec3 ndc2 = linePosEnd.xyz / linePosEnd.w;
    vec2 lineScreenDirection = normalize((ndc2.xy - ndc1.xy) * ScreenSize);
    vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x) * LineWidth / ScreenSize;
    if (lineOffset.x < 0.0) {
        lineOffset *= -1.0;
    }
    if (gl_VertexIndex % 2 == 0) {
        gl_Position = vec4((ndc1 + vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
    } else {
        gl_Position = vec4((ndc1 - vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
    }

    skFog = vec4(fog_spherical_distance(v.sk_position), fog_cylindrical_distance(v.sk_position), 1.0, 1.0);
    skColor = v.sk_color;
    skPosition = v.sk_position;
}
