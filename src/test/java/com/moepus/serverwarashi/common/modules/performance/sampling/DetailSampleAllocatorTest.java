package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailSampleAllocatorTest {
    @Test
    void dispatchDetailsReassignUnknownPrimarySamplesWithoutChangingWeight() {
        TickSamples tick = unknownTick(12);
        tick.tickId = 1L;
        record(tick, 3, entityDispatchStack(), PrimaryObservationRing.CoarseState.UNKNOWN);
        record(tick, 1, blockEntityDispatchStack(), PrimaryObservationRing.CoarseState.UNKNOWN);
        record(tick, 2, unknownStack(), PrimaryObservationRing.CoarseState.UNKNOWN);
        UnknownStackReservoir stacks = new UnknownStackReservoir(7L);

        tick.applyDetails(stacks);
        stacks.completeTick(tick.tickId, tick.unknownSamples);

        assertEquals(12, tick.totalSamples);
        assertEquals(12, attributedSamples(tick));
        assertEquals(6, samples(tick, "Entity Dispatch"));
        assertEquals(2, samples(tick, "Block Entity Dispatch"));
        assertEquals(4, samples(tick, "Unknown"));
        assertTrue(tick.tickables.keySet().stream()
                .filter(key -> key.displayName().contains("Dispatch"))
                .allMatch(key -> key.type() == TickType.TASK));
        assertFalse(tick.tickables.keySet().stream()
                .anyMatch(key -> key.type().name().contains("DISPATCH")));
        assertEquals(2L, stacks.snapshot(1L, 1L, 1_000).totalObserved());
    }

    @Test
    void handlerEvidenceRemainsAvailableForCrossTickAllocation() {
        TickSamples tick = new TickSamples(null);
        PerfWorkTracker.Snapshot generic = snapshot(TickType.HANDLER, "Handler");
        for (int sample = 0; sample < 8; sample++) {
            tick.sample(generic);
        }
        record(tick, 2, handlerStack("FirstEvents", "first"),
                PrimaryObservationRing.CoarseState.HANDLER);
        record(tick, 2, handlerStack("SecondEvents", "second"),
                PrimaryObservationRing.CoarseState.HANDLER);

        tick.applyDetails(new UnknownStackReservoir(9L));

        assertEquals(8, tick.totalSamples);
        assertEquals(8, attributedSamples(tick));
        assertEquals(8, samples(tick, "Handler"));
        assertEquals(4, tick.details.handlerEvidence().values().stream()
                .mapToInt(Integer::intValue).sum());
    }

    @Test
    void insufficientDetailEvidenceLeavesGenericSamplesUntouched() {
        TickSamples tick = unknownTick(5);
        record(tick, 1, entityDispatchStack(), PrimaryObservationRing.CoarseState.UNKNOWN);

        tick.applyDetails(new UnknownStackReservoir(11L));

        assertEquals(5, tick.totalSamples);
        assertEquals(5, attributedSamples(tick));
        assertEquals(5, samples(tick, "Unknown"));
        assertEquals(0, samples(tick, "Entity Dispatch"));
    }

    @Test
    void blockEntityWrapperLookupReassignsUnknownToDispatch() {
        TickSamples tick = unknownTick(5);
        record(tick, 2, blockEntityPositionLookupStack(),
                PrimaryObservationRing.CoarseState.UNKNOWN);

        tick.applyDetails(new UnknownStackReservoir(12L));

        assertEquals(5, tick.totalSamples);
        assertEquals(5, attributedSamples(tick));
        assertEquals(5, samples(tick, "Block Entity Dispatch"));
        assertEquals(0, samples(tick, "Unknown"));
    }

    @Test
    void entityIterationReassignsUnknownToDispatch() {
        TickSamples tick = unknownTick(5);
        record(tick, 2, entityIterationStack(), PrimaryObservationRing.CoarseState.UNKNOWN);

        tick.applyDetails(new UnknownStackReservoir(14L));

        assertEquals(5, tick.totalSamples);
        assertEquals(5, attributedSamples(tick));
        assertEquals(5, samples(tick, "Entity Dispatch"));
        assertEquals(0, samples(tick, "Unknown"));
    }

    @Test
    void absentJfrPreservesKnownAndCoarsePrimaryCategories() {
        TickSamples tick = new TickSamples(null);
        tick.sample(snapshot(TickType.ENTITY, "Known Entity"));
        tick.sample(snapshot(TickType.BLOCK_ENTITY, "Known Block Entity"));
        tick.sample(snapshot(TickType.HANDLER, "Handler"));
        tick.sample(PerfWorkTracker.unknownSnapshot());

        tick.applyDetails(new UnknownStackReservoir(13L));

        assertEquals(4, tick.totalSamples);
        assertEquals(4, attributedSamples(tick));
        assertTrue(tick.tickables.keySet().stream()
                .anyMatch(key -> key.type() == TickType.ENTITY));
        assertTrue(tick.tickables.keySet().stream()
                .anyMatch(key -> key.type() == TickType.BLOCK_ENTITY));
        assertEquals(1, samples(tick, "Handler"));
        assertEquals(1, samples(tick, "Unknown"));
    }

    @Test
    void trackedEntityAndBlockEntityDetailsCannotChangeUnknown() {
        TickSamples tick = unknownTick(6);
        record(tick, 2, entityExecutionStack(), PrimaryObservationRing.CoarseState.TRACKED);
        record(tick, 2, blockEntityExecutionStack(),
                PrimaryObservationRing.CoarseState.TRACKED);
        UnknownStackReservoir stacks = new UnknownStackReservoir(15L);

        tick.applyDetails(stacks);

        assertEquals(6, tick.totalSamples);
        assertEquals(6, attributedSamples(tick));
        assertEquals(6, samples(tick, "Unknown"));
        assertEquals(0, samples(tick, "Entity Dispatch"));
        assertEquals(0L, stacks.snapshot(1L, 1L, 1_000).totalObserved());
    }

    @Test
    void trackedPrimaryChunkAndTaskStacksCannotEnterUnknownReservoir() {
        TickSamples tick = unknownTick(6);
        record(tick, 2, chunkExecutionStack(), PrimaryObservationRing.CoarseState.TRACKED);
        record(tick, 2, taskExecutionStack(), PrimaryObservationRing.CoarseState.TRACKED);
        UnknownStackReservoir stacks = new UnknownStackReservoir(17L);

        tick.applyDetails(stacks);

        assertEquals(6, tick.totalSamples);
        assertEquals(6, attributedSamples(tick));
        assertEquals(6, samples(tick, "Unknown"));
        assertEquals(0L, stacks.snapshot(1L, 1L, 1_000).totalObserved());
    }

    @Test
    void unknownPrimaryRetainsOnlyUnclassifiedStacks() {
        TickSamples tick = unknownTick(6);
        tick.tickId = 1L;
        record(tick, 1, entityExecutionStack(), PrimaryObservationRing.CoarseState.UNKNOWN);
        record(tick, 1, createBlockEntityExecutionStack(),
                PrimaryObservationRing.CoarseState.UNKNOWN);
        record(tick, 1, chunkExecutionStack(), PrimaryObservationRing.CoarseState.UNKNOWN);
        record(tick, 1, taskExecutionStack(), PrimaryObservationRing.CoarseState.UNKNOWN);
        record(tick, 1, handlerStack("MissedEvents", "tick"),
                PrimaryObservationRing.CoarseState.UNKNOWN);
        record(tick, 2, unknownStack(), PrimaryObservationRing.CoarseState.UNKNOWN);
        UnknownStackReservoir stacks = new UnknownStackReservoir(19L);

        tick.applyDetails(stacks);
        stacks.completeTick(tick.tickId, tick.unknownSamples);
        UnknownStackSnapshot snapshot = stacks.snapshot(1L, 1L, 1_000);

        assertEquals(6, attributedSamples(tick));
        assertEquals(6, samples(tick, "Unknown"));
        assertEquals(2L, snapshot.totalObserved());
        assertEquals(6L, snapshot.totalPrimarySamples());
        assertTrue(java.util.stream.IntStream.range(0, snapshot.retainedCount())
                .mapToObj(snapshot::stackAt)
                .flatMap(java.util.Arrays::stream)
                .noneMatch(frame -> frame.getClassName().startsWith("com.simibubi.create.")));
    }

    private static TickSamples unknownTick(int samples) {
        TickSamples tick = new TickSamples(null);
        for (int sample = 0; sample < samples; sample++) {
            tick.sample(PerfWorkTracker.unknownSnapshot(), SampleMetricMode.STATISTICAL,
                    PerfReportData.AttributionSource.FALLBACK_UNKNOWN,
                    PerfReportData.Confidence.LOW);
            tick.unknownSamples++;
        }
        return tick;
    }

    private static void record(TickSamples tick,
                               int count,
                               StackTraceElement[] stack,
                               PrimaryObservationRing.CoarseState state) {
        for (int sample = 0; sample < count; sample++) {
            tick.recordDetail(new AsyncStackSample(sample, stack), state);
        }
    }

    private static int attributedSamples(TickSamples tick) {
        return tick.tickables.values().stream().mapToInt(count -> count.count).sum();
    }

    private static int samples(TickSamples tick, String displayName) {
        return tick.tickables.entrySet().stream()
                .filter(entry -> displayName.equals(entry.getKey().displayName()))
                .map(Map.Entry::getValue)
                .mapToInt(count -> count.count)
                .sum();
    }

    private static PerfWorkTracker.Snapshot snapshot(TickType type, String displayName) {
        return new PerfWorkTracker.Snapshot(type, null, DetailSampleAllocatorTest.class,
                null, TickDetailKind.NONE, -1, false,
                0, 0, 0, false, "", displayName);
    }

    private static StackTraceElement[] entityDispatchStack() {
        return stack(frame("net.minecraft.server.level.ServerLevel", "tickNonPassenger"));
    }

    private static StackTraceElement[] blockEntityDispatchStack() {
        return stack(frame(
                "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity", "tick"));
    }

    private static StackTraceElement[] blockEntityPositionLookupStack() {
        return stack(
                frame("net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity",
                        "getPos"),
                frame("net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper",
                        "getPos"),
                frame("net.minecraft.world.level.Level", "tickBlockEntities"));
    }

    private static StackTraceElement[] unknownStack() {
        return stack(frame("com.example.TickWork", "calculate"));
    }

    private static StackTraceElement[] entityExecutionStack() {
        return stack(
                frame("com.example.SomeEntity", "tick"),
                frame("net.minecraft.server.level.ServerLevel", "tickNonPassenger"));
    }

    private static StackTraceElement[] entityIterationStack() {
        return stack(
                frame("com.example.SomeEntity", "checkDespawn"),
                frame("net.minecraft.server.level.ServerLevel", "lambda$tick$4"),
                frame("net.minecraft.world.level.entity.EntityTickList", "forEach"));
    }

    private static StackTraceElement[] blockEntityExecutionStack() {
        return stack(
                frame("com.example.SomeBlockEntity", "serverTick"),
                frame("net.minecraft.world.level.block.entity.BlockEntityTicker", "tick"),
                frame("net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity", "tick"));
    }

    private static StackTraceElement[] createBlockEntityExecutionStack() {
        return stack(
                frame("com.simibubi.create.foundation.blockEntity.SmartBlockEntity$$Lambda.0x0",
                        "accept"),
                frame("it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap$ValuesCollection",
                        "forEach"),
                frame("com.simibubi.create.foundation.blockEntity.SmartBlockEntity",
                        "forEachBehaviour"),
                frame("com.simibubi.create.foundation.blockEntity.SmartBlockEntity", "tick"),
                frame("com.simibubi.create.content.kinetics.base.KineticBlockEntity", "tick"),
                frame("com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker", "tick"),
                frame("net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity", "tick"),
                frame("net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper",
                        "tick"),
                frame("net.minecraft.world.level.Level", "tickBlockEntities"));
    }

    private static StackTraceElement[] chunkExecutionStack() {
        return stack(
                frame("com.example.ChunkWork", "run"),
                frame("net.minecraft.server.level.ServerLevel", "tickChunk"));
    }

    private static StackTraceElement[] taskExecutionStack() {
        return stack(
                frame("com.example.NamedTask", "run"),
                frame("net.minecraft.server.TickTask", "run"));
    }

    private static StackTraceElement[] handlerStack(String owner, String method) {
        return stack(
                frame("com.example." + owner, method),
                frame("net.neoforged.bus.EventBus", "post"),
                frame("net.neoforged.neoforge.event.EventHooks", "fireServerTickPre"));
    }

    private static StackTraceElement frame(String className, String methodName) {
        return new StackTraceElement(className, methodName, "Test.java", 1);
    }

    private static StackTraceElement[] stack(StackTraceElement... frames) {
        return frames;
    }
}
