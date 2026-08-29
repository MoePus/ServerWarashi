package com.moepus.serverwarashi.common.network;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class PerfViewerSubscriptions {
    private final Set<UUID> screen = new HashSet<>();
    private final Set<UUID> map = new HashSet<>();
    private final Set<UUID> world = new HashSet<>();
    private final Set<UUID> pendingScreenOpen = new HashSet<>();

    void openScreen(UUID playerId) {
        openScreen(playerId, false);
    }

    void openScreen(UUID playerId, boolean deferred) {
        screen.add(playerId);
        if (deferred) {
            pendingScreenOpen.add(playerId);
        } else {
            pendingScreenOpen.remove(playerId);
        }
    }

    void closeScreen(UUID playerId) {
        screen.remove(playerId);
        pendingScreenOpen.remove(playerId);
    }

    void setMap(UUID playerId, boolean subscribed) {
        setMembership(map, playerId, subscribed);
    }

    void setWorld(UUID playerId, boolean subscribed) {
        setMembership(world, playerId, subscribed);
    }

    boolean isScreenViewer(UUID playerId) {
        return screen.contains(playerId);
    }

    boolean isMapViewer(UUID playerId) {
        return map.contains(playerId);
    }

    boolean isWorldViewer(UUID playerId) {
        return world.contains(playerId);
    }

    boolean receivesWorldHotspots(UUID playerId) {
        return isScreenViewer(playerId) || isWorldViewer(playerId);
    }

    boolean receivesWorldProjection(UUID playerId) {
        return isWorldViewer(playerId) && !isMapViewer(playerId) && !isScreenViewer(playerId);
    }

    boolean isLiveViewer(UUID playerId) {
        return isScreenViewer(playerId) || isMapViewer(playerId) || isWorldViewer(playerId);
    }

    boolean retainPermittedLiveViewer(UUID playerId, boolean permitted) {
        if (!permitted) {
            remove(playerId);
        }
        return isLiveViewer(playerId);
    }

    PerfReportPayload.Mode nextMode(UUID playerId) {
        return pendingScreenOpen.remove(playerId)
                ? PerfReportPayload.Mode.OPEN : PerfReportPayload.Mode.UPDATE;
    }

    Set<UUID> recipients() {
        Set<UUID> result = new HashSet<>(screen);
        result.addAll(map);
        result.addAll(world);
        return result;
    }

    Iterable<UUID> mapViewers() {
        return map;
    }

    Iterable<UUID> worldViewers() {
        return world;
    }

    boolean hasMapRecipients() {
        return !map.isEmpty();
    }

    boolean isEmpty() {
        return screen.isEmpty() && map.isEmpty() && world.isEmpty();
    }

    void remove(UUID playerId) {
        screen.remove(playerId);
        map.remove(playerId);
        world.remove(playerId);
        pendingScreenOpen.remove(playerId);
    }

    void clear() {
        screen.clear();
        map.clear();
        world.clear();
        pendingScreenOpen.clear();
    }

    private static void setMembership(Set<UUID> viewers, UUID playerId, boolean subscribed) {
        if (subscribed) {
            viewers.add(playerId);
        } else {
            viewers.remove(playerId);
        }
    }
}
