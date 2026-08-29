package com.moepus.serverwarashi.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfSubscriptionAccessTest {
    @Test
    void permitsOperatorsConfiguredPlayersAndIntegratedServerOwner() {
        assertTrue(PerfSubscriptionAccess.isPermitted(true, false, false));
        assertTrue(PerfSubscriptionAccess.isPermitted(false, true, false));
        assertTrue(PerfSubscriptionAccess.isPermitted(false, false, true));
        assertFalse(PerfSubscriptionAccess.isPermitted(false, false, false));
    }
}
