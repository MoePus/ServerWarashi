package com.moepus.serverwarashi.common.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class UnknownStackRequestGate {
    static final long REQUEST_INTERVAL_NANOS = 1_000_000_000L;
    private final Map<UUID, PlayerRequest> requests = new HashMap<>();

    synchronized boolean tryBegin(UUID playerId, long requestId, long now) {
        PlayerRequest request = requests.get(playerId);
        if (request == null) {
            request = new PlayerRequest();
            requests.put(playerId, request);
        }
        if (request.isBusy(now)) {
            return false;
        }
        request.accept(requestId, now);
        return true;
    }

    synchronized void finish(UUID playerId, long requestId) {
        PlayerRequest request = requests.get(playerId);
        if (request != null && request.requestId == requestId) {
            request.inFlight = false;
        }
    }

    synchronized void remove(UUID playerId) {
        requests.remove(playerId);
    }

    synchronized void clear() {
        requests.clear();
    }

    private static final class PlayerRequest {
        private long requestId;
        private long lastAcceptedNanos = Long.MIN_VALUE;
        private boolean inFlight;

        void accept(long nextRequestId, long now) {
            requestId = nextRequestId;
            lastAcceptedNanos = now;
            inFlight = true;
        }

        boolean isBusy(long now) {
            return inFlight || lastAcceptedNanos != Long.MIN_VALUE
                    && now - lastAcceptedNanos < REQUEST_INTERVAL_NANOS;
        }
    }
}
