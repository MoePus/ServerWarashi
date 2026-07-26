package com.moepus.serverwarashi.common.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Date;

@Mixin(targets = "net.minecraft.server.players.GameProfileCache$GameProfileInfo", remap = false)
public interface GameProfileInfoAccessor {
    @Accessor("expirationDate")
    Date serverWarashi$getExpirationDate();
}
