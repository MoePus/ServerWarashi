package com.moepus.serverwarashi;

import com.moepus.serverwarashi.mixin.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = Serverwarashi.MODID)
public class TicketManager {
    static int age = 0;

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        age++;
    }

    @SubscribeEvent
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (!Config.ENABLED.get()) return;

        Level level = event.getLevel();
        if (level.isClientSide) return;

        if (level instanceof ServerLevel serverLevel) {
            processTickets(serverLevel);
        }
    }


    private static void processTickets(ServerLevel level) {
        if (age % Config.RUN_EVERY.get() != 0) return;

        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        var tickets = ((DistanceManagerAccessor) distanceManager).getTickets().long2ObjectEntrySet();

        // Collect all tickets
        List<TicketEntry> allTickets = new ArrayList<>();
        for (var entry : tickets) {
            long chunkPosLong = entry.getLongKey();
            int chunkX = ChunkPos.getX(chunkPosLong);
            int chunkZ = ChunkPos.getZ(chunkPosLong);

            long morton = morton2D(chunkX, chunkZ);

            for (Ticket<?> ticket : entry.getValue()) {
                if (isSystemTicket(ticket)) continue;
                allTickets.add(new TicketEntry(ticket, morton, chunkPosLong));
            }
        }

        if (allTickets.isEmpty()) return;

        if (Config.PAUSE_ALL_TICKETS.get()) {
            Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
            for (TicketEntry entry : allTickets) {
                IPauseableTicket pauseable = (IPauseableTicket) (Object) entry.ticket;
                pauseable.serverWarashi$setPaused(true);
                if (pauseable.serverWarashi$needUpdate()) {
                    modifiedChunks.put(entry.chunkPos, false);
                    pauseable.serverWarashi$clearDirty();
                }
            }
            updateChunkLevel((DistanceManagerAccessor) distanceManager, modifiedChunks);
            return;
        }

        // Sort tickets by Morton code
        allTickets.sort(Comparator.comparingLong(te -> te.morton));
        List<List<TicketEntry>> buckets = divideChunkBuckets(allTickets);

        // Determine active bucket
        int currentGroupIndex = (age / Config.RUN_EVERY.get()) % buckets.size();

        Long2BooleanOpenHashMap modifiedChunks = new Long2BooleanOpenHashMap();
        for (int i = 0; i < buckets.size(); i++) {
            boolean isActive = (i == currentGroupIndex);
            for (TicketEntry entry : buckets.get(i)) {
                IPauseableTicket pauseable = (IPauseableTicket) (Object) entry.ticket;
                pauseable.serverWarashi$setPaused(!isActive);
                if (pauseable.serverWarashi$needUpdate()) {
                    modifiedChunks.computeIfAbsent(entry.chunkPos, k -> isActive);
                    pauseable.serverWarashi$clearDirty();
                }
            }
        }

        // Update chunk levels
        updateChunkLevel((DistanceManagerAccessor) distanceManager, modifiedChunks);
    }

    private static void updateChunkLevel(DistanceManagerAccessor distanceManager, Long2BooleanOpenHashMap modifiedChunks) {
        for (long chunkPos : modifiedChunks.keySet()) {
            var ticketSet = distanceManager.getTickets().get(chunkPos);
            if (ticketSet != null) {
                int newLevel = updateTicketSet(ticketSet);
                boolean isDecreasing = modifiedChunks.get(chunkPos);
                distanceManager.getTicketTracker().update(chunkPos, newLevel, isDecreasing);
                if (!ticketSet.isEmpty() && isDecreasing) {
                    distanceManager.getTickingTicketsTracker().update(chunkPos, newLevel, true);
                }
            }
        }
    }

    private static int updateTicketSet(SortedArraySet<Ticket<?>> tickets) {
        List<Ticket<?>> tmp = new ArrayList<>(tickets);
        tickets.clear();
        tickets.addAll(tmp);
        return getTicketLevel(tickets);
    }

    private static int getTicketLevel(SortedArraySet<Ticket<?>> tickets) {
        if (tickets.isEmpty()) return 45;
        return tickets.first().getTicketLevel();
    }

    private static @NotNull List<List<TicketEntry>> divideChunkBuckets(List<TicketEntry> allTickets) {
        int targetBucketSize = Config.TICKET_GROUP_SIZE.get();
        int maxBucketSize = targetBucketSize * 2;

        List<List<TicketEntry>> buckets = new ArrayList<>();
        List<TicketEntry> current = new ArrayList<>();

        for (int i = 0; i < allTickets.size(); i++) {
            TicketEntry entry = allTickets.get(i);

            if (current.size() < targetBucketSize) {
                current.add(entry);
            } else {
                boolean tooClose = false;
                if (!current.isEmpty()) {
                    long diff = entry.morton - current.get(current.size() - 1).morton;
                    tooClose = (diff <= Config.PROXIMITY_THRESHOLD.get());
                }

                if (tooClose && current.size() < maxBucketSize) {
                    current.add(entry);
                } else {
                    buckets.add(current);
                    current = new ArrayList<>();
                    current.add(entry);
                }
            }
        }
        if (!current.isEmpty()) {
            buckets.add(current);
        }
        return buckets;
    }

    private static boolean isSystemTicket(Ticket<?> ticket) {
        if (((IPauseableTicket) (Object) ticket).serverWarashi$getLevel() > 32) return true;

        var type = ticket.getType();
        return type == TicketType.START ||
                type == TicketType.PLAYER ||
                type == TicketType.FORCED ||
                type == TicketType.PORTAL ||
                type == TicketType.POST_TELEPORT ||
                type == TicketType.DRAGON;
    }

    private static long morton2D(int x, int z) {
        long xx = Integer.toUnsignedLong(x);
        long zz = Integer.toUnsignedLong(z);
        return interleaveBits(xx) | (interleaveBits(zz) << 1);
    }

    private static long interleaveBits(long x) {
        x = (x | (x << 16)) & 0x0000FFFF0000FFFFL;
        x = (x | (x << 8)) & 0x00FF00FF00FF00FFL;
        x = (x | (x << 4)) & 0x0F0F0F0F0F0F0F0FL;
        x = (x | (x << 2)) & 0x3333333333333333L;
        x = (x | (x << 1)) & 0x5555555555555555L;
        return x;
    }

    private record TicketEntry(Ticket<?> ticket, long morton, long chunkPos) {
    }
}
