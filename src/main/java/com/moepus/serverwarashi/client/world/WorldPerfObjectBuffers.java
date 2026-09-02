package com.moepus.serverwarashi.client.world;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

final class WorldPerfObjectBuffers implements AutoCloseable {
    static final int MAX_MARKER_COUNT = PerfReportLimits.MAX_WORLD_HOTSPOT_ENTRIES;
    static final int TEXELS_PER_MARKER = 4;
    static final int FLOATS_PER_MARKER = TEXELS_PER_MARKER * 4;
    private static final int QUAD_VERTEX_COUNT = 4;
    private static final int FLOAT_BYTES = Float.BYTES;
    private static final int MARKER_TEXTURE_UNIT = 2;
    private static final String MARKER_SAMPLER = "MarkerData";

    private final MarkerBuffer staticMarkers = new MarkerBuffer();
    private final MarkerBuffer dynamicMarkers = new MarkerBuffer();
    private int quadVao = -1;
    private int quadVertexBuffer = -1;
    private FloatBuffer markerStaging;
    private int stagingCapacity;
    private ShaderInstance samplerShader;
    private int markerSamplerLocation = -1;

    void replace(Source source,
                 List<WorldEntityFlameClusters.Marker> markers,
                 Vec3 camera) {
        RenderSystem.assertOnRenderThread();
        if (markers.size() > MAX_MARKER_COUNT) {
            throw new IllegalArgumentException("Too many world overlay markers: " + markers.size());
        }
        WorldPerfMarkerBuckets.Batch ordered = WorldPerfMarkerBuckets.order(markers, camera);
        MarkerBuffer target = buffer(source);
        if (ordered.size() == 0) {
            target.clear();
            return;
        }
        ensureStagingCapacity(ordered.size());
        ensureGpuObjects(target);
        target.originX = camera.x;
        target.originZ = camera.z;
        UploadOrigin origin = new UploadOrigin(target.originX, target.originZ);
        markerStaging.clear();
        for (WorldEntityFlameClusters.Marker marker : ordered.markers()) {
            encodeMarker(marker, origin, markerStaging);
        }
        markerStaging.flip();
        upload(target);
        target.markerCount = ordered.size();
        target.sparkMarkerBase = ordered.sparkBase();
        target.labelMarkerBase = ordered.labelBase();
    }

    private void upload(MarkerBuffer target) {
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, target.markerBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, markerStaging, GL15.GL_STREAM_DRAW);
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    void clear() {
        staticMarkers.clear();
        dynamicMarkers.clear();
    }

    void clear(Source source) {
        buffer(source).clear();
    }

    boolean isEmpty() {
        return staticMarkers.markerCount == 0 && dynamicMarkers.markerCount == 0;
    }

    boolean isEmpty(Source source) {
        return buffer(source).markerCount == 0;
    }

    double originX(Source source) {
        return buffer(source).originX;
    }

    double originZ(Source source) {
        return buffer(source).originZ;
    }

    void draw(Source source, Batch batch, DrawContext context) {
        MarkerBuffer target = buffer(source);
        int markerBase = markerBase(target, batch);
        int drawMarkerCount = target.markerCount - markerBase;
        if (drawMarkerCount == 0) {
            return;
        }
        ShaderInstance shader = context.shader();
        setDrawUniforms(batch, context, markerBase);
        shader.setSampler("Sampler0", RenderSystem.getShaderTexture(0));
        shader.setSampler("Sampler1", RenderSystem.getShaderTexture(1));
        shader.apply();
        batch.applyBlend();
        drawInstances(target, shader, batch.instancesFor(drawMarkerCount));
    }

    private void drawInstances(MarkerBuffer target, ShaderInstance shader, int instanceCount) {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + MARKER_TEXTURE_UNIT);
        int previousMarkerTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        try {
            bindMarkerData(target, shader);
            GL30.glBindVertexArray(quadVao);
            GL31.glDrawArraysInstanced(
                    GL11.GL_TRIANGLE_STRIP, 0, QUAD_VERTEX_COUNT, instanceCount);
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, previousMarkerTexture);
            GlStateManager._activeTexture(previousActiveTexture);
            shader.clear();
            BufferUploader.invalidate();
        }
    }

    private static int markerBase(MarkerBuffer target, Batch batch) {
        return switch (batch) {
            case SPARK -> target.sparkMarkerBase;
            case LABEL -> target.labelMarkerBase;
            default -> 0;
        };
    }

    private static void setDrawUniforms(Batch batch, DrawContext context, int markerBase) {
        ShaderInstance shader = context.shader();
        shader.safeGetUniform("RenderPass").set(batch.renderPass());
        shader.safeGetUniform("ParticleStart").set(batch.particleStart());
        shader.safeGetUniform("SlotsPerMarker").set(batch.slotsPerMarker());
        shader.safeGetUniform("MarkerBase").set(markerBase);
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(context.modelView());
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(context.projection());
        }
    }

    private void bindMarkerData(MarkerBuffer target, ShaderInstance shader) {
        if (samplerShader != shader) {
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            markerSamplerLocation = GL20.glGetUniformLocation(program, MARKER_SAMPLER);
            if (markerSamplerLocation < 0) {
                throw new IllegalStateException("Missing shader sampler " + MARKER_SAMPLER);
            }
            samplerShader = shader;
        }
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, target.markerTexture);
        GL20.glUniform1i(markerSamplerLocation, MARKER_TEXTURE_UNIT);
    }

    private void ensureStagingCapacity(int required) {
        if (required <= stagingCapacity) {
            return;
        }
        int nextCapacity = Math.max(16, Integer.highestOneBit(required - 1) << 1);
        int maximumTexels = GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE);
        if ((long) nextCapacity * TEXELS_PER_MARKER > maximumTexels) {
            throw new IllegalStateException("Marker TBO requires "
                    + (long) nextCapacity * TEXELS_PER_MARKER
                    + " texels, GL limit is " + maximumTexels);
        }
        FloatBuffer nextStaging = MemoryUtil.memAllocFloat(nextCapacity * FLOATS_PER_MARKER);
        MemoryUtil.memFree(markerStaging);
        markerStaging = nextStaging;
        stagingCapacity = nextCapacity;
    }

    private void ensureGpuObjects(MarkerBuffer target) {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousMarkerTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        try {
            if (quadVao < 0) {
                createQuad();
            }
            if (target.markerBuffer < 0) {
                createMarkerBuffer(target);
            }
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, previousMarkerTexture);
            BufferUploader.invalidate();
        }
    }

    private void createMarkerBuffer(MarkerBuffer target) {
        target.markerBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, target.markerBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
                (long) FLOATS_PER_MARKER * FLOAT_BYTES, GL15.GL_STREAM_DRAW);
        target.markerTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, target.markerTexture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, target.markerBuffer);
    }

    private void createQuad() {
        quadVao = GL30.glGenVertexArrays();
        quadVertexBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(quadVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVertexBuffer);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer vertices = stack.floats(
                    0.0F, 0.0F, 0.0F,
                    1.0F, 0.0F, 0.0F,
                    0.0F, 1.0F, 0.0F,
                    1.0F, 1.0F, 0.0F);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        }
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false,
                3 * FLOAT_BYTES, 0L);
    }

    static void encodeMarker(WorldEntityFlameClusters.Marker marker,
                             UploadOrigin origin,
                             FloatBuffer target) {
        int rgb = WorldOverlayMath.colorRgb(marker.flameStrength());
        int strength = WorldOverlayMath.packFlameStrength(marker.flameStrength());
        WorldOverlayMath.PackedLabel label = WorldOverlayMath.packLabel(
                WorldOverlayMath.roundedMicros(marker.metric()));
        target.put((float) (marker.x() - origin.x())).put((float) (marker.y() + 0.04D))
                .put((float) (marker.z() - origin.z())).put((float) strength);
        target.put((rgb >> 16 & 0xFF) / 255.0F).put((rgb >> 8 & 0xFF) / 255.0F)
                .put((rgb & 0xFF) / 255.0F)
                .put((float) WorldFlameParticleLayout.phaseWord(marker.phase()));
        target.put((float) label.lowWord()).put((float) label.markedHighWord())
                .put(WorldOverlayMath.packLabelStrength(marker.flameStrength()) / 255.0F)
                .put(0.0F);
        target.put((float) (marker.previousX() - origin.x()))
                .put((float) (marker.previousY() + 0.04D))
                .put((float) (marker.previousZ() - origin.z())).put(0.0F);
    }

    private MarkerBuffer buffer(Source source) {
        return source == Source.STATIC ? staticMarkers : dynamicMarkers;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        deleteMarkerBuffer(staticMarkers);
        deleteMarkerBuffer(dynamicMarkers);
        if (quadVertexBuffer >= 0) {
            GL15.glDeleteBuffers(quadVertexBuffer);
        }
        if (quadVao >= 0) {
            GL30.glDeleteVertexArrays(quadVao);
        }
        if (markerStaging != null) {
            MemoryUtil.memFree(markerStaging);
        }
        reset();
    }

    private static void deleteMarkerBuffer(MarkerBuffer target) {
        if (target.markerTexture >= 0) {
            GL11.glDeleteTextures(target.markerTexture);
        }
        if (target.markerBuffer >= 0) {
            GL15.glDeleteBuffers(target.markerBuffer);
        }
        target.reset();
    }

    private void reset() {
        quadVao = -1;
        quadVertexBuffer = -1;
        markerStaging = null;
        stagingCapacity = 0;
        samplerShader = null;
        markerSamplerLocation = -1;
    }

    enum Source {
        STATIC,
        DYNAMIC
    }

    enum Batch {
        SMOKE(2, WorldFlameParticleLayout.ORANGE_PARTICLE_START,
                WorldFlameParticleLayout.ORANGE_PARTICLE_COUNT, BlendMode.PREMULTIPLIED),
        NORMAL(0, 0, WorldFlameParticleLayout.NORMAL_PARTICLE_COUNT,
                BlendMode.PREMULTIPLIED),
        GLOW(1, WorldFlameParticleLayout.GLOW_PARTICLE_START,
                WorldFlameParticleLayout.GLOW_PARTICLE_COUNT, BlendMode.ADDITIVE),
        SPARK(4, WorldFlameParticleLayout.SPARK_PARTICLE_START,
                WorldFlameParticleLayout.SPARK_PARTICLE_COUNT, BlendMode.ADDITIVE),
        LABEL(3, 0, 1, BlendMode.PREMULTIPLIED);

        private final int renderPass;
        private final int particleStart;
        private final int slotsPerMarker;
        private final BlendMode blendMode;

        Batch(int renderPass, int particleStart, int slotsPerMarker, BlendMode blendMode) {
            this.renderPass = renderPass;
            this.particleStart = particleStart;
            this.slotsPerMarker = slotsPerMarker;
            this.blendMode = blendMode;
        }

        int instancesFor(int markers) {
            return Math.multiplyExact(markers, slotsPerMarker);
        }

        int renderPass() {
            return renderPass;
        }

        int particleStart() {
            return particleStart;
        }

        int slotsPerMarker() {
            return slotsPerMarker;
        }

        BlendMode blendMode() {
            return blendMode;
        }

        private void applyBlend() {
            blendMode.apply();
        }
    }

    enum BlendMode {
        PREMULTIPLIED(GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA),
        ADDITIVE(GlStateManager.DestFactor.ONE);

        private final GlStateManager.DestFactor colorDestination;

        BlendMode(GlStateManager.DestFactor colorDestination) {
            this.colorDestination = colorDestination;
        }

        private void apply() {
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, colorDestination,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }
    }

    record UploadOrigin(double x, double z) {
    }

    record DrawContext(ShaderInstance shader, Matrix4f modelView, Matrix4f projection) {
    }

    private static final class MarkerBuffer {
        private int markerBuffer = -1;
        private int markerTexture = -1;
        private int markerCount;
        private int sparkMarkerBase;
        private int labelMarkerBase;
        private double originX;
        private double originZ;

        private void clear() {
            markerCount = 0;
            sparkMarkerBase = 0;
            labelMarkerBase = 0;
        }

        private void reset() {
            markerBuffer = -1;
            markerTexture = -1;
            clear();
            originX = 0.0D;
            originZ = 0.0D;
        }
    }
}
