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
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL14;

import java.util.List;

public final class ChunkHeatmapRenderer implements AutoCloseable {
    private static final int PASS_GLOW = 0;
    private static final int PASS_BODY = 1;
    private static final int PASS_HOVER = 2;
    private static final int HOVER_COLOR = 0xFFE8FFFF;
    private static final int ACTIVE_ICON_STATE = 0xFFFFFFFF;
    private static final int INACTIVE_ICON_STATE = 0xFF000000;
    private static final float ICON_HALF_PIXELS = 7.0F;
    private static final float MAX_ICON_HALF_WORLD = 32.0F;

    private final Matrix4f modelViewMatrix = new Matrix4f();
    private VertexBuffer vertexBuffer;
    private VertexBuffer hoverBuffer;
    private VertexBuffer iconBuffer;
    private ChunkHeatmapModel.Plan basePlan = ChunkHeatmapModel.Plan.EMPTY;
    private ChunkHeatmapModel.Plan plan = ChunkHeatmapModel.Plan.EMPTY;
    private List<WeakChunkSnapshot.ChunkEntry> visibleWeakChunks = List.of();
    private HeatmapViewport coverage;
    private String dimension;
    private long reportRevision = Long.MIN_VALUE;
    private long weakRevision = Long.MIN_VALUE;
    private long hoverChunk = Long.MIN_VALUE;
    private boolean meshValid;

    public HoverResult render(RenderContext context) {
        ClientPerfReportState.Snapshot snapshot = ClientPerfReportState.snapshot();
        ClientWeakChunkState.Snapshot weakSnapshot = ClientWeakChunkState.snapshot();
        if (needsBaseRebuild(context, snapshot.revision())) {
            rebuild(context, snapshot, weakSnapshot);
        } else if (weakSnapshot.revision() != weakRevision) {
            refreshWeakChunks(context, weakSnapshot);
        }
        Hover hover = context.hover();
        ChunkHeatmapModel.HoverInfo info = plan.hoverAt(hover.chunkX(), hover.chunkZ());
        ChunkHeatmapModel.LoaderIcon loader = hoveredLoader(hover, context.transform().mapScale());
        draw(context, info != null);
        return info == null && loader == null ? null : new HoverResult(info, loader);
    }

    private boolean needsBaseRebuild(RenderContext context, long revision) {
        return !meshValid || revision != reportRevision
                || !context.dimension().equals(dimension)
                || coverage == null || !coverage.contains(context.transform().viewport());
    }

    private void rebuild(RenderContext context,
                         ClientPerfReportState.Snapshot snapshot,
                         ClientWeakChunkState.Snapshot weakSnapshot) {
        coverage = context.transform().viewport().expandedBySelf();
        dimension = context.dimension();
        reportRevision = snapshot.revision();
        basePlan = ChunkHeatmapModel.build(snapshot.report(), dimension, coverage);
        visibleWeakChunks = visibleWeakChunks(weakSnapshot, dimension, coverage);
        plan = ChunkHeatmapModel.applyWeakChunks(basePlan, visibleWeakChunks,
                dimension, dimension);
        weakRevision = weakSnapshot.revision();
        replaceBuffers(plan);
        meshValid = true;
    }

    private void refreshWeakChunks(RenderContext context,
                                   ClientWeakChunkState.Snapshot weakSnapshot) {
        weakRevision = weakSnapshot.revision();
        var nextVisible = visibleWeakChunks(weakSnapshot, context.dimension(), coverage);
        if (nextVisible.equals(visibleWeakChunks)) {
            return;
        }
        visibleWeakChunks = nextVisible;
        plan = ChunkHeatmapModel.applyWeakChunks(basePlan, visibleWeakChunks,
                context.dimension(), context.dimension());
        replaceHeatmapBuffer(plan);
    }

    private static List<WeakChunkSnapshot.ChunkEntry> visibleWeakChunks(
            ClientWeakChunkState.Snapshot snapshot,
            String dimension,
            HeatmapViewport coverage) {
        if (!dimension.equals(snapshot.dimension())) {
            return List.of();
        }
        return snapshot.chunks().stream()
                .filter(entry -> coverage.intersectsChunk(entry.chunkX(), entry.chunkZ()))
                .toList();
    }

    private void replaceBuffers(ChunkHeatmapModel.Plan nextPlan) {
        closeIconBuffer();
        replaceHeatmapBuffer(nextPlan);
        if (!nextPlan.icons().isEmpty()) {
            BufferBuilder builder = beginBuilder();
            nextPlan.icons().forEach(icon -> addIcon(builder, icon));
            iconBuffer = createBuffer(builder, VertexBuffer.Usage.STATIC);
        }
    }

    private void replaceHeatmapBuffer(ChunkHeatmapModel.Plan nextPlan) {
        closeBuffer();
        if (!nextPlan.quads().isEmpty()) {
            BufferBuilder builder = beginBuilder();
            nextPlan.quads().forEach(quad -> addQuad(builder, quad.chunkX(), quad.chunkZ(),
                    quad.argb(), nextPlan.weakEdgeMaskAt(quad.chunkX(), quad.chunkZ())));
            vertexBuffer = createBuffer(builder, VertexBuffer.Usage.STATIC);
        }
    }

    private static BufferBuilder beginBuilder() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private static VertexBuffer createBuffer(BufferBuilder builder, VertexBuffer.Usage usage) {
        VertexBuffer buffer = new VertexBuffer(usage);
        try {
            upload(buffer, builder);
            return buffer;
        } catch (RuntimeException | Error exception) {
            buffer.close();
            throw exception;
        }
    }

    private static void upload(VertexBuffer buffer, BufferBuilder builder) {
        try {
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static void addQuad(BufferBuilder builder, int chunkX, int chunkZ, int color) {
        addQuad(builder, chunkX, chunkZ, color, 0);
    }

    private static void addQuad(BufferBuilder builder,
                                int chunkX,
                                int chunkZ,
                                int color,
                                int weakEdgeMask) {
        float centerX = (chunkX << 4) + 8.0F;
        float centerZ = (chunkZ << 4) + 8.0F;
        int encodedColor = encodeWeakPattern(color, weakEdgeMask);
        addVertex(builder, centerX, centerZ, -1.0F, 1.0F, encodedColor);
        addVertex(builder, centerX, centerZ, 1.0F, 1.0F, encodedColor);
        addVertex(builder, centerX, centerZ, 1.0F, -1.0F, encodedColor);
        addVertex(builder, centerX, centerZ, -1.0F, -1.0F, encodedColor);
    }

    private static int encodeWeakPattern(int color, int weakEdgeMask) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        if (weakEdgeMask != 0) {
            red = (red & ~0x3) | (weakEdgeMask & 0x3);
            green = (green & ~0x3) | (weakEdgeMask >> 2 & 0x3);
            blue = (blue & ~0x1) | 0x1;
        } else {
            blue &= ~0x1;
        }
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void addIcon(BufferBuilder builder, ChunkHeatmapModel.LoaderIcon icon) {
        float centerX = icon.x() + 0.5F;
        float centerZ = icon.z() + 0.5F;
        int state = icon.working() ? ACTIVE_ICON_STATE : INACTIVE_ICON_STATE;
        addVertex(builder, centerX, centerZ, -1.0F, 1.0F, state);
        addVertex(builder, centerX, centerZ, 1.0F, 1.0F, state);
        addVertex(builder, centerX, centerZ, 1.0F, -1.0F, state);
        addVertex(builder, centerX, centerZ, -1.0F, -1.0F, state);
    }

    private static void addVertex(BufferBuilder builder, float x, float z, float u, float v, int color) {
        builder.addVertex(x, z, 0.0F).setUv(u, v).setColor(color);
    }

    private void draw(RenderContext context, boolean hovered) {
        if (vertexBuffer == null && iconBuffer == null) {
            return;
        }
        ViewTransform transform = context.transform();
        Matrix4f modelView = modelViewMatrix.set(RenderSystem.getModelViewMatrix()).mul(transform.pose());
        RenderType renderType = RenderType.guiOverlay();
        renderType.setupRenderState();
        try {
            drawHeatmap(modelView, context, hovered);
            drawIcons(modelView, transform.mapScale());
        } finally {
            resetBlendState();
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    private void drawHeatmap(Matrix4f modelView, RenderContext context, boolean hovered) {
        ShaderInstance shader = ChunkHeatmapShaders.shader();
        if (vertexBuffer == null || shader == null) {
            return;
        }
        float mapScale = context.transform().mapScale();
        shader.safeGetUniform("WeakPattern").set(1);
        vertexBuffer.bind();
        drawGlow(shader, modelView, mapScale);
        drawBody(shader, modelView, mapScale);
        if (hovered) {
            drawHover(shader, modelView, context.hover(), mapScale);
        }
    }

    private void drawIcons(Matrix4f modelView, float mapScale) {
        ShaderInstance shader = ChunkHeatmapShaders.iconShader();
        if (iconBuffer == null || shader == null) {
            return;
        }
        setPremultipliedBlend();
        iconBuffer.bind();
        shader.safeGetUniform("MapScale").set(mapScale);
        iconBuffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
    }

    private void drawGlow(ShaderInstance shader, Matrix4f modelView, float mapScale) {
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        setPass(shader, mapScale, PASS_GLOW);
        drawBuffer(vertexBuffer, shader, modelView);
    }

    private void drawBody(ShaderInstance shader, Matrix4f modelView, float mapScale) {
        setPremultipliedBlend();
        setPass(shader, mapScale, PASS_BODY);
        drawBuffer(vertexBuffer, shader, modelView);
    }

    private void drawHover(ShaderInstance shader, Matrix4f modelView, Hover hover, float mapScale) {
        updateHoverBuffer(hover);
        setPremultipliedBlend();
        hoverBuffer.bind();
        setPass(shader, mapScale, PASS_HOVER);
        drawBuffer(hoverBuffer, shader, modelView);
    }

    private static void setPremultipliedBlend() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private static void resetBlendState() {
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.defaultBlendFunc();
    }

    private static void setPass(ShaderInstance shader, float mapScale, int pass) {
        shader.safeGetUniform("MapScale").set(mapScale);
        shader.safeGetUniform("Pass").set(pass);
    }

    private static void drawBuffer(VertexBuffer buffer, ShaderInstance shader, Matrix4f modelView) {
        buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
    }

    private void updateHoverBuffer(Hover hover) {
        long nextChunk = ChunkPos.asLong(hover.chunkX(), hover.chunkZ());
        if (hoverBuffer != null && nextChunk == hoverChunk) {
            return;
        }
        BufferBuilder builder = beginBuilder();
        addQuad(builder, hover.chunkX(), hover.chunkZ(), HOVER_COLOR);
        if (hoverBuffer == null) {
            hoverBuffer = createBuffer(builder, VertexBuffer.Usage.DYNAMIC);
        } else {
            upload(hoverBuffer, builder);
        }
        hoverChunk = nextChunk;
    }

    private ChunkHeatmapModel.LoaderIcon hoveredLoader(Hover hover, float mapScale) {
        return hoveredLoader(plan.icons(), hover, mapScale);
    }

    static ChunkHeatmapModel.LoaderIcon hoveredLoader(
            List<ChunkHeatmapModel.LoaderIcon> icons, Hover hover, float mapScale) {
        double halfWorld = iconHalfWorld(mapScale);
        ChunkHeatmapModel.LoaderIcon nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (ChunkHeatmapModel.LoaderIcon icon : icons) {
            double dx = Math.abs(hover.worldX() - icon.x() - 0.5D);
            double dz = Math.abs(hover.worldZ() - icon.z() - 0.5D);
            double distance = Math.max(dx, dz);
            if (distance <= halfWorld && distance <= nearestDistance) {
                nearest = icon;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    static float iconHalfWorld(float mapScale) {
        return Math.min(ICON_HALF_PIXELS / Math.max(mapScale, 0.0001F), MAX_ICON_HALF_WORLD);
    }

    @Override
    public void close() {
        closeBuffer();
        closeHoverBuffer();
        closeIconBuffer();
        basePlan = ChunkHeatmapModel.Plan.EMPTY;
        plan = ChunkHeatmapModel.Plan.EMPTY;
        visibleWeakChunks = List.of();
        coverage = null;
        dimension = null;
        reportRevision = Long.MIN_VALUE;
        weakRevision = Long.MIN_VALUE;
        hoverChunk = Long.MIN_VALUE;
        meshValid = false;
    }

    private void closeBuffer() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }

    private void closeHoverBuffer() {
        if (hoverBuffer != null) {
            hoverBuffer.close();
            hoverBuffer = null;
        }
    }

    private void closeIconBuffer() {
        if (iconBuffer != null) {
            iconBuffer.close();
            iconBuffer = null;
        }
    }

    public record RenderContext(String dimension, ViewTransform transform, Hover hover) {
    }

    public record ViewTransform(HeatmapViewport viewport, Matrix4f pose, float mapScale) {
    }

    public record Hover(double worldX, double worldZ, int chunkX, int chunkZ) {
    }

    public record HoverResult(ChunkHeatmapModel.HoverInfo chunk,
                              ChunkHeatmapModel.LoaderIcon loader) {
    }

}
