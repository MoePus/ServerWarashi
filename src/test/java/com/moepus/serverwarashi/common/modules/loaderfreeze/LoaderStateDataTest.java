package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderStateDataTest {
    private static final ResourceLocation FIRST_CONTROLLER = id("test:first");
    private static final ResourceLocation SECOND_CONTROLLER = id("test:second");
    private static final BlockPos FIRST_POS = new BlockPos(10, 64, -20);
    private static final BlockPos SECOND_POS = new BlockPos(-5, 70, 8);
    private static final UUID FIRST_PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND_PLAYER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void manualPolicyWritesAreIdempotentAndEmptyPolicyIsNotRetained() {
        LoaderStateData data = new LoaderStateData();

        assertFalse(data.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, false));
        assertTrue(data.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, true));
        data.setDirty(false);
        assertFalse(data.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, true));
        assertFalse(data.isDirty());

        assertTrue(data.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, false));
        assertTrue(data.find(FIRST_CONTROLLER, FIRST_POS).isEmpty());
    }

    @Test
    void placedByIsFirstWriteWinsAndSurvivesClearingManualPolicy() {
        LoaderStateData data = new LoaderStateData();

        assertTrue(data.recordPlacedBy(FIRST_CONTROLLER, FIRST_POS, FIRST_PLAYER));
        data.setDirty(false);
        assertFalse(data.recordPlacedBy(FIRST_CONTROLLER, FIRST_POS, SECOND_PLAYER));
        assertFalse(data.isDirty());
        assertTrue(data.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, true));
        assertTrue(data.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, false));

        assertEquals(new LoaderRecord(false, Optional.of(FIRST_PLAYER)),
                data.find(FIRST_CONTROLLER, FIRST_POS).orElseThrow());
    }

    @Test
    void versionOneRoundTripPreservesBothRecordKinds() {
        LoaderStateData original = new LoaderStateData();
        original.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, true);
        original.recordPlacedBy(SECOND_CONTROLLER, SECOND_POS, SECOND_PLAYER);

        LoaderStateData restored = LoaderStateData.load(save(original));

        assertEquals(new LoaderRecord(true, Optional.empty()),
                restored.find(FIRST_CONTROLLER, FIRST_POS).orElseThrow());
        assertEquals(new LoaderRecord(false, Optional.of(SECOND_PLAYER)),
                restored.find(SECOND_CONTROLLER, SECOND_POS).orElseThrow());
        assertFalse(restored.isDirty());
    }

    @Test
    void saveUsesStableControllerThenCoordinateOrder() {
        LoaderStateData data = new LoaderStateData();
        data.setManuallyFrozen(SECOND_CONTROLLER, FIRST_POS, true);
        data.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, true);
        data.setManuallyFrozen(FIRST_CONTROLLER, SECOND_POS, true);

        ListTag loaders = save(data).getList("loaders", CompoundTag.TAG_COMPOUND);

        assertEquals("test:first", loaders.getCompound(0).getString("controller"));
        assertEquals(SECOND_POS.getX(), owner(loaders, 0).getInt("X"));
        assertEquals("test:first", loaders.getCompound(1).getString("controller"));
        assertEquals(FIRST_POS.getX(), owner(loaders, 1).getInt("X"));
        assertEquals("test:second", loaders.getCompound(2).getString("controller"));
    }

    @Test
    void malformedEntriesAreSkippedAndMalformedUuidOnlyDropsAttribution() {
        CompoundTag validFrozen = entry(FIRST_CONTROLLER.toString(), FIRST_POS, true, null);
        validFrozen.putIntArray("placed_by", new int[]{1, 2});
        CompoundTag invalidController = entry("Bad Controller", SECOND_POS, true, FIRST_PLAYER);
        CompoundTag invalidOwner = entry(SECOND_CONTROLLER.toString(), SECOND_POS, true, SECOND_PLAYER);
        invalidOwner.getCompound("owner").putString("Y", "bad");

        LoaderStateData data = LoaderStateData.load(root(validFrozen, invalidController, invalidOwner));

        assertEquals(new LoaderRecord(true, Optional.empty()),
                data.find(FIRST_CONTROLLER, FIRST_POS).orElseThrow());
        assertTrue(data.find(SECOND_CONTROLLER, SECOND_POS).isEmpty());
    }

    @Test
    void duplicateKeysKeepFirstValidPlayerAndAnyFrozenPolicy() {
        CompoundTag first = entry(FIRST_CONTROLLER.toString(), FIRST_POS, false, FIRST_PLAYER);
        CompoundTag second = entry(FIRST_CONTROLLER.toString(), FIRST_POS, true, SECOND_PLAYER);

        LoaderRecord record = LoaderStateData.load(root(first, second))
                .find(FIRST_CONTROLLER, FIRST_POS).orElseThrow();

        assertTrue(record.manuallyFrozen());
        assertEquals(Optional.of(FIRST_PLAYER), record.placedBy());
        assertEquals(1, save(LoaderStateData.load(root(first, second))).getList("loaders", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void missingOrUnsupportedVersionDoesNotLoadRecords() {
        CompoundTag missingVersion = root(entry(FIRST_CONTROLLER.toString(), FIRST_POS, true, FIRST_PLAYER));
        missingVersion.remove("version");
        CompoundTag futureVersion = root(entry(FIRST_CONTROLLER.toString(), FIRST_POS, true, FIRST_PLAYER));
        futureVersion.putInt("version", 2);

        assertTrue(LoaderStateData.load(missingVersion).find(FIRST_CONTROLLER, FIRST_POS).isEmpty());
        assertTrue(LoaderStateData.load(futureVersion).find(FIRST_CONTROLLER, FIRST_POS).isEmpty());
    }

    @Test
    void clearsOnlyOrphanedFrozenPoliciesAndRetainsAttribution() {
        LoaderStateData data = new LoaderStateData();
        data.setManuallyFrozen(FIRST_CONTROLLER, FIRST_POS, true);
        data.recordPlacedBy(FIRST_CONTROLLER, FIRST_POS, FIRST_PLAYER);
        data.setManuallyFrozen(SECOND_CONTROLLER, SECOND_POS, true);
        var live = LoaderStateData.LoaderIdentity.of(FIRST_CONTROLLER, FIRST_POS);

        assertEquals(1, data.clearOrphanedPolicies(Set.of(live)));
        assertTrue(data.isManuallyFrozen(FIRST_CONTROLLER, FIRST_POS));
        assertTrue(data.find(SECOND_CONTROLLER, SECOND_POS).isEmpty());
        assertEquals(List.of(new LoaderStateData.FrozenPolicy(
                live, Optional.of(FIRST_PLAYER))), data.frozenPolicies());

        assertEquals(1, data.clearOrphanedPolicies(Set.of()));
        assertTrue(data.frozenPolicies().isEmpty());
        assertEquals(Optional.of(FIRST_PLAYER), data.find(FIRST_CONTROLLER, FIRST_POS)
                .orElseThrow().placedBy());
    }

    private static CompoundTag save(LoaderStateData data) {
        return data.save(new CompoundTag(), null);
    }

    private static CompoundTag root(CompoundTag... entries) {
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        ListTag loaders = new ListTag();
        for (CompoundTag entry : entries) {
            loaders.add(entry);
        }
        root.put("loaders", loaders);
        return root;
    }

    private static CompoundTag entry(String controller, BlockPos pos, boolean frozen, UUID placedBy) {
        CompoundTag entry = new CompoundTag();
        entry.putString("controller", controller);
        CompoundTag owner = new CompoundTag();
        owner.putInt("X", pos.getX());
        owner.putInt("Y", pos.getY());
        owner.putInt("Z", pos.getZ());
        entry.put("owner", owner);
        entry.putBoolean("frozen", frozen);
        if (placedBy != null) {
            entry.putUUID("placed_by", placedBy);
        }
        return entry;
    }

    private static CompoundTag owner(ListTag loaders, int index) {
        return loaders.getCompound(index).getCompound("owner");
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
