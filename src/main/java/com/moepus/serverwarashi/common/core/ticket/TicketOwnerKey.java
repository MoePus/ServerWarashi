package com.moepus.serverwarashi.common.core.ticket;

public record TicketOwnerKey(String typeName, Object ownerIdentity) {
    public String stableSortKey() {
        return typeName + ":" + ownerIdentity;
    }
}
