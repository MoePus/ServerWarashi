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
// Particle layout. Keep in sync with WorldFlameParticleLayout.
const int OUTER_PARTICLE_COUNT = 24;
const int BODY_PARTICLE_COUNT = 20;
const int CORE_PARTICLE_COUNT = 20;
const int SPARK_PARTICLE_COUNT = 12;
const int BODY_PARTICLE_START = OUTER_PARTICLE_COUNT;
const int CORE_PARTICLE_START = BODY_PARTICLE_START + BODY_PARTICLE_COUNT;
const int SPARK_PARTICLE_START = CORE_PARTICLE_START + CORE_PARTICLE_COUNT;
const int OUTER_LAYER = 0;
const int BODY_LAYER = 1;
const int CORE_LAYER = 2;
const int SPARK_LAYER = 3;
const int FLAME_PASS = 0;
const int SPARK_PASS = 1;
const int LABEL_PASS = 2;
const int MARKER_TEXELS = 4;
const float TAU = 6.28318530;
// Every emission cycle divides this period so particles stay continuous when the
// Java clock wraps ParticleTime and advances ParticleEpoch.
const float PARTICLE_CLOCK_PERIOD = 7.0;
const int OUTER_CYCLES_PER_PERIOD = 7;
const int BODY_CYCLES_PER_PERIOD = 10;
const int CORE_CYCLES_PER_PERIOD = 14;
const int SPARK_MIN_CYCLES_PER_PERIOD = 3;
const int SPARK_CYCLE_VARIANTS = 3;
// The flame is drawn on its own pixel grid: size in flame pixels, plus the world size of
// one flame pixel (0.0625 = one block texel). Higher severity grows both.
const vec2 FLAME_PIXELS_TALL = vec2(12.0, 30.0);
const vec2 FLAME_PIXELS_WIDE = vec2(8.0, 16.0);
const vec2 PIXEL_WORLD_SIZE = vec2(0.0625, 0.105);
// 1 snaps every square onto the flame pixel grid; 0 lets squares float freely.
const float PIXEL_SNAP = 1.0;
// Buoyancy: y = rise * (k*s + (1-k)*s^2), so the gas accelerates as it climbs.
const float RISE_LINEAR = 0.65;
// Entrainment pulls the flame toward its axis as it rises.
const float NECKING = 0.45;
// Shared centerline wave travelling up the flame, in cycles per clock period.
const int WAVE_CYCLES = 9;
const int WAVE_CYCLES_SECONDARY = 16;
const float WAVE_TRAVEL = 2.4;
const float WAVE_AMPLITUDE = 0.16;
// Puffing: particles born on a strong pulse rise higher and detach as tongues.
const int PUFF_CYCLES = 11;
const float PUFF_AMOUNT = 0.20;
// Squares shrink over their life in whole pixels, from their start size down to 1.
const float SHRINK_EXPONENT = 2.2;
// Soot below this temperature stops glowing, so the particle disappears.
const float BURNOUT_TEMPERATURE = 0.20;
// Hot bands lag in blue so every severity hue heats through yellow-white.
const vec3 HOT_BIAS = vec3(1.0, 1.0, 0.6);
// Distance LOD. Particle density drops after 16 blocks and settles at 32.
const float PARTICLE_LOD_START = 16.0;
const float PARTICLE_LOD_END = 32.0;
const float SPARK_FADE_START = 20.0;
const float SPARK_FADE_END = 24.0;
const float MIN_OUTER_COUNT = 16.0;
const float MIN_BODY_COUNT = 14.0;
const float MIN_CORE_COUNT = 14.0;
const float MIN_SPARK_COUNT = 3.0;
const float FAR_OUTER_COUNT = 4.0;
const float FAR_BODY_COUNT = 3.0;
const float FAR_CORE_COUNT = 3.0;
// Sparks are lofted by the plume, slowed by drag, then drift and twinkle out.
const vec2 SPARK_DURATION = vec2(0.9, 1.6);
const vec2 SPARK_SPAWN_HEIGHT = vec2(0.25, 0.60);
const vec2 SPARK_LAUNCH_SPEED = vec2(1.3, 2.2);
const float SPARK_DRIFT_SPEED = 0.30;
const float SPARK_DRAG_TIME = 0.45;
const float SPARK_LATERAL_SPEED = 0.30;
const float SPARK_WOBBLE = 0.05;
const float SPARK_POP_LIFE = 0.08;
const float SPARK_TWINKLE_START = 0.60;
const float SPARK_TWINKLE_RATE = 14.0;
const float SPARK_TWINKLE_OFF = 0.40;
const float LABEL_GLYPH_ASPECT = 0.75;
out vec2 localCoord;
out vec4 vertexColor;
flat out int primitiveKind;
flat out int labelValue;
flat out int labelChars;
flat out float particlePixels;
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
struct Particle {
    vec2 center;
    float pixels;
    vec4 color;
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
float flamePixelsTall(float strength) {
    return mix(FLAME_PIXELS_TALL.x, FLAME_PIXELS_TALL.y, severity(strength));
}
float flamePixelsWide(float strength) {
    return mix(FLAME_PIXELS_WIDE.x, FLAME_PIXELS_WIDE.y, severity(strength));
}
float pixelWorldSize(float strength) {
    return mix(PIXEL_WORLD_SIZE.x, PIXEL_WORLD_SIZE.y, severity(strength));
}
float flameHeight(float strength) {
    return flamePixelsTall(strength) * pixelWorldSize(strength);
}
float flameViewportScale(vec4 viewPosition, float height) {
    vec4 clipPosition = ProjMat * viewPosition;
    float projectedHeight = max(abs(ProjMat[1][1]) * height, 0.001);
    float maximumHeight = 2.0 * MaxFlameViewportFraction * abs(clipPosition.w);
    return min(1.0, maximumHeight / projectedHeight);
}
int layerForParticle(int particleIndex) {
    if (particleIndex < BODY_PARTICLE_START) return OUTER_LAYER;
    if (particleIndex < CORE_PARTICLE_START) return BODY_LAYER;
    if (particleIndex < SPARK_PARTICLE_START) return CORE_LAYER;
    return SPARK_LAYER;
}
int layerStart(int layer) {
    if (layer == OUTER_LAYER) return 0;
    if (layer == BODY_LAYER) return BODY_PARTICLE_START;
    if (layer == CORE_LAYER) return CORE_PARTICLE_START;
    return SPARK_PARTICLE_START;
}
int layerCapacity(int layer) {
    if (layer == OUTER_LAYER) return OUTER_PARTICLE_COUNT;
    if (layer == BODY_LAYER) return BODY_PARTICLE_COUNT;
    if (layer == CORE_LAYER) return CORE_PARTICLE_COUNT;
    return SPARK_PARTICLE_COUNT;
}
float minimumParticles(int layer) {
    if (layer == OUTER_LAYER) return MIN_OUTER_COUNT;
    if (layer == BODY_LAYER) return MIN_BODY_COUNT;
    if (layer == CORE_LAYER) return MIN_CORE_COUNT;
    return MIN_SPARK_COUNT;
}
float farParticles(int layer) {
    if (layer == OUTER_LAYER) return FAR_OUTER_COUNT;
    if (layer == BODY_LAYER) return FAR_BODY_COUNT;
    return FAR_CORE_COUNT;
}
int visibleParticles(int layer, float strength, float distanceToCamera) {
    float nearCount = mix(minimumParticles(layer), float(layerCapacity(layer)),
            severity(strength));
    if (layer == SPARK_LAYER) {
        float sparkRemaining = 1.0 - smoothRange(PARTICLE_LOD_START, SPARK_FADE_END,
                distanceToCamera);
        return int(floor(nearCount * sparkRemaining * sparkRemaining + 0.5));
    }
    float lodAmount = smoothRange(PARTICLE_LOD_START, PARTICLE_LOD_END, distanceToCamera);
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
    if (layer != CORE_LAYER) return selectedSlot(slot, count, capacity);
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
float random01(uint seed, uint salt) {
    uint bits = hashParticleSeed(seed ^ (salt * 0x27D4EB2Fu)) & 0x00FFFFFFu;
    return float(bits) / 16777215.0;
}
uint particleSeed(int phaseWord, int particleIndex, int generation) {
    uint seed = uint(phaseWord) * 0x632BE5ABu;
    seed ^= uint(particleIndex) * 0x9E3779B9u;
    seed ^= uint(generation) * 0x85EBCA6Bu;
    return hashParticleSeed(seed);
}
// Each slot owns a repeating emission window. Within it the particle is born after a
// random delay and lives a random duration, so emission never looks metronomic.
bool emitParticle(int cyclesPerPeriod, float offset, int phaseWord, int particleIndex,
        vec2 durationSeconds, out float life, out float age, out float birth,
        out uint seed) {
    float cycle = PARTICLE_CLOCK_PERIOD / float(cyclesPerPeriod);
    float clock = ParticleTime + offset * cycle;
    float window = floor(clock / cycle);
    int generation = int(window) + ParticleEpoch * cyclesPerPeriod;
    seed = particleSeed(phaseWord, particleIndex, generation);
    float duration = min(mix(durationSeconds.x, durationSeconds.y, random01(seed, 11u)),
            cycle);
    float delay = (cycle - duration) * random01(seed, 12u);
    age = clock - window * cycle - delay;
    life = age / duration;
    birth = ParticleTime - age;
    return age >= 0.0 && age < duration;
}
float markerPhase(int phaseWord) {
    return float(phaseWord) / float(ParticlePhaseBase);
}
float clockAngle(int cycles, float time) {
    return time / PARTICLE_CLOCK_PERIOD * float(cycles) * TAU;
}
// Lateral offset of the flame axis at a height (0 = base, 1 = tip), in flame widths.
float flameWave(float height, int phaseWord) {
    float phase = markerPhase(phaseWord) * TAU;
    float primary = sin(clockAngle(WAVE_CYCLES, ParticleTime) - height * WAVE_TRAVEL + phase);
    float secondary = sin(clockAngle(WAVE_CYCLES_SECONDARY, ParticleTime)
            - height * WAVE_TRAVEL * 1.7 + phase * 2.3);
    float growth = pow(clamp(height, 0.0, 1.4), 1.3);
    return (primary * 0.7 + secondary * 0.3) * growth * WAVE_AMPLITUDE;
}
vec3 heatColor(vec3 base, float exposure) {
    vec3 bias = exposure > 1.0 ? HOT_BIAS : vec3(1.0);
    return min(pow(base, vec3(1.4)) * exposure * bias, vec3(1.0));
}
// Five flat heat bands. Overexposing the severity tint pushes it through yellow toward
// white like a hot flame; cooler bands shift toward red like cooling soot.
vec3 flamePalette(vec3 base, float temperature) {
    if (temperature >= 0.86) return mix(heatColor(base, 6.0), vec3(1.0), 0.55);
    if (temperature >= 0.70) return heatColor(base, 2.6);
    if (temperature >= 0.52) return heatColor(base, 1.6);
    if (temperature >= 0.36) return heatColor(base, 1.0) * vec3(1.0, 0.92, 0.90);
    return heatColor(base, 0.70) * vec3(1.0, 0.72, 0.62);
}
int layerCyclesPerPeriod(int layer) {
    if (layer == OUTER_LAYER) return OUTER_CYCLES_PER_PERIOD;
    if (layer == BODY_LAYER) return BODY_CYCLES_PER_PERIOD;
    return CORE_CYCLES_PER_PERIOD;
}
float layerSpawnWidth(int layer) {
    if (layer == OUTER_LAYER) return 0.30;
    if (layer == BODY_LAYER) return 0.22;
    return 0.14;
}
vec2 layerRise(int layer) {
    if (layer == OUTER_LAYER) return vec2(0.80, 1.05);
    if (layer == BODY_LAYER) return vec2(0.60, 0.90);
    return vec2(0.25, 0.45);
}
float layerSize(int layer) {
    if (layer == OUTER_LAYER) return 0.46;
    if (layer == BODY_LAYER) return 0.40;
    return 0.32;
}
float layerHeat(int layer) {
    if (layer == OUTER_LAYER) return 0.56;
    if (layer == BODY_LAYER) return 0.76;
    return 0.89;
}
float layerCooling(int layer) {
    if (layer == OUTER_LAYER) return 0.42;
    if (layer == BODY_LAYER) return 0.46;
    return 0.30;
}
bool flameParticle(int layer, int slot, FlameMarker marker, float strength,
        float distanceToCamera, out Particle particle) {
    if (!selectedParticleSlot(layer, slot,
            visibleParticles(layer, strength, distanceToCamera))) {
        return false;
    }
    float tall = flamePixelsTall(strength);
    float wide = flamePixelsWide(strength);
    if (layer == CORE_LAYER && slot == 0) {
        // Anchored ember bed: a steady hot block resting on the base.
        float pixels = max(1.0, floor(wide * 0.30 + 0.5));
        particle = Particle(vec2(0.0, pixels * 0.5), pixels,
                vec4(flamePalette(marker.color, 0.80), 1.0));
        return true;
    }
    float offset = fract(markerPhase(marker.phaseWord) + float(layer) * 0.319
            + float(slot) / float(layerCapacity(layer)));
    int cycles = layerCyclesPerPeriod(layer);
    float cycle = PARTICLE_CLOCK_PERIOD / float(cycles);
    float life;
    float age;
    float birth;
    uint seed;
    if (!emitParticle(cycles, offset, marker.phaseWord, layerStart(layer) + slot,
            vec2(0.75, 1.0) * cycle, life, age, birth, seed)) {
        return false;
    }
    float temperature = layerHeat(layer) + (random01(seed, 5u) - 0.5) * 0.12
            - layerCooling(layer) * life;
    if (temperature < BURNOUT_TEMPERATURE) {
        return false;
    }
    float spread = (random01(seed, 1u) + random01(seed, 2u) - 1.0)
            * wide * layerSpawnWidth(layer);
    float puff = 1.0 + PUFF_AMOUNT * sin(clockAngle(PUFF_CYCLES, birth)
            + markerPhase(marker.phaseWord) * TAU);
    vec2 riseRange = layerRise(layer);
    float rise = tall * mix(riseRange.x, riseRange.y, random01(seed, 3u)) * puff;
    float y = rise * life * (RISE_LINEAR + (1.0 - RISE_LINEAR) * life);
    float startPixels = wide * layerSize(layer) * mix(0.85, 1.15, random01(seed, 4u));
    float pixels = max(1.0, floor(mix(startPixels, 1.0, pow(life, SHRINK_EXPONENT)) + 0.5));
    float x = spread * (1.0 - NECKING * life) + wide * flameWave(y / tall, marker.phaseWord);
    particle = Particle(vec2(x, y + pixels * 0.5), pixels,
            vec4(flamePalette(marker.color, temperature), 1.0));
    return true;
}
bool sparkParticle(int slot, FlameMarker marker, float strength, float distanceToCamera,
        out Particle particle) {
    if (!selectedSlot(slot, visibleParticles(SPARK_LAYER, strength, distanceToCamera),
            SPARK_PARTICLE_COUNT)) {
        return false;
    }
    float tall = flamePixelsTall(strength);
    float wide = flamePixelsWide(strength);
    // Each spark slot gets its own cadence so sparks never leave in synchronized bursts.
    uint slotSeed = hashParticleSeed(uint(marker.phaseWord) * 0x632BE5ABu
            ^ uint(slot) * 0x27D4EB2Fu);
    int cycles = SPARK_MIN_CYCLES_PER_PERIOD
            + int(slotSeed % uint(SPARK_CYCLE_VARIANTS));
    float life;
    float age;
    float birth;
    uint seed;
    if (!emitParticle(cycles, random01(slotSeed, 7u), marker.phaseWord,
            SPARK_PARTICLE_START + slot, SPARK_DURATION, life, age, birth, seed)) {
        return false;
    }
    if (life > SPARK_TWINKLE_START
            && random01(seed, uint(floor(age * SPARK_TWINKLE_RATE)) + 32u) < SPARK_TWINKLE_OFF) {
        return false;
    }
    float drag = SPARK_DRAG_TIME * (1.0 - exp(-age / SPARK_DRAG_TIME));
    float launch = mix(SPARK_LAUNCH_SPEED.x, SPARK_LAUNCH_SPEED.y, random01(seed, 3u)) * tall;
    float drift = SPARK_DRIFT_SPEED * tall;
    float y = tall * mix(SPARK_SPAWN_HEIGHT.x, SPARK_SPAWN_HEIGHT.y, random01(seed, 2u))
            + drift * age + (launch - drift) * drag;
    float x = (random01(seed, 1u) * 2.0 - 1.0) * wide * 0.22
            + (random01(seed, 4u) * 2.0 - 1.0) * SPARK_LATERAL_SPEED * tall * drag
            + sin(TAU * 1.3 * age + random01(seed, 5u) * TAU) * SPARK_WOBBLE * tall * age;
    float pixels = life < SPARK_POP_LIFE && random01(seed, 6u) > 0.5 ? 2.0 : 1.0;
    float temperature = 0.95 - 0.45 * life;
    float alpha = 1.0 - smoothRange(SPARK_FADE_START, SPARK_FADE_END, distanceToCamera);
    particle = Particle(vec2(x, y), pixels,
            vec4(flamePalette(marker.color, temperature), alpha));
    return true;
}
void hidePrimitive() {
    vertexColor = vec4(0.0);
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}
vec2 snapToPixelGrid(vec2 center, float pixels) {
    vec2 corner = floor(center - pixels * 0.5 + 0.5);
    return mix(center, corner + pixels * 0.5, PIXEL_SNAP);
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
void renderParticleVertex(vec4 viewPosition, float distanceToCamera, int particleIndex,
        FlameMarker marker, float strength) {
    int layer = layerForParticle(particleIndex);
    int slot = particleIndex - layerStart(layer);
    Particle particle;
    bool alive = false;
    if (RenderPass == SPARK_PASS) {
        alive = layer == SPARK_LAYER
                && sparkParticle(slot, marker, strength, distanceToCamera, particle);
    } else if (RenderPass == FLAME_PASS) {
        alive = layer != SPARK_LAYER
                && flameParticle(layer, slot, marker, strength, distanceToCamera, particle);
    }
    if (!alive) {
        hidePrimitive();
        return;
    }
    float scale = pixelWorldSize(strength)
            * flameViewportScale(viewPosition, flameHeight(strength));
    vec2 center = snapToPixelGrid(particle.center, particle.pixels);
    viewPosition.xy += (center + (localCoord - 0.5) * particle.pixels) * scale;
    vertexColor = vec4(particle.color.rgb, particle.color.a * vertexColor.a);
    particlePixels = particle.pixels;
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
    particlePixels = 1.0;
    if (label) {
        renderLabelVertex(viewPosition, distanceToCamera, marker);
        return;
    }
    renderParticleVertex(viewPosition, distanceToCamera, ParticleStart + slot, marker,
            marker.packedStrength / StrengthWordScale);
}
