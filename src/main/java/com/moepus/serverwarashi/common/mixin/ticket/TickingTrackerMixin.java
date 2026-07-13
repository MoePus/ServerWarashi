package com.moepus.serverwarashi.common.mixin.ticket;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让 {@link TickingTracker} 用 masked 最小（而非原始 level 最严格的 first()）作为「最严格 ticket」的取值口径，
 * 与暂停掩码语义一致（不修正的失效场景见 {@link TicketUtils#strictestMaskedTicket}）。覆盖两条取值路径：
 * <ul>
 *   <li>{@code getLevelFromSource}：邻居触发的重算（修复回滚）；</li>
 *   <li>{@code getTicketLevelAt}：addTicket/removeTicket 时对 tickingTracker 的 update 实参（修复增删无关 ticket 时的瞬态回滚）。</li>
 * </ul>
 *
 * <p>仅在「含被暂停 ticket 的 chunk」上与原版行为不同：未暂停时 masked 最小 == first()。
 */
@Mixin(value = TickingTracker.class, remap = false)

public abstract class TickingTrackerMixin {
    @WrapOperation(
            method = {
                    "getLevelFromSource(J)I",
                    "getTicketLevelAt(Lnet/minecraft/util/SortedArraySet;)I"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/SortedArraySet;first()Ljava/lang/Object;"
            )
    )
    private Object serverWarashi$strictestMasked(SortedArraySet<Ticket<?>> tickets, Operation<Object> original) {
        // 两条取值路径统一改用 masked 最小者，保证 tickingTracker 与暂停语义一致
        return TicketUtils.strictestMaskedTicket(tickets);
    }
}
