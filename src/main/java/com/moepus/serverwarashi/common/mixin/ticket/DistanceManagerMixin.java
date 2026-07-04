package com.moepus.serverwarashi.common.mixin.ticket;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让 {@code DistanceManager.getTicketLevelAt}（static）用 masked 最小作「最严格 ticket」口径。
 * 它是 addTicket/removeTicket/purge 给 {@code ticketTracker.update(...)} 提供的实参来源；不修正会在
 * 「分裂暂停」chunk 上增删无关 ticket 时把 ticketTracker 瞬时回滚到 33（见 bug-01 复查 SF1）。
 */
@Mixin(value = DistanceManager.class, remap = false)

public abstract class DistanceManagerMixin {
    @WrapOperation(
            method = "getTicketLevelAt(Lnet/minecraft/util/SortedArraySet;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/SortedArraySet;first()Ljava/lang/Object;"
            )
    )
    private static Object serverWarashi$strictestMasked(SortedArraySet<Ticket<?>> tickets, Operation<Object> original) {
        // 把 first() 替换为 masked 最小者，避免增删无关 ticket 时 tracker 被瞬时回滚到 33
        return TicketUtils.strictestMaskedTicket(tickets);
    }
}
