#ifndef SKAFFY_SKY_GLSL
#define SKAFFY_SKY_GLSL

#ifndef SK_PART
#define SK_PART 0
#endif

vec3 skSkyRelative(vec3 position) {
    return (sk_inverseViewMatrix * (ModelViewMat * vec4(position, 1.0))).xyz;
}

vec3 skSkyDirection(vec3 position) {
    return normalize(skSkyRelative(position) + vec3(0.0, 1e-6, 0.0));
}

int skSkyPart(vec3 direction) {
#if SK_PART >= 0
    return SK_PART;
#else
    if (sk_dimension == 2) {
        return 6;
    }
    return dot(direction, sk_sunDirection) >= dot(direction, sk_moonDirection) ? 3 : 4;
#endif
}

SkVertex skSkyVertex(vec3 position, vec4 color, vec2 uv) {
    SkVertex v;
    v.sk_position = position;
    v.sk_color = color;
    v.sk_uv = uv;
    v.sk_worldPos = sk_cameraPosition + skSkyDirection(position) * sk_far;
    v.sk_normal = -skSkyDirection(position);
    v.sk_material = 0;
    v.sk_blockLight = 0.0;
    v.sk_skyLight = 1.0;
    v.sk_top = false;
    v.sk_isHand = false;
    return v;
}


#endif
