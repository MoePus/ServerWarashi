package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketPerfRuntimeTest {
    @Test
    void authoritativeActionDiscardsOlderQueuedStateForSameLoader() {
        BlockLoaderRef ref = new BlockLoaderRef(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")),
                ResourceLocation.parse("test:loader"), new BlockPos(8, 64, 8));
        var queue = new TicketPerfRuntime.LoaderControlUpdateQueue();
        queue.put(new TicketPerfRuntime.LoaderControlUpdate(7L, ref, false, true));
        var authoritative = new TicketPerfRuntime.LoaderControlUpdate(7L, ref, true, false);

        assertEquals(authoritative, queue.acceptAuthoritative(authoritative));
        assertTrue(queue.drain().isEmpty());
    }
}
