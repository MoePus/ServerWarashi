package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;

/**
 * ticket 口径的唯一出处：保护型/系统型 ticket 判定、原始 level 读取，
 * 以及所有 tracker mixin 共用的「masked level 最小」ticket 选取。
 * 层级：core 工具（纯静态）。上游：mixin/ticket、TicketPauseBatch、各模块。
 */
public final class TicketUtils {
    public static final int MAX_PAUSEABLE_LEVEL = 32;
    public static final int PAUSED_TICKET_LEVEL = MAX_PAUSEABLE_LEVEL + 1;
    public static final int NO_TICKET_LEVEL = 45;

    private TicketUtils() {
    }

    public static PauseableTicket asPauseable(Ticket<?> ticket) {
        return (PauseableTicket) (Object) ticket;
    }

    public static boolean isChunkProtectionTicket(Ticket<?> ticket) {
        return isSystemType(ticket.getType());
    }

    public static int getOriginLevel(Ticket<?> ticket) {
        return asPauseable(ticket).serverWarashi$getLevel();
    }

    /**
     * 返回集合中「masked level（{@link Ticket#getTicketLevel()}，已考虑暂停）最小」的那个 ticket。
     *
     * <p>原版 tracker 的 {@code getLevelFromSource} 用 {@code SortedArraySet.first()} 取「原始 level 最严格」
     * 的 ticket，但暂停掩码只改 {@code getTicketLevel()} 不改排序：当最严格的 ticket 被暂停（masked→33）而同
     * chunk 另有未暂停的较弱 ticket 时，{@code first()} 会得到 33，导致区块被错误降级。此方法改取 masked 最小者，
     * 与 mod 注入用的 masked-min 语义一致。集合非空（调用方已保证）。
     */
    public static Ticket<?> strictestMaskedTicket(SortedArraySet<Ticket<?>> tickets) {
        // 快路径：first() 是原始 level 最严格者；它未暂停时即为 masked 最小者，可 O(1) 直接返回。
        // 证明：设 r0 = raw(first) 为集合最小原始 level。first 未暂停 ⇒ masked(first)=r0；其余票若未暂停则
        // masked=raw≥r0，若暂停则 masked=33，而暂停票必满足 origin≤32（见 ARCHITECTURE 不变量），故存在暂停票时
        // r0≤32<33。两种情况 masked(first) 均最小。覆盖绝大多数未降负载 chunk，把热路径从 O(n) 拉回 O(1)。
        Ticket<?> first = tickets.first();
        if (!asPauseable(first).serverWarashi$isPaused()) {
            return first;
        }
        // 慢路径：仅当最严格票被暂停（降负载 chunk）时才需扫描全集合取 masked 最小者（与原实现一致）
        Ticket<?> strictest = first;
        int best = first.getTicketLevel();
        for (Ticket<?> ticket : tickets) {
            int level = ticket.getTicketLevel();
            if (level < best) {
                best = level;
                strictest = ticket;
            }
        }
        return strictest;
    }

    private static boolean isSystemType(TicketType<?> type) {
        return type == TicketType.START
                || type == TicketType.PLAYER
                || type == TicketType.FORCED
                || type == TicketType.PORTAL
                || type == TicketType.POST_TELEPORT
                || type == TicketType.DRAGON;
    }

    public static DistanceManagerAccessor getDistanceManager(ServerLevel level) {
        return (DistanceManagerAccessor) level.getChunkSource().chunkMap.getDistanceManager();
    }
}
