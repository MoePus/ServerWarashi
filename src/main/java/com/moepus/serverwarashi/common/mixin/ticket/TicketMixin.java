package com.moepus.serverwarashi.common.mixin.ticket;

import com.moepus.serverwarashi.common.core.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import net.minecraft.server.level.Ticket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Ticket.class, remap = false)

public abstract class TicketMixin implements IPauseableTicket {
    @Shadow
    @Final
    private int ticketLevel;

    @Unique
    private volatile int serverWarashi$pauseMask = 0;

    @Unique
    private volatile TicketOwner serverWarashi$cachedOwner;

    @Unique
    private volatile long serverWarashi$ownerResolvedAtGameTime = Long.MIN_VALUE;

    @Override
    public boolean serverWarashi$isPaused() {
        // 任一暂停位非 0 即视为已暂停
        return serverWarashi$pauseMask != 0;
    }

    @Override
    public int serverWarashi$getPauseMask() {
        return serverWarashi$pauseMask;
    }

    @Override
    public void serverWarashi$setPauseMask(int mask) {
        this.serverWarashi$pauseMask = mask;
    }

    @Override
    public int serverWarashi$getLevel() {
        // 暴露原始（未受暂停影响的）ticketLevel
        return ticketLevel;
    }

    @Override
    public Object serverWarashi$getKey() {
        return getKey();
    }

    @Override
    public TicketOwner serverWarashi$getCachedOwner() {
        return serverWarashi$cachedOwner;
    }

    @Override
    public long serverWarashi$getOwnerResolvedAtGameTime() {
        return serverWarashi$ownerResolvedAtGameTime;
    }

    @Override
    public void serverWarashi$cacheOwner(TicketOwner owner, long gameTime) {
        serverWarashi$cachedOwner = owner;
        serverWarashi$ownerResolvedAtGameTime = gameTime;
    }

    @Inject(method = "getTicketLevel", at = @At("HEAD"), cancellable = true)
    private void onGetTicketLevel(CallbackInfoReturnable<Integer> cir) {
        // 已暂停时对外返回 ticket level 33（区块保持加载但不再 tick）——降负载真正生效的地方
        if (serverWarashi$pauseMask != 0) {
            cir.setReturnValue(TicketUtils.PAUSED_TICKET_LEVEL);
        }
    }

    @Accessor
    abstract Object getKey();
}
