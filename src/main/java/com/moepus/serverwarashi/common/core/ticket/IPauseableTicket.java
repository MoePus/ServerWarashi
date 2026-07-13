package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.common.core.owner.TicketOwner;

public interface IPauseableTicket {
    int PAUSE_REASON_AUTO = 2;
    int PAUSE_REASON_IDLE = 4;

    int serverWarashi$getPauseMask();
    void serverWarashi$setPauseMask(int mask);
    boolean serverWarashi$isPaused();
    int serverWarashi$getLevel();
    Object serverWarashi$getKey();
    TicketOwner serverWarashi$getCachedOwner();
    long serverWarashi$getOwnerResolvedAtGameTime();
    void serverWarashi$cacheOwner(TicketOwner owner, long gameTime);
}
