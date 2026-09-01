package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.sampling.UnknownStackClusterer;
import com.moepus.serverwarashi.common.modules.performance.sampling.UnknownStackSnapshot;

import java.util.ArrayList;
import java.util.List;

record UnknownStackPreparedDetails(long reportId,
                                   long revision,
                                   long totalObserved,
                                   int retainedCount,
                                   List<UnknownStackDetailsPayload.Cluster> average,
                                   List<UnknownStackDetailsPayload.Cluster> burst) {
    private static final int MAX_CLUSTER_TEXT_BYTES =
            UnknownStackDetailsPayload.MAX_TOTAL_TEXT_BYTES / (UnknownStackClusterer.TOP_LIMIT * 2);

    static UnknownStackPreparedDetails prepare(UnknownStackSnapshot snapshot) {
        return from(UnknownStackClusterer.cluster(snapshot));
    }

    static UnknownStackPreparedDetails from(UnknownStackClusterer.Result result) {
        return new UnknownStackPreparedDetails(result.reportId(), result.revision(),
                result.totalObserved(), result.retainedCount(), clusters(result.average()),
                clusters(result.burst()));
    }

    UnknownStackDetailsPayload payload(long requestId) {
        return new UnknownStackDetailsPayload(requestId, reportId, revision,
                UnknownStackDetailsPayload.Status.OK, totalObserved, retainedCount,
                average, burst);
    }

    private static List<UnknownStackDetailsPayload.Cluster> clusters(
            List<UnknownStackClusterer.Cluster> clusters) {
        List<UnknownStackDetailsPayload.Cluster> result = new ArrayList<>(clusters.size());
        for (UnknownStackClusterer.Cluster cluster : clusters) {
            result.add(cluster(cluster));
        }
        return UnknownStackDetailsPayload.prepareClusters(result);
    }

    private static UnknownStackDetailsPayload.Cluster cluster(
            UnknownStackClusterer.Cluster cluster) {
        int remaining = MAX_CLUSTER_TEXT_BYTES
                - UnknownStackDetailsPayload.utf8Length(cluster.anchor());
        List<String> frames = new ArrayList<>(cluster.frames().size());
        for (String frame : cluster.frames()) {
            int bytes = UnknownStackDetailsPayload.utf8Length(frame);
            if (bytes > remaining) {
                break;
            }
            frames.add(frame);
            remaining -= bytes;
        }
        return new UnknownStackDetailsPayload.Cluster(
                cluster.anchor(), cluster.sampleCount(), cluster.representativeCount(),
                cluster.peakSampleCount(), cluster.estimatedAverageMillis(),
                cluster.estimatedBurstMillis(), frames);
    }
}
