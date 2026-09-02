package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.function.Consumer;

interface AsyncStackSampler {
    void start(Thread target, int intervalMicros, Consumer<AsyncStackSample> sink);

    void stop();

    String backendName();

    String degradationReason();

    long settlingNanos();
}
