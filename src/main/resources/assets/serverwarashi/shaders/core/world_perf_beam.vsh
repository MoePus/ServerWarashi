#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 FadeDistances;
uniform vec2 CameraOffset;

out vec4 vertexColor;

float smoothRange(float start, float end, float value) {
    float amount = clamp((value - start) / (end - start), 0.0, 1.0);
    return amount * amount * (3.0 - 2.0 * amount);
}

void main() {
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);
    float distanceToCamera = length(Position.xz - CameraOffset);
    vertexColor = Color;
    vertexColor.a *= smoothRange(FadeDistances.x, FadeDistances.y, distanceToCamera);
    gl_Position = ProjMat * viewPosition;
    if (gl_Position.w > 0.0) {
        gl_Position.z = min(gl_Position.z, gl_Position.w * 0.9999);
    }
}
