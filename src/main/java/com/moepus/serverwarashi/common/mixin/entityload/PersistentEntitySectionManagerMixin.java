package com.moepus.serverwarashi.common.mixin.entityload;

import com.moepus.serverwarashi.common.modules.entityload.EntityLoadGuard;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.stream.Stream;

@Mixin(value = PersistentEntitySectionManager.class, remap = false)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> {
    @Redirect(
            method = "processPendingLoads",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/ChunkEntities;getEntities()Ljava/util/stream/Stream;"
            )
    )
    private Stream<T> serverWarashi$filterOverloadedEntityChunk(
            ChunkEntities<T> batch) {
        return EntityLoadGuard.filter(batch);
    }
}
