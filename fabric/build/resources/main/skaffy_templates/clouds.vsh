#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <skaffy:hooks_vertex.glsl>
#include <skaffy:template.glsl>

const int FLAG_MASK_DIR = 7;
const int FLAG_INSIDE_FACE = 1 << 4;
const int FLAG_USE_TOP_COLOR = 1 << 5;
const int FLAG_EXTRA_Z = 1 << 6;
const int FLAG_EXTRA_X = 1 << 7;

layout(std140) uniform CloudInfo {
    vec4 CloudColor;
    vec3 CloudOffset;
    vec3 CellSize;
};

uniform isamplerBuffer CloudFaces;

layout(location = 0) out vec4 skFog;
layout(location = 1) out vec4 skColor;
layout(location = 2) out vec3 skPosition;
layout(location = 3) out vec3 skNormal;

const int packedX = 0xF03CC3;
const int packedY = 0x6666F0;
const int packedZ = 0xC3F066;

vec3 vertex(int index) {
    vec3 pos = vec3(0);
    pos.x = float((packedX >> index) & 1);
    pos.y = float((packedY >> index) & 1);
    pos.z = float((packedZ >> index) & 1);
    return pos;
}

const vec4[] faceColors = vec4[](
    vec4(0.7, 0.7, 0.7, 1.0),
    vec4(1.0, 1.0, 1.0, 1.0),
    vec4(0.8, 0.8, 0.8, 1.0),
    vec4(0.8, 0.8, 0.8, 1.0),
    vec4(0.9, 0.9, 0.9, 1.0),
    vec4(0.9, 0.9, 0.9, 1.0)
);

void main() {
    int quadVertex = gl_VertexIndex % 4;
    int index = (gl_VertexIndex / 4) * 3;

    int cellX = texelFetch(CloudFaces, index).r;
    int cellZ = texelFetch(CloudFaces, index + 1).r;
    int dirAndFlags = texelFetch(CloudFaces, index + 2).r;
    int direction = dirAndFlags & FLAG_MASK_DIR;
    bool isInsideFace = (dirAndFlags & FLAG_INSIDE_FACE) == FLAG_INSIDE_FACE;
    bool useTopColor = (dirAndFlags & FLAG_USE_TOP_COLOR) == FLAG_USE_TOP_COLOR;
    cellX = (cellX << 1) | ((dirAndFlags & FLAG_EXTRA_X) >> 7);
    cellZ = (cellZ << 1) | ((dirAndFlags & FLAG_EXTRA_Z) >> 6);
    vec3 faceVertex = vertex((direction * 4) + (isInsideFace ? 3 - quadVertex : quadVertex));
    vec3 pos = (faceVertex * CellSize) + (vec3(cellX, 0, cellZ) * CellSize) + CloudOffset;

    vec3 normal = skFaceNormal(direction + 1);
    if (isInsideFace) {
        normal = -normal;
    }

    SkVertex v;
    v.sk_position = pos;
    v.sk_color = (useTopColor ? faceColors[1] : faceColors[direction]) * CloudColor;
    v.sk_uv = vec2(0.0);
    v.sk_worldPos = pos + sk_cameraPosition;
    v.sk_normal = normal;
    v.sk_material = 0;
    v.sk_blockLight = 0.0;
    v.sk_skyLight = 1.0;
    v.sk_top = false;
    v.sk_isHand = false;
    sk_vertexHook(v);

    gl_Position = ProjMat * ModelViewMat * vec4(v.sk_position, 1.0);
    skFog = vec4(fog_spherical_distance(v.sk_position), 0.0, 0.0, 0.0);
    skColor = v.sk_color;
    skPosition = v.sk_position;
    skNormal = normal;
}
