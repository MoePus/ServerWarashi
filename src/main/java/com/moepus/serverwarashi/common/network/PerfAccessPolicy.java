package com.moepus.serverwarashi.common.network;

public final class PerfAccessPolicy {
    private PerfAccessPolicy() {
    }

    public static boolean canControl(boolean operator, boolean singleplayer) {
        return operator || singleplayer;
    }

    public static boolean canSubscribe(boolean operator,
                                       boolean allowNonOperators,
                                       boolean singleplayer) {
        return allowNonOperators || canControl(operator, singleplayer);
    }
}
