package com.moepus.serverwarashi.common.modules.loaderfreeze;

import com.mojang.logging.LogUtils;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reads NeoForge's private forced-owner key and resolves the controllable block-owner subset. */
public final class BlockLoaderTicketResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TICKET_OWNER_CLASS =
            "net.neoforged.neoforge.common.world.chunk.ForcedChunkManager$TicketOwner";
    private static final ConcurrentHashMap<Class<?>, Optional<OwnerAccess>> ACCESSORS = new ConcurrentHashMap<>();

    private BlockLoaderTicketResolver() {
    }

    public static Optional<BlockLoaderRef> resolve(ServerLevel level, Ticket<?> ticket) {
        Object key = TicketUtils.asPauseable(ticket).serverWarashi$getKey();
        return readOwner(key)
                .filter(owner -> owner.controllerId() instanceof ResourceLocation)
                .filter(owner -> owner.owner() instanceof BlockPos)
                .map(owner -> BlockLoaderRef.of(
                        level,
                        (ResourceLocation) owner.controllerId(),
                        (BlockPos) owner.owner()));
    }

    public static Optional<ForcedOwner> readOwner(Object key) {
        if (key == null) {
            return Optional.empty();
        }
        return ACCESSORS.computeIfAbsent(key.getClass(), BlockLoaderTicketResolver::createAccess)
                .flatMap(access -> access.read(key));
    }

    private static Optional<OwnerAccess> createAccess(Class<?> type) {
        if (!TICKET_OWNER_CLASS.equals(type.getName())) {
            return Optional.empty();
        }
        try {
            Field id = type.getDeclaredField("id");
            Field owner = type.getDeclaredField("owner");
            id.setAccessible(true);
            owner.setAccessible(true);
            return Optional.of(new OwnerAccess(id, owner));
        } catch (NoSuchFieldException | RuntimeException exception) {
            LOGGER.warn("Unable to access NeoForge forced ticket owner fields on {}", type.getName(), exception);
            return Optional.empty();
        }
    }

    public record ForcedOwner(Object controllerId, Object owner) {
    }

    private record OwnerAccess(Field id, Field owner) {
        Optional<ForcedOwner> read(Object key) {
            try {
                return Optional.of(new ForcedOwner(id.get(key), owner.get(key)));
            } catch (IllegalAccessException | RuntimeException exception) {
                LOGGER.warn("Unable to read NeoForge forced ticket owner {}", key.getClass().getName(), exception);
                return Optional.empty();
            }
        }
    }
}
