package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.sampling.UnknownStackClusterer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.moepus.serverwarashi.testsupport.NetworkTestFixtures.roundTrip;

class UnknownStackDetailsPayloadTest {
    @Test
    void roundTripsRequestAndAverageTopThree() {
        UnknownStackDetailsRequestPayload request =
                new UnknownStackDetailsRequestPayload(8L, 12L);
        UnknownStackDetailsPayload.Cluster cluster = cluster(List.of(
                "com.example.Work.run(Work.java:10)",
                "net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1)"));
        UnknownStackDetailsPayload response = new UnknownStackDetailsPayload(
                8L, 12L, 14L, UnknownStackDetailsPayload.Status.OK,
                400L, 256, List.of(cluster));
        roundTrip(UnknownStackDetailsRequestPayload.STREAM_CODEC, request);
        roundTrip(UnknownStackDetailsPayload.STREAM_CODEC, response);
    }

    @Test
    void acceptsNinetySixFramesButRejectsProtocolOverflows() {
        List<String> frames = Collections.nCopies(96, "example.Work.run(Work.java:1)");
        UnknownStackDetailsPayload.Cluster boundary = cluster(frames);
        new UnknownStackDetailsPayload(1L, 1L, 2L,
                UnknownStackDetailsPayload.Status.OK, 256L, 256,
                List.of(boundary));

        assertThrows(IllegalArgumentException.class, () -> new UnknownStackDetailsPayload(
                1L, 1L, 2L, UnknownStackDetailsPayload.Status.OK, 256L, 256,
                List.of(cluster(Collections.nCopies(97, "frame")))));
        assertThrows(IllegalArgumentException.class, () -> new UnknownStackDetailsPayload(
                1L, 1L, 2L, UnknownStackDetailsPayload.Status.OK, 256L, 256,
                List.of(cluster(List.of("x".repeat(513))))));
        assertThrows(IllegalArgumentException.class, () -> new UnknownStackDetailsPayload(
                1L, 1L, 2L, UnknownStackDetailsPayload.Status.OK, 256L, 256,
                Collections.nCopies(4, boundary)));

        String multibyte = "界".repeat(512);
        UnknownStackDetailsPayload.Cluster large = cluster(Collections.nCopies(96, multibyte));
        assertThrows(IllegalArgumentException.class, () -> new UnknownStackDetailsPayload(
                1L, 1L, 2L, UnknownStackDetailsPayload.Status.OK, 256L, 256,
                List.of(large)));
    }

    @Test
    void failureStatusesCarryNoClusters() {
        for (UnknownStackDetailsPayload.Status status : UnknownStackDetailsPayload.Status.values()) {
            if (status == UnknownStackDetailsPayload.Status.OK) {
                continue;
            }
            assertEquals(status, UnknownStackDetailsPayload.failed(1L, 2L, status).status());
        }
        assertThrows(IllegalArgumentException.class, () -> new UnknownStackDetailsPayload(
                1L, 2L, 0L, UnknownStackDetailsPayload.Status.BUSY,
                1L, 1, List.of(cluster(List.of("frame")))));
    }

    @Test
    void deliveryTruncatesPathFramesToTheTotalTextBudget() {
        String multibyte = "界".repeat(512);
        var cluster = new UnknownStackClusterer.Cluster(
                "example.Work#run", 4, 3, 1.25D,
                Collections.nCopies(96, multibyte));
        var result = new UnknownStackClusterer.Result(
                2L, 4L, 256L, 256,
                Collections.nCopies(3, cluster));

        UnknownStackDetailsPayload payload = UnknownStackDetailDelivery.response(
                new UnknownStackDetailsRequestPayload(1L, 2L), result);

        assertEquals(3, payload.average().size());
        assertEquals(14, payload.average().getFirst().frames().size());
    }

    @Test
    void preparedDetailsShareBoundedContentAcrossRequestPayloads() {
        var cluster = new UnknownStackClusterer.Cluster(
                "example.Work#run", 4, 3, 1.25D,
                List.of("example.Work.run(Work.java:10)"));
        var result = new UnknownStackClusterer.Result(
                2L, 4L, 256L, 256, List.of(cluster));
        UnknownStackPreparedDetails details = UnknownStackPreparedDetails.from(result);

        UnknownStackDetailsPayload first = details.payload(1L);
        UnknownStackDetailsPayload second = details.payload(2L);

        assertEquals(1L, first.requestId());
        assertEquals(2L, second.requestId());
        assertSame(first.average(), second.average());
    }

    @Test
    void allocationFreeUtf8LengthMatchesEncodedText() {
        for (String value : List.of("plain ASCII", "未知详情", "stack-\uD83D\uDD25")) {
            assertEquals(value.getBytes(StandardCharsets.UTF_8).length,
                    UnknownStackDetailsPayload.utf8Length(value));
        }
    }

    private static UnknownStackDetailsPayload.Cluster cluster(List<String> frames) {
        return new UnknownStackDetailsPayload.Cluster(
                "example.Work#run", 4, 3, 1.25D, frames);
    }

}
