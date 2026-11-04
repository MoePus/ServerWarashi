#version 150

uniform sampler2D Sampler0;
uniform int OverflowValue;
uniform float GlyphCount;

in vec2 localCoord;
in vec4 vertexColor;
flat in int primitiveKind;
flat in int labelValue;
flat in int labelChars;
flat in float particlePixels;

out vec4 fragColor;

// Squares at least this many pixels wide lose their four corner pixels.
const float CHIP_CORNERS_FROM = 4.0;

int powerOfTen(int exponent) {
    int result = 1;
    for (int index = 0; index < 9; index++) {
        if (index >= exponent) break;
        result *= 10;
    }
    return result;
}

int digitCount(int value) {
    int digits = 1;
    int remaining = value;
    for (int index = 1; index < 9; index++) {
        if (remaining < 10) break;
        remaining /= 10;
        digits++;
    }
    return digits;
}

int suffixGlyph(int index) {
    if (index == 0) return 12;
    if (index == 1) return 13;
    if (index == 2) return 14;
    if (index == 3) return 15;
    return 16;
}

int glyphAt(int character) {
    if (labelValue <= 0) {
        if (character == 0) return 10;
        if (character == 1) return 1;
        return suffixGlyph(character - 2);
    }
    if (labelValue >= OverflowValue) {
        if (character < 9) return 9;
        if (character == 9) return 11;
        return suffixGlyph(character - 10);
    }
    int digits = digitCount(labelValue);
    if (character >= digits) return suffixGlyph(character - digits);
    int divisor = powerOfTen(digits - character - 1);
    return (labelValue / divisor) % 10;
}

void renderParticle() {
    if (particlePixels >= CHIP_CORNERS_FROM) {
        vec2 cell = floor(clamp(localCoord, 0.0, 0.999999) * particlePixels);
        vec2 edge = min(cell, particlePixels - 1.0 - cell);
        if (edge.x < 0.5 && edge.y < 0.5) discard;
    }
    float alpha = vertexColor.a;
    if (alpha <= 0.002) discard;
    fragColor = vec4(vertexColor.rgb * alpha, alpha);
}

void renderLabel() {
    float cellPosition = clamp(localCoord.x, 0.0, 0.999999) * float(labelChars);
    int character = int(floor(cellPosition));
    int glyph = glyphAt(character);
    vec2 glyphUv = vec2((float(glyph) + fract(cellPosition)) / GlyphCount,
            localCoord.y);
    float signedDistance = texture(Sampler0, glyphUv).r;
    float glyphEdge = max(fwidth(signedDistance) * 0.82, 0.010);
    float outlineAlpha = smoothstep(0.30 - glyphEdge, 0.30 + glyphEdge,
            signedDistance) * vertexColor.a;
    float fillAlpha = smoothstep(0.52 - glyphEdge, 0.52 + glyphEdge,
            signedDistance) * vertexColor.a;
    fragColor = vec4(vec3(fillAlpha), outlineAlpha);
    if (fragColor.a <= 0.002) discard;
}

void main() {
    if (primitiveKind == 0) {
        renderParticle();
    } else {
        renderLabel();
    }
}
