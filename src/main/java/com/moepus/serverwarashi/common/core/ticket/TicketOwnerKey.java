package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.Serverwarashi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public record TicketOwnerKey(String typeName, Object ownerIdentity) {
    private static final String NEOFORGE_FORCED_TICKET_OWNER_CLASS =
            "net.neoforged.neoforge.common.world.chunk.ForcedChunkManager$TicketOwner";
    private static final ConcurrentHashMap<Class<?>, Optional<ForcedOwnerAccess>> FORCED_OWNER_ACCESS_CACHE =
            new ConcurrentHashMap<>();

    public static TicketOwnerKey from(Ticket<?> ticket, ServerLevel level) {
        return TicketOwnerResolver.resolveKey(ticket, level);
    }

    public String stableSortKey() {
        return typeName + ":" + ownerIdentity;
    }

    static Object rawKey(Ticket<?> ticket) {
        return ((IPauseableTicket) (Object) ticket).serverWarashi$getKey();
    }

    static Optional<ForcedOwner> readForcedOwner(Object key) {
        if (key == null) {
            return Optional.empty();
        }
        return forcedOwnerAccess(key.getClass()).map(access -> access.read(key));
    }

    static Optional<ServerPlayer> nearestPlayer(ServerLevel level, Object key) {
        if (!(key instanceof ChunkPos chunkPos)) {
            return Optional.empty();
        }
        var ticketPos = chunkPos.getWorldPosition();
        return level.players().stream()
                .min(Comparator.comparingDouble(player -> player.blockPosition().distSqr(ticketPos)));
    }

    static TicketOwnerKey playerKey(String typeName, ServerPlayer nearestPlayer) {
        String playerName = nearestPlayer == null ? "player" : nearestPlayer.getName().getString();
        return new TicketOwnerKey(typeName, playerName);
    }

    private static Optional<ForcedOwnerAccess> forcedOwnerAccess(Class<?> type) {
        return FORCED_OWNER_ACCESS_CACHE.computeIfAbsent(type, TicketOwnerKey::createForcedOwnerAccess);
    }

    private static Optional<ForcedOwnerAccess> createForcedOwnerAccess(Class<?> type) {
        if (!NEOFORGE_FORCED_TICKET_OWNER_CLASS.equals(type.getName())) {
            return Optional.empty();
        }
        try {
            Field idField = type.getDeclaredField("id");
            Field ownerField = type.getDeclaredField("owner");
            idField.setAccessible(true);
            ownerField.setAccessible(true);
            return Optional.of(new ForcedOwnerAccess(idField, ownerField));
        } catch (NoSuchFieldException | SecurityException e) {
            Serverwarashi.LOGGER.warn(
                    "Unable to access NeoForge forced chunk ticket owner fields on {}",
                    type.getName(),
                    e
            );
            return Optional.empty();
        }
    }

    record ForcedTicketIdentity(Object id, Object owner) {
    }

    record ForcedOwner(Object id, Object owner) {
    }

    private record ForcedOwnerAccess(Field idField, Field ownerField) {
        ForcedOwner read(Object key) {
            try {
                return new ForcedOwner(idField.get(key), ownerField.get(key));
            } catch (IllegalAccessException | RuntimeException e) {
                Serverwarashi.LOGGER.warn(
                        "Unable to read NeoForge forced chunk ticket owner values from {}",
                        key.getClass().getName(),
                        e
                );
                return new ForcedOwner(null, key);
            }
        }
    }
}
