package com.moepus.serverwarashi.client.network;

import java.util.Objects;

final class PerfMapSubscriptionState {
    private final Sender sender;
    private boolean mapOpen;
    private boolean analysisLive;
    private Request sent = new Request(false, false);

    PerfMapSubscriptionState(Sender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    boolean setMapOpen(boolean open) {
        mapOpen = open;
        return sync();
    }

    boolean setAnalysisLive(boolean live) {
        analysisLive = live;
        return sync();
    }

    void clear() {
        mapOpen = false;
        analysisLive = false;
        sent = new Request(false, false);
    }

    private boolean sync() {
        Request requested = new Request(mapOpen, mapOpen || analysisLive);
        if (sent.equals(requested) || !sender.send(requested)) {
            return false;
        }
        sent = requested;
        return true;
    }

    record Request(boolean mapOpen, boolean worldHotspots) {
    }

    @FunctionalInterface
    interface Sender {
        boolean send(Request request);
    }
}
