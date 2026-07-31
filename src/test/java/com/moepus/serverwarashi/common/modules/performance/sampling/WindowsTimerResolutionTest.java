package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsTimerResolutionTest {
    @Test
    void detectsWindowsNamesWithoutMatchingOtherSystems() {
        assertTrue(WindowsTimerResolution.isWindows("Windows 11"));
        assertTrue(WindowsTimerResolution.isWindows("WINDOWS Server 2025"));
        assertFalse(WindowsTimerResolution.isWindows("Linux"));
        assertFalse(WindowsTimerResolution.isWindows("Mac OS X"));
    }

    @Test
    void takesControlOfTimerResolutionWithoutChangingOtherPowerPolicies() {
        var policy = WindowsTimerResolution.honorTimerResolution(0x1, 0x5);

        assertEquals(0x5, policy.controlMask());
        assertEquals(0x1, policy.stateMask());
    }
}
