package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.EventHooks;
import org.mixinlite.injector.MethodScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = EventHooks.class, remap = false)
public abstract class EventHooksTickMixin {
    @MethodScope(
            method = "fireServerTickPre(Ljava/util/function/BooleanSupplier;Lnet/minecraft/server/MinecraftServer;)V",
            exit = "serverWarashi$exitHandler",
            remap = false
    )
    private static boolean serverWarashi$enterServerTickPre() {
        return serverWarashi$enterServerHandler();
    }

    @MethodScope(
            method = "fireServerTickPost(Ljava/util/function/BooleanSupplier;Lnet/minecraft/server/MinecraftServer;)V",
            exit = "serverWarashi$exitHandler",
            remap = false
    )
    private static boolean serverWarashi$enterServerTickPost() {
        return serverWarashi$enterServerHandler();
    }

    @MethodScope(
            method = "fireLevelTickPre(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",
            exit = "serverWarashi$exitHandler",
            remap = false
    )
    private static boolean serverWarashi$enterLevelTickPre(Level level) {
        return serverWarashi$enterLevelHandler(level);
    }

    @MethodScope(
            method = "fireLevelTickPost(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",
            exit = "serverWarashi$exitHandler",
            remap = false
    )
    private static boolean serverWarashi$enterLevelTickPost(Level level) {
        return serverWarashi$enterLevelHandler(level);
    }

    @MethodScope(
            method = "firePlayerTickPre(Lnet/minecraft/world/entity/player/Player;)V",
            exit = "serverWarashi$exitHandler",
            remap = false
    )
    private static boolean serverWarashi$enterPlayerTickPre(Player player) {
        return serverWarashi$enterEntityHandler(player);
    }

    @MethodScope(
            method = "firePlayerTickPost(Lnet/minecraft/world/entity/player/Player;)V",
            exit = "serverWarashi$exitHandler",
            remap = false
    )
    private static boolean serverWarashi$enterPlayerTickPost(Player player) {
        return serverWarashi$enterEntityHandler(player);
    }

    @MethodScope(
            method = "fireEntityTickPre(Lnet/minecraft/world/entity/Entity;)Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;",
            exit = "serverWarashi$exitHandler",
            remap = false
    )
    private static boolean serverWarashi$enterEntityTickPre(Entity entity) {
        return serverWarashi$enterEntityHandler(entity);
    }

    @MethodScope(
            method = "fireEntityTickPost(Lnet/minecraft/world/entity/Entity;)V",
            exit = "serverWarashi$exitHandler",
            remap = false
    )
    private static boolean serverWarashi$enterEntityTickPost(Entity entity) {
        return serverWarashi$enterEntityHandler(entity);
    }

    @Unique
    private static boolean serverWarashi$enterServerHandler() {
        return PerfWorkTracker.isProfiling && PerfWorkTracker.enterHandler();
    }

    @Unique
    private static boolean serverWarashi$enterLevelHandler(Level level) {
        return PerfWorkTracker.isProfiling && level instanceof ServerLevel
                && PerfWorkTracker.enterHandler();
    }

    @Unique
    private static boolean serverWarashi$enterEntityHandler(Entity entity) {
        return PerfWorkTracker.isProfiling && entity.level() instanceof ServerLevel
                && PerfWorkTracker.enterHandler();
    }

    @Unique
    private static void serverWarashi$exitHandler(boolean entered) {
        if (entered) {
            PerfWorkTracker.exit(TickType.HANDLER);
        }
    }
}
