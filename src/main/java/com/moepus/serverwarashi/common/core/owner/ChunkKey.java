package com.moepus.serverwarashi.common.core.owner;

record ChunkKey(String dimension, long chunkPos) {
    private static final long MURMUR_C1 = 0xff51afd7ed558ccdL;
    private static final long MURMUR_C2 = 0xc4ceb9fe1a85ec53L;

    @Override
    public int hashCode() {
        long mixed = chunkPos;
        mixed = (mixed ^ mixed >>> 33) * MURMUR_C1;
        mixed = (mixed ^ mixed >>> 33) * MURMUR_C2;
        mixed ^= mixed >>> 33;
        return 31 * dimension.hashCode() + Long.hashCode(mixed);
    }
}
