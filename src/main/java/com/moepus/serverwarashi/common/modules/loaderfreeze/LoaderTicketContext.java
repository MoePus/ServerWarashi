package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Carries the owning level through ServerChunkCache's synchronous region-ticket call. */
public final class LoaderTicketContext {
    private static final ThreadLocal<ServerLevel> CURRENT_LEVEL = new ThreadLocal<>();

    private LoaderTicketContext() {
    }

    public static void run(ServerLevel level, Runnable action) {
        ServerLevel previous = CURRENT_LEVEL.get();
        CURRENT_LEVEL.set(level);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT_LEVEL.remove();
            } else {
                CURRENT_LEVEL.set(previous);
            }
        }
    }

    public static @Nullable ServerLevel currentLevel() {
        return CURRENT_LEVEL.get();
    }
}
