package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.util.PackedPositionHash;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

public record TicketOwnerKey(String typeName, Object ownerIdentity) {
    public String stableSortKey() {
        return typeName + ":" + ownerIdentity;
    }

    @Override
    public int hashCode() {
        return 31 * typeName.hashCode() + identityHash(ownerIdentity);
    }

    static int identityHash(Object identity) {
        if (identity instanceof BlockPos pos) {
            return PackedPositionHash.hash(pos.asLong());
        }
        if (identity instanceof ChunkPos pos) {
            return PackedPositionHash.hash(pos.toLong());
        }
        return Objects.hashCode(identity);
    }
}
