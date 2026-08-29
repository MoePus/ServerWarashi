package com.moepus.serverwarashi.common.modules.loaderfreeze;

import com.moepus.serverwarashi.common.util.PackedPositionHash;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded insertion-ordered storage for placement-to-ticket correlation. */
final class PendingPlacementStore {
    private final int perDimensionLimit;
    private final int globalLimit;
    private final int ttlTicks;
    private final LinkedHashMap<Key, Candidate> candidates = new LinkedHashMap<>();
    private long overflowCount;

    PendingPlacementStore(int perDimensionLimit, int globalLimit, int ttlTicks) {
        if (perDimensionLimit < 1 || globalLimit < perDimensionLimit || ttlTicks < 1) {
            throw new IllegalArgumentException("invalid pending placement limits");
        }
        this.perDimensionLimit = perDimensionLimit;
        this.globalLimit = globalLimit;
        this.ttlTicks = ttlTicks;
    }

    synchronized void put(Placement placement) {
        pruneExpired(placement.createdTick());
        Key key = new Key(placement.dimension(), placement.pos().immutable());
        candidates.remove(key);
        candidates.put(key, new Candidate(
                placement.blockId(), placement.playerId(), placement.createdTick()));
        evictDimensionOverflow(placement.dimension());
        evictGlobalOverflow();
    }

    synchronized Optional<Candidate> take(ResourceKey<Level> dimension, BlockPos pos, int currentTick) {
        pruneExpired(currentTick);
        return Optional.ofNullable(candidates.remove(new Key(dimension, pos)));
    }

    synchronized int size() {
        return candidates.size();
    }

    synchronized int size(ResourceKey<Level> dimension) {
        return (int) candidates.keySet().stream().filter(key -> key.dimension().equals(dimension)).count();
    }

    synchronized long overflowCount() {
        return overflowCount;
    }

    synchronized void clear() {
        candidates.clear();
        overflowCount = 0;
    }

    private void pruneExpired(int currentTick) {
        candidates.values().removeIf(candidate -> elapsedTicks(currentTick, candidate.createdTick()) > ttlTicks);
    }

    private static long elapsedTicks(int currentTick, int createdTick) {
        return Integer.toUnsignedLong(currentTick - createdTick);
    }

    private void evictDimensionOverflow(ResourceKey<Level> dimension) {
        while (size(dimension) > perDimensionLimit) {
            Iterator<Map.Entry<Key, Candidate>> iterator = candidates.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getKey().dimension().equals(dimension)) {
                    iterator.remove();
                    overflowCount++;
                    break;
                }
            }
        }
    }

    private void evictGlobalOverflow() {
        while (candidates.size() > globalLimit) {
            Iterator<Key> iterator = candidates.keySet().iterator();
            iterator.next();
            iterator.remove();
            overflowCount++;
        }
    }

    record Candidate(ResourceLocation blockId, UUID playerId, int createdTick) {
    }

    record Placement(ResourceKey<Level> dimension, BlockPos pos, ResourceLocation blockId,
                     UUID playerId, int createdTick) {
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
        @Override
        public int hashCode() {
            return 31 * dimension.hashCode() + PackedPositionHash.hash(pos.asLong());
        }
    }
}
