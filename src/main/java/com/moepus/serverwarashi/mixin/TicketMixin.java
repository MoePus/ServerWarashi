package com.moepus.serverwarashi.mixin;

import com.moepus.serverwarashi.IPauseableTicket;
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
    private boolean serverWarashi$isPaused = false;

    @Unique
    private boolean serverWarashi$dirty = false;

    @Override
    @Unique
    public boolean serverWarashi$isPaused() {
        return serverWarashi$isPaused;
    }

    @Override
    public boolean serverWarashi$needUpdate() {
        return serverWarashi$dirty;
    }

    @Override
    @Unique
    public void serverWarashi$setPaused(boolean paused) {
        if (this.serverWarashi$isPaused != paused) {
            this.serverWarashi$dirty = true;
        }
        serverWarashi$isPaused = paused;
    }

    @Override
    public void serverWarashi$clearDirty() {
        serverWarashi$dirty = false;
    }

    @Override
    public int serverWarashi$getLevel() {
        return ticketLevel;
    }

    @Override
    public Object serverWarashi$getKey() {
        return getKey();
    }

    @Inject(method = "getTicketLevel", at = @At("HEAD"), cancellable = true)
    private void onGetTicketLevel(CallbackInfoReturnable<Integer> cir) {
        if (serverWarashi$isPaused) {
            cir.setReturnValue(33);
        }
    }

    @Accessor
    abstract Object getKey();
}
