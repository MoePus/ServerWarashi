package com.moepus.serverwarashi.common.modules.weakload;

import com.moepus.serverwarashi.common.mixin.accessor.ClassInstanceMultiMapAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.EntitySectionAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.EntitySectionStorageAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.ServerLevelAccessor;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.util.Collection;
import java.util.List;

final class WeakChunkEntityAccess {
    private WeakChunkEntityAccess() {
    }

    @SuppressWarnings("unchecked")
    static EntitySectionStorage<Entity> storage(ServerLevel level) {
        PersistentEntitySectionManager<Entity> manager = ((ServerLevelAccessor) level).getEntityManager();
        return (EntitySectionStorage<Entity>) ((PersistentEntitySectionManagerAccessor) manager)
                .getSectionStorage();
    }

    static LongSortedSet sectionIds(EntitySectionStorage<Entity> storage) {
        return ((EntitySectionStorageAccessor) storage).serverwarashi$getSectionIds();
    }

    static List<Entity> entities(EntitySection<Entity> section) {
        return multiMap(section).serverwarashi$getAllInstances();
    }

    static Collection<List<Entity>> classLists(EntitySection<Entity> section) {
        return multiMap(section).serverwarashi$getByClass().values();
    }

    @SuppressWarnings("unchecked")
    private static ClassInstanceMultiMapAccessor<Entity> multiMap(EntitySection<Entity> section) {
        ClassInstanceMultiMap<Entity> values =
                ((EntitySectionAccessor<Entity>) (Object) section).serverwarashi$getStorage();
        return (ClassInstanceMultiMapAccessor<Entity>) (Object) values;
    }
}
