package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PerfWorldHotspotResolver {
    private PerfWorldHotspotResolver() {
    }

    static PerfWorldHotspotIndex refreshEntityPositions(
            MinecraftServer server, PerfWorldHotspotIndex index) {
        if (index == null || index.entries().isEmpty()) {
            return index;
        }
        Map<String, ServerLevel> levels = new HashMap<>();
        server.getAllLevels().forEach(level ->
                levels.put(level.dimension().location().toString(), level));
        List<PerfWorldHotspotIndex.Entry> entries = index.entries().stream()
                .map(entry -> refresh(entry, levels))
                .filter(java.util.Objects::nonNull)
                .toList();
        return entries.equals(index.entries()) ? index : new PerfWorldHotspotIndex(
                index.streamEpoch(), index.revision(), index.tickCount(),
                index.nanosPerSample(), entries);
    }

    private static PerfWorldHotspotIndex.Entry refresh(
            PerfWorldHotspotIndex.Entry entry, Map<String, ServerLevel> levels) {
        if (entry.key().kind() != PerfWorldHotspotIndex.Kind.ENTITY) {
            return entry;
        }
        Entity entity = findEntity(entry, levels);
        if (entity == null || entity.isRemoved()) {
            return null;
        }
        String dimension = entity.level().dimension().location().toString();
        return new PerfWorldHotspotIndex.Entry(entry.key(), dimension,
                entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(),
                entry.detailKind(), entry.detailId(), entry.measurementStartTick(),
                entry.samples(), entry.maxSamplesQ8());
    }

    private static Entity findEntity(PerfWorldHotspotIndex.Entry entry,
                                     Map<String, ServerLevel> levels) {
        ServerLevel expected = levels.get(entry.dimension());
        Entity entity = expected == null ? null : expected.getEntity(entry.key().entityId());
        if (matchesEntity(entry, entity)) {
            return entity;
        }
        for (ServerLevel level : levels.values()) {
            if (level == expected) {
                continue;
            }
            entity = level.getEntity(entry.key().entityId());
            if (matchesEntity(entry, entity)) {
                return entity;
            }
        }
        return null;
    }

    private static boolean matchesEntity(
            PerfWorldHotspotIndex.Entry entry, Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        if (entry.detailKind() == TickDetailKind.ITEM && entity instanceof ItemEntity item) {
            return entry.detailId().equals(
                    BuiltInRegistries.ITEM.getKey(item.getItem().getItem()));
        }
        return entry.detailKind() == TickDetailKind.ENTITY
                && entry.detailId().equals(
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }
}
