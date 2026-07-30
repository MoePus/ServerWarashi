#version 150

uniform float MapScale;
uniform int Pass;

in vec2 localCoord;
in vec4 vertexColor;
in float glowRadiusPixels;
in float weakEdgeMask;

out vec4 fragColor;

const float LOADER_ALPHA_MAX = 64.0 / 255.0;
const float HEAT_ALPHA_MIN = 96.0 / 255.0;
const float ZOOMED_OUT_LOADER_EDGE_ALPHA = 0.25;

float boxDistancePixels() {
    vec2 outside = abs(localCoord) - vec2(1.0);
    return max(outside.x, outside.y) * 8.0 * MapScale;
}

float loaderEdgeOpacity() {
    float heatWeight = smoothstep(LOADER_ALPHA_MAX, HEAT_ALPHA_MIN, vertexColor.a);
    float zoomOut = smoothstep(0.0, 1.0,
            log2(max(1.0 / max(MapScale, 0.0001), 1.0)));
    float loaderWeight = (1.0 - heatWeight) * zoomOut;
    return mix(1.0, ZOOMED_OUT_LOADER_EDGE_ALPHA, loaderWeight);
}

void applyWeakHatch(inout vec3 color, inout float alpha);

float indicatorCoverage(float distance) {
    float edge = max(fwidth(distance) * 1.15, 0.006);
    return 1.0 - smoothstep(-edge, edge, distance);
}

vec4 indicatorLayer(vec3 color, float alpha) {
    return vec4(color * alpha, alpha);
}

vec4 indicatorOver(vec4 under, vec4 upper) {
    return upper + under * (1.0 - upper.a);
}

void renderIndicator() {
    vec2 point = localCoord;
    float horizontal = abs(point.x);
    float curve = 0.32 - 0.38 * point.x * point.x;
    float taper = 1.0 - smoothstep(0.62, 0.98, horizontal);
    float halfWidth = 0.045 + 0.115 * taper;
    float stroke = abs(point.y - curve) - halfWidth;
    float shape = max(stroke, horizontal - 0.94);
    float glow = (1.0 - smoothstep(0.02, 0.30, max(shape, 0.0)))
            * vertexColor.a * 0.24;
    float outline = indicatorCoverage(shape - 0.055) * vertexColor.a * 0.86;
    float fill = indicatorCoverage(shape) * vertexColor.a;
    float highlight = smoothstep(-0.10, 0.16, point.y - curve) * 0.30;
    vec3 base = vertexColor.rgb;
    vec3 face = mix(base * 0.82, mix(base, vec3(1.0), 0.40), highlight);
    vec4 color = indicatorLayer(base, glow);
    color = indicatorOver(color, indicatorLayer(vec3(0.045, 0.012, 0.070), outline));
    color = indicatorOver(color, indicatorLayer(face, fill));
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color;
}

void renderGlow(float distancePixels) {
    float outside = 1.0 - smoothstep(0.0, glowRadiusPixels, distancePixels);
    float innerCutoff = smoothstep(-0.75, 0.0, distancePixels);
    float alpha = outside * innerCutoff * 0.38 * loaderEdgeOpacity();
    if (alpha <= 0.001) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb * alpha, alpha);
}

void renderBody(float distancePixels) {
    if (distancePixels > 0.0) {
        discard;
    }
    float inside = -distancePixels;
    float borderWeight = smoothstep(5.0, 9.0, 16.0 * MapScale);
    float darkEdge = (1.0 - smoothstep(0.0, 1.1, inside)) * borderWeight;
    float brightEdge = smoothstep(0.65, 1.15, inside)
            * (1.0 - smoothstep(1.15, 2.05, inside)) * borderWeight;

    vec3 color = mix(vertexColor.rgb, mix(vertexColor.rgb, vec3(1.0), 0.38), brightEdge);
    color = mix(color, vec3(0.025, 0.035, 0.045), darkEdge * 0.92);
    float edgeAlpha = max(brightEdge * 0.78, darkEdge * 0.88) * loaderEdgeOpacity();
    float alpha = max(vertexColor.a, edgeAlpha);
    applyWeakHatch(color, alpha);
    if (alpha <= 0.001) {
        discard;
    }
    fragColor = vec4(color * alpha, alpha);
}

void applyWeakHatch(inout vec3 color, inout float alpha) {
    if (weakEdgeMask < 0.5) {
        return;
    }
    float west = mod(floor(weakEdgeMask), 2.0);
    float east = mod(floor(weakEdgeMask / 2.0), 2.0);
    float north = mod(floor(weakEdgeMask / 4.0), 2.0);
    float south = mod(floor(weakEdgeMask / 8.0), 2.0);
    float edge = 0.0;
    edge = max(edge, west * (1.0 - smoothstep(-0.95, -0.72, localCoord.x)));
    edge = max(edge, east * smoothstep(0.72, 0.95, localCoord.x));
    edge = max(edge, north * (1.0 - smoothstep(-0.95, -0.72, localCoord.y)));
    edge = max(edge, south * smoothstep(0.72, 0.95, localCoord.y));
    float distanceToStripe = abs(fract((localCoord.x + localCoord.y) * 3.2) - 0.5);
    float stripe = 1.0 - smoothstep(0.07, 0.18, distanceToStripe);
    float hatch = edge * stripe * 0.72;
    color = mix(color, vec3(0.82, 0.96, 1.0), hatch);
    alpha = max(alpha, hatch * 0.72);
}

void renderHover(float distancePixels) {
    float innerCutoff = smoothstep(-0.8, 0.0, distancePixels);
    float outerCutoff = 1.0 - smoothstep(1.4, 2.5, distancePixels);
    float ring = innerCutoff * outerCutoff;
    if (ring <= 0.001) {
        discard;
    }
    vec3 cyan = vec3(0.25, 0.96, 1.0);
    float highlight = clamp(1.0 - abs(distancePixels) / 2.5, 0.0, 1.0);
    vec3 color = mix(cyan, vec3(1.0), 0.38 * highlight);
    float alpha = ring * 0.96;
    fragColor = vec4(color * alpha, alpha);
}

void main() {
    if (Pass == 3) {
        renderIndicator();
        return;
    }
    float distancePixels = boxDistancePixels();
    if (Pass == 0) {
        renderGlow(distancePixels);
    } else if (Pass == 1) {
        renderBody(distancePixels);
    } else {
        renderHover(distancePixels);
    }
}
