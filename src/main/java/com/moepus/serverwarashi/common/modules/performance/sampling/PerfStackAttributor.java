package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;

final class PerfStackAttributor {
    private static final int MAX_STACK_DEPTH = 96;
    private static final String EVENT_BUS_PACKAGE = "net.neoforged.bus.";
    private static final String EVENT_BUS_CLASS = "net.neoforged.bus.EventBus";
    private static final String EVENT_HOOKS_CLASS = "net.neoforged.neoforge.event.EventHooks";
    private static final String MINECRAFT_SERVER_CLASS = "net.minecraft.server.MinecraftServer";
    private static final String UNKNOWN_NAME = "Unknown";
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final Map<String, TickEvent> TICK_EVENTS = Map.of(
            "fireServerTickPre", tickEvent("ServerTickEvent.Pre"),
            "fireServerTickPost", tickEvent("ServerTickEvent.Post"),
            "fireLevelTickPre", tickEvent("LevelTickEvent.Pre"),
            "fireLevelTickPost", tickEvent("LevelTickEvent.Post"),
            "firePlayerTickPre", tickEvent("PlayerTickEvent.Pre"),
            "firePlayerTickPost", tickEvent("PlayerTickEvent.Post"),
            "fireEntityTickPre", tickEvent("EntityTickEvent.Pre"),
            "fireEntityTickPost", tickEvent("EntityTickEvent.Post")
    );

    private PerfStackAttributor() {
    }

    static PerfWorkTracker.Snapshot capture(Thread thread) {
        StackTraceElement[] stack = captureStack(thread);
        return stack == null ? null : classify(stack);
    }

    static PerfWorkTracker.Snapshot captureTickHandler(Thread thread) {
        StackTraceElement[] stack = captureStack(thread);
        return stack == null ? null : findTickHandler(stack);
    }

    static SampleMetricMode metricMode(PerfWorkTracker.Snapshot snapshot) {
        return snapshot != null && snapshot.workClass() == UnknownWork.class
                ? SampleMetricMode.DIRECT_INTERVAL : SampleMetricMode.STATISTICAL;
    }

    private static StackTraceElement[] captureStack(Thread thread) {
        if (thread == null || !thread.isAlive()) {
            return null;
        }
        ThreadInfo info = THREADS.getThreadInfo(thread.threadId(), MAX_STACK_DEPTH);
        return info == null ? null : info.getStackTrace();
    }

    static PerfWorkTracker.Snapshot classify(StackTraceElement[] stack) {
        PerfWorkTracker.Snapshot handler = findTickHandler(stack);
        if (handler != null) {
            return handler;
        }
        return isNormalTickIdle(stack) ? null : unknown();
    }

    private static PerfWorkTracker.Snapshot findTickHandler(StackTraceElement[] stack) {
        for (int hookIndex = 0; hookIndex < stack.length; hookIndex++) {
            StackTraceElement hook = stack[hookIndex];
            TickEvent event = tickEvent(hook);
            if (event == null) {
                continue;
            }
            StackTraceElement handler = handlerAboveDispatch(stack, hookIndex);
            if (handler != null) {
                return handler(handler, event);
            }
        }
        return null;
    }

    private static TickEvent tickEvent(StackTraceElement frame) {
        return EVENT_HOOKS_CLASS.equals(frame.getClassName())
                ? TICK_EVENTS.get(frame.getMethodName()) : null;
    }

    private static StackTraceElement handlerAboveDispatch(StackTraceElement[] stack, int hookIndex) {
        boolean foundPost = false;
        for (int index = hookIndex - 1; index >= 0; index--) {
            StackTraceElement frame = stack[index];
            if (isDispatchInfrastructure(frame)) {
                foundPost |= isEventBusPost(frame);
                continue;
            }
            return foundPost ? frame : null;
        }
        return null;
    }

    private static boolean isDispatchInfrastructure(StackTraceElement frame) {
        String className = frame.getClassName();
        return className.startsWith(EVENT_BUS_PACKAGE)
                || className.startsWith("java.lang.invoke.")
                || className.startsWith("java.lang.reflect.")
                || className.startsWith("jdk.internal.reflect.");
    }

    private static boolean isEventBusPost(StackTraceElement frame) {
        return EVENT_BUS_CLASS.equals(frame.getClassName()) && "post".equals(frame.getMethodName());
    }

    private static boolean isNormalTickIdle(StackTraceElement[] stack) {
        int waitIndex = -1;
        int tickWaitIndex = -1;
        for (int index = 0; index < stack.length; index++) {
            StackTraceElement frame = stack[index];
            if (waitIndex < 0 && "waitForTasks".equals(frame.getMethodName())) {
                waitIndex = index;
            }
            if (MINECRAFT_SERVER_CLASS.equals(frame.getClassName())
                    && "waitUntilNextTick".equals(frame.getMethodName())) {
                tickWaitIndex = index;
                break;
            }
        }
        if (waitIndex < 0 || tickWaitIndex <= waitIndex) {
            return false;
        }
        for (int index = waitIndex + 1; index < tickWaitIndex; index++) {
            if ("doRunTask".equals(stack[index].getMethodName())) {
                return false;
            }
        }
        return true;
    }

    private static PerfWorkTracker.Snapshot handler(StackTraceElement frame, TickEvent event) {
        String fullClass = readableClassName(frame.getClassName());
        String shortClass = shortClassName(frame.getClassName());
        String fullName = fullClass + "#" + frame.getMethodName() + "(" + event.fullName() + ")";
        String shortName = shortClass + "#" + frame.getMethodName() + "(" + event.shortName() + ")";
        return snapshot(TickType.HANDLER, HandlerWork.class, shortName, fullName);
    }

    private static PerfWorkTracker.Snapshot unknown() {
        return snapshot(TickType.TASK, UnknownWork.class, UNKNOWN_NAME, "");
    }

    private static PerfWorkTracker.Snapshot snapshot(
            TickType type, Class<?> workClass, String displayName, String fullName) {
        return new PerfWorkTracker.Snapshot(type, null, workClass, null, TickDetailKind.NONE,
                -1, false, 0, 0, 0, false, fullName, displayName);
    }

    private static TickEvent tickEvent(String nestedName) {
        String packageName = "net.neoforged.neoforge.event.tick.";
        return new TickEvent(nestedName, packageName + nestedName);
    }

    private static String readableClassName(String binaryName) {
        int hiddenSuffix = binaryName.indexOf('/');
        String visible = hiddenSuffix < 0 ? binaryName : binaryName.substring(0, hiddenSuffix);
        return visible.replace('$', '.');
    }

    private static String shortClassName(String binaryName) {
        int packageEnd = binaryName.lastIndexOf('.');
        return readableClassName(binaryName.substring(packageEnd + 1));
    }

    private record TickEvent(String shortName, String fullName) {
    }

    private static final class HandlerWork {
    }

    private static final class UnknownWork {
    }
}
