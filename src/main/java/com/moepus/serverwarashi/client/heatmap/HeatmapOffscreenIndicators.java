package com.moepus.serverwarashi.client.heatmap;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moepus.serverwarashi.api.client.map.MapRenderContext;
import com.moepus.serverwarashi.client.state.ClientPerfMapState;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;

public final class HeatmapOffscreenIndicators {
    private static final int DIRECTION_BUCKETS = 16;
    private static final int INDICATOR_PASS = 3;
    private static final int EDGE_INSET = 20;
    private static final float HALF_TANGENT = 17.0F;
    private static final float HALF_RADIAL = 10.0F;
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static long cachedReportRevision = Long.MIN_VALUE;
    private static long cachedWeakRevision = Long.MIN_VALUE;
    private static long candidateRevision;
    private static long bufferedRevision = Long.MIN_VALUE;
    private static String cachedDimension;
    private static List<DangerousChunkModel.Chunk> dangerousChunks = List.of();
    private static RenderContext bufferedContext;
    private static VertexBuffer indicatorBuffer;

    private HeatmapOffscreenIndicators() {
    }

    public static void render(GuiGraphics graphics, RenderContext context) {
        refreshDangerousChunks(context.dimension());
        if (candidateRevision != bufferedRevision || !context.equals(bufferedContext)) {
            replaceBuffer(plan(dangerousChunks, context));
            bufferedRevision = candidateRevision;
            bufferedContext = context;
        }
        draw(graphics);
    }

    static List<Indicator> plan(PerfReportData report,
                                WeakChunkSnapshot weakSnapshot,
                                RenderContext context) {
        return plan(DangerousChunkModel.build(report,
                DangerousChunkModel.WeakChunks.from(weakSnapshot), context.dimension()), context);
    }

    private static List<Indicator> plan(List<DangerousChunkModel.Chunk> chunks, RenderContext context) {
        DangerousChunkModel.Chunk[] selected = new DangerousChunkModel.Chunk[DIRECTION_BUCKETS];
        double[] angles = new double[DIRECTION_BUCKETS];
        for (DangerousChunkModel.Chunk chunk : chunks) {
            if (context.visible().world().intersectsChunk(chunk.chunkX(), chunk.chunkZ())) {
                continue;
            }
            double angle = direction(chunk, context);
            int bucket = directionBucket(angle);
            if (selected[bucket] == null || chunk.priority() > selected[bucket].priority()) {
                selected[bucket] = chunk;
                angles[bucket] = angle;
            }
        }
        return indicators(selected, angles, context);
    }

    public static void close() {
        closeBuffer();
        cachedReportRevision = Long.MIN_VALUE;
        cachedWeakRevision = Long.MIN_VALUE;
        candidateRevision++;
        bufferedRevision = Long.MIN_VALUE;
        cachedDimension = null;
        dangerousChunks = List.of();
        bufferedContext = null;
    }

    private static void refreshDangerousChunks(String dimension) {
        ClientPerfMapState.Snapshot reportSnapshot = ClientPerfMapState.snapshot();
        ClientWeakChunkState.Snapshot weakSnapshot = ClientWeakChunkState.snapshot();
        if (reportSnapshot.revision() == cachedReportRevision
                && weakSnapshot.revision() == cachedWeakRevision
                && dimension.equals(cachedDimension)) {
            return;
        }
        cachedReportRevision = reportSnapshot.revision();
        cachedWeakRevision = weakSnapshot.revision();
        cachedDimension = dimension;
        DangerousChunkModel.WeakChunks weak = weakSnapshot.dimension() == null ? null
                : new DangerousChunkModel.WeakChunks(
                weakSnapshot.dimension(), weakSnapshot.chunks());
        List<DangerousChunkModel.Chunk> next = DangerousChunkModel.build(
                reportSnapshot.report(), weak, dimension);
        if (!next.equals(dangerousChunks)) {
            dangerousChunks = next;
            candidateRevision++;
        }
    }

    private static List<Indicator> indicators(DangerousChunkModel.Chunk[] selected,
                                               double[] angles,
                                               RenderContext context) {
        List<Indicator> result = new ArrayList<>(DIRECTION_BUCKETS);
        for (int bucket = 0; bucket < selected.length; bucket++) {
            DangerousChunkModel.Chunk chunk = selected[bucket];
            if (chunk != null) {
                result.add(indicator(chunk, angles[bucket], context));
            }
        }
        return List.copyOf(result);
    }

    private static Indicator indicator(DangerousChunkModel.Chunk chunk,
                                       double angle,
                                       RenderContext context) {
        var screen = context.visible().screen();
        double limitX = Math.max(1.0D, screen.size().width() * 0.5D - EDGE_INSET);
        double limitY = Math.max(1.0D, screen.size().height() * 0.5D - EDGE_INSET);
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double edgeScale = Math.min(limitX / Math.max(Math.abs(dx), 1.0E-9D),
                limitY / Math.max(Math.abs(dy), 1.0E-9D));
        int x = (int) Math.round(screen.centerX() + dx * edgeScale);
        int y = (int) Math.round(screen.centerY() + dy * edgeScale);
        return new Indicator(x, y, (float) angle, chunk.color(), chunk.entityCount());
    }

    private static double direction(DangerousChunkModel.Chunk chunk, RenderContext context) {
        double worldX = (chunk.chunkX() << 4) + 8.0D;
        double worldZ = (chunk.chunkZ() << 4) + 8.0D;
        return Math.atan2(worldZ - context.camera().z(), worldX - context.camera().x());
    }

    private static int directionBucket(double angle) {
        double normalized = (angle + Math.PI) / (Math.PI * 2.0D);
        int bucket = (int) Math.floor(normalized * DIRECTION_BUCKETS + 0.5D);
        return Math.floorMod(bucket, DIRECTION_BUCKETS);
    }

    static int indicatorArgb(float color) {
        return DangerousChunkModel.argb(color);
    }

    private static void replaceBuffer(List<Indicator> indicators) {
        if (indicators.isEmpty()) {
            closeBuffer();
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        indicators.forEach(indicator -> addQuad(builder, indicator));
        if (indicatorBuffer == null) {
            indicatorBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        try {
            indicatorBuffer.bind();
            indicatorBuffer.upload(builder.buildOrThrow());
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static void addQuad(BufferBuilder builder, Indicator indicator) {
        IndicatorQuad quad = new IndicatorQuad(builder, indicator);
        quad.add(-1.0F, 1.0F);
        quad.add(1.0F, 1.0F);
        quad.add(1.0F, -1.0F);
        quad.add(-1.0F, -1.0F);
    }

    private static void draw(GuiGraphics graphics) {
        ShaderInstance shader = ChunkHeatmapShaders.shader();
        if (indicatorBuffer == null || shader == null) {
            return;
        }
        RenderType renderType = RenderType.guiOverlay();
        renderType.setupRenderState();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        try {
            indicatorBuffer.bind();
            shader.safeGetUniform("MapScale").set(1.0F);
            shader.safeGetUniform("Pass").set(INDICATOR_PASS);
            shader.safeGetUniform("WeakPattern").set(0);
            Matrix4f modelView = MODEL_VIEW.set(RenderSystem.getModelViewMatrix())
                    .mul(graphics.pose().last().pose());
            indicatorBuffer.drawWithShader(modelView,
                    RenderSystem.getProjectionMatrix(), shader);
        } finally {
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.defaultBlendFunc();
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    private static void closeBuffer() {
        if (indicatorBuffer != null) {
            indicatorBuffer.close();
            indicatorBuffer = null;
        }
    }

    public record RenderContext(String dimension,
                                MapRenderContext.VisibleArea visible,
                                MapRenderContext.WorldPoint camera) {
    }

    record Indicator(int x, int y, float angle, float color, int entityCount) {
    }

    private static final class IndicatorQuad {
        private final BufferBuilder builder;
        private final Indicator indicator;
        private final float centerX;
        private final float centerY;
        private final float tangentX;
        private final float tangentY;
        private final float outwardX;
        private final float outwardY;

        private IndicatorQuad(BufferBuilder builder, Indicator indicator) {
            this.builder = builder;
            this.indicator = indicator;
            outwardX = (float) Math.cos(indicator.angle());
            outwardY = (float) Math.sin(indicator.angle());
            tangentX = outwardY;
            tangentY = -outwardX;
            centerX = indicator.x() - outwardX * HALF_RADIAL;
            centerY = indicator.y() - outwardY * HALF_RADIAL;
        }

        private void add(float localX, float localY) {
            float x = centerX + tangentX * localX * HALF_TANGENT + outwardX * localY * HALF_RADIAL;
            float y = centerY + tangentY * localX * HALF_TANGENT + outwardY * localY * HALF_RADIAL;
            builder.addVertex(x, y, 0.0F)
                    .setUv((localX + 1.0F) * 0.5F, (localY + 1.0F) * 0.5F)
                    .setColor(indicatorArgb(indicator.color()));
        }
    }
}
