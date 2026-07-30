package com.moepus.serverwarashi.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeakChunkDeliveryTest {
    @Test
    void sendsOnlyToSubscribedPermittedPlayersStillInSnapshotDimension() {
        assertTrue(WeakChunkDelivery.shouldSend(
                "minecraft:overworld", "minecraft:overworld", true, true));
        assertFalse(WeakChunkDelivery.shouldSend(
                "minecraft:overworld", "minecraft:the_nether", true, true));
        assertFalse(WeakChunkDelivery.shouldSend(
                "minecraft:overworld", "minecraft:overworld", false, true));
        assertFalse(WeakChunkDelivery.shouldSend(
                "minecraft:overworld", "minecraft:overworld", true, false));
    }
}
