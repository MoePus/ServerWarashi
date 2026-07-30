package com.moepus.serverwarashi.common.modules.weakload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

final class WeakEntityClassifier {
    private WeakEntityClassifier() {
    }

    static WeakEntityType typeOf(Entity entity) {
        if (entity instanceof ItemEntity itemEntity) {
            return new WeakEntityType(WeakEntityType.Kind.ITEM,
                    BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem()).toString());
        }
        return new WeakEntityType(WeakEntityType.Kind.ENTITY,
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    static boolean matches(WeakEntityType type, Entity entity) {
        return !entity.isRemoved() && type.equals(typeOf(entity));
    }
}
