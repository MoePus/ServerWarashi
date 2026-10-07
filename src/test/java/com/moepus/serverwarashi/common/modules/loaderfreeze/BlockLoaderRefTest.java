package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockLoaderRefTest {
    @Test
    void mixesPositionsWhoseVanillaBlockHashesAllCollide() {
        int positionCount = 4_096;
        int bucketCount = 8_192;
        int[] buckets = new int[bucketCount];
        Set<Integer> hashes = new HashSet<>();
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("test:dimension"));
        var controller = ResourceLocation.parse("test:loader");
        for (int i = 0; i < positionCount; i++) {
            BlockPos position = new BlockPos(-961 * i, 64, i);
            assertEquals(new BlockPos(0, 64, 0).hashCode(), position.hashCode());
            int hash = new BlockLoaderRef(dimension, controller, position).hashCode();
            hashes.add(hash);
            buckets[(hash ^ (hash >>> 16)) & (bucketCount - 1)]++;
        }
        assertTrue(hashes.size() >= positionCount * 99 / 100);
        for (int bucket : buckets) {
            assertTrue(bucket < 16, "Mixed loader keys must not concentrate in a few HashMap buckets");
        }
    }

    @Test
    void copiesMutableOwnerPosition() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        var ref = new BlockLoaderRef(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("test:dimension")),
                ResourceLocation.parse("test:loader"),
                mutable);

        mutable.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), ref.ownerPos());
    }
}
