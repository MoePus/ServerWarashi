package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerfStackAttributorTest {
    private static final String EVENT_BUS = "net.neoforged.bus.EventBus";
    private static final String EVENT_HOOKS = "net.neoforged.neoforge.event.EventHooks";
    private static final String SERVER_LEVEL = "net.minecraft.server.level.ServerLevel";
    private static final String ENTITY_TICK_LIST =
            "net.minecraft.world.level.entity.EntityTickList";
    private static final String BOUND_BLOCK_ENTITY =
            "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity";
    private static final String REBINDABLE_BLOCK_ENTITY =
            "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper";
    private static final String TICK_PACKAGE = "net.neoforged.neoforge.event.tick.";

    @Test
    void identifiesAllServerTickEventHandlers() {
        List<TickCase> cases = List.of(
                new TickCase("fireServerTickPre", "ServerTickEvent.Pre"),
                new TickCase("fireServerTickPost", "ServerTickEvent.Post"),
                new TickCase("fireLevelTickPre", "LevelTickEvent.Pre"),
                new TickCase("fireLevelTickPost", "LevelTickEvent.Post"),
                new TickCase("firePlayerTickPre", "PlayerTickEvent.Pre"),
                new TickCase("firePlayerTickPost", "PlayerTickEvent.Post"),
                new TickCase("fireEntityTickPre", "EntityTickEvent.Pre"),
                new TickCase("fireEntityTickPost", "EntityTickEvent.Post")
        );

        for (TickCase tickCase : cases) {
            PerfStackAttributor.Classification detail = PerfStackAttributor.classify(stack(
                    frame("com.example.ServerEvents", "onTick"),
                    frame("net.neoforged.bus.ConsumerEventHandler", "invoke"),
                    frame(EVENT_BUS, "post"),
                    frame(EVENT_BUS, "post"),
                    frame(EVENT_HOOKS, tickCase.hook())
            ));
            PerfWorkTracker.Snapshot sample = detail.snapshot();

            assertEquals(PerfStackAttributor.Kind.HANDLER, detail.kind());
            assertEquals(TickType.HANDLER, sample.type());
            assertEquals("ServerEvents#onTick(" + tickCase.event() + ")", sample.displayName());
            assertEquals("com.example.ServerEvents#onTick(" + TICK_PACKAGE + tickCase.event() + ")",
                    sample.eventLoop());
        }
    }

    @Test
    void nestedEventWorkStaysAttributedToTheOuterTickHandler() {
        PerfStackAttributor.Classification detail = PerfStackAttributor.classify(stack(
                frame("com.example.NestedEvents", "onNested"),
                frame(EVENT_BUS, "post"),
                frame("com.example.EventSender", "sendNested"),
                frame("com.example.ServerEvents", "onServerTick"),
                frame("net.neoforged.bus.ConsumerEventHandler", "invoke"),
                frame(EVENT_BUS, "post"),
                frame(EVENT_HOOKS, "fireServerTickPost")
        ));

        assertEquals(PerfStackAttributor.Kind.HANDLER, detail.kind());
        assertEquals("ServerEvents#onServerTick(ServerTickEvent.Post)",
                detail.snapshot().displayName());
    }

    @Test
    void recognizesMethodScopeInnerEventHookFrames() {
        PerfStackAttributor.Classification detail = PerfStackAttributor.classify(stack(
                frame("com.example.ServerEvents", "onTick"),
                frame(EVENT_BUS, "post"),
                frame(EVENT_HOOKS, "fireServerTickPre$mixinlite$scope$0")
        ));

        assertEquals(PerfStackAttributor.Kind.HANDLER, detail.kind());
        assertEquals("ServerEvents#onTick(ServerTickEvent.Pre)",
                detail.snapshot().displayName());
    }

    @Test
    void entityTickAncestorIsTrackedRatherThanDispatch() {
        PerfStackAttributor.Classification subclass = PerfStackAttributor.classify(stack(
                frame("com.example.SomeEntity", "tick"),
                frame(SERVER_LEVEL, "tickNonPassenger")
        ));
        PerfStackAttributor.Classification base = PerfStackAttributor.classify(stack(
                frame("net.minecraft.world.entity.Entity", "tick"),
                frame(SERVER_LEVEL, "tickNonPassenger")
        ));

        assertEquals(PerfStackAttributor.Kind.TRACKED, subclass.kind());
        assertEquals(PerfStackAttributor.Kind.TRACKED, base.kind());
    }

    @Test
    void blockEntityTickerAncestorIsTrackedRatherThanDispatch() {
        PerfStackAttributor.Classification detail = PerfStackAttributor.classify(stack(
                frame("com.example.SomeBlockEntity", "serverTick"),
                frame("net.minecraft.world.level.block.entity.BlockEntityTicker", "tick"),
                frame(BOUND_BLOCK_ENTITY, "tick")
        ));

        assertEquals(PerfStackAttributor.Kind.TRACKED, detail.kind());
    }

    @Test
    void createBlockEntityExecutionIsTrackedRatherThanUnknown() {
        PerfStackAttributor.Classification detail = PerfStackAttributor.classify(stack(
                frame("com.simibubi.create.foundation.blockEntity.SmartBlockEntity$$Lambda.0x0",
                        "accept"),
                frame("it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap$ValuesCollection",
                        "forEach"),
                frame("com.simibubi.create.foundation.blockEntity.SmartBlockEntity",
                        "forEachBehaviour"),
                frame("com.simibubi.create.foundation.blockEntity.SmartBlockEntity", "tick"),
                frame("com.simibubi.create.content.kinetics.base.KineticBlockEntity", "tick"),
                frame("com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker", "tick"),
                frame(BOUND_BLOCK_ENTITY, "tick"),
                frame("net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper",
                        "tick"),
                frame("net.minecraft.world.level.Level", "tickBlockEntities")
        ));

        assertEquals(PerfStackAttributor.Kind.TRACKED, detail.kind());
    }

    @Test
    void wrapperExecutionIsDispatch() {
        assertEquals(PerfStackAttributor.Kind.ENTITY_DISPATCH,
                PerfStackAttributor.classify(stack(
                        frame(SERVER_LEVEL, "tickNonPassenger"))).kind());
        assertEquals(PerfStackAttributor.Kind.BLOCK_ENTITY_DISPATCH,
                PerfStackAttributor.classify(stack(
                        frame(BOUND_BLOCK_ENTITY, "tick"))).kind());
        assertEquals(PerfStackAttributor.Kind.ENTITY_DISPATCH,
                PerfStackAttributor.classify(stack(
                        frame("net.minecraft.util.profiling.ActiveProfiler", "incrementCounter"),
                        frame(SERVER_LEVEL, "tickNonPassenger"))).kind());
    }

    @Test
    void blockEntityWrapperPositionLookupIsDispatch() {
        PerfStackAttributor.Classification detail = PerfStackAttributor.classify(stack(
                frame(BOUND_BLOCK_ENTITY, "getPos"),
                frame(REBINDABLE_BLOCK_ENTITY, "getPos"),
                frame("net.minecraft.world.level.Level", "tickBlockEntities"),
                frame("net.minecraft.server.level.ServerLevel", "tick")
        ));

        assertEquals(PerfStackAttributor.Kind.BLOCK_ENTITY_DISPATCH, detail.kind());
        assertEquals(TickType.TASK, detail.snapshot().type());
        assertEquals("Block Entity Dispatch", detail.snapshot().displayName());
    }

    @Test
    void entityIterationBeforeTickIsDispatch() {
        PerfStackAttributor.Classification detail = PerfStackAttributor.classify(stack(
                frame("com.example.SomeEntity", "checkDespawn"),
                frame(SERVER_LEVEL, "lambda$tick$4"),
                frame(ENTITY_TICK_LIST, "forEach"),
                frame(SERVER_LEVEL, "tick")
        ));

        assertEquals(PerfStackAttributor.Kind.ENTITY_DISPATCH, detail.kind());
        assertEquals(TickType.TASK, detail.snapshot().type());
        assertEquals("Entity Dispatch", detail.snapshot().displayName());
    }

    @Test
    void passengerWrapperAndActualRideTickAreSeparated() {
        PerfStackAttributor.Classification wrapper = PerfStackAttributor.classify(stack(
                frame("net.minecraft.world.entity.Entity", "getVehicle"),
                frame(SERVER_LEVEL, "tickPassenger")
        ));
        PerfStackAttributor.Classification actual = PerfStackAttributor.classify(stack(
                frame("com.example.SomePassenger", "rideTick"),
                frame(SERVER_LEVEL, "tickPassenger")
        ));

        assertEquals(PerfStackAttributor.Kind.ENTITY_DISPATCH, wrapper.kind());
        assertEquals(PerfStackAttributor.Kind.TRACKED, actual.kind());
    }

    @Test
    void knownChunkAndTaskStacksAreTracked() {
        List<StackTraceElement[]> stacks = List.of(
                stack(
                        frame("com.example.ChunkWork", "run"),
                        frame(SERVER_LEVEL, "tickChunk")
                ),
                stack(
                        frame("com.example.LongTask", "run"),
                        frame("net.minecraft.server.TickTask", "run"),
                        frame("net.minecraft.util.thread.BlockableEventLoop", "doRunTask")
                )
        );

        for (StackTraceElement[] stack : stacks) {
            assertEquals(PerfStackAttributor.Kind.TRACKED,
                    PerfStackAttributor.classify(stack).kind());
        }
    }

    @Test
    void distinguishesIdleFromUnmatchedActiveWork() {
        assertEquals(PerfStackAttributor.Kind.IDLE, PerfStackAttributor.classify(stack(
                frame("java.util.concurrent.locks.LockSupport", "parkNanos"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "waitForTasks"),
                frame("net.minecraft.server.MinecraftServer", "waitForTasks"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "managedBlock"),
                frame("net.minecraft.server.MinecraftServer", "waitUntilNextTick")
        )).kind());
        PerfStackAttributor.Classification active = PerfStackAttributor.classify(stack(
                frame("com.example.TickWork", "calculate")));
        assertEquals(PerfStackAttributor.Kind.UNKNOWN, active.kind());
        assertEquals(TickType.TASK, active.snapshot().type());
        assertEquals("Unknown", active.snapshot().displayName());
    }

    private static StackTraceElement[] stack(StackTraceElement... frames) {
        return frames;
    }

    private static StackTraceElement frame(String className, String methodName) {
        return new StackTraceElement(className, methodName, "Test.java", 1);
    }

    private record TickCase(String hook, String event) {
    }
}
