#version 150
in vec3 Position;
uniform samplerBuffer MarkerData;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float LabelPixels;
uniform vec2 FadeDistances;
uniform vec2 LabelDistances;
uniform vec2 LabelFadeDistances;
uniform int OverflowValue;
uniform int LabelWordBase;
uniform float StrengthWordScale;
uniform int ParticlePhaseBase;
uniform float MaxFlameStrength;
uniform float MaxFlameViewportFraction;
uniform float PartialTick;
uniform float ParticleTime;
uniform int ParticleEpoch;
uniform int RenderPass;
uniform int ParticleStart;
uniform int SlotsPerMarker;
uniform int MarkerBase;
const int RED_PARTICLE_COUNT = 24;
const int ORANGE_PARTICLE_COUNT = 20;
const int YELLOW_PARTICLE_COUNT = 20;
const int GLOW_PARTICLE_COUNT = 16;
const int SPARK_PARTICLE_COUNT = 64;
const int ORANGE_PARTICLE_START = RED_PARTICLE_COUNT;
const int YELLOW_PARTICLE_START = ORANGE_PARTICLE_START + ORANGE_PARTICLE_COUNT;
const int GLOW_PARTICLE_START = YELLOW_PARTICLE_START + YELLOW_PARTICLE_COUNT;
const int SPARK_PARTICLE_START = GLOW_PARTICLE_START + GLOW_PARTICLE_COUNT;
const int SPARK_LAYER = 4;
const int LABEL_PASS = 3;
const int SPARK_PASS = 4;
const int MARKER_TEXELS = 4;
// Flame shape tuning. Higher severity widens the full tapered envelope.
const float GREEN_BASE_WIDTH = 1.5;
const float GREEN_TIP_WIDTH = 0.8;
const float PURPLE_BASE_WIDTH = 2.4;
const float PURPLE_TIP_WIDTH = 1.0;
const float WIDTH_TAPER_START = 0.0;
const float WIDTH_TAPER_END = 0.88;
// Distance LOD tuning. Particle density drops after 16 blocks and settles at 32.
const float PARTICLE_LOD_START = 16.0;
const float PARTICLE_LOD_END = 32.0;
const float SPARK_FADE_START = 20.0;
const float SPARK_FADE_END = 24.0;
const float SMOKE_FADE_START = 24.0;
const float SMOKE_FADE_END = 32.0;
const float MIN_RED_COUNT = 16.0;
const float MIN_ORANGE_COUNT = 14.0;
const float MIN_YELLOW_COUNT = 14.0;
const float MIN_GLOW_COUNT = 10.0;
const float MIN_SPARK_COUNT = 10.0;
const float FAR_RED_COUNT = 4.0;
const float FAR_ORANGE_COUNT = 3.0;
const float FAR_YELLOW_COUNT = 3.0;
const float FAR_GLOW_COUNT = 1.0;
const vec2 SPARK_COUNT_RAMP = vec2(3.50, 5.0);
// Spark motion tuning. Sparks deliberately do not use the flame width envelope.
const vec2 SPARK_ASPECT = vec2(1.0, 1.0);
const vec2 SPARK_SIZE_RANGE = vec2(0.040, 0.055);
const vec2 SPARK_EMITTER_HALF_WIDTH_RANGE = vec2(0.10, 0.32);
const vec2 SPARK_PLUME_SPREAD_RANGE = vec2(0.10, 0.48);
const float SPARK_SPREAD_LIFE_EXPONENT = 1.15;
const vec2 SPARK_RISE_RANGE = vec2(1.40, 5.20);
const float SPARK_RISE_DECELERATION = 0.12;
const float SPARK_CYCLE = 1.40;
const float SPARK_LIFETIME = 0.90;
const int SPARK_BURST_COUNT = 4;
const int SPARKS_PER_BURST = 16;
const float SPARK_BURST_JITTER = 0.04;
const int SPARK_CYCLES_PER_PERIOD = 5;
const float PARTICLE_CLOCK_PERIOD = 7.0;
const float DEFAULT_PARTICLE_CYCLE = 1.0;
const float ORANGE_PARTICLE_CYCLE = 0.70;
const float FAST_PARTICLE_SPEED = 2.0;
const int RED_CYCLES_PER_PERIOD = 7;
const int ORANGE_CYCLES_PER_PERIOD = 10;
const int FAST_CYCLES_PER_PERIOD = 14;
const vec2 SPARK_PLUME_TEMPORAL_HARMONICS = vec2(1.0, 3.0);
const vec2 SPARK_PLUME_VERTICAL_PHASE = vec2(2.30, -4.10);
const vec2 SPARK_PLUME_WEIGHTS = vec2(0.75, 0.25);
const vec2 SPARK_PLUME_DRIFT_RANGE = vec2(0.06, 0.22);
const float SPARK_PLUME_LIFE_EXPONENT = 1.25;
const float SPARK_INDIVIDUAL_DRIFT = 0.035;
const vec2 PARTICLE_ASPECT = vec2(0.80, 1.12);
const float GLOW_QUAD_SCALE = 2.40;
const float GLOW_BODY_RADIUS = 0.44;
const float LABEL_GLYPH_ASPECT = 0.75;
out vec2 localCoord;
out vec4 vertexColor;
flat out int primitiveKind;
flat out int labelValue;
flat out int labelChars;
flat out float flameStrength;
flat out float particleRandom;
flat out int particleLayer;
flat out float particleOpacity;
flat out float particleLife;
struct FlameMarker {
    vec3 position;
    vec3 previousPosition;
    float packedStrength;
    vec3 color;
    int phaseWord;
    int labelLow;
    int labelHigh;
    float labelStrength;
};
FlameMarker markerAt(int markerIndex) {
    int base = markerIndex * MARKER_TEXELS;
    vec4 positionStrength = texelFetch(MarkerData, base);
    vec4 colorPhase = texelFetch(MarkerData, base + 1);
    vec4 label = texelFetch(MarkerData, base + 2);
    vec4 previousPosition = texelFetch(MarkerData, base + 3);
    return FlameMarker(positionStrength.xyz, previousPosition.xyz, positionStrength.w,
            colorPhase.rgb, int(colorPhase.w), int(label.x), int(label.y), label.z);
}
int decimalDigits(int value) {
    if (value < 10) return 1;
    if (value < 100) return 2;
    if (value < 1000) return 3;
    if (value < 10000) return 4;
    if (value < 100000) return 5;
    if (value < 1000000) return 6;
    if (value < 10000000) return 7;
    if (value < 100000000) return 8;
    return 9;
}
float smoothRange(float start, float end, float value) {
    float amount = clamp((value - start) / (end - start), 0.0, 1.0);
    return amount * amount * (3.0 - 2.0 * amount);
}
float severity(float strength) {
    return smoothRange(1.0, 5.0, strength);
}
float flameWidthEnvelope(float life, float strength) {
    float level = severity(strength);
    float baseWidth = mix(GREEN_BASE_WIDTH, PURPLE_BASE_WIDTH, level);
    float tipWidth = mix(GREEN_TIP_WIDTH, PURPLE_TIP_WIDTH, level);
    float heightAmount = smoothRange(WIDTH_TAPER_START, WIDTH_TAPER_END, life);
    return mix(baseWidth, tipWidth, heightAmount);
}
float lateralSway(float life, float randomValue, float strength) {
    const float PI = 3.14159265;
    const float TAU = 6.28318530;
    float phase = life * TAU + randomValue * PI;
    float amplitude = mix(0.025, 0.10, severity(strength)) * life;
    return sin(phase) * amplitude;
}
float particleScale(float strength) {
    return mix(0.42, 1.15, severity(strength));
}
float riseDistance(float strength) {
    return mix(0.48, 3.0, pow(severity(strength), 0.82));
}
float flameHeight(float strength) {
    return riseDistance(strength) + particleScale(strength) * 0.5;
}
float flameViewportScale(vec4 viewPosition, float height) {
    vec4 clipPosition = ProjMat * viewPosition;
    float projectedHeight = max(abs(ProjMat[1][1]) * height, 0.001);
    float maximumHeight = 2.0 * MaxFlameViewportFraction * abs(clipPosition.w);
    return min(1.0, maximumHeight / projectedHeight);
}
int layerForParticle(int particleIndex) {
    if (particleIndex < ORANGE_PARTICLE_START) return 0;
    if (particleIndex < YELLOW_PARTICLE_START) return 1;
    if (particleIndex < GLOW_PARTICLE_START) return 2;
    if (particleIndex < SPARK_PARTICLE_START) return 3;
    return SPARK_LAYER;
}
int layerStart(int layer) {
    if (layer == 0) return 0;
    if (layer == 1) return ORANGE_PARTICLE_START;
    if (layer == 2) return YELLOW_PARTICLE_START;
    if (layer == 3) return GLOW_PARTICLE_START;
    return SPARK_PARTICLE_START;
}
int layerCapacity(int layer) {
    if (layer == 0) return RED_PARTICLE_COUNT;
    if (layer == 1) return ORANGE_PARTICLE_COUNT;
    if (layer == 2) return YELLOW_PARTICLE_COUNT;
    if (layer == 3) return GLOW_PARTICLE_COUNT;
    return SPARK_PARTICLE_COUNT;
}
float minimumParticles(int layer) {
    if (layer == 0) return MIN_RED_COUNT;
    if (layer == 1) return MIN_ORANGE_COUNT;
    if (layer == 2) return MIN_YELLOW_COUNT;
    if (layer == 3) return MIN_GLOW_COUNT;
    return MIN_SPARK_COUNT;
}
float farParticles(int layer) {
    if (layer == 0) return FAR_RED_COUNT;
    if (layer == 1) return FAR_ORANGE_COUNT;
    if (layer == 2) return FAR_YELLOW_COUNT;
    return FAR_GLOW_COUNT;
}
int visibleParticles(int layer, float strength, float distanceToCamera) {
    float countAmount = layer == SPARK_LAYER ? smoothRange(
            SPARK_COUNT_RAMP.x, SPARK_COUNT_RAMP.y, strength)
            : severity(strength);
    float nearCount = mix(minimumParticles(layer), float(layerCapacity(layer)), countAmount);
    if (layer == SPARK_LAYER) {
        float sparkLod = smoothRange(PARTICLE_LOD_START, SPARK_FADE_END,
                distanceToCamera);
        float sparkRemaining = 1.0 - sparkLod;
        return int(floor(nearCount * sparkRemaining * sparkRemaining + 0.5));
    }
    float lodAmount = smoothRange(PARTICLE_LOD_START, PARTICLE_LOD_END,
            distanceToCamera);
    float fastLod = 1.0 - (1.0 - lodAmount) * (1.0 - lodAmount);
    return int(floor(mix(nearCount, farParticles(layer), fastLod) + 0.5));
}
bool selectedSlot(int slot, int count, int capacity) {
    int previous = int(floor(float(slot * count) / float(capacity)));
    int next = int(floor(float((slot + 1) * count) / float(capacity)));
    return next > previous;
}
bool selectedParticleSlot(int layer, int slot, int count) {
    int capacity = layerCapacity(layer);
    if (layer != 2) return selectedSlot(slot, count, capacity);
    if (slot == 0) return count > 0;
    return count > 1 && selectedSlot(slot - 1, count - 1, capacity - 1);
}
uint hashParticleSeed(uint value) {
    value ^= value >> 16;
    value *= 0x7FEB352Du;
    value ^= value >> 15;
    value *= 0x846CA68Bu;
    value ^= value >> 16;
    return value;
}
float signedHash(uint seed) {
    uint randomBits = hashParticleSeed(seed) & 0x00FFFFFFu;
    return float(randomBits) / 8388607.5 - 1.0;
}
float randomSigned(int phaseWord, int particleIndex, int generation) {
    uint seed = uint(phaseWord);
    seed ^= uint(particleIndex) * 0x9E3779B9u;
    seed ^= uint(generation) * 0x85EBCA6Bu;
    return signedHash(seed);
}
float sparkPlumeCenter(float life, int phaseWord) {
    const float TAU = 6.28318530;
    float clockPhase = ParticleTime / PARTICLE_CLOCK_PERIOD * TAU;
    float markerPhase = float(phaseWord) / float(ParticlePhaseBase) * TAU;
    vec2 flowPhase = clockPhase * SPARK_PLUME_TEMPORAL_HARMONICS
            + vec2(markerPhase, markerPhase * -1.73)
            + life * SPARK_PLUME_VERTICAL_PHASE;
    float lifeRamp = pow(clamp(life, 0.0, 1.0), SPARK_PLUME_LIFE_EXPONENT);
    return dot(sin(flowPhase), SPARK_PLUME_WEIGHTS) * lifeRamp;
}
float sparkIndividualDrift(float life, float randomValue) {
    const float TAU = 6.28318530;
    return sin(life * 3.14159265 + randomValue * TAU)
            * life * SPARK_INDIVIDUAL_DRIFT;
}
float sparkSlotOffset(int slot, int phaseWord) {
    int burst = slot / SPARKS_PER_BURST;
    float burstOffset = float(burst) * SPARK_CYCLE / float(SPARK_BURST_COUNT);
    uint seed = uint(phaseWord) ^ uint(slot) * 0x27D4EB2Fu;
    return burstOffset + signedHash(seed) * SPARK_BURST_JITTER;
}
int cyclesPerParticlePeriod(int layer) {
    if (layer == 1) return ORANGE_CYCLES_PER_PERIOD;
    if (layer == 2 || layer == 3) return FAST_CYCLES_PER_PERIOD;
    if (layer == SPARK_LAYER) return SPARK_CYCLES_PER_PERIOD;
    return RED_CYCLES_PER_PERIOD;
}
bool particleState(int layer, int slot, int phaseWord,
        out float life, out float elapsed, out int generation) {
    float simulationSpeed = layer == 2 || layer == 3 ? FAST_PARTICLE_SPEED : 1.0;
    float cycle = layer == 1 ? ORANGE_PARTICLE_CYCLE
            : (layer == SPARK_LAYER ? SPARK_CYCLE : DEFAULT_PARTICLE_CYCLE);
    float lifetime = layer == 1 ? 0.62
            : (layer == SPARK_LAYER ? SPARK_LIFETIME : 1.0);
    float phase = float(phaseWord) / float(ParticlePhaseBase);
    float emitterOffset = fract(phase + float(layer) * 0.319) * cycle;
    float slotInterval = cycle / float(layerCapacity(layer));
    float slotOffset = layer == SPARK_LAYER
            ? sparkSlotOffset(slot, phaseWord) : float(slot) * slotInterval;
    float clock = ParticleTime * simulationSpeed + emitterOffset
            - slotOffset;
    float generationValue = floor(clock / cycle);
    elapsed = clock - generationValue * cycle;
    life = elapsed / lifetime;
    generation = int(generationValue)
            + ParticleEpoch * cyclesPerParticlePeriod(layer);
    return elapsed < lifetime;
}
void hidePrimitive() {
    particleOpacity = 0.0;
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}
void renderLabelVertex(vec4 viewPosition, float distanceToCamera, FlameMarker marker) {
    float labelStrength = marker.labelStrength * MaxFlameStrength;
    float labelFlameHeight = flameHeight(labelStrength);
    viewPosition.y += labelFlameHeight
            * flameViewportScale(viewPosition, labelFlameHeight) + 0.10;
    vec4 clipPosition = ProjMat * viewPosition;
    labelValue = marker.labelLow + (-marker.labelHigh - 1) * LabelWordBase;
    labelChars = labelValue <= 0 ? 7
            : (labelValue >= OverflowValue ? 15 : decimalDigits(labelValue) + 5);
    float perspective = LabelDistances.x / max(distanceToCamera, LabelDistances.x);
    float readableScale = max(perspective, 0.62);
    float labelScale = mix(1.0, readableScale,
            smoothRange(LabelDistances.x, LabelDistances.y, distanceToCamera));
    vertexColor.a *= 1.0 - smoothRange(
            LabelFadeDistances.x, LabelFadeDistances.y, distanceToCamera);
    float heightPixels = LabelPixels * labelScale;
    float widthPixels = heightPixels * LABEL_GLYPH_ASPECT * float(labelChars);
    vec2 pixelOffset = vec2((localCoord.x - 0.5) * widthPixels,
            (1.1 - localCoord.y) * heightPixels);
    clipPosition.xy += pixelOffset * 2.0 / ScreenSize * clipPosition.w;
    gl_Position = clipPosition;
}
vec2 sparkOffset(float elapsed, vec2 randomValues, int phaseWord) {
    float amount = severity(flameStrength);
    float life = elapsed / SPARK_LIFETIME;
    float plumeDrift = sparkPlumeCenter(life, phaseWord)
            * mix(SPARK_PLUME_DRIFT_RANGE.x, SPARK_PLUME_DRIFT_RANGE.y, amount);
    float emitterWidth = mix(SPARK_EMITTER_HALF_WIDTH_RANGE.x, SPARK_EMITTER_HALF_WIDTH_RANGE.y, amount);
    float spread = randomValues.y * mix(SPARK_PLUME_SPREAD_RANGE.x,
            SPARK_PLUME_SPREAD_RANGE.y, amount)
            * pow(life, SPARK_SPREAD_LIFE_EXPONENT);
    float x = randomValues.x * emitterWidth + spread + plumeDrift + sparkIndividualDrift(life, randomValues.y);
    float riseScale = 1.0 - SPARK_RISE_DECELERATION * life;
    float y = elapsed * mix(SPARK_RISE_RANGE.x, SPARK_RISE_RANGE.y, amount) * riseScale;
    return vec2(x, y);
}
void renderParticleVertex(vec4 viewPosition, float distanceToCamera, int particleIndex, int phaseWord) {
    particleLayer = layerForParticle(particleIndex);
    bool smokePass = RenderPass == 2;
    bool spark = particleLayer == SPARK_LAYER;
    bool anchoredCore = particleLayer == 2
            && particleIndex == YELLOW_PARTICLE_START;
    int expectedPass = particleLayer == 3 ? 1 : (spark ? SPARK_PASS : 0);
    int slot = particleIndex - layerStart(particleLayer);
    int count = visibleParticles(particleLayer, flameStrength, distanceToCamera);
    if ((smokePass && (particleLayer != 1 || distanceToCamera >= SMOKE_FADE_END))
            || (!smokePass && RenderPass != expectedPass)
            || !selectedParticleSlot(particleLayer, slot, count)) {
        hidePrimitive();
        return;
    }
    float elapsed;
    int generation;
    bool alive = particleState(particleLayer, slot, phaseWord,
            particleLife, elapsed, generation);
    if (!alive) {
        hidePrimitive();
        return;
    }
    if (anchoredCore) {
        particleLife = 0.25;
        generation = 0;
    }
    particleRandom = randomSigned(phaseWord, particleIndex, generation);
    float scale = particleScale(flameStrength);
    float size = spark ? mix(SPARK_SIZE_RANGE.x, SPARK_SIZE_RANGE.y, severity(flameStrength))
            : (particleLayer == 0 ? 0.86
            : (particleLayer == 1 ? 0.68
            : (particleLayer == 2 ? 0.42 : 0.32))) * scale;
    float smokeExpansion = mix(1.0, 1.24,
            smoothRange(0.42, 1.0, particleLife));
    float quadScale = particleLayer == 3 ? GLOW_QUAD_SCALE
            : (smokePass ? smokeExpansion : 1.0);
    float viewportScale = flameViewportScale(viewPosition, flameHeight(flameStrength));
    float widthEnvelope = spark ? 1.0
            : flameWidthEnvelope(particleLife, flameStrength);
    vec2 offset;
    if (spark) {
        vec2 sparkRandoms = vec2(particleRandom,
                randomSigned(phaseWord, particleIndex + SPARK_PARTICLE_COUNT, generation));
        offset = sparkOffset(elapsed, sparkRandoms, phaseWord);
    } else {
        float sway = anchoredCore ? 0.0
                : lateralSway(particleLife, particleRandom, flameStrength);
        float rise = anchoredCore ? 0.0
                : elapsed * riseDistance(flameStrength);
        offset = vec2((particleRandom * 0.04 + sway) * scale * widthEnvelope, rise);
    }
    float visibleRadius = particleLayer == 2 ? 0.22
            : (particleLayer == 3 ? GLOW_BODY_RADIUS : (spark ? 0.50 : 0.36));
    float baseGap = particleLayer == 1 ? 0.08
            : (particleLayer == 2 ? 0.18
            : (particleLayer == 3 ? 0.12 : (spark ? 0.03 : 0.0)));
    float anchorRadius = particleLayer == 3
            ? visibleRadius : quadScale * visibleRadius;
    vec2 aspect = spark ? SPARK_ASPECT : PARTICLE_ASPECT;
    offset.y += size * aspect.y * (anchorRadius + baseGap);
    vec2 quadOffset = (localCoord - 0.5) * size * aspect * quadScale;
    quadOffset.x *= widthEnvelope;
    viewPosition.xy += (offset + quadOffset) * viewportScale;
    particleOpacity = spark
            ? 1.0 - smoothRange(SPARK_FADE_START, SPARK_FADE_END, distanceToCamera)
            : (smokePass
            ? 1.0 - smoothRange(SMOKE_FADE_START, SMOKE_FADE_END, distanceToCamera)
            : 1.0);
    gl_Position = ProjMat * viewPosition;
}
void main() {
    bool label = RenderPass == LABEL_PASS;
    int batchMarkerIndex = label ? gl_InstanceID : gl_InstanceID / SlotsPerMarker;
    int markerIndex = MarkerBase + batchMarkerIndex;
    int slot = label ? 0 : gl_InstanceID - batchMarkerIndex * SlotsPerMarker;
    FlameMarker marker = markerAt(markerIndex);
    vec3 markerPosition = mix(marker.previousPosition, marker.position,
            clamp(PartialTick, 0.0, 1.0));
    vec4 viewPosition = ModelViewMat * vec4(markerPosition, 1.0);
    float distanceToCamera = length(viewPosition.xyz);
    localCoord = label ? vec2(Position.x, 1.0 - Position.y) : Position.xy;
    vertexColor = vec4(label ? vec3(1.0) : marker.color,
            1.0 - smoothRange(FadeDistances.x, FadeDistances.y, distanceToCamera));
    primitiveKind = label ? 1 : 0;
    labelValue = 0;
    labelChars = 0;
    flameStrength = marker.packedStrength / StrengthWordScale;
    particleRandom = 0.0;
    particleLayer = 0;
    particleOpacity = 1.0;
    particleLife = 0.0;
    if (label) {
        renderLabelVertex(viewPosition, distanceToCamera, marker);
        return;
    }
    renderParticleVertex(viewPosition, distanceToCamera,
            ParticleStart + slot, marker.phaseWord);
}
