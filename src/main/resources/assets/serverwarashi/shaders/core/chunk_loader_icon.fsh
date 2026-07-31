#version 150

in vec2 localCoord;
in float loaderWorking;

out vec4 fragColor;

float roundedBox(vec2 point, vec2 bounds, float radius) {
    vec2 delta = abs(point) - bounds + radius;
    return min(max(delta.x, delta.y), 0.0) + length(max(delta, 0.0)) - radius;
}

float coverage(float distance) {
    float edge = max(fwidth(distance), 0.006);
    return 1.0 - smoothstep(-edge, edge, distance);
}

vec4 layer(vec3 color, float alpha) {
    return vec4(color * alpha, alpha);
}

vec4 over(vec4 under, vec4 upper) {
    return upper + under * (1.0 - upper.a);
}

void main() {
    vec2 point = localCoord;
    float outer = coverage(roundedBox(point, vec2(0.94), 0.25));
    float badge = coverage(roundedBox(point, vec2(0.86), 0.20));
    vec4 color = layer(vec3(0.01, 0.025, 0.035), outer * 0.82);
    color = over(color, layer(vec3(0.025, 0.095, 0.115), badge * 0.96));

    float frameDistance = abs(roundedBox(point, vec2(0.62), 0.08)) - 0.045;
    float frame = coverage(frameDistance);
    color = over(color, layer(vec3(0.20, 0.92, 1.0), frame * 0.92));

    float insideFrame = coverage(roundedBox(point, vec2(0.56), 0.06));
    float crossDistance = min(abs(point.x), abs(point.y)) - 0.018;
    float cross = coverage(crossDistance) * insideFrame;
    color = over(color, layer(vec3(0.12, 0.52, 0.60), cross * 0.56));

    float glow = (1.0 - smoothstep(0.16, 0.52, length(point))) * 0.42;
    color = over(color, layer(vec3(1.0, 0.65, 0.12), glow));
    float core = coverage(roundedBox(point, vec2(0.24), 0.055));
    color = over(color, layer(vec3(1.0, 0.67, 0.10), core * 0.98));
    float center = coverage(roundedBox(point, vec2(0.09), 0.025));
    color = over(color, layer(vec3(1.0, 0.96, 0.72), center));

    if (color.a <= 0.001) {
        discard;
    }
    if (loaderWorking < 0.5) {
        vec3 straightColor = color.rgb / max(color.a, 0.001);
        float luminance = dot(straightColor, vec3(0.2126, 0.7152, 0.0722));
        vec3 neutral = mix(vec3(0.25), vec3(0.72), clamp(luminance, 0.0, 1.0));
        color.rgb = neutral * color.a;
    }
    fragColor = color;
}
