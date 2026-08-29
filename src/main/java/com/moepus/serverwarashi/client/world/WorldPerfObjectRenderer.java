package com.moepus.serverwarashi.client.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.client.state.ClientPerfWorldState;
import com.moepus.serverwarashi.common.modules.performance.report.ObjectPerformanceScale;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.util.PackedPositionHash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class WorldPerfObjectRenderer {
    private static final double MAX_DISTANCE_SQUARED =
            WorldOverlayMath.OBJECT_FADE_END * WorldOverlayMath.OBJECT_FADE_END;
    private static final double FLAME_FRUSTUM_MARGIN = 1.2D;
    private static final double FLAME_FRUSTUM_HEIGHT = 4.0D;
    static final float LABEL_PIXELS = 12.0F;
    private final WorldPerfObjectBuffers buffers = new WorldPerfObjectBuffers();
    private List<Candidate> candidates = List.of();
    private long reportRevision = Long.MIN_VALUE;
    private String dimension;
    private ClientConfig.WorldOverlayFilter filter;

    void render(RenderLevelStageEvent event,
                ClientLevel level,
                ClientPerfWorldState.Snapshot snapshot) {
        ShaderInstance shader = WorldPerfOverlayShaders.objectShader();
        if (shader == null || snapshot.tickCount() == 0L) {
            close();
            return;
        }
        String currentDimension = level.dimension().location().toString();
        refreshCandidates(snapshot, currentDimension);
        List<WorldPerfObjectBuffers.Entry> flames = resolve(event, level, snapshot.tickCount());
        if (flames.isEmpty()) {
            buffers.clear();
            return;
        }
        buffers.replace(flames);
        draw(event, shader);
    }

    private void refreshCandidates(ClientPerfWorldState.Snapshot snapshot, String currentDimension) {
        ClientConfig.WorldOverlayFilter currentFilter = ClientConfig.worldOverlayFilter();
        if (snapshot.clientRevision() == reportRevision && currentDimension.equals(dimension)
                && currentFilter == filter) {
            return;
        }
        reportRevision = snapshot.clientRevision();
        dimension = currentDimension;
        filter = currentFilter;
        List<Candidate> next = new ArrayList<>();
        if (!currentDimension.equals(snapshot.dimension())) {
            candidates = List.of();
            return;
        }
        for (ClientPerfWorldState.Hotspot hotspot : snapshot.hotspots()) {
            double flameStrength = ObjectPerformanceScale.averageSamples(
                    hotspot.metric(), hotspot.measurementTicks());
            if (WorldOverlayMath.shouldRender(flameStrength, currentFilter)) {
                next.add(new Candidate(hotspot, hotspot.metric(), flameStrength));
                if (next.size() == WorldPerfObjectBuffers.MAX_MARKER_COUNT) {
                    break;
                }
            }
        }
        candidates = List.copyOf(next);
    }

    private List<WorldPerfObjectBuffers.Entry> resolve(RenderLevelStageEvent event,
                                                       ClientLevel level,
                                                       long tickCount) {
        Vec3 camera = event.getCamera().getPosition();
        List<WorldEntityFlameClusters.Input> itemEntities = new ArrayList<>();
        List<WorldEntityFlameClusters.Marker> markers = new ArrayList<>();
        for (Candidate candidate : candidates) {
            AABB bounds = candidate.hotspot().key().kind() == PerfWorldHotspotIndex.Kind.ENTITY
                    ? entityBounds(event, level, candidate.hotspot())
                    : blockEntityBounds(level, candidate.hotspot());
            if (bounds == null) {
                continue;
            }
            double distanceSquared = bounds.distanceToSqr(camera);
            if (distanceSquared > MAX_DISTANCE_SQUARED
                    || !event.getFrustum().isVisible(flameBounds(bounds))) {
                continue;
            }
            if (candidate.hotspot().key().kind() == PerfWorldHotspotIndex.Kind.ENTITY) {
                var input = new WorldEntityFlameClusters.Input(
                        bounds, candidate.metric(), candidate.hotspot().measurementTicks(),
                        candidate.hotspot().key().entityId());
                if (shouldClusterEntity(candidate.hotspot().detailKind())) {
                    itemEntities.add(input);
                } else {
                    markers.add(WorldEntityFlameClusters.single(input, tickCount));
                }
            } else {
                markers.add(blockEntityMarker(candidate, bounds));
            }
        }
        markers.addAll(WorldEntityFlameClusters.cluster(itemEntities, tickCount));
        List<WorldPerfObjectBuffers.Entry> result = markers.stream()
                .map(marker -> WorldPerfObjectBuffers.Entry.from(
                        marker, camera, distanceSquared(marker, camera)))
                .sorted(Comparator.comparingDouble(
                        WorldPerfObjectBuffers.Entry::distanceSquared).reversed())
                .toList();
        return result;
    }

    private static AABB flameBounds(AABB bounds) {
        return new AABB(bounds.minX - FLAME_FRUSTUM_MARGIN, bounds.minY,
                bounds.minZ - FLAME_FRUSTUM_MARGIN, bounds.maxX + FLAME_FRUSTUM_MARGIN,
                bounds.maxY + FLAME_FRUSTUM_HEIGHT, bounds.maxZ + FLAME_FRUSTUM_MARGIN);
    }

    private static WorldEntityFlameClusters.Marker blockEntityMarker(
            Candidate candidate, AABB bounds) {
        int phase = PackedPositionHash.hash(
                candidate.hotspot().key().blockPosition()) & 0x7FFF;
        var anchor = new WorldEntityFlameClusters.Anchor(
                (bounds.minX + bounds.maxX) * 0.5D, bounds.maxY,
                (bounds.minZ + bounds.maxZ) * 0.5D);
        var visual = new WorldEntityFlameClusters.Visual(
                candidate.flameStrength(), phase, 1);
        return new WorldEntityFlameClusters.Marker(anchor, candidate.metric(), visual);
    }

    private static double distanceSquared(WorldEntityFlameClusters.Marker marker, Vec3 camera) {
        double dx = marker.x() - camera.x;
        double dy = marker.y() - camera.y;
        double dz = marker.z() - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static AABB entityBounds(RenderLevelStageEvent event,
                                     ClientLevel level,
                                     ClientPerfWorldState.Hotspot hotspot) {
        Entity entity = level.getEntity(hotspot.key().entityId());
        if (entity == null || entity.isRemoved() || !matchesEntity(hotspot, entity)
                || (!event.getCamera().isDetached() && entity == event.getCamera().getEntity())) {
            return null;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double x = Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
        return entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
    }

    private static AABB blockEntityBounds(ClientLevel level, ClientPerfWorldState.Hotspot hotspot) {
        BlockPos pos = BlockPos.of(hotspot.key().blockPosition());
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && !blockEntity.isRemoved() && matchesBlockEntity(hotspot, blockEntity)
                ? new AABB(pos) : null;
    }

    static boolean shouldClusterEntity(TickDetailKind detailKind) {
        return detailKind == TickDetailKind.ITEM;
    }

    private static boolean matchesEntity(ClientPerfWorldState.Hotspot hotspot, Entity entity) {
        int actual;
        if (hotspot.detailKind() == TickDetailKind.ITEM && entity instanceof ItemEntity item) {
            actual = BuiltInRegistries.ITEM.getId(item.getItem().getItem());
        } else if (hotspot.detailKind() == TickDetailKind.ENTITY) {
            actual = BuiltInRegistries.ENTITY_TYPE.getId(entity.getType());
        } else {
            return false;
        }
        return actual == hotspot.registryId();
    }

    private static boolean matchesBlockEntity(
            ClientPerfWorldState.Hotspot hotspot, BlockEntity blockEntity) {
        return hotspot.detailKind() == TickDetailKind.BLOCK
                && BuiltInRegistries.BLOCK.getId(blockEntity.getBlockState().getBlock())
                == hotspot.registryId();
    }

    private void draw(RenderLevelStageEvent event, ShaderInstance shader) {
        Minecraft minecraft = Minecraft.getInstance();
        int previousGlyphTexture = RenderSystem.getShaderTexture(0);
        int previousNoiseTexture = RenderSystem.getShaderTexture(1);
        RenderSystem.setShaderTexture(0, WorldGlyphAtlas.textureId());
        RenderSystem.setShaderTexture(1, WorldFlameNoiseTexture.textureId());
        shader.safeGetUniform("ScreenSize").set(
                (float) minecraft.getWindow().getWidth(), (float) minecraft.getWindow().getHeight());
        shader.safeGetUniform("LabelPixels").set(LABEL_PIXELS * (float) minecraft.getWindow().getGuiScale());
        shader.safeGetUniform("FadeDistances").set(
                (float) WorldOverlayMath.OBJECT_FADE_START,
                (float) WorldOverlayMath.OBJECT_FADE_END);
        shader.safeGetUniform("LabelDistances").set(
                (float) WorldOverlayMath.LABEL_FIXED_DISTANCE,
                (float) WorldOverlayMath.LABEL_TRANSITION_END);
        shader.safeGetUniform("LabelFadeDistances").set(
                (float) WorldOverlayMath.LABEL_FADE_START,
                (float) WorldOverlayMath.LABEL_FADE_END);
        shader.safeGetUniform("OverflowValue").set(WorldOverlayMath.OVERFLOW_MICROS);
        shader.safeGetUniform("LabelWordBase").set(WorldOverlayMath.LABEL_WORD_BASE);
        shader.safeGetUniform("GlyphCount").set((float) WorldGlyphAtlas.GLYPH_COUNT);
        shader.safeGetUniform("StrengthWordScale").set((float) WorldOverlayMath.FLAME_STRENGTH_SCALE);
        shader.safeGetUniform("ParticlePhaseBase").set(WorldFlameParticleLayout.PHASE_BASE);
        shader.safeGetUniform("MaxFlameStrength").set(
                (float) WorldOverlayMath.MAX_PACKED_FLAME_STRENGTH);
        shader.safeGetUniform("MaxFlameViewportFraction").set(
                (float) WorldOverlayMath.MAX_FLAME_VIEWPORT_FRACTION);
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        WorldFlameAnimationClock.Snapshot clock = WorldFlameAnimationClock.sample(
                gameTime, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        shader.safeGetUniform("AnimationTime").set(clock.noiseSeconds());
        shader.safeGetUniform("ParticleTime").set(clock.particleSeconds());
        shader.safeGetUniform("ParticleEpoch").set(clock.particleEpoch());
        Matrix4f modelView = viewRotationMatrix(event);
        var drawContext = new WorldPerfObjectBuffers.DrawContext(
                shader, modelView, event.getProjectionMatrix());
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        try {
            buffers.draw(WorldPerfObjectBuffers.Batch.SMOKE, drawContext);
            buffers.draw(WorldPerfObjectBuffers.Batch.NORMAL, drawContext);
            buffers.draw(WorldPerfObjectBuffers.Batch.GLOW, drawContext);
            buffers.draw(WorldPerfObjectBuffers.Batch.SPARK, drawContext);
            buffers.draw(WorldPerfObjectBuffers.Batch.LABEL, drawContext);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0, previousGlyphTexture);
            RenderSystem.setShaderTexture(1, previousNoiseTexture);
        }
    }

    static Matrix4f cameraRelativeMatrix(
            RenderLevelStageEvent event, double originX, double originZ) {
        Vec3 camera = event.getCamera().getPosition();
        return viewRotationMatrix(event).translate(
                (float) (originX - camera.x),
                (float) -camera.y,
                (float) (originZ - camera.z));
    }

    private static Matrix4f viewRotationMatrix(RenderLevelStageEvent event) {
        return new Matrix4f(event.getModelViewMatrix());
    }

    void close() {
        buffers.close();
        candidates = List.of();
        reportRevision = Long.MIN_VALUE;
        dimension = null;
        filter = null;
    }

    private record Candidate(ClientPerfWorldState.Hotspot hotspot,
                             PerfReportData.Metric metric,
                             double flameStrength) {
    }
}
