package com.moepus.serverwarashi.common.core.owner;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * ticket 持有者的展示身份。相等性仅取决于 {@code key}（跨 tick 稳定），
 * name / representativePos 只是展示信息，不参与比较。
 */
public record TicketOwner(TicketOwnerKey key, String name, BlockPos representativePos) {
    public TicketOwner {
        Objects.requireNonNull(key, "key");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TicketOwner that && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

}
