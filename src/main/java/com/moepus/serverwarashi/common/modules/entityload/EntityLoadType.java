package com.moepus.serverwarashi.common.modules.entityload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

record EntityLoadType(Kind kind, ResourceLocation id) implements Comparable<EntityLoadType> {
    EntityLoadType {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    static EntityLoadType item(ResourceLocation id) {
        return new EntityLoadType(Kind.ITEM, id);
    }

    static EntityLoadType entity(ResourceLocation id) {
        return new EntityLoadType(Kind.ENTITY, id);
    }

    @Override
    public int compareTo(EntityLoadType other) {
        int kindComparison = kind.compareTo(other.kind);
        return kindComparison != 0 ? kindComparison : id.compareTo(other.id);
    }

    enum Kind {
        ENTITY,
        ITEM
    }

    static final class Resolver {
        private final Map<EntityType<?>, EntityLoadType> entityTypes = new IdentityHashMap<>();
        private final Map<Item, EntityLoadType> itemTypes = new IdentityHashMap<>();

        EntityLoadType typeOf(Entity entity) {
            if (entity instanceof ItemEntity itemEntity) {
                Item item = itemEntity.getItem().getItem();
                return itemTypes.computeIfAbsent(item, key ->
                        EntityLoadType.item(BuiltInRegistries.ITEM.getKey(key)));
            }
            EntityType<?> type = entity.getType();
            return entityTypes.computeIfAbsent(type, key ->
                    EntityLoadType.entity(BuiltInRegistries.ENTITY_TYPE.getKey(key)));
        }
    }
}
