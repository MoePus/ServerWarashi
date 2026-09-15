package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderTicketResolver;
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
    public static final String UNKNOWN_ENTITY = "Unknown entity";
    private static final String WORLD_START = "world_start";
    private TicketOwnerResolver() {
    }

    public static TicketOwner resolve(Ticket<?> ticket, ServerLevel level) {
        Object rawKey = TicketUtils.asPauseable(ticket).serverWarashi$getKey();
        String typeName = ticket.getType().toString();
        String displayName = typeName;
        BlockPos displayPos = null;

        Optional<ForcedOwner> forcedOwner = readForcedOwner(rawKey);
        TicketOwnerKey key;
        if (forcedOwner.isPresent()) {
            key = new TicketOwnerKey(typeName, forcedOwner.get());
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
        } else if (isPlayerTicket(ticket)) {
            Optional<ServerPlayer> nearestPlayer = nearestPlayer(level, rawKey);
            ServerPlayer player = nearestPlayer.orElse(null);
            key = playerKey(typeName, player);
            if (player != null) {
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

    static Optional<ForcedOwner> readForcedOwner(Object key) {
        return BlockLoaderTicketResolver.readOwner(key)
                .map(owner -> new ForcedOwner(owner.controllerId(), owner.owner()));
    }

    static Optional<ServerPlayer> nearestPlayer(ServerLevel level, Object key) {
        if (!(key instanceof ChunkPos chunkPos)) {
            return Optional.empty();
        }
        var ticketPos = chunkPos.getWorldPosition();
        ServerPlayer nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            double distance = player.blockPosition().distSqr(ticketPos);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    static TicketOwnerKey playerKey(String typeName, ServerPlayer nearestPlayer) {
        String playerName = nearestPlayer == null ? "player" : nearestPlayer.getName().getString();
        return new TicketOwnerKey(typeName, playerName);
    }

    private static TicketOwnerKey nonPlayerKey(Ticket<?> ticket, Object rawKey, String typeName) {
        if (ticket.getType() == TicketType.START) {
            return new TicketOwnerKey(typeName, WORLD_START);
        }
        return new TicketOwnerKey(typeName, rawKey);
    }

    private static boolean isPlayerTicket(Ticket<?> ticket) {
        return ticket.getType() == TicketType.PLAYER;
    }

    record ForcedOwner(Object id, Object owner) {
        @Override
        public int hashCode() {
            return 31 * TicketOwnerKey.identityHash(id)
                    + TicketOwnerKey.identityHash(owner);
        }
    }
}
