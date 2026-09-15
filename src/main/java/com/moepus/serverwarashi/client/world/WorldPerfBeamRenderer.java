package com.moepus.serverwarashi.client.world;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moepus.serverwarashi.client.heatmap.DangerousChunkModel;
import com.moepus.serverwarashi.client.state.ClientPerfMapState;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL14;

import java.util.List;

final class WorldPerfBeamRenderer {
    static final double CORE_HALF_WIDTH = 0.42D;
    private static final double GLOW_HALF_WIDTH = 1.8D;
    private static final double ORIGIN_GRID_SIZE = 4_096.0D;
    private static final int BEAM_EXTENSION = 1_024;
    private static final long WEAK_REFRESH_INTERVAL_TICKS = 5L;
    private final Matrix4f modelView = new Matrix4f();
    private VertexBuffer buffer;
    private List<DangerousChunkModel.Chunk> bufferedChunks = List.of();
    private long reportRevision = Long.MIN_VALUE;
    private long weakRevision = Long.MIN_VALUE;
    private long lastWeakRefreshTick = Long.MIN_VALUE;
    private String dimension;
    private int minimumY;
    private int maximumY;
    private double originX = Double.NaN;
    private double originZ = Double.NaN;

    void render(RenderLevelStageEvent event,
                ClientLevel level,
                ClientPerfMapState.Snapshot report,
                ClientWeakChunkState.Snapshot weak) {
        ShaderInstance shader = WorldPerfOverlayShaders.beamShader();
        if (shader == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        refresh(level, report, weak, camera);
        if (buffer == null) {
            return;
        }
        shader.safeGetUniform("FadeDistances").set(
                (float) WorldOverlayMath.BEAM_FADE_START,
                (float) WorldOverlayMath.BEAM_FADE_END);
        shader.safeGetUniform("CameraOffset").set(
                (float) (camera.x - originX), (float) (camera.z - originZ));
        modelView.set(event.getModelViewMatrix()).translate(
                (float) (originX - camera.x),
                (float) -camera.y,
                (float) (originZ - camera.z));
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            buffer.bind();
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView,
                    event.getProjectionMatrix(), Minecraft.getInstance().getWindow());
            shader.apply();
            try {
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                buffer.draw();
            } finally {
                shader.clear();
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private void refresh(ClientLevel level,
                         ClientPerfMapState.Snapshot report,
                         ClientWeakChunkState.Snapshot weak,
                         Vec3 camera) {
        String currentDimension = level.dimension().location().toString();
        int nextMinimumY = level.getMinBuildHeight();
        int nextMaximumY = level.getMaxBuildHeight() + BEAM_EXTENSION;
        double nextOriginX = localOrigin(camera.x);
        double nextOriginZ = localOrigin(camera.z);
        boolean originChanged = nextOriginX != originX || nextOriginZ != originZ;
        boolean layoutChanged = !currentDimension.equals(dimension)
                || minimumY != nextMinimumY || maximumY != nextMaximumY
                || originChanged;
        boolean reportChanged = report.revision() != reportRevision;
        boolean weakChanged = weak.revision() != weakRevision;
        if (!layoutChanged && !reportChanged && !weakChanged) {
            return;
        }
        long currentTick = level.getGameTime();
        if (!layoutChanged && !reportChanged && weakChanged
                && currentTick >= lastWeakRefreshTick
                && currentTick - lastWeakRefreshTick < WEAK_REFRESH_INTERVAL_TICKS) {
            return;
        }
        reportRevision = report.revision();
        weakRevision = weak.revision();
        lastWeakRefreshTick = currentTick;
        dimension = currentDimension;
        minimumY = nextMinimumY;
        maximumY = nextMaximumY;
        originX = nextOriginX;
        originZ = nextOriginZ;
        DangerousChunkModel.WeakChunks weakChunks = weak.dimension() == null ? null
                : new DangerousChunkModel.WeakChunks(weak.dimension(), weak.chunks());
        List<DangerousChunkModel.Chunk> next = DangerousChunkModel.build(
                report.report(), weakChunks, currentDimension);
        if (originChanged || !next.equals(bufferedChunks)) {
            replaceBuffer(next);
            bufferedChunks = next;
        }
    }

    private void replaceBuffer(List<DangerousChunkModel.Chunk> chunks) {
        if (chunks.isEmpty()) {
            closeBuffer();
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (DangerousChunkModel.Chunk chunk : chunks) {
            int argb = DangerousChunkModel.argb(chunk.color());
            float x = localCoordinate((chunk.chunkX() << 4) + 8.0D, originX);
            float z = localCoordinate((chunk.chunkZ() << 4) + 8.0D, originZ);
            addCross(builder, x, z, CORE_HALF_WIDTH, argb, 104);
            addCross(builder, x, z, GLOW_HALF_WIDTH, argb, 30);
        }
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        try {
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
        } catch (RuntimeException | Error exception) {
            closeBuffer();
            throw exception;
        } finally {
            VertexBuffer.unbind();
        }
    }

    private void addCross(BufferBuilder builder,
                          double x,
                          double z,
                          double halfWidth,
                          int argb,
                          int alpha) {
        addPlane(builder, x - halfWidth, minimumY, z, x + halfWidth, maximumY, z, argb, alpha);
        addPlane(builder, x, minimumY, z - halfWidth, x, maximumY, z + halfWidth, argb, alpha);
    }

    private static void addPlane(BufferBuilder builder,
                                 double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ,
                                 int argb, int alpha) {
        vertex(builder, minX, minY, minZ, argb, alpha);
        vertex(builder, maxX, minY, maxZ, argb, alpha);
        vertex(builder, maxX, maxY, maxZ, argb, alpha);
        vertex(builder, minX, maxY, minZ, argb, alpha);
    }

    private static void vertex(BufferBuilder builder,
                               double x, double y, double z,
                               int argb, int alpha) {
        builder.addVertex((float) x, (float) y, (float) z).setColor(
                argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, alpha);
    }

    static double localOrigin(double cameraCoordinate) {
        return Math.floor(cameraCoordinate / ORIGIN_GRID_SIZE) * ORIGIN_GRID_SIZE;
    }

    static float localCoordinate(double worldCoordinate, double originCoordinate) {
        return (float) (worldCoordinate - originCoordinate);
    }

    void close() {
        closeBuffer();
        bufferedChunks = List.of();
        reportRevision = Long.MIN_VALUE;
        weakRevision = Long.MIN_VALUE;
        lastWeakRefreshTick = Long.MIN_VALUE;
        dimension = null;
        originX = Double.NaN;
        originZ = Double.NaN;
    }

    private void closeBuffer() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }
}
