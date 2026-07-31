package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockLoaderTicketResolverTest {
    @Test
    void readsNeoForgePrivateBlockOwnerShape() throws ReflectiveOperationException {
        Class<?> ownerType = Class.forName(
                "net.neoforged.neoforge.common.world.chunk.ForcedChunkManager$TicketOwner");
        Constructor<?> constructor = ownerType.getDeclaredConstructor(ResourceLocation.class, Comparable.class);
        constructor.setAccessible(true);
        ResourceLocation controller = ResourceLocation.parse("test:controller");
        BlockPos pos = new BlockPos(4, 64, -9);
        Object key = constructor.newInstance(controller, pos);

        var owner = BlockLoaderTicketResolver.readOwner(key).orElseThrow();

        assertEquals(controller, owner.controllerId());
        assertEquals(pos, owner.owner());
    }

    @Test
    void rejectsUnrelatedComparableKeys() {
        assertTrue(BlockLoaderTicketResolver.readOwner(BlockPos.ZERO).isEmpty());
    }
}
