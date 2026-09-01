package com.moepus.serverwarashi.common.modules.performance.sampling;

record SampleMetricContext(long currentTick,
                           long measurementTicks,
                           double statisticalNanosPerSample,
                           double directNanosPerSample) {
    SampleMetricContext withMeasurementTicks(long ticks) {
        return new SampleMetricContext(currentTick, ticks,
                statisticalNanosPerSample, directNanosPerSample);
    }

    double nanosPerSample(SampleMetricMode mode) {
        return mode == SampleMetricMode.DIRECT_INTERVAL
                ? directNanosPerSample : statisticalNanosPerSample;
    }
}
