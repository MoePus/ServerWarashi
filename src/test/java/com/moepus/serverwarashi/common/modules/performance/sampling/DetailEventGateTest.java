package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DetailEventGateTest {
    @Test
    void externalHighFrequencyEventsAreDownsampledByTimestamp() {
        DetailEventGate gate = new DetailEventGate();
        gate.reset(10_000_000L);

        long accepted = LongStream.range(0L, 100L)
                .map(index -> index * 1_000_000L)
                .filter(gate::accept)
                .count();

        assertEquals(10L, accepted);
    }
}
