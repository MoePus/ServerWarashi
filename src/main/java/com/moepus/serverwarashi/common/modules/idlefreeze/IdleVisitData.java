package com.moepus.serverwarashi.common.modules.idlefreeze;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 每维度区块最近玩家访问日期。
 */
final class IdleVisitData extends SavedData {
    private static final String DATA_NAME = "serverwarashi_idle_last_seen";
    private static final String TAG_CHUNKS = "chunks";
    private static final String TAG_LAST_SEEN_DAY = "last_seen_day";
    private static final SavedData.Factory<IdleVisitData> FACTORY =
            new SavedData.Factory<>(IdleVisitData::new, (tag, registries) -> load(tag));

    private final Long2IntOpenHashMap lastSeenByChunk = new Long2IntOpenHashMap();

    IdleVisitData() {
        // 缺省值 MIN_VALUE 表示"从未访问"
        lastSeenByChunk.defaultReturnValue(Integer.MIN_VALUE);
    }

    static IdleVisitData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    void markSeen(long chunkPos, int epochDay) {
        int previous = lastSeenByChunk.put(chunkPos, epochDay);
        if (previous != epochDay) {
            setDirty();
        }
    }

    int getLastSeenDay(long chunkPos) {
        return lastSeenByChunk.get(chunkPos);
    }

    void pruneBefore(int minDayInclusive) {
        boolean removed = false;
        var iterator = lastSeenByChunk.long2IntEntrySet().iterator();
        while (iterator.hasNext()) {
            Long2IntMap.Entry entry = iterator.next();
            if (entry.getIntValue() >= minDayInclusive) {
                continue;
            }
            iterator.remove();
            removed = true;
        }
        if (removed) {
            setDirty();
        }
    }

    void clear() {
        if (lastSeenByChunk.isEmpty()) {
            return;
        }
        lastSeenByChunk.clear();
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        // 把 map 拆成 chunk[] 与 lastSeen[] 两个并行数组写入
        int size = lastSeenByChunk.size();
        long[] chunks = new long[size];
        int[] lastSeen = new int[size];
        int index = 0;
        for (var entry : lastSeenByChunk.long2IntEntrySet()) {
            chunks[index] = entry.getLongKey();
            lastSeen[index] = entry.getIntValue();
            index++;
        }
        tag.putLongArray(TAG_CHUNKS, chunks);
        tag.putIntArray(TAG_LAST_SEEN_DAY, lastSeen);
        return tag;
    }

    private static IdleVisitData load(CompoundTag tag) {
        // 按两个并行数组的较小长度逐项重建（容错长度不一致）
        IdleVisitData data = new IdleVisitData();
        long[] chunks = tag.getLongArray(TAG_CHUNKS);
        int[] lastSeen = tag.getIntArray(TAG_LAST_SEEN_DAY);
        int size = Math.min(chunks.length, lastSeen.length);
        for (int i = 0; i < size; i++) {
            data.lastSeenByChunk.put(chunks[i], lastSeen[i]);
        }
        return data;
    }
}
