package com.moepus.serverwarashi;

public interface IPauseableTicket {
    boolean serverWarashi$isPaused();
    boolean serverWarashi$needUpdate();
    void serverWarashi$setPaused(boolean paused);
    void serverWarashi$clearDirty();
    int serverWarashi$getLevel();
    Object serverWarashi$getKey();
}
