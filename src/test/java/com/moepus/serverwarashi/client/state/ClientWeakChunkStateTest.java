package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import com.moepus.serverwarashi.common.network.WeakChunkPayload;
import com.moepus.serverwarashi.common.network.WeakChunkCorrectionPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientWeakChunkStateTest {
    private static final String DIMENSION = "minecraft:overworld";

    @AfterEach
    void reset() {
        ClientWeakChunkState.clear();
    }

    @Test
    void mergesDeltaChunksAndRemovesUnseenEntriesAtSweepEnd() {
        ClientWeakChunkState.update(payload(0L, false, chunk(1, 2)));
        ClientWeakChunkState.update(payload(0L, false, chunk(3, 4)));
        assertEquals(2, ClientWeakChunkState.snapshot().chunks().size());

        ClientWeakChunkState.update(payload(1L, true, chunk(3, 4)));
        assertEquals(List.of(chunk(3, 4)), ClientWeakChunkState.snapshot().chunks());
    }

    @Test
    void newerSweepKeepsEntriesUntilItsCompletion() {
        ClientWeakChunkState.update(payload(0L, true, chunk(1, 2)));
        ClientWeakChunkState.update(payload(1L, false, chunk(3, 4)));
        assertEquals(2, ClientWeakChunkState.snapshot().chunks().size());
    }

    @Test
    void retainsMoreWarningsThanOneWireBatch() {
        WeakChunkSnapshot.ChunkEntry[] firstBatch = new WeakChunkSnapshot.ChunkEntry[8];
        for (int index = 0; index < firstBatch.length; index++) {
            firstBatch[index] = chunk(index, 0);
        }
        ClientWeakChunkState.update(payload(0L, false, firstBatch));
        ClientWeakChunkState.update(payload(0L, true, chunk(8, 0)));

        assertEquals(9, ClientWeakChunkState.snapshot().chunks().size());
    }

    @Test
    void identicalRefreshDoesNotAdvanceRenderRevision() {
        ClientWeakChunkState.update(payload(0L, false, chunk(1, 2)));
        ClientWeakChunkState.tick();
        ClientWeakChunkState.Snapshot snapshot = ClientWeakChunkState.snapshot();

        ClientWeakChunkState.update(payload(0L, false, chunk(1, 2)));
        ClientWeakChunkState.tick();

        assertSame(snapshot, ClientWeakChunkState.snapshot());
        assertEquals(snapshot.revision(), ClientWeakChunkState.snapshot().revision());
    }

    @Test
    void coalescesMultiplePayloadsIntoOneRenderRevisionPerTick() {
        long revision = ClientWeakChunkState.snapshot().revision();
        ClientWeakChunkState.update(payload(0L, false, chunk(1, 2)));
        ClientWeakChunkState.update(payload(0L, false, chunk(3, 4)));

        ClientWeakChunkState.tick();

        assertEquals(revision + 1L, ClientWeakChunkState.snapshot().revision());
    }

    @Test
    void correctionUpsertsAndRemovesOneChunk() {
        ClientWeakChunkState.update(payload(0L, true, chunk(1, 2)));
        WeakChunkSnapshot.ChunkEntry updated = chunk(1, 2, 80);

        ClientWeakChunkState.correct(correction(updated));
        assertEquals(updated, ClientWeakChunkState.chunk(DIMENSION, 1, 2));

        ClientWeakChunkState.correct(new WeakChunkCorrectionPayload(
                DIMENSION, 1, 2, 3L, java.util.Optional.empty()));
        assertEquals(null, ClientWeakChunkState.chunk(DIMENSION, 1, 2));
    }

    @Test
    void ignoresCorrectionForAnotherDimension() {
        ClientWeakChunkState.update(payload(0L, true, chunk(1, 2)));
        ClientWeakChunkState.correct(new WeakChunkCorrectionPayload(
                "minecraft:the_nether", 1, 2, 3L, chunk(1, 2, 80)));

        assertEquals(chunk(1, 2), ClientWeakChunkState.chunk(DIMENSION, 1, 2));
    }

    @Test
    void correctionSurvivesCurrentSweepCompletion() {
        ClientWeakChunkState.update(payload(0L, true, chunk(1, 2)));
        ClientWeakChunkState.update(payload(1L, false));
        WeakChunkSnapshot.ChunkEntry corrected = chunk(3, 4, 80);

        ClientWeakChunkState.correct(correction(corrected));
        ClientWeakChunkState.update(payload(1L, true));

        assertEquals(List.of(corrected), ClientWeakChunkState.snapshot().chunks());
    }

    private static WeakChunkPayload payload(long sweepId,
                                            boolean complete,
                                            WeakChunkSnapshot.ChunkEntry... chunks) {
        return new WeakChunkPayload(new WeakChunkSnapshot(DIMENSION, 1L, List.of(chunks)),
                sweepId, complete);
    }

    private static WeakChunkSnapshot.ChunkEntry chunk(int x, int z) {
        return chunk(x, z, 50);
    }

    private static WeakChunkSnapshot.ChunkEntry chunk(int x, int z, int count) {
        var item = new WeakEntityType(WeakEntityType.Kind.ITEM, "minecraft:cobblestone");
        return new WeakChunkSnapshot.ChunkEntry(x, z, count, Map.of(item, count));
    }

    private static WeakChunkCorrectionPayload correction(WeakChunkSnapshot.ChunkEntry entry) {
        return new WeakChunkCorrectionPayload(DIMENSION, entry.chunkX(), entry.chunkZ(), 2L, entry);
    }
}
