package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.UUID;

/** Correlates a tagged player placement with the first newly inserted supported ticket. */
public final class LoaderPlacementAttribution {
    private static final int PENDING_TTL_TICKS = 200;
    private static final int PER_DIMENSION_LIMIT = 1_024;
    private static final int GLOBAL_LIMIT = 4_096;
    private static final PendingPlacementStore PENDING =
            new PendingPlacementStore(PER_DIMENSION_LIMIT, GLOBAL_LIMIT, PENDING_TTL_TICKS);

    private LoaderPlacementAttribution() {
    }

    public static void remember(ServerLevel level, Placement placement) {
        PENDING.put(new PendingPlacementStore.Placement(
                level.dimension(), placement.pos(), placement.blockId(), placement.playerId(),
                level.getServer().getTickCount()));
    }

    public static boolean consume(ServerLevel level, BlockLoaderRef ref) {
        if (!level.dimension().equals(ref.dimension())) {
            return false;
        }
        var candidate = PENDING.take(ref.dimension(), ref.ownerPos(), level.getServer().getTickCount());
        if (candidate.isEmpty() || !stillMatches(level, ref.ownerPos(), candidate.get().blockId())) {
            return false;
        }
        return LoaderStateData.get(level).recordPlacedBy(
                ref.controllerId(), ref.ownerPos(), candidate.get().playerId());
    }

    public static void clear() {
        PENDING.clear();
    }

    public static long overflowCount() {
        return PENDING.overflowCount();
    }

    private static boolean stillMatches(ServerLevel level, BlockPos pos, ResourceLocation expectedBlockId) {
        var state = level.getBlockState(pos);
        return state.is(BlockLoaderTags.ATTRIBUTION_CANDIDATES)
                && expectedBlockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public record Placement(BlockPos pos, ResourceLocation blockId, UUID playerId) {
        public Placement {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            blockId = Objects.requireNonNull(blockId, "blockId");
            playerId = Objects.requireNonNull(playerId, "playerId");
        }
    }
}
