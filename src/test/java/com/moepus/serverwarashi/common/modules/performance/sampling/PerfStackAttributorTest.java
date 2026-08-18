package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PerfStackAttributorTest {
    private static final String EVENT_BUS = "net.neoforged.bus.EventBus";
    private static final String EVENT_HOOKS = "net.neoforged.neoforge.event.EventHooks";
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
            PerfWorkTracker.Snapshot sample = PerfStackAttributor.classify(stack(
                    frame("com.example.ServerEvents", "onTick"),
                    frame("net.neoforged.bus.ConsumerEventHandler", "invoke"),
                    frame(EVENT_BUS, "post"),
                    frame(EVENT_BUS, "post"),
                    frame(EVENT_HOOKS, tickCase.hook())
            ));

            assertEquals(TickType.HANDLER, sample.type());
            assertEquals("ServerEvents#onTick(" + tickCase.event() + ")", sample.displayName());
            assertEquals("com.example.ServerEvents#onTick(" + TICK_PACKAGE + tickCase.event() + ")",
                    sample.eventLoop());
        }
    }

    @Test
    void nestedEventWorkStaysAttributedToTheOuterTickHandler() {
        PerfWorkTracker.Snapshot sample = PerfStackAttributor.classify(stack(
                frame("com.example.NestedEvents", "onNested"),
                frame(EVENT_BUS, "post"),
                frame("com.example.EventSender", "sendNested"),
                frame("com.example.ServerEvents", "onServerTick"),
                frame("net.neoforged.bus.ConsumerEventHandler", "invoke"),
                frame(EVENT_BUS, "post"),
                frame(EVENT_HOOKS, "fireServerTickPost")
        ));

        assertEquals("ServerEvents#onServerTick(ServerTickEvent.Post)", sample.displayName());
    }

    @Test
    void ignoresNormalWaitUntilNextTickIdle() {
        assertNull(PerfStackAttributor.classify(stack(
                frame("java.util.concurrent.locks.LockSupport", "parkNanos"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "waitForTasks"),
                frame("net.minecraft.server.MinecraftServer", "waitForTasks"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "managedBlock"),
                frame("net.minecraft.server.MinecraftServer", "waitUntilNextTick")
        )));
    }

    @Test
    void treatsTaskBlockingInsideWaitUntilNextTickAsUnknown() {
        PerfWorkTracker.Snapshot sample = PerfStackAttributor.classify(stack(
                frame("java.util.concurrent.locks.LockSupport", "parkNanos"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "waitForTasks"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "managedBlock"),
                frame("net.minecraft.server.level.ServerChunkCache", "getChunk"),
                frame("com.example.LongTask", "run"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "doRunTask"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "pollTask"),
                frame("net.minecraft.util.thread.BlockableEventLoop", "managedBlock"),
                frame("net.minecraft.server.MinecraftServer", "waitUntilNextTick")
        ));

        assertEquals(TickType.TASK, sample.type());
        assertEquals("Unknown", sample.displayName());
    }

    @Test
    void collapsesTasksBlockingWaitsAndOtherActiveWorkIntoUnknown() {
        List<StackTraceElement[]> stacks = List.of(
                stack(
                        frame("com.example.LongTask", "run"),
                        frame("net.minecraft.server.TickTask", "run"),
                        frame("net.minecraft.util.thread.BlockableEventLoop", "doRunTask"),
                        frame("net.minecraft.server.MinecraftServer", "waitUntilNextTick")
                ),
                stack(
                        frame("java.util.concurrent.locks.LockSupport", "parkNanos"),
                        frame("net.minecraft.util.thread.BlockableEventLoop", "waitForTasks"),
                        frame("net.minecraft.util.thread.BlockableEventLoop", "managedBlock"),
                        frame("net.minecraft.server.level.ServerChunkCache", "getChunk")
                ),
                stack(frame("com.example.TickWork", "calculate"))
        );

        for (StackTraceElement[] stack : stacks) {
            PerfWorkTracker.Snapshot sample = PerfStackAttributor.classify(stack);
            assertEquals(TickType.TASK, sample.type());
            assertEquals("Unknown", sample.displayName());
            assertEquals("", sample.eventLoop());
        }
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
