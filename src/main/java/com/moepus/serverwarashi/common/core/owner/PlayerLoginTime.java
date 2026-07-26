package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.mixin.accessor.GameProfileCacheAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.GameProfileInfoAccessor;
import net.minecraft.server.MinecraftServer;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

final class PlayerLoginTime {
    private PlayerLoginTime() {
    }

    static long fromCache(MinecraftServer server, UUID playerId) {
        Object info = ((GameProfileCacheAccessor) server.getProfileCache())
                .serverWarashi$getProfilesByUUID().get(playerId);
        if (!(info instanceof GameProfileInfoAccessor accessor)) {
            return 0L;
        }
        return fromExpiration(accessor.serverWarashi$getExpirationDate());
    }

    static long fromExpiration(Date expiration) {
        if (expiration == null) {
            return 0L;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(expiration);
        calendar.add(Calendar.MONTH, -1);
        return calendar.getTimeInMillis();
    }
}
