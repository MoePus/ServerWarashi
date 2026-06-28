package com.moepus.serverwarashi.common.mixin.ticket;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 与 {@link TickingTrackerMixin} 对应：让 {@code DistanceManager$ChunkTicketTracker.getLevelFromSource} 也以
 * masked 最小作为 source，使 ChunkHolder 的 ticket level 与暂停掩码语义一致（见 bug-01）。
 *
 * <p>仅在「含被暂停 ticket 的 chunk」上与原版行为不同：未暂停时 masked 最小 == first()。
 * 用 {@code @WrapOperation}（而非 {@code @Redirect}）以便与其它同样修改该 {@code first()} 调用的 mod 组合，
 * 避免 {@code defaultRequire:1} 下的加载期硬冲突（见 bug-01 复查 SF2）。
 */
@Mixin(targets = "net.minecraft.server.level.DistanceManager$ChunkTicketTracker", remap = false)
@SuppressWarnings("unused")
public abstract class ChunkTicketTrackerMixin {
    @WrapOperation(
            method = "getLevelFromSource(J)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/SortedArraySet;first()Ljava/lang/Object;"
            )
    )
    private Object serverWarashi$strictestMasked(SortedArraySet<Ticket<?>> tickets, Operation<Object> original) {
        // 把原版 first()(原始 level 最严格) 替换为 masked 最小者，与暂停语义对齐
        return TicketUtils.strictestMaskedTicket(tickets);
    }
}
