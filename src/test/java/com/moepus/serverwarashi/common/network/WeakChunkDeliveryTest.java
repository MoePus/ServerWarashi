package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeakChunkDeliveryTest {
    @Test
    void sendsOnlyToSubscribedPermittedPlayersStillInSnapshotDimension() {
        assertTrue(WeakChunkDelivery.shouldSend(
                "minecraft:overworld", "minecraft:overworld", true, true));
        assertFalse(WeakChunkDelivery.shouldSend(
                "minecraft:overworld", "minecraft:the_nether", true, true));
        assertFalse(WeakChunkDelivery.shouldSend(
                "minecraft:overworld", "minecraft:overworld", false, true));
        assertFalse(WeakChunkDelivery.shouldSend(
                "minecraft:overworld", "minecraft:overworld", true, false));
    }

    @Test
    void completedSweepReplacesPreviousCache() {
        var cache = new WeakChunkDelivery.SweepCache();
        cache.mergeCurrent(List.of(chunk(1, 2, 50), chunk(3, 4, 60)));
        cache.complete(0L, 10L);
        cache.mergeCurrent(List.of(chunk(3, 4, 70)));
        cache.complete(1L, 20L);

        assertEquals(List.of(chunk(3, 4, 70)), cache.completedEntries());
        assertEquals(List.of(), cache.currentEntries());
        assertEquals(1L, cache.completedSweepId());
        assertEquals(20L, cache.completedGeneratedAt());
    }

    @Test
    void correctionUpdatesBothCompletedAndCurrentCaches() {
        var cache = new WeakChunkDelivery.SweepCache();
        cache.mergeCurrent(List.of(chunk(1, 2, 50)));
        cache.complete(0L, 10L);
        cache.mergeCurrent(List.of(chunk(1, 2, 60)));

        cache.correct(ChunkPos.asLong(1, 2), chunk(1, 2, 80));
        assertEquals(List.of(chunk(1, 2, 80)), cache.completedEntries());
        assertEquals(List.of(chunk(1, 2, 80)), cache.currentEntries());

        cache.correct(ChunkPos.asLong(1, 2), null);
        assertTrue(cache.completedEntries().isEmpty());
        assertTrue(cache.currentEntries().isEmpty());
    }

    @Test
    void emptyStorageDiscardsPartialSweepBeforeCompletion() {
        var cache = new WeakChunkDelivery.SweepCache();
        cache.mergeCurrent(List.of(chunk(1, 2, 50)));

        cache.clearCurrent();
        cache.complete(0L, 10L);

        assertTrue(cache.completedEntries().isEmpty());
        assertTrue(cache.currentEntries().isEmpty());
    }

    @Test
    void newLevelWaitsTwoHundredTicksBeforeScanning() {
        var throttle = new WeakChunkDelivery.ScanThrottle();

        for (int tick = 0; tick < WeakChunkDelivery.IDLE_SWEEP_COOLDOWN_TICKS; tick++) {
            assertFalse(throttle.shouldScan(false));
        }

        assertTrue(throttle.shouldScan(false));
    }

    @Test
    void idleSweepCompletionStartsAnotherCooldown() {
        var throttle = readyThrottle();
        throttle.onSweepComplete(true);

        assertEquals(WeakChunkDelivery.IDLE_SWEEP_COOLDOWN_TICKS, throttle.cooldownTicks());
        assertFalse(throttle.shouldScan(false));
    }

    @Test
    void viewerImmediatelyCancelsCooldown() {
        var throttle = new WeakChunkDelivery.ScanThrottle();

        assertTrue(throttle.shouldScan(true));
        assertEquals(0, throttle.cooldownTicks());
        assertTrue(throttle.shouldScan(false));
    }

    @Test
    void subscribedSweepCompletionDoesNotStartCooldown() {
        var throttle = readyThrottle();

        throttle.onSweepComplete(false);

        assertTrue(throttle.shouldScan(false));
    }

    @Test
    void idleEmptyStorageProbeStartsAnotherCooldown() {
        var throttle = readyThrottle();

        throttle.onEmptyStorage(true);

        assertEquals(WeakChunkDelivery.IDLE_SWEEP_COOLDOWN_TICKS, throttle.cooldownTicks());
        assertFalse(throttle.shouldScan(false));
    }

    private static WeakChunkDelivery.ScanThrottle readyThrottle() {
        var throttle = new WeakChunkDelivery.ScanThrottle();
        throttle.shouldScan(true);
        return throttle;
    }

    private static WeakChunkSnapshot.ChunkEntry chunk(int x, int z, int count) {
        var type = new WeakEntityType(WeakEntityType.Kind.ENTITY, "minecraft:cow");
        return new WeakChunkSnapshot.ChunkEntry(x, z, count, Map.of(type, count));
    }
}
