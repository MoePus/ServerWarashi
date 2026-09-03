package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.Map;
import java.util.Set;

final class PerfStackAttributor {
    private static final String EVENT_BUS_PACKAGE = "net.neoforged.bus.";
    private static final String EVENT_BUS_CLASS = "net.neoforged.bus.EventBus";
    private static final String EVENT_HOOKS_CLASS = "net.neoforged.neoforge.event.EventHooks";
    private static final String MINECRAFT_SERVER_CLASS = "net.minecraft.server.MinecraftServer";
    private static final String SERVER_LEVEL_CLASS = "net.minecraft.server.level.ServerLevel";
    private static final String ENTITY_CLASS = "net.minecraft.world.entity.Entity";
    private static final String ENTITY_TICK_LIST_CLASS =
            "net.minecraft.world.level.entity.EntityTickList";
    private static final String BOUND_BLOCK_ENTITY_CLASS =
            "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity";
    private static final String REBINDABLE_BLOCK_ENTITY_CLASS =
            "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper";
    private static final String LEVEL_CHUNK_CLASS = "net.minecraft.world.level.chunk.LevelChunk";
    private static final String BLOCK_ENTITY_CLASS =
            "net.minecraft.world.level.block.entity.BlockEntity";
    private static final String BLOCK_ENTITY_TICKER_CLASS =
            "net.minecraft.world.level.block.entity.BlockEntityTicker";
    private static final String BLOCKABLE_EVENT_LOOP_CLASS =
            "net.minecraft.util.thread.BlockableEventLoop";
    private static final String TICK_TASK_CLASS = "net.minecraft.server.TickTask";
    private static final String METHOD_SCOPE_SUFFIX = "$mixinlite$scope$";
    private static final Set<String> ENTITY_WRAPPER_METHODS = Set.of(
            "setOldPosAndRot", "getType", "getPassengers", "isRemoved",
            "getVehicle", "stopRiding");
    private static final Set<String> BLOCK_ENTITY_WRAPPER_METHODS = Set.of(
            "isRemoved", "hasLevel", "getBlockPos", "getType", "getBlockState");
    private static final Classification ENTITY_DISPATCH = new Classification(
            Kind.ENTITY_DISPATCH, snapshot(TickType.TASK, EntityDispatchWork.class,
            "Entity Dispatch", ""));
    private static final Classification BLOCK_ENTITY_DISPATCH = new Classification(
            Kind.BLOCK_ENTITY_DISPATCH, snapshot(TickType.TASK, BlockEntityDispatchWork.class,
            "Block Entity Dispatch", ""));
    private static final Classification UNKNOWN = new Classification(
            Kind.UNKNOWN, PerfWorkTracker.unknownSnapshot());
    private static final Classification TRACKED = new Classification(Kind.TRACKED, null);
    private static final Classification IDLE = new Classification(Kind.IDLE, null);
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

    static SampleMetricMode metricMode(PerfWorkTracker.Snapshot snapshot) {
        return SampleMetricMode.STATISTICAL;
    }

    static Classification classify(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) {
            return IDLE;
        }
        PerfWorkTracker.Snapshot handler = findTickHandler(stack);
        if (handler != null) {
            return new Classification(Kind.HANDLER, handler);
        }
        Classification dispatch = findDispatch(stack);
        if (dispatch != null) {
            return dispatch;
        }
        if (isKnownTrackedStack(stack)) {
            return TRACKED;
        }
        return isNormalTickIdle(stack) ? IDLE : UNKNOWN;
    }

    private static Classification findDispatch(StackTraceElement[] stack) {
        for (int index = 0; index < stack.length; index++) {
            StackTraceElement frame = stack[index];
            if (isEntityTickWrapper(frame)) {
                return onlyWrapperInfrastructureAbove(stack, index, Wrapper.ENTITY)
                        ? ENTITY_DISPATCH : TRACKED;
            }
            if (ENTITY_TICK_LIST_CLASS.equals(frame.getClassName())
                    && "forEach".equals(frame.getMethodName())) {
                return ENTITY_DISPATCH;
            }
            if (isBlockEntityWrapper(frame)) {
                return onlyWrapperInfrastructureAbove(stack, index, Wrapper.BLOCK_ENTITY)
                        ? BLOCK_ENTITY_DISPATCH : TRACKED;
            }
        }
        return null;
    }

    private static boolean isBlockEntityWrapper(StackTraceElement frame) {
        String className = frame.getClassName();
        return BOUND_BLOCK_ENTITY_CLASS.equals(className)
                || REBINDABLE_BLOCK_ENTITY_CLASS.equals(className);
    }

    private static boolean isEntityTickWrapper(StackTraceElement frame) {
        if (!SERVER_LEVEL_CLASS.equals(frame.getClassName())) {
            return false;
        }
        return "tickNonPassenger".equals(frame.getMethodName())
                || "tickPassenger".equals(frame.getMethodName());
    }

    private static boolean onlyWrapperInfrastructureAbove(
            StackTraceElement[] stack, int wrapperIndex, Wrapper wrapper) {
        for (int index = 0; index < wrapperIndex; index++) {
            if (!isWrapperInfrastructure(stack[index], wrapper)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWrapperInfrastructure(StackTraceElement frame, Wrapper wrapper) {
        String className = frame.getClassName();
        if (className.startsWith("net.minecraft.util.profiling.")
                || className.startsWith("java.util.")) {
            return true;
        }
        if (wrapper == Wrapper.ENTITY) {
            return SERVER_LEVEL_CLASS.equals(className)
                    || ENTITY_TICK_LIST_CLASS.equals(className)
                    || ENTITY_CLASS.equals(className)
                    && ENTITY_WRAPPER_METHODS.contains(frame.getMethodName());
        }
        return LEVEL_CHUNK_CLASS.equals(className)
                || BLOCK_ENTITY_CLASS.equals(className)
                && BLOCK_ENTITY_WRAPPER_METHODS.contains(frame.getMethodName());
    }

    private static boolean isKnownTrackedStack(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            String methodName = frame.getMethodName();
            if (TICK_TASK_CLASS.equals(className) && "run".equals(methodName)
                    || BLOCKABLE_EVENT_LOOP_CLASS.equals(className)
                    && "doRunTask".equals(methodName)
                    || SERVER_LEVEL_CLASS.equals(className) && "tickChunk".equals(methodName)
                    || ENTITY_CLASS.equals(className)
                    && ("tick".equals(methodName) || "rideTick".equals(methodName))
                    || BLOCK_ENTITY_TICKER_CLASS.equals(className) && "tick".equals(methodName)) {
                return true;
            }
        }
        return false;
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
        if (!EVENT_HOOKS_CLASS.equals(frame.getClassName())) {
            return null;
        }
        String method = frame.getMethodName();
        int scopeSuffix = method.indexOf(METHOD_SCOPE_SUFFIX);
        return TICK_EVENTS.get(scopeSuffix < 0 ? method : method.substring(0, scopeSuffix));
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

    record Classification(Kind kind, PerfWorkTracker.Snapshot snapshot) {
    }

    enum Kind {
        HANDLER,
        ENTITY_DISPATCH,
        BLOCK_ENTITY_DISPATCH,
        UNKNOWN,
        TRACKED,
        IDLE
    }

    private enum Wrapper {
        ENTITY,
        BLOCK_ENTITY
    }

    private static final class HandlerWork {
    }

    private static final class EntityDispatchWork {
    }

    private static final class BlockEntityDispatchWork {
    }
}
