package com.moepus.serverwarashi.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfAccessPolicyTest {
    @Test
    void controlsPermitOperatorsAndSingleplayer() {
        assertTrue(PerfAccessPolicy.canControl(true, false));
        assertTrue(PerfAccessPolicy.canControl(false, true));
        assertFalse(PerfAccessPolicy.canControl(false, false));
    }

    @Test
    void subscriptionsAlsoHonorTheNonOperatorSetting() {
        assertTrue(PerfAccessPolicy.canSubscribe(true, false, false));
        assertTrue(PerfAccessPolicy.canSubscribe(false, true, false));
        assertTrue(PerfAccessPolicy.canSubscribe(false, false, true));
        assertFalse(PerfAccessPolicy.canSubscribe(false, false, false));
    }
}
