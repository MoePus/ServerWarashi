package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.PauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性解析 ticket owner 身份与展示信息。
 */
public final class TicketOwnerResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NEOFORGE_FORCED_TICKET_OWNER_CLASS =
            "net.neoforged.neoforge.common.world.chunk.ForcedChunkManager$TicketOwner";
    private static final ConcurrentHashMap<Class<?>, Optional<ForcedOwnerAccess>> FORCED_OWNER_ACCESS_CACHE =
            new ConcurrentHashMap<>();
    private static final String C2ME_NOTICK_VIEW_DISTANCE = "c2me_notickvd";
    public static final String UNKNOWN_ENTITY = "Unknown entity";
    private static final String WORLD_START = "world_start";
    private static final long OWNER_CACHE_TTL_TICKS = 100L;

    private TicketOwnerResolver() {
    }

    public static TicketOwner resolve(Ticket<?> ticket, ServerLevel level) {
        PauseableTicket pauseableTicket = TicketUtils.asPauseable(ticket);
        long gameTime = level.getGameTime();
        TicketOwner cachedOwner = pauseableTicket.serverWarashi$getCachedOwner();
        long cachedAt = pauseableTicket.serverWarashi$getOwnerResolvedAtGameTime();
        if (cachedOwner != null && gameTime - cachedAt <= OWNER_CACHE_TTL_TICKS) {
            return cachedOwner;
        }

        Object rawKey = pauseableTicket.serverWarashi$getKey();
        String typeName = ticket.getType().toString();
        String displayName = typeName;
        BlockPos displayPos = null;
        boolean cacheable = true;

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
                    cacheable = false;
                }
            }
        } else if (isPlayerTicket(ticket, typeName)) {
            Optional<ServerPlayer> nearestPlayer = nearestPlayer(level, rawKey);
            ServerPlayer player = nearestPlayer.orElse(null);
            cacheable = player != null;
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

        TicketOwner owner = new TicketOwner(key, displayName, displayPos);
        if (cacheable) {
            pauseableTicket.serverWarashi$cacheOwner(owner, gameTime);
        }
        return owner;
    }

    public static TicketOwnerKey resolveKey(Ticket<?> ticket, ServerLevel level) {
        Object rawKey = TicketUtils.asPauseable(ticket).serverWarashi$getKey();
        String typeName = ticket.getType().toString();
        Optional<ForcedOwner> forcedOwner = readForcedOwner(rawKey);
        if (forcedOwner.isPresent()) {
            return new TicketOwnerKey(typeName, forcedOwner.get());
        }
        if (isPlayerTicket(ticket, typeName)) {
            return playerKey(typeName, nearestPlayer(level, rawKey).orElse(null));
        }
        return nonPlayerKey(ticket, rawKey, typeName);
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
        return FORCED_OWNER_ACCESS_CACHE.computeIfAbsent(type, TicketOwnerResolver::createForcedOwnerAccess);
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
            LOGGER.warn(
                    "Unable to access NeoForge forced chunk ticket owner fields on {}",
                    type.getName(),
                    e
            );
            return Optional.empty();
        }
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

    record ForcedOwner(Object id, Object owner) {
    }

    private record ForcedOwnerAccess(Field idField, Field ownerField) {
        ForcedOwner read(Object key) {
            try {
                return new ForcedOwner(idField.get(key), ownerField.get(key));
            } catch (IllegalAccessException | RuntimeException e) {
                LOGGER.warn(
                        "Unable to read NeoForge forced chunk ticket owner values from {}",
                        key.getClass().getName(),
                        e
                );
                return new ForcedOwner(null, key);
            }
        }
    }
}
