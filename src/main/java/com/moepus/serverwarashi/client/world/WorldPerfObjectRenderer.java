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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

final class WorldPerfObjectRenderer {
    private static final long STATIC_REFRESH_TICKS = 20L;
    private static final WorldPerfObjectBuffers.Batch[] DRAW_ORDER = {
            WorldPerfObjectBuffers.Batch.SMOKE,
            WorldPerfObjectBuffers.Batch.NORMAL,
            WorldPerfObjectBuffers.Batch.GLOW,
            WorldPerfObjectBuffers.Batch.SPARK,
            WorldPerfObjectBuffers.Batch.LABEL
    };
    static final float LABEL_PIXELS = 12.0F;

    private final WorldPerfObjectBuffers buffers = new WorldPerfObjectBuffers();
    private final BlockPos.MutableBlockPos lookupPos = new BlockPos.MutableBlockPos();
    private final Matrix4f staticModelView = new Matrix4f();
    private final Matrix4f dynamicModelView = new Matrix4f();
    private List<Candidate> staticCandidates = List.of();
    private List<Candidate> dynamicCandidates = List.of();
    private long reportRevision = Long.MIN_VALUE;
    private long staticRefreshTick = Long.MIN_VALUE;
    private long dynamicRefreshTick = Long.MIN_VALUE;
    private String dimension;
    private ClientConfig.WorldOverlayFilter filter;
    private int markerLimit;

    void render(RenderLevelStageEvent event,
                ClientLevel level,
                ClientPerfWorldState.Snapshot snapshot) {
        ShaderInstance shader = WorldPerfOverlayShaders.objectShader();
        if (shader == null || snapshot.tickCount() == 0L) {
            close();
            return;
        }
        String currentDimension = level.dimension().location().toString();
        boolean sourceChanged = refreshCandidates(snapshot, currentDimension);
        long clientTick = level.getGameTime();
        Vec3 camera = event.getCamera().getPosition();
        refreshStaticIfNeeded(level, camera, clientTick, sourceChanged);
        refreshDynamicIfNeeded(event, level, snapshot.tickCount(), camera,
                clientTick, sourceChanged);
        if (buffers.isEmpty()) {
            return;
        }
        draw(event, shader);
    }

    private boolean refreshCandidates(
            ClientPerfWorldState.Snapshot snapshot, String currentDimension) {
        ClientConfig.WorldOverlayFilter currentFilter = ClientConfig.worldOverlayFilter();
        int currentMarkerLimit = ClientConfig.worldOverlayMarkerLimit();
        if (snapshot.clientRevision() == reportRevision && currentDimension.equals(dimension)
                && currentFilter == filter && currentMarkerLimit == markerLimit) {
            return false;
        }
        reportRevision = snapshot.clientRevision();
        dimension = currentDimension;
        filter = currentFilter;
        markerLimit = currentMarkerLimit;
        splitCandidates(snapshot, currentDimension, currentFilter, currentMarkerLimit);
        return true;
    }

    private void splitCandidates(ClientPerfWorldState.Snapshot snapshot,
                                 String currentDimension,
                                 ClientConfig.WorldOverlayFilter currentFilter,
                                 int currentMarkerLimit) {
        if (!currentDimension.equals(snapshot.dimension())) {
            staticCandidates = List.of();
            dynamicCandidates = List.of();
            return;
        }
        int initialCapacity = Math.min(snapshot.hotspots().size(), currentMarkerLimit);
        List<Candidate> nextStatic = new ArrayList<>(initialCapacity);
        List<Candidate> nextDynamic = new ArrayList<>();
        int selected = 0;
        for (ClientPerfWorldState.Hotspot hotspot : snapshot.hotspots()) {
            double flameStrength = ObjectPerformanceScale.averageSamples(
                    hotspot.metric(), hotspot.measurementTicks());
            if (!WorldOverlayMath.shouldRender(flameStrength, currentFilter)) {
                continue;
            }
            Candidate candidate = new Candidate(hotspot, hotspot.metric(), flameStrength);
            if (hotspot.key().kind() == PerfWorldHotspotIndex.Kind.ENTITY) {
                nextDynamic.add(candidate);
            } else {
                nextStatic.add(candidate);
            }
            selected++;
            if (selected == currentMarkerLimit) {
                break;
            }
        }
        staticCandidates = nextStatic;
        dynamicCandidates = nextDynamic;
    }

    private void refreshStaticIfNeeded(ClientLevel level,
                                       Vec3 camera,
                                       long clientTick,
                                       boolean sourceChanged) {
        if (!sourceChanged && (staticCandidates.isEmpty()
                || !staticRefreshDue(staticRefreshTick, clientTick))) {
            return;
        }
        buffers.replace(WorldPerfObjectBuffers.Source.STATIC,
                resolveBlockEntities(level), camera);
        staticRefreshTick = clientTick;
    }

    private void refreshDynamicIfNeeded(RenderLevelStageEvent event,
                                        ClientLevel level,
                                        long reportTickCount,
                                        Vec3 camera,
                                        long clientTick,
                                        boolean sourceChanged) {
        if (!sourceChanged && (dynamicCandidates.isEmpty()
                || !dynamicRefreshDue(dynamicRefreshTick, clientTick))) {
            return;
        }
        buffers.replace(WorldPerfObjectBuffers.Source.DYNAMIC,
                resolveEntities(event, level, reportTickCount), camera);
        dynamicRefreshTick = clientTick;
    }

    private List<WorldEntityFlameClusters.Marker> resolveBlockEntities(ClientLevel level) {
        List<WorldEntityFlameClusters.Marker> markers =
                new ArrayList<>(staticCandidates.size());
        for (Candidate candidate : staticCandidates) {
            long packedPosition = candidate.hotspot().key().blockPosition();
            lookupPos.set(BlockPos.getX(packedPosition), BlockPos.getY(packedPosition),
                    BlockPos.getZ(packedPosition));
            if (!level.hasChunkAt(lookupPos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(lookupPos);
            if (blockEntity != null && !blockEntity.isRemoved()
                    && matchesBlockEntity(candidate.hotspot(), blockEntity)) {
                markers.add(blockEntityMarker(candidate, packedPosition));
            }
        }
        return markers;
    }

    private List<WorldEntityFlameClusters.Marker> resolveEntities(
            RenderLevelStageEvent event, ClientLevel level, long tickCount) {
        List<WorldEntityFlameClusters.Input> itemEntities = new ArrayList<>();
        List<WorldEntityFlameClusters.Marker> markers =
                new ArrayList<>(dynamicCandidates.size());
        for (Candidate candidate : dynamicCandidates) {
            Entity entity = level.getEntity(candidate.hotspot().key().entityId());
            if (!isVisibleEntity(event, candidate.hotspot(), entity)) {
                continue;
            }
            AABB bounds = entity.getBoundingBox();
            var input = new WorldEntityFlameClusters.Input(
                    bounds, candidate.metric(), candidate.hotspot().measurementTicks(),
                    candidate.hotspot().key().entityId(), entity.xo - entity.getX(),
                    entity.yo - entity.getY(), entity.zo - entity.getZ());
            if (shouldClusterEntity(candidate.hotspot().detailKind())) {
                itemEntities.add(input);
            } else {
                markers.add(WorldEntityFlameClusters.single(input, tickCount));
            }
        }
        markers.addAll(WorldEntityFlameClusters.cluster(itemEntities, tickCount));
        return markers;
    }

    private static boolean isVisibleEntity(RenderLevelStageEvent event,
                                           ClientPerfWorldState.Hotspot hotspot,
                                           Entity entity) {
        return entity != null && !entity.isRemoved() && matchesEntity(hotspot, entity)
                && (event.getCamera().isDetached() || entity != event.getCamera().getEntity());
    }

    private static WorldEntityFlameClusters.Marker blockEntityMarker(
            Candidate candidate, long packedPosition) {
        int phase = PackedPositionHash.hash(packedPosition) & 0x7FFF;
        var anchor = new WorldEntityFlameClusters.Anchor(
                BlockPos.getX(packedPosition) + 0.5D,
                BlockPos.getY(packedPosition) + 1.0D,
                BlockPos.getZ(packedPosition) + 0.5D);
        var visual = new WorldEntityFlameClusters.Visual(
                candidate.flameStrength(), phase, 1);
        return new WorldEntityFlameClusters.Marker(anchor, candidate.metric(), visual);
    }

    static boolean staticRefreshDue(long previousTick, long currentTick) {
        return previousTick == Long.MIN_VALUE || currentTick < previousTick
                || currentTick - previousTick >= STATIC_REFRESH_TICKS;
    }

    static boolean dynamicRefreshDue(long previousTick, long currentTick) {
        return previousTick != currentTick;
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
        setFrameUniforms(event, shader, minecraft);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        try {
            drawBuffers(event, shader);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0, previousGlyphTexture);
            RenderSystem.setShaderTexture(1, previousNoiseTexture);
        }
    }

    private static void setFrameUniforms(
            RenderLevelStageEvent event, ShaderInstance shader, Minecraft minecraft) {
        shader.safeGetUniform("ScreenSize").set(
                (float) minecraft.getWindow().getWidth(), (float) minecraft.getWindow().getHeight());
        shader.safeGetUniform("LabelPixels").set(
                LABEL_PIXELS * (float) minecraft.getWindow().getGuiScale());
        shader.safeGetUniform("FadeDistances").set(
                (float) WorldOverlayMath.OBJECT_FADE_START,
                (float) WorldOverlayMath.OBJECT_FADE_END);
        shader.safeGetUniform("LabelDistances").set(
                (float) WorldOverlayMath.LABEL_FIXED_DISTANCE,
                (float) WorldOverlayMath.LABEL_TRANSITION_END);
        shader.safeGetUniform("LabelFadeDistances").set(
                (float) WorldOverlayMath.LABEL_FADE_START,
                (float) WorldOverlayMath.LABEL_FADE_END);
        shader.safeGetUniform("PartialTick").set(
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        setPackedDataUniforms(shader);
        setAnimationUniforms(event, shader, minecraft);
    }

    private static void setPackedDataUniforms(ShaderInstance shader) {
        shader.safeGetUniform("OverflowValue").set(WorldOverlayMath.OVERFLOW_MICROS);
        shader.safeGetUniform("LabelWordBase").set(WorldOverlayMath.LABEL_WORD_BASE);
        shader.safeGetUniform("GlyphCount").set((float) WorldGlyphAtlas.GLYPH_COUNT);
        shader.safeGetUniform("StrengthWordScale").set((float) WorldOverlayMath.FLAME_STRENGTH_SCALE);
        shader.safeGetUniform("ParticlePhaseBase").set(WorldFlameParticleLayout.PHASE_BASE);
        shader.safeGetUniform("MaxFlameStrength").set(
                (float) WorldOverlayMath.MAX_PACKED_FLAME_STRENGTH);
        shader.safeGetUniform("MaxFlameViewportFraction").set(
                (float) WorldOverlayMath.MAX_FLAME_VIEWPORT_FRACTION);
    }

    private static void setAnimationUniforms(
            RenderLevelStageEvent event, ShaderInstance shader, Minecraft minecraft) {
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        WorldFlameAnimationClock.Snapshot clock = WorldFlameAnimationClock.sample(
                gameTime, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        shader.safeGetUniform("AnimationTime").set(clock.noiseSeconds());
        shader.safeGetUniform("ParticleTime").set(clock.particleSeconds());
        shader.safeGetUniform("ParticleEpoch").set(clock.particleEpoch());
    }

    private void drawBuffers(RenderLevelStageEvent event, ShaderInstance shader) {
        WorldPerfObjectBuffers.DrawContext staticContext = drawContext(
                event, shader, WorldPerfObjectBuffers.Source.STATIC);
        WorldPerfObjectBuffers.DrawContext dynamicContext = drawContext(
                event, shader, WorldPerfObjectBuffers.Source.DYNAMIC);
        for (WorldPerfObjectBuffers.Batch batch : DRAW_ORDER) {
            if (staticContext != null) {
                buffers.draw(WorldPerfObjectBuffers.Source.STATIC, batch, staticContext);
            }
            if (dynamicContext != null) {
                buffers.draw(WorldPerfObjectBuffers.Source.DYNAMIC, batch, dynamicContext);
            }
        }
    }

    private WorldPerfObjectBuffers.DrawContext drawContext(
            RenderLevelStageEvent event,
            ShaderInstance shader,
            WorldPerfObjectBuffers.Source source) {
        if (buffers.isEmpty(source)) {
            return null;
        }
        return new WorldPerfObjectBuffers.DrawContext(
                shader, sourceModelView(event, source), event.getProjectionMatrix());
    }

    private Matrix4f sourceModelView(
            RenderLevelStageEvent event, WorldPerfObjectBuffers.Source source) {
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f target = source == WorldPerfObjectBuffers.Source.STATIC
                ? staticModelView : dynamicModelView;
        return target.set(event.getModelViewMatrix()).translate(
                (float) (buffers.originX(source) - camera.x),
                (float) -camera.y,
                (float) (buffers.originZ(source) - camera.z));
    }

    void close() {
        buffers.close();
        staticCandidates = List.of();
        dynamicCandidates = List.of();
        reportRevision = Long.MIN_VALUE;
        staticRefreshTick = Long.MIN_VALUE;
        dynamicRefreshTick = Long.MIN_VALUE;
        dimension = null;
        filter = null;
        markerLimit = 0;
    }

    private record Candidate(ClientPerfWorldState.Hotspot hotspot,
                             PerfReportData.Metric metric,
                             double flameStrength) {
    }
}
