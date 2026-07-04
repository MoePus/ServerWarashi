package com.moepus.serverwarashi.common.modules.idlefreeze;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 每维度因 IDLE 规则被冻结的 owner chunk 集合。
 */
public final class IdlePauseData extends SavedData {
    private static final String DATA_NAME = "serverwarashi_idle_paused_owners";
    private static final String TAG_OWNERS = "owners";
    private static final String TAG_CHUNKS = "chunks";
    private static final SavedData.Factory<IdlePauseData> FACTORY =
            new SavedData.Factory<>(IdlePauseData::new, (tag, registries) -> load(tag));

    private final List<LongOpenHashSet> frozenOwners = new ArrayList<>();

    boolean isEmpty() {
        return frozenOwners.isEmpty();
    }

    List<LongOpenHashSet> ownerChunks() {
        return Collections.unmodifiableList(frozenOwners);
    }

    public boolean containsChunk(long chunkPos) {
        for (LongOpenHashSet owner : frozenOwners) {
            if (owner.contains(chunkPos)) {
                return true;
            }
        }
        return false;
    }

    public static IdlePauseData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void replaceOwnerChunks(Iterable<? extends Set<Long>> owners) {
        frozenOwners.clear();
        for (Set<Long> chunks : owners) {
            addOwnerChunks(new LongOpenHashSet(chunks));
        }
        setDirty();
    }

    LongOpenHashSet removeOwnersContaining(long chunkPos) {
        LongOpenHashSet removedChunks = new LongOpenHashSet();
        Iterator<LongOpenHashSet> iterator = frozenOwners.iterator();
        while (iterator.hasNext()) {
            LongOpenHashSet owner = iterator.next();
            if (owner.contains(chunkPos)) {
                removedChunks.addAll(owner);
                iterator.remove();
            }
        }
        if (!removedChunks.isEmpty()) {
            setDirty();
        }
        return removedChunks;
    }

    LongOpenHashSet allChunks() {
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (LongOpenHashSet owner : frozenOwners) {
            chunks.addAll(owner);
        }
        return chunks;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag ownersTag = new ListTag();
        for (LongOpenHashSet owner : frozenOwners) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putLongArray(TAG_CHUNKS, owner.toLongArray());
            ownersTag.add(ownerTag);
        }
        tag.put(TAG_OWNERS, ownersTag);
        return tag;
    }

    static IdlePauseData load(CompoundTag tag) {
        IdlePauseData data = new IdlePauseData();
        ListTag owners = tag.getList(TAG_OWNERS, Tag.TAG_COMPOUND);
        for (Tag rawOwnerTag : owners) {
            CompoundTag ownerTag = (CompoundTag) rawOwnerTag;
            data.addOwnerChunks(new LongOpenHashSet(ownerTag.getLongArray(TAG_CHUNKS)));
        }
        return data;
    }

    private void addOwnerChunks(LongOpenHashSet incoming) {
        if (!incoming.isEmpty()) {
            frozenOwners.add(incoming);
        }
    }
}
