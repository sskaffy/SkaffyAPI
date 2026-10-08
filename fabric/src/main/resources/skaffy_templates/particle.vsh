#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in ivec2 UV2;

uniform sampler2D Sampler2;

layout(location = 0) out vec4 skFog;
layout(location = 1) out vec4 skColor;
layout(location = 2) out vec4 skLightmap;
layout(location = 3) out vec2 skUv;
layout(location = 4) out vec3 skPosition;

void main() {
    ivec2 light = UV2 & ivec2(0xFF);

    SkVertex v;
    v.sk_position = Position;
    v.sk_color = Color;
    v.sk_uv = UV0;
    v.sk_worldPos = Position + sk_cameraPosition;
    v.sk_normal = -normalize(Position + vec3(0.0, 0.0, 1e-6));
    v.sk_material = 0;
    v.sk_blockLight = float(light.x) / 240.0;
    v.sk_skyLight = float(light.y) / 240.0;
    v.sk_top = false;
    v.sk_isHand = false;
    sk_vertexHook(v);

    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);
    skFog = vec4(fog_spherical_distance(v.sk_position), fog_cylindrical_distance(v.sk_position), v.sk_blockLight, v.sk_skyLight);
    skColor = v.sk_color;
    skLightmap = sample_lightmap(Sampler2, light);
    skUv = v.sk_uv;
    skPosition = v.sk_position;
}
