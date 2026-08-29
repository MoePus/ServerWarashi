#version 150

in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float alpha = vertexColor.a;
    if (alpha <= 0.002) discard;
    fragColor = vec4(vertexColor.rgb * alpha, alpha);
}
