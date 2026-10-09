#ifndef SKAFFY_MODEL_GLSL
#define SKAFFY_MODEL_GLSL

layout(std140) uniform SkaffyModel {

    mat4 SkModelMatrix;

    mat4 SkNormalMatrix;
    vec4 SkTint;

    vec4 SkUvTransform;

    ivec4 SkLight;
    vec4 SkOverlay;
    vec4 SkParams;
    vec4 SkEmissive;
    vec4 SkMaterial;
    vec4 SkSpecular;

};

#endif
