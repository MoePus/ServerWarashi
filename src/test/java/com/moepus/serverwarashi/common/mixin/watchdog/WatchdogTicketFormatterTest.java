package com.moepus.serverwarashi.common.mixin.watchdog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatchdogTicketFormatterTest {
    @Test
    void formatsMultipleDimensionsWithOwnersAndEmptyState() {
        WatchdogTicketFormatter.Owner owner = new WatchdogTicketFormatter.Owner(
                "Block{minecraft:hopper}",
                new WatchdogTicketFormatter.Position(12, 64, -3),
                new WatchdogTicketFormatter.Counts(4, 7, 2));
        var dimensions = List.of(
                WatchdogTicketFormatter.Dimension.success("minecraft:overworld", List.of(owner)),
                WatchdogTicketFormatter.Dimension.success("minecraft:the_nether", List.of()));

        assertEquals("""
                Dimension: minecraft:overworld
                Running ticket owners:
                - Block{minecraft:hopper}@(12, 64, -3) (chunks=4, BE=7, E=2)
                Dimension: minecraft:the_nether
                Running ticket owners:
                No active ticket owners.""", WatchdogTicketFormatter.format(dimensions));
    }

    @Test
    void formatsMissingCoordinatesAndCollectionFailure() {
        WatchdogTicketFormatter.Owner owner = new WatchdogTicketFormatter.Owner(
                "Unknown", null, new WatchdogTicketFormatter.Counts(2, 0, 0));
        var dimensions = List.of(
                WatchdogTicketFormatter.Dimension.success("test:one", List.of(owner)),
                WatchdogTicketFormatter.Dimension.failure("test:two", new IllegalStateException()));

        assertEquals("""
                Dimension: test:one
                Running ticket owners:
                - Unknown@null (chunks=2, BE=0, E=0)
                Dimension: test:two
                Failed to collect active ticket owners: IllegalStateException""",
                WatchdogTicketFormatter.format(dimensions));
    }
}
