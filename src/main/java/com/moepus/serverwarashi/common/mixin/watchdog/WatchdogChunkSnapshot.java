package com.moepus.serverwarashi.common.mixin.watchdog;

import com.moepus.serverwarashi.common.mixin.accessor.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.ServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class WatchdogChunkSnapshot {
    private final Map<Long, ChunkLoad> loads;

    private WatchdogChunkSnapshot(Map<Long, ChunkLoad> loads) {
        this.loads = Map.copyOf(loads);
    }

    static WatchdogChunkSnapshot capture(ServerLevel level) {
        EntitySectionStorage<?> sections = sectionStorage(level);
        Map<Long, ChunkLoad> loads = new HashMap<>();
        var visibleChunks = level.getChunkSource().chunkMap.visibleChunkMap;
        for (var entry : visibleChunks.long2ObjectEntrySet()) {
            long chunkPos = entry.getLongKey();
            ChunkAccess chunk = entry.getValue().getChunkIfPresent(ChunkStatus.FULL);
            int blockEntities = chunk == null ? 0 : chunk.getBlockEntitiesPos().size();
            loads.put(chunkPos, new ChunkLoad(blockEntities, entityCount(sections, chunkPos)));
        }
        return new WatchdogChunkSnapshot(loads);
    }

    Counts count(Set<Long> chunks) {
        int blockEntities = 0;
        int entities = 0;
        for (long chunkPos : chunks) {
            ChunkLoad load = loads.get(chunkPos);
            if (load != null) {
                blockEntities += load.blockEntities();
                entities += load.entities();
            }
        }
        return new Counts(blockEntities, entities);
    }

    private static int entityCount(EntitySectionStorage<?> sections, long chunkPos) {
        return sections.getExistingSectionsInChunk(chunkPos)
                .mapToInt(EntitySection::size)
                .sum();
    }

    private static EntitySectionStorage<?> sectionStorage(ServerLevel level) {
        PersistentEntitySectionManager<Entity> manager = ((ServerLevelAccessor) level).getEntityManager();
        return ((PersistentEntitySectionManagerAccessor) manager).getSectionStorage();
    }

    record Counts(int blockEntities, int entities) {
    }

    private record ChunkLoad(int blockEntities, int entities) {
    }
}
