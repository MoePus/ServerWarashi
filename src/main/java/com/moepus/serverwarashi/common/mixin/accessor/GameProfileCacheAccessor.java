package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.server.players.GameProfileCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(value = GameProfileCache.class, remap = false)
public interface GameProfileCacheAccessor {
    @Accessor("profilesByUUID")
    Map<UUID, Object> serverWarashi$getProfilesByUUID();
}
