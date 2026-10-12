#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec4 skFog;
layout(location = 1) out vec2 skUv;
layout(location = 2) out vec3 skPosition;
layout(location = 3) out vec4 skColor;

void main() {
    SkVertex v;
    v.sk_position = Position;
    v.sk_color = vec4(1.0);
    v.sk_uv = UV0;
    v.sk_worldPos = Position + sk_cameraPosition;
    v.sk_normal = -normalize(Position + vec3(0.0, 0.0, 1e-6));
    v.sk_material = 0;
    v.sk_blockLight = 1.0;
    v.sk_skyLight = 1.0;
    v.sk_top = false;
    v.sk_isHand = false;
    v.sk_entity = 0;
    v.sk_item = 0;
    sk_vertexHook(v);

    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);
    skFog = vec4(fog_spherical_distance(v.sk_position), fog_cylindrical_distance(v.sk_position), 1.0, 1.0);
    skUv = (TextureMat * vec4(v.sk_uv, 0.0, 1.0)).xy;
    skPosition = v.sk_position;
    skColor = v.sk_color;
}
