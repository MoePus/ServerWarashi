#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float MapScale;

out vec2 localCoord;
out float loaderWorking;

void main() {
    float halfWorld = min(7.0 / max(MapScale, 0.0001), 32.0);
    vec2 worldPosition = Position.xy + UV0 * halfWorld;
    gl_Position = ProjMat * ModelViewMat * vec4(worldPosition, Position.z, 1.0);
    localCoord = UV0;
    loaderWorking = Color.r;
}
