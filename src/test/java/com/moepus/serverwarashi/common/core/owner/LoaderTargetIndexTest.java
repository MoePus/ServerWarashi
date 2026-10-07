package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoaderTargetIndexTest {
    private static final int LARGE_TARGET_COUNT = 20_000;
    private static final TicketType<String> TYPE =
            TicketType.create("loader_index_test", Comparator.<String>naturalOrder());
    private static final TicketType<IdentityOnlyKey> IDENTITY_TYPE =
            TicketType.create("loader_identity_test", (a, b) -> 0);
    private static final BlockLoaderRef REF = new BlockLoaderRef(
            Level.OVERWORLD, ResourceLocation.parse("test:loader"), BlockPos.ZERO);

    @Test
    void deduplicatesCaptureAndAppendByIdentityWithoutCallingTicketHashOrEquals() {
        var first = target(0, identityOnlyTicket());
        var second = target(0, identityOnlyTicket());
        var captured = new ArrayList<>(List.of(first, first));
        var index = new LoaderTargetIndex(Map.of(REF, captured));
        captured.clear();

        assertEquals(1, index.targets(REF).size());
        assertFalse(index.add(REF, target(1, first.ticket())));
        assertTrue(index.add(REF, second));
        assertEquals(2, index.targets(REF).size());
        assertFalse(index.remove(identityOnlyTicket()));
        assertTrue(index.remove(first.ticket()));
        assertSame(second.ticket(), index.targets(REF).getFirst().ticket());
    }

    @Test
    void retainsDistinctEqualTicketsAtCollidingPackedChunkCoordinates() {
        var index = new LoaderTargetIndex(Map.of(REF, List.of()));
        List<Ticket<?>> tickets = new ArrayList<>();
        for (int i = 0; i < LARGE_TARGET_COUNT; i++) {
            long chunk = ChunkPos.asLong(i, i);
            assertEquals(0, Long.hashCode(chunk));
            var ticket = new Ticket<>(TYPE, 31, "same-key", false);
            tickets.add(ticket);
            assertTrue(index.add(REF, new LoaderTicketTarget(chunk, ticket)));
        }
        assertEquals(LARGE_TARGET_COUNT, index.targets(REF).size());
        assertEquals(tickets.getFirst(), tickets.getLast());
        assertFalse(index.remove(new Ticket<>(TYPE, 31, "same-key", false)));
        for (Ticket<?> ticket : tickets) {
            assertFalse(index.add(REF, target(0, ticket)));
            assertTrue(index.remove(ticket));
        }
        assertTrue(index.targets(REF).isEmpty());
    }

    @Test
    void repeatedRemovalAndReinsertionKeepsOnlyTheLiveTicket() {
        var index = new LoaderTargetIndex(Map.of(REF, List.of()));
        for (int i = 0; i < LARGE_TARGET_COUNT; i++) {
            var next = target(0, identityOnlyTicket());
            assertTrue(index.add(REF, next));
            assertEquals(1, index.targets(REF).size());
            assertTrue(index.remove(next.ticket()));
            assertFalse(index.remove(next.ticket()));
            assertTrue(index.targets(REF).isEmpty());
        }
    }

    @Test
    void rejectsUnknownLoadersAndReturnsDetachedImmutableLists() {
        var target = target(0, identityOnlyTicket());
        var index = new LoaderTargetIndex(Map.of(REF, List.of(target)));
        var unknown = new BlockLoaderRef(REF.dimension(), REF.controllerId(), new BlockPos(1, 0, 0));
        assertFalse(index.add(unknown, target));
        assertTrue(index.targets(unknown).isEmpty());
        var copied = index.targets(REF);
        assertThrows(UnsupportedOperationException.class, copied::clear);
        assertTrue(index.remove(target.ticket()));
        assertSame(target.ticket(), copied.getFirst().ticket());
        assertTrue(index.targets(REF).isEmpty());
    }

    private static LoaderTicketTarget target(long chunk, Ticket<?> ticket) {
        return new LoaderTicketTarget(chunk, ticket);
    }

    private static Ticket<?> identityOnlyTicket() {
        return new Ticket<>(IDENTITY_TYPE, 31, new IdentityOnlyKey(), false);
    }

    private static final class IdentityOnlyKey {
        @Override
        public int hashCode() {
            throw new AssertionError("Ticket.hashCode must not participate in the identity index");
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("Ticket.equals must not participate in the identity index");
        }
    }
}
