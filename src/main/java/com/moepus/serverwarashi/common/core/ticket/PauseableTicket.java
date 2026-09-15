package com.moepus.serverwarashi.common.core.ticket;

public interface PauseableTicket {
    int PAUSE_REASON_MANUAL = 1;

    int serverWarashi$getPauseMask();
    void serverWarashi$setPauseMask(int mask);
    boolean serverWarashi$isPaused();
    int serverWarashi$getLevel();
    Object serverWarashi$getKey();
}
