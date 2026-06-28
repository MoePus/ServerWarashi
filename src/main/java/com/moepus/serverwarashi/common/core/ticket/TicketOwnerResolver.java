package com.moepus.serverwarashi.common.core.ticket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.UUID;

/**
 * 一次性解析 ticket owner 身份与展示信息。
 */
public final class TicketOwnerResolver {
    private static final String C2ME_NOTICK_VIEW_DISTANCE = "c2me_notickvd";
    public static final String UNKNOWN_ENTITY = "Unknown entity";
    private static final String WORLD_START = "world_start";

    private TicketOwnerResolver() {
    }

    public static TicketOwner resolve(Ticket<?> ticket, ServerLevel level) {
        Object rawKey = TicketOwnerKey.rawKey(ticket);
        String typeName = ticket.getType().toString();
        String displayName = typeName;
        BlockPos displayPos = null;

        Optional<TicketOwnerKey.ForcedOwner> forcedOwner = TicketOwnerKey.readForcedOwner(rawKey);
        TicketOwnerKey key;
        if (forcedOwner.isPresent()) {
            key = forcedOwnerKey(typeName, forcedOwner.get());
            Object rawOwner = forcedOwner.get().owner();
            if (rawOwner instanceof BlockPos blockPos) {
                BlockState blockState = level.getBlockState(blockPos);
                displayName = blockState.getBlock().toString();
                displayPos = blockPos;
            } else if (rawOwner instanceof UUID uuid) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    displayName = entity.getType().toString();
                    displayPos = entity.blockPosition();
                } else {
                    displayName = UNKNOWN_ENTITY;
                }
            }
        } else if (isPlayerTicket(ticket, typeName)) {
            Optional<ServerPlayer> nearestPlayer = TicketOwnerKey.nearestPlayer(level, rawKey);
            key = TicketOwnerKey.playerKey(typeName, nearestPlayer.orElse(null));
            if (nearestPlayer.isPresent()) {
                ServerPlayer player = nearestPlayer.get();
                displayName = player.getName().getString();
                displayPos = player.blockPosition();
            }
        } else {
            key = nonPlayerKey(ticket, rawKey, typeName);
            if (ticket.getType() == TicketType.START) {
                displayPos = level.getSharedSpawnPos();
            } else if (rawKey instanceof BlockPos blockPos) {
                displayPos = blockPos;
            } else if (rawKey instanceof ChunkPos chunkPos) {
                displayPos = chunkPos.getWorldPosition();
            }
        }

        return new TicketOwner(key, displayName, displayPos);
    }

    static TicketOwnerKey resolveKey(Ticket<?> ticket, ServerLevel level) {
        Object rawKey = TicketOwnerKey.rawKey(ticket);
        String typeName = ticket.getType().toString();
        Optional<TicketOwnerKey.ForcedOwner> forcedOwner = TicketOwnerKey.readForcedOwner(rawKey);
        if (forcedOwner.isPresent()) {
            return forcedOwnerKey(typeName, forcedOwner.get());
        }
        if (isPlayerTicket(ticket, typeName)) {
            return TicketOwnerKey.playerKey(typeName, TicketOwnerKey.nearestPlayer(level, rawKey).orElse(null));
        }
        return nonPlayerKey(ticket, rawKey, typeName);
    }

    private static TicketOwnerKey forcedOwnerKey(String typeName, TicketOwnerKey.ForcedOwner forced) {
        return new TicketOwnerKey(typeName, new TicketOwnerKey.ForcedTicketIdentity(forced.id(), forced.owner()));
    }

    private static TicketOwnerKey nonPlayerKey(Ticket<?> ticket, Object rawKey, String typeName) {
        if (ticket.getType() == TicketType.START) {
            return new TicketOwnerKey(typeName, WORLD_START);
        }
        return new TicketOwnerKey(typeName, rawKey);
    }

    private static boolean isPlayerTicket(Ticket<?> ticket, String typeName) {
        return ticket.getType() == TicketType.PLAYER || C2ME_NOTICK_VIEW_DISTANCE.equals(typeName);
    }
}
