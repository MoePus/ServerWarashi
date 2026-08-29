package com.moepus.serverwarashi.client.world;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

final class WorldPerfObjectBuffers implements AutoCloseable {
    static final int MAX_MARKER_COUNT = 4_096;
    static final int TEXELS_PER_MARKER = 3;
    static final int FLOATS_PER_MARKER = TEXELS_PER_MARKER * 4;
    private static final int QUAD_VERTEX_COUNT = 4;
    private static final int FLOAT_BYTES = Float.BYTES;
    private static final int MARKER_TEXTURE_UNIT = 2;
    private static final String MARKER_SAMPLER = "MarkerData";

    private int quadVao = -1;
    private int quadVertexBuffer = -1;
    private int markerBuffer = -1;
    private int markerTexture = -1;
    private FloatBuffer markerStaging;
    private int markerCapacity;
    private int markerCount;
    private ShaderInstance samplerShader;
    private int markerSamplerLocation = -1;

    void replace(List<Entry> entries) {
        RenderSystem.assertOnRenderThread();
        int entryCount = Math.min(entries.size(), MAX_MARKER_COUNT);
        if (entryCount == 0) {
            clear();
            return;
        }
        ensureMarkerCapacity(entryCount);
        ensureGpuObjects();
        markerStaging.clear();
        for (int index = 0; index < entryCount; index++) {
            encodeMarker(entries.get(index), markerStaging);
        }
        markerStaging.flip();
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, markerBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, markerStaging, GL15.GL_STREAM_DRAW);
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
        markerCount = entryCount;
    }

    void clear() {
        markerCount = 0;
    }

    void draw(Batch batch, DrawContext context) {
        if (markerCount == 0) {
            return;
        }
        ShaderInstance shader = context.shader();
        setDrawUniforms(batch, context);
        shader.setSampler("Sampler0", RenderSystem.getShaderTexture(0));
        shader.setSampler("Sampler1", RenderSystem.getShaderTexture(1));
        shader.apply();
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + MARKER_TEXTURE_UNIT);
        int previousMarkerTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        try {
            bindMarkerData(shader);
            GL30.glBindVertexArray(quadVao);
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, QUAD_VERTEX_COUNT,
                    batch.instancesFor(markerCount));
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, previousMarkerTexture);
            GlStateManager._activeTexture(previousActiveTexture);
            shader.clear();
            BufferUploader.invalidate();
        }
    }

    private void setDrawUniforms(Batch batch, DrawContext context) {
        ShaderInstance shader = context.shader();
        shader.safeGetUniform("RenderPass").set(batch.renderPass());
        shader.safeGetUniform("ParticleStart").set(batch.particleStart());
        shader.safeGetUniform("SlotsPerMarker").set(batch.slotsPerMarker());
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(context.modelView());
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(context.projection());
        }
    }

    private void bindMarkerData(ShaderInstance shader) {
        if (samplerShader != shader) {
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            markerSamplerLocation = GL20.glGetUniformLocation(program, MARKER_SAMPLER);
            if (markerSamplerLocation < 0) {
                throw new IllegalStateException("Missing shader sampler " + MARKER_SAMPLER);
            }
            samplerShader = shader;
        }
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, markerTexture);
        GL20.glUniform1i(markerSamplerLocation, MARKER_TEXTURE_UNIT);
    }

    private void ensureMarkerCapacity(int required) {
        if (required <= markerCapacity) {
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
        markerCapacity = nextCapacity;
    }

    private void ensureGpuObjects() {
        if (quadVao >= 0) {
            return;
        }
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousMarkerTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        try {
            createQuad();
            markerBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, markerBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
                    (long) FLOATS_PER_MARKER * FLOAT_BYTES, GL15.GL_STREAM_DRAW);
            markerTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, markerTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, markerBuffer);
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, previousMarkerTexture);
            BufferUploader.invalidate();
        }
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

    static void encodeMarker(Entry entry, FloatBuffer target) {
        WorldEntityFlameClusters.Marker marker = entry.marker();
        int rgb = WorldOverlayMath.colorRgb(marker.effectiveSamples());
        int strength = WorldOverlayMath.packFlameStrength(marker.effectiveSamples());
        WorldOverlayMath.PackedLabel label = WorldOverlayMath.packLabel(
                WorldOverlayMath.roundedMicros(marker.metric()));
        target.put((float) entry.x()).put((float) (entry.y() + 0.04D))
                .put((float) entry.z()).put((float) strength);
        target.put((rgb >> 16 & 0xFF) / 255.0F).put((rgb >> 8 & 0xFF) / 255.0F)
                .put((rgb & 0xFF) / 255.0F)
                .put((float) WorldFlameParticleLayout.phaseWord(marker.phase()));
        target.put((float) label.lowWord()).put((float) label.markedHighWord())
                .put(WorldOverlayMath.packLabelStrength(marker.effectiveSamples()) / 255.0F)
                .put(0.0F);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (markerTexture >= 0) {
            GL11.glDeleteTextures(markerTexture);
        }
        if (markerBuffer >= 0) {
            GL15.glDeleteBuffers(markerBuffer);
        }
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

    private void reset() {
        quadVao = -1;
        quadVertexBuffer = -1;
        markerBuffer = -1;
        markerTexture = -1;
        markerStaging = null;
        markerCapacity = 0;
        markerCount = 0;
        samplerShader = null;
        markerSamplerLocation = -1;
    }

    enum Batch {
        SMOKE(2, WorldFlameParticleLayout.ORANGE_PARTICLE_START,
                WorldFlameParticleLayout.ORANGE_PARTICLE_COUNT),
        NORMAL(0, 0, WorldFlameParticleLayout.NORMAL_PARTICLE_COUNT),
        GLOW(1, WorldFlameParticleLayout.GLOW_PARTICLE_START,
                WorldFlameParticleLayout.GLOW_PARTICLE_COUNT),
        SPARK(4, WorldFlameParticleLayout.SPARK_PARTICLE_START,
                WorldFlameParticleLayout.SPARK_PARTICLE_COUNT),
        LABEL(3, 0, 1);

        private final int renderPass;
        private final int particleStart;
        private final int slotsPerMarker;

        Batch(int renderPass, int particleStart, int slotsPerMarker) {
            this.renderPass = renderPass;
            this.particleStart = particleStart;
            this.slotsPerMarker = slotsPerMarker;
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
    }

    record Entry(WorldEntityFlameClusters.Marker marker,
                 double x,
                 double y,
                 double z,
                 double distanceSquared) {
        static Entry from(WorldEntityFlameClusters.Marker marker,
                          Vec3 camera,
                          double distanceSquared) {
            return new Entry(marker, marker.x() - camera.x, marker.y() - camera.y,
                    marker.z() - camera.z, distanceSquared);
        }
    }

    record DrawContext(ShaderInstance shader, Matrix4f modelView, Matrix4f projection) {
    }
}
