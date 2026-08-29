package com.moepus.serverwarashi.common.util;

public final class PackedPositionHash {
    private static final long MURMUR_C1 = 0xff51afd7ed558ccdL;
    private static final long MURMUR_C2 = 0xc4ceb9fe1a85ec53L;

    private PackedPositionHash() {
    }

    public static int hash(long packedPosition) {
        long mixed = packedPosition;
        mixed = (mixed ^ mixed >>> 33) * MURMUR_C1;
        mixed = (mixed ^ mixed >>> 33) * MURMUR_C2;
        mixed ^= mixed >>> 33;
        return Long.hashCode(mixed);
    }
}
