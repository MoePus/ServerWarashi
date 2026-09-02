package com.moepus.serverwarashi.common.modules.performance.sampling;

record AsyncStackSample(long timestampNanos, StackTraceElement[] stack) {
    AsyncStackSample {
        stack = stack == null ? new StackTraceElement[0] : stack;
    }
}
