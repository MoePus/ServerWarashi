package com.moepus.serverwarashi.common.modules.weakload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeakChunkCollectorTest {
    @Test
    void restartKeepsSweepIdsMonotonic() {
        var cursor = new WeakChunkCollector.ScanCursor();

        cursor.restart();
        cursor.restart();

        assertEquals(2L, cursor.sweepId());
    }
}
