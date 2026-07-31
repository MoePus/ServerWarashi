package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockLoaderRefTest {
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
