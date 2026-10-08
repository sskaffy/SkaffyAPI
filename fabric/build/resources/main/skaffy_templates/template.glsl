#ifndef SKAFFY_TEMPLATE_GLSL
#define SKAFFY_TEMPLATE_GLSL

vec3 skFaceNormal(int face) {
    if (face == 1) return vec3(0.0, -1.0, 0.0);
    if (face == 2) return vec3(0.0, 1.0, 0.0);
    if (face == 3) return vec3(0.0, 0.0, -1.0);
    if (face == 4) return vec3(0.0, 0.0, 1.0);
    if (face == 5) return vec3(-1.0, 0.0, 0.0);
    if (face == 6) return vec3(1.0, 0.0, 0.0);
    return vec3(0.0);
}

float skShade(int direction) {
    if (direction < 4) {
        return sk_shadeA[direction];
    }
    return direction == 4 ? sk_shadeB.x : sk_shadeB.y;
}



vec4 skShadowClip(vec3 relative) {
    vec4 clip = sk_shadowMatrix * vec4(relative, 1.0);
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    return vec4(clip.xy, clip.z, 1.0);
#else
    return vec4(clip.xy, clip.z * 2.0 - 1.0, 1.0);
#endif
}

#ifdef SK_FRAGMENT_STAGE
vec3 skSurfaceNormal(vec3 position) {
    vec3 normal = cross(dFdx(position), dFdy(position));

    float length2 = dot(normal, normal);
    if (length2 < 1e-12) {
        return -normalize(position + vec3(0.0, 0.0, 1e-6));
    }

    normal *= inversesqrt(length2);
    return dot(normal, position) > 0.0 ? -normal : normal;
}




vec3 skFacing(vec3 normal, vec3 position) {
    return dot(normal, position) > 0.0 && !gl_FrontFacing ? -normal : normal;
}
#endif


#endif
