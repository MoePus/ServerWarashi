#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float MapScale;
uniform int Pass;

out vec2 localCoord;
out vec4 vertexColor;
out float glowRadiusPixels;

const float HEAT_ALPHA_MIN = 96.0 / 255.0;
const float HEAT_ALPHA_MAX = 160.0 / 255.0;
const float BASE_GLOW_RADIUS_PIXELS = 3.0;
const float ZOOM_GLOW_RADIUS_PIXELS = 3.5;

float glowRadius() {
    float heatWeight = smoothstep(HEAT_ALPHA_MIN, HEAT_ALPHA_MAX, Color.a);
    float zoomOut = clamp(log2(max(1.0 / max(MapScale, 0.0001), 1.0)), 0.0, 4.0);
    return BASE_GLOW_RADIUS_PIXELS + heatWeight * zoomOut * ZOOM_GLOW_RADIUS_PIXELS;
}

void main() {
    glowRadiusPixels = glowRadius();
    float expansionPixels = Pass == 0 ? glowRadiusPixels : (Pass == 2 ? 2.5 : 0.0);
    float extent = 8.0 + expansionPixels / max(MapScale, 0.0001);
    vec2 worldPosition = Position.xy + UV0 * extent;
    gl_Position = ProjMat * ModelViewMat * vec4(worldPosition, Position.z, 1.0);
    localCoord = UV0 * (extent / 8.0);
    vertexColor = Color;
}
