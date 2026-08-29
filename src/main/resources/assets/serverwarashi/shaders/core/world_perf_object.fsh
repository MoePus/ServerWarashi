#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform int OverflowValue;
uniform float GlyphCount;
uniform float AnimationTime;
uniform int RenderPass;

in vec2 localCoord;
in vec4 vertexColor;
flat in int primitiveKind;
flat in int labelValue;
flat in int labelChars;
flat in float flameStrength;
flat in float particleRandom;
flat in int particleLayer;
flat in float particleOpacity;
flat in float particleLife;

out vec4 fragColor;

const float NOISE_TEXTURE_SCALE = 1.875;
const float FIRE_SCROLL_SPEED = 0.30;
const vec2 DISTORTION_TILING = vec2(0.80, 0.55);
const float BASE_TWIST = 0.015;
const float TIP_TWIST = 0.095;
const float VERTICAL_WARP = 0.018;
const float MIN_BODY_RADIUS = 0.29;
const float MAX_BODY_RADIUS = 0.44;
const float BASE_EDGE_WARP = 0.045;
const float TIP_EDGE_WARP = 0.075;
const float CORE_GLOW_ENERGY = 0.09;
const float NEAR_GLOW_ENERGY = 0.055;
const float FAR_GLOW_ENERGY = 0.015;
const float NEAR_GLOW_FALLOFF = 12.0;
const float FAR_GLOW_FALLOFF = 6.0;
const float GLOW_QUAD_SCALE = 2.40;
const float SPARK_CORE_RADIUS = 0.20;
const float SPARK_HALO_RADIUS = 0.48;
const float SPARK_CORE_ENERGY = 0.42;
const float SPARK_HALO_ENERGY = 0.03;
const float SPARK_WHITE_MIX = 0.72;
const float SPARK_FADE_IN_END = 0.08;
const float SPARK_FADE_OUT_START = 0.62;

struct ParticleShape {
    float signedDistance;
    float bodyMask;
};

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

float sampleSimpleNoise(vec2 uv) {
    return texture(Sampler1, uv * NOISE_TEXTURE_SCALE).r;
}

vec2 flameTwist(vec2 uv) {
    float scroll = AnimationTime * FIRE_SCROLL_SPEED;
    vec2 distortionUv = uv * DISTORTION_TILING
            + vec2(particleRandom, particleRandom - scroll);
    float flow = sampleSimpleNoise(distortionUv) * 2.0 - 1.0;
    float heightAmount = smoothstep(0.0, 1.0, clamp(uv.y, 0.0, 1.0));
    float lateralWarp = flow * mix(BASE_TWIST, TIP_TWIST, heightAmount);
    return uv + vec2(lateralWarp, flow * VERTICAL_WARP)
            + vec2(scroll + particleRandom, particleRandom);
}

ParticleShape particleShape(vec2 uv, float threshold) {
    float fireNoise = sampleSimpleNoise(flameTwist(uv));
    float heightAmount = smoothstep(0.0, 1.0, clamp(uv.y, 0.0, 1.0));
    float bodyRadius = mix(MAX_BODY_RADIUS, MIN_BODY_RADIUS,
            clamp(threshold, 0.0, 1.0));
    float edgeWarp = (fireNoise * 2.0 - 1.0)
            * mix(BASE_EDGE_WARP, TIP_EDGE_WARP, heightAmount);
    float signedDistance = bodyRadius + edgeWarp - length(uv - 0.5);
    float edge = max(fwidth(signedDistance) * 0.82, 0.010);
    float bodyMask = smoothstep(-edge, edge, signedDistance);
    return ParticleShape(signedDistance, bodyMask);
}

float hermite(float value, float start, float end,
        float from, float to, float fromSlope, float toSlope) {
    float duration = end - start;
    float amount = clamp((value - start) / duration, 0.0, 1.0);
    float amountSquared = amount * amount;
    float amountCubed = amountSquared * amount;
    return (2.0 * amountCubed - 3.0 * amountSquared + 1.0) * from
            + (amountCubed - 2.0 * amountSquared + amount) * duration * fromSlope
            + (-2.0 * amountCubed + 3.0 * amountSquared) * to
            + (amountCubed - amountSquared) * duration * toSlope;
}

float lifeEdge(float life) {
    const float start = 0.002193451;
    const float startValue = 0.67567605;
    const float middleValue = 0.35178512;
    float middle = particleLayer == 0 ? 0.2565918 : 0.37062073;
    if (life <= start) return startValue;
    if (life < middle) {
        return hermite(life, start, middle, startValue, middleValue,
                -2.0121472, 0.0);
    }
    float endSlope = particleLayer == 0 ? -0.63746375 : 2.3253443;
    return hermite(life, middle, 1.0, middleValue, 1.0, 0.0, endSlope);
}

vec3 normalParticleColor() {
    if (particleLayer == 0) {
        vec3 dying = mix(vertexColor.rgb * 0.24, vec3(0.055), 0.34);
        return mix(vertexColor.rgb * 0.78, dying,
                smoothstep(0.18, 1.0, particleLife));
    }
    if (particleLayer == 2) {
        vec3 young = mix(vertexColor.rgb, vec3(1.0), 0.46);
        return mix(young, mix(vertexColor.rgb, vec3(1.0), 0.14),
                smoothstep(0.42, 1.0, particleLife));
    }
    vec3 young = mix(vertexColor.rgb, vec3(1.0), 0.20);
    return mix(young, vertexColor.rgb * 0.56,
            smoothstep(0.35, 1.0, particleLife));
}

void renderNormalParticle(float bodyMask) {
    float alphaEnvelope = 1.0;
    if (particleLayer == 0) {
        alphaEnvelope = 1.0 - clamp(particleLife, 0.0, 1.0);
    } else if (particleLayer == 1) {
        alphaEnvelope = 1.0 - smoothstep(0.48, 0.80, particleLife);
    } else if (particleLayer == 2) {
        alphaEnvelope = 1.0 - smoothstep(0.68, 1.0, particleLife);
    }
    float alpha = bodyMask * alphaEnvelope * particleOpacity * vertexColor.a;
    if (alpha <= 0.002) discard;
    fragColor = vec4(normalParticleColor() * alpha, alpha);
}

void renderSmokeParticle(float bodyMask) {
    float smokeIn = smoothstep(0.42, 0.72, particleLife);
    float smokeOut = 1.0 - smoothstep(0.88, 1.0, particleLife);
    float radialFade = 1.0 - smoothstep(0.20, 0.54,
            length(localCoord - 0.5));
    float alpha = bodyMask * radialFade * smokeIn * smokeOut * 0.58;
    alpha *= particleOpacity * vertexColor.a;
    if (alpha <= 0.002) discard;
    vec3 warmSmoke = vec3(0.368, 0.234, 0.207);
    vec3 graySmoke = vec3(0.285, 0.290, 0.295);
    vec3 smokeColor = mix(warmSmoke, graySmoke,
            smoothstep(0.68, 1.0, particleLife));
    fragColor = vec4(smokeColor * alpha, alpha);
}

void renderGlowParticle(ParticleShape shape) {
    vec3 coreColor = mix(vertexColor.rgb, vec3(1.0), 0.62);
    float ageFade = 1.0 - smoothstep(0.72, 1.0, particleLife);
    float outsideDistance = max(-shape.signedDistance, 0.0);
    float nearGlow = exp2(-NEAR_GLOW_FALLOFF * outsideDistance);
    float farGlow = exp2(-FAR_GLOW_FALLOFF * outsideDistance);
    float quadRadius = max(abs(localCoord.x - 0.5), abs(localCoord.y - 0.5));
    float supportFade = 1.0 - smoothstep(0.42, 0.50, quadRadius);
    float energy = shape.bodyMask * CORE_GLOW_ENERGY
            + (nearGlow * NEAR_GLOW_ENERGY + farGlow * FAR_GLOW_ENERGY)
            * supportFade;
    energy *= ageFade;
    energy *= particleOpacity * vertexColor.a;
    if (energy <= 0.001) discard;
    fragColor = vec4(coreColor * energy, 0.0);
}

void renderSparkParticle() {
    float radius = length(localCoord - 0.5);
    float edge = max(fwidth(radius) * 0.82, 0.008);
    float core = 1.0 - smoothstep(
            SPARK_CORE_RADIUS - edge, SPARK_CORE_RADIUS + edge, radius);
    float halo = 1.0 - smoothstep(
            SPARK_CORE_RADIUS, SPARK_HALO_RADIUS, radius);
    float lifeFade = smoothstep(0.0, SPARK_FADE_IN_END, particleLife)
            * (1.0 - smoothstep(SPARK_FADE_OUT_START, 1.0, particleLife));
    float energy = (core * SPARK_CORE_ENERGY + halo * SPARK_HALO_ENERGY)
            * lifeFade * particleOpacity * vertexColor.a;
    if (energy <= 0.001) discard;
    vec3 sparkColor = mix(vertexColor.rgb, vec3(1.0), SPARK_WHITE_MIX);
    fragColor = vec4(sparkColor * energy, 0.0);
}

void renderFlame() {
    if (particleLayer == 4) {
        renderSparkParticle();
        return;
    }
    float particleUvScale = particleLayer == 2 ? 1.70
            : (particleLayer == 3 ? GLOW_QUAD_SCALE : 1.0);
    vec2 particleUv = (localCoord - 0.5) * particleUvScale + 0.5;
    float threshold = lifeEdge(particleLife);
    ParticleShape shape = particleShape(particleUv, threshold);
    if (RenderPass == 2) {
        renderSmokeParticle(shape.bodyMask);
    } else if (particleLayer == 3) {
        renderGlowParticle(shape);
    } else {
        renderNormalParticle(shape.bodyMask);
    }
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
        renderFlame();
    } else {
        renderLabel();
    }
}
