package com.moepus.serverwarashi.client.heatmap;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moepus.serverwarashi.client.state.ClientPerfReportState;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HeatmapOffscreenIndicators {
    static final int DANGER_ENTITY_COUNT = WeakChunkSnapshot.ENTITY_DANGER_THRESHOLD;
    private static final int DIRECTION_BUCKETS = 16;
    private static final int INDICATOR_PASS = 3;
    private static final int EDGE_INSET = 20;
    private static final float HALF_TANGENT = 17.0F;
    private static final float HALF_RADIAL = 10.0F;
    private static final HeatmapColorStop DANGER_VIOLET = new HeatmapColorStop(224, 204, 70, 255);
    private static final HeatmapColorStop DANGER_PINK = new HeatmapColorStop(232, 255, 48, 112);
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static long cachedReportRevision = Long.MIN_VALUE;
    private static long cachedWeakRevision = Long.MIN_VALUE;
    private static long candidateRevision;
    private static long bufferedRevision = Long.MIN_VALUE;
    private static String cachedDimension;
    private static List<DangerousChunk> dangerousChunks = List.of();
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
        String weakDimension = weakSnapshot == null ? null : weakSnapshot.dimension();
        List<WeakChunkSnapshot.ChunkEntry> weakChunks = weakSnapshot == null
                ? List.of() : weakSnapshot.chunks();
        return plan(dangerousChunks(report, weakDimension, weakChunks, context.dimension()), context);
    }

    private static List<Indicator> plan(List<DangerousChunk> chunks, RenderContext context) {
        DangerousChunk[] selected = new DangerousChunk[DIRECTION_BUCKETS];
        double[] angles = new double[DIRECTION_BUCKETS];
        for (DangerousChunk chunk : chunks) {
            if (context.viewport().intersectsChunk(chunk.chunkX(), chunk.chunkZ())) {
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
        ClientPerfReportState.Snapshot reportSnapshot = ClientPerfReportState.snapshot();
        ClientWeakChunkState.Snapshot weakSnapshot = ClientWeakChunkState.snapshot();
        if (reportSnapshot.revision() == cachedReportRevision
                && weakSnapshot.revision() == cachedWeakRevision
                && dimension.equals(cachedDimension)) {
            return;
        }
        cachedReportRevision = reportSnapshot.revision();
        cachedWeakRevision = weakSnapshot.revision();
        cachedDimension = dimension;
        dangerousChunks = dangerousChunks(reportSnapshot.report(), weakSnapshot.dimension(),
                weakSnapshot.chunks(), dimension);
        candidateRevision++;
    }

    private static List<DangerousChunk> dangerousChunks(PerfReportData report,
                                                        String weakDimension,
                                                        List<WeakChunkSnapshot.ChunkEntry> weakChunks,
                                                        String dimension) {
        Map<Long, ChunkRisk> risks = new LinkedHashMap<>();
        if (report != null) {
            for (PerfReportData.ChunkEntry chunk : report.chunks()) {
                if (dimension.equals(chunk.dimension())) {
                    risks.computeIfAbsent(ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ()),
                            ignored -> new ChunkRisk(chunk.chunkX(), chunk.chunkZ())).metric = chunk.metric();
                }
            }
        }
        if (dimension.equals(weakDimension)) {
            for (WeakChunkSnapshot.ChunkEntry chunk : weakChunks) {
                risks.computeIfAbsent(ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ()),
                        ignored -> new ChunkRisk(chunk.chunkX(), chunk.chunkZ())).entityCount = chunk.entityCount();
            }
        }
        return risks.values().stream()
                .map(ChunkRisk::dangerousChunk)
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<Indicator> indicators(DangerousChunk[] selected,
                                               double[] angles,
                                               RenderContext context) {
        List<Indicator> result = new ArrayList<>(DIRECTION_BUCKETS);
        for (int bucket = 0; bucket < selected.length; bucket++) {
            DangerousChunk chunk = selected[bucket];
            if (chunk != null) {
                result.add(indicator(chunk, angles[bucket], context));
            }
        }
        return List.copyOf(result);
    }

    private static Indicator indicator(DangerousChunk chunk,
                                       double angle,
                                       RenderContext context) {
        double limitX = Math.max(1.0D, context.screenWidth() * 0.5D - EDGE_INSET);
        double limitY = Math.max(1.0D, context.screenHeight() * 0.5D - EDGE_INSET);
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double edgeScale = Math.min(limitX / Math.max(Math.abs(dx), 1.0E-9D),
                limitY / Math.max(Math.abs(dy), 1.0E-9D));
        int x = (int) Math.round(context.screenWidth() * 0.5D + dx * edgeScale);
        int y = (int) Math.round(context.screenHeight() * 0.5D + dy * edgeScale);
        int color = indicatorColor(chunk);
        return new Indicator(x, y, (float) angle, color, chunk.entityCount());
    }

    private static double direction(DangerousChunk chunk, RenderContext context) {
        double worldX = (chunk.chunkX() << 4) + 8.0D;
        double worldZ = (chunk.chunkZ() << 4) + 8.0D;
        return Math.atan2(worldZ - context.cameraZ(), worldX - context.cameraX());
    }

    private static int directionBucket(double angle) {
        double normalized = (angle + Math.PI) / (Math.PI * 2.0D);
        int bucket = (int) Math.floor(normalized * DIRECTION_BUCKETS + 0.5D);
        return Math.floorMod(bucket, DIRECTION_BUCKETS);
    }

    private static int indicatorColor(DangerousChunk chunk) {
        double weakRatio = chunk.entityCount() / (double) DANGER_ENTITY_COUNT;
        double contributionRatio = chunk.priority() / ChunkHeatmapModel.PURPLE_CONTRIBUTION;
        double severity = Math.log(Math.max(weakRatio, contributionRatio)) / Math.log(8.0D);
        return DANGER_VIOLET.interpolate(DANGER_PINK, Math.clamp(severity, 0.0D, 1.0D));
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
                                HeatmapViewport viewport,
                                double cameraX,
                                double cameraZ,
                                int screenWidth,
                                int screenHeight) {
    }

    record Indicator(int x, int y, float angle, int color, int entityCount) {
    }

    private record DangerousChunk(int chunkX, int chunkZ, int entityCount, double priority) {
    }

    private static final class ChunkRisk {
        private final int chunkX;
        private final int chunkZ;
        private PerfReportData.Metric metric;
        private int entityCount;

        private ChunkRisk(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private DangerousChunk dangerousChunk() {
            if (!ChunkHeatmapModel.isPurple(metric, entityCount)) {
                return null;
            }
            double combined = ChunkHeatmapModel.combinedContribution(metric, entityCount);
            double weakScale = entityCount / (double) DANGER_ENTITY_COUNT
                    * ChunkHeatmapModel.PURPLE_CONTRIBUTION;
            return new DangerousChunk(chunkX, chunkZ, entityCount, Math.max(combined, weakScale));
        }
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
                    .setColor(indicator.color());
        }
    }
}
