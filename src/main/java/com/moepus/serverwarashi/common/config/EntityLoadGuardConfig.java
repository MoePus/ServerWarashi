package com.moepus.serverwarashi.common.config;

public final class EntityLoadGuardConfig {
    private EntityLoadGuardConfig() {
    }

    public static Settings current() {
        return new Settings(
                ServerConfig.ENTITY_LOAD_GUARD_ENABLED.get(),
                ServerConfig.ENTITY_LOAD_GUARD_BATCH_THRESHOLD.get(),
                ServerConfig.ENTITY_LOAD_GUARD_TOP_TYPE_LIMIT.get(),
                ServerConfig.ENTITY_LOAD_GUARD_RETAIN_PER_TYPE.get(),
                ServerConfig.ENTITY_LOAD_GUARD_BROADCAST.get());
    }

    public record Settings(boolean enabled,
                           int batchThreshold,
                           int topTypeLimit,
                           int retainedPerType,
                           boolean broadcast) {
    }
}
