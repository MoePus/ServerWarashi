package com.moepus.serverwarashi.common.modules.performance.analyze;

final class ChunkPerfStats {
    long blockEntityNanos;
    long entityNanos;
    long chunkNanos;

    void accumulate(ChunkPerfStats other) {
        blockEntityNanos += other.blockEntityNanos;
        entityNanos += other.entityNanos;
        chunkNanos += other.chunkNanos;
    }
}
