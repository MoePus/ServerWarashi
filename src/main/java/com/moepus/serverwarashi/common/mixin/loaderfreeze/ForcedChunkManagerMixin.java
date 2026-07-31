package com.moepus.serverwarashi.common.mixin.loaderfreeze;

import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderStateData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForcedChunkManager.class, remap = false)
public abstract class ForcedChunkManagerMixin {
    @Inject(
            method = "reinstatePersistentChunks(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ForcedChunksSavedData;)V",
            at = @At("HEAD")
    )
    private static void serverWarashi$preloadLoaderState(
            ServerLevel level, ForcedChunksSavedData forcedChunks, CallbackInfo callback) {
        LoaderStateData.get(level);
    }
}
