package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Per-dimension persistent state for controllable block loaders. */
public final class LoaderStateData extends SavedData {
    private static final String DATA_NAME = "serverwarashi_block_loader_state";
    private static final int DATA_VERSION = 1;
    private static final String TAG_VERSION = "version";
    private static final String TAG_LOADERS = "loaders";
    private static final String TAG_CONTROLLER = "controller";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_FROZEN = "frozen";
    private static final String TAG_PLACED_BY = "placed_by";
    private static final Comparator<LoaderKey> KEY_ORDER = Comparator
            .comparing((LoaderKey key) -> key.controllerId().toString())
            .thenComparingInt(key -> key.ownerPos().getX())
            .thenComparingInt(key -> key.ownerPos().getY())
            .thenComparingInt(key -> key.ownerPos().getZ());
    private static final SavedData.Factory<LoaderStateData> FACTORY =
            new SavedData.Factory<>(LoaderStateData::new, (tag, registries) -> load(tag));

    private final Map<LoaderKey, LoaderRecord> records = new HashMap<>();

    LoaderStateData() {
    }

    public static LoaderStateData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<LoaderRecord> find(ResourceLocation controllerId, BlockPos ownerPos) {
        return Optional.ofNullable(records.get(LoaderKey.of(controllerId, ownerPos)));
    }

    public boolean isManuallyFrozen(ResourceLocation controllerId, BlockPos ownerPos) {
        return find(controllerId, ownerPos).map(LoaderRecord::manuallyFrozen).orElse(false);
    }

    /** Writes the target policy and returns whether persistent state changed. */
    public boolean setManuallyFrozen(ResourceLocation controllerId, BlockPos ownerPos, boolean frozen) {
        LoaderKey key = LoaderKey.of(controllerId, ownerPos);
        LoaderRecord previous = records.getOrDefault(key, LoaderRecord.EMPTY);
        LoaderRecord updated = previous.withManuallyFrozen(frozen);
        return replaceIfChanged(key, previous, updated);
    }

    /** Records the first known placer and never overwrites an existing attribution. */
    public boolean recordPlacedBy(ResourceLocation controllerId, BlockPos ownerPos, UUID playerId) {
        LoaderKey key = LoaderKey.of(controllerId, ownerPos);
        LoaderRecord previous = records.getOrDefault(key, LoaderRecord.EMPTY);
        LoaderRecord updated = previous.withPlacedByIfAbsent(playerId);
        return replaceIfChanged(key, previous, updated);
    }

    public List<FrozenPolicy> frozenPolicies() {
        return sortedEntries().stream()
                .filter(entry -> entry.getValue().manuallyFrozen())
                .map(entry -> new FrozenPolicy(
                        LoaderIdentity.of(entry.getKey().controllerId(), entry.getKey().ownerPos()),
                        entry.getValue().placedBy()))
                .toList();
    }

    /** Clears manual policy for identities without a current supported ticket. */
    public int clearOrphanedPolicies(Set<LoaderIdentity> liveIdentities) {
        Objects.requireNonNull(liveIdentities, "liveIdentities");
        List<LoaderKey> orphaned = records.entrySet().stream()
                .filter(entry -> entry.getValue().manuallyFrozen())
                .filter(entry -> !liveIdentities.contains(LoaderIdentity.of(
                        entry.getKey().controllerId(), entry.getKey().ownerPos())))
                .map(Map.Entry::getKey)
                .toList();
        for (LoaderKey key : orphaned) {
            LoaderRecord updated = records.get(key).withManuallyFrozen(false);
            if (updated.shouldPersist()) {
                records.put(key, updated);
            } else {
                records.remove(key);
            }
        }
        if (!orphaned.isEmpty()) {
            setDirty();
        }
        return orphaned.size();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        ListTag loaders = new ListTag();
        sortedEntries().forEach(entry -> loaders.add(saveEntry(entry.getKey(), entry.getValue())));
        tag.put(TAG_LOADERS, loaders);
        return tag;
    }

    static LoaderStateData load(CompoundTag tag) {
        LoaderStateData data = new LoaderStateData();
        if (!tag.contains(TAG_VERSION, Tag.TAG_INT) || tag.getInt(TAG_VERSION) != DATA_VERSION) {
            return data;
        }
        for (Tag rawEntry : tag.getList(TAG_LOADERS, Tag.TAG_COMPOUND)) {
            loadEntry((CompoundTag) rawEntry).ifPresent(entry -> data.mergeLoaded(entry.key(), entry.record()));
        }
        return data;
    }

    private boolean replaceIfChanged(LoaderKey key, LoaderRecord previous, LoaderRecord updated) {
        if (previous.equals(updated)) {
            return false;
        }
        if (updated.shouldPersist()) {
            records.put(key, updated);
        } else {
            records.remove(key);
        }
        setDirty();
        return true;
    }

    private List<Map.Entry<LoaderKey, LoaderRecord>> sortedEntries() {
        List<Map.Entry<LoaderKey, LoaderRecord>> entries = new ArrayList<>(records.entrySet());
        entries.removeIf(entry -> !entry.getValue().shouldPersist());
        entries.sort(Map.Entry.comparingByKey(KEY_ORDER));
        return entries;
    }

    private static CompoundTag saveEntry(LoaderKey key, LoaderRecord record) {
        CompoundTag entry = new CompoundTag();
        entry.putString(TAG_CONTROLLER, key.controllerId().toString());
        CompoundTag owner = new CompoundTag();
        owner.putInt(TAG_X, key.ownerPos().getX());
        owner.putInt(TAG_Y, key.ownerPos().getY());
        owner.putInt(TAG_Z, key.ownerPos().getZ());
        entry.put(TAG_OWNER, owner);
        entry.putBoolean(TAG_FROZEN, record.manuallyFrozen());
        record.placedBy().ifPresent(playerId -> entry.putUUID(TAG_PLACED_BY, playerId));
        return entry;
    }

    private static Optional<LoadedEntry> loadEntry(CompoundTag entry) {
        if (!entry.contains(TAG_CONTROLLER, Tag.TAG_STRING) || !entry.contains(TAG_OWNER, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        ResourceLocation controllerId = ResourceLocation.tryParse(entry.getString(TAG_CONTROLLER));
        Optional<BlockPos> ownerPos = loadOwner(entry.getCompound(TAG_OWNER));
        if (controllerId == null || ownerPos.isEmpty()) {
            return Optional.empty();
        }
        boolean frozen = entry.contains(TAG_FROZEN, Tag.TAG_BYTE) && entry.getBoolean(TAG_FROZEN);
        Optional<UUID> placedBy = entry.hasUUID(TAG_PLACED_BY)
                ? Optional.of(entry.getUUID(TAG_PLACED_BY))
                : Optional.empty();
        LoaderRecord record = new LoaderRecord(frozen, placedBy);
        return record.shouldPersist()
                ? Optional.of(new LoadedEntry(LoaderKey.of(controllerId, ownerPos.get()), record))
                : Optional.empty();
    }

    private static Optional<BlockPos> loadOwner(CompoundTag owner) {
        if (!owner.contains(TAG_X, Tag.TAG_INT)
                || !owner.contains(TAG_Y, Tag.TAG_INT)
                || !owner.contains(TAG_Z, Tag.TAG_INT)) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(owner.getInt(TAG_X), owner.getInt(TAG_Y), owner.getInt(TAG_Z)));
    }

    private void mergeLoaded(LoaderKey key, LoaderRecord incoming) {
        records.merge(key, incoming, (first, next) -> new LoaderRecord(
                first.manuallyFrozen() || next.manuallyFrozen(),
                first.placedBy().isPresent() ? first.placedBy() : next.placedBy()));
    }

    private record LoaderKey(ResourceLocation controllerId, BlockPos ownerPos) {
        static LoaderKey of(ResourceLocation controllerId, BlockPos ownerPos) {
            Objects.requireNonNull(controllerId, "controllerId");
            Objects.requireNonNull(ownerPos, "ownerPos");
            return new LoaderKey(controllerId, ownerPos.immutable());
        }
    }

    private record LoadedEntry(LoaderKey key, LoaderRecord record) {
    }

    public record LoaderIdentity(ResourceLocation controllerId, BlockPos ownerPos) {
        public LoaderIdentity {
            controllerId = Objects.requireNonNull(controllerId, "controllerId");
            ownerPos = Objects.requireNonNull(ownerPos, "ownerPos").immutable();
        }

        public static LoaderIdentity of(ResourceLocation controllerId, BlockPos ownerPos) {
            return new LoaderIdentity(controllerId, ownerPos);
        }
    }

    public record FrozenPolicy(LoaderIdentity identity, Optional<UUID> placedBy) {
        public FrozenPolicy {
            identity = Objects.requireNonNull(identity, "identity");
            placedBy = Objects.requireNonNull(placedBy, "placedBy");
        }
    }
}
