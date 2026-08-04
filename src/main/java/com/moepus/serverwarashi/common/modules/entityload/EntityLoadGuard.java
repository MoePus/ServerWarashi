package com.moepus.serverwarashi.common.modules.entityload;

import com.moepus.serverwarashi.common.mixin.accessor.ChunkEntitiesAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityAccess;

import java.util.AbstractList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class EntityLoadGuard {
    static final int BATCH_THRESHOLD = 5_000;
    static final int TOP_TYPE_LIMIT = 3;
    static final int RETAINED_PER_TYPE = 100;
    static final String MESSAGE_PREFIX = "Warashi deleted ";

    private EntityLoadGuard() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends EntityAccess> Stream<T> filter(ChunkEntities<T> batch) {
        List<T> loaded = ((ChunkEntitiesAccessor<T>) batch).serverwarashi$getEntities();
        if (loaded.size() <= BATCH_THRESHOLD) {
            return loaded.stream();
        }

        ServerLevel level = serverLevel(loaded);
        if (level == null) {
            return loaded.stream();
        }

        List<EntityLoadType> types = typeView(loaded);
        EntityLoadBatchPlanner.Plan<EntityLoadType> plan = EntityLoadBatchPlanner.plan(
                types, BATCH_THRESHOLD, TOP_TYPE_LIMIT, RETAINED_PER_TYPE);
        if (plan.removalCount() == 0) {
            return loaded.stream();
        }

        Set<EntityLoadType> targets = plan.removals().stream()
                .map(EntityLoadBatchPlanner.Removal::type)
                .collect(Collectors.toUnmodifiableSet());
        Map<EntityLoadType, Entity> representatives = new HashMap<>(targets.size());
        for (int index = 0; index < loaded.size(); index++) {
            Entity entity = (Entity) loaded.get(index);
            EntityLoadType type = types.get(index);
            if (targets.contains(type)) {
                representatives.putIfAbsent(type, entity);
            }
            if (plan.removes(index)) {
                entity.discard();
            }
        }
        broadcast(level, batch.getPos(), plan.removals(), representatives);
        return survivorStream(loaded, plan);
    }

    static Component deletionMessage(ResourceLocation dimension, ChunkPos pos,
                                     Component typeName, int removedCount) {
        String location = "%s (%d, %d)".formatted(
                dimension, pos.getMiddleBlockX(), pos.getMiddleBlockZ());
        return Component.literal(MESSAGE_PREFIX + removedCount + " ")
                .append(typeName)
                .append(Component.literal(" at " + location));
    }

    private static <T extends EntityAccess> ServerLevel serverLevel(List<T> loaded) {
        ServerLevel level = null;
        for (T access : loaded) {
            if (!(access instanceof Entity entity)
                    || !(entity.level() instanceof ServerLevel entityLevel)) {
                return null;
            }
            if (level == null) {
                level = entityLevel;
            } else if (level != entityLevel) {
                return null;
            }
        }
        return level;
    }

    private static <T extends EntityAccess> List<EntityLoadType> typeView(List<T> loaded) {
        EntityLoadType.Resolver resolver = new EntityLoadType.Resolver();
        return new AbstractList<>() {
            @Override
            public EntityLoadType get(int index) {
                return resolver.typeOf((Entity) loaded.get(index));
            }

            @Override
            public int size() {
                return loaded.size();
            }
        };
    }

    private static <T extends EntityAccess> Stream<T> survivorStream(
            List<T> loaded, EntityLoadBatchPlanner.Plan<?> plan) {
        return IntStream.range(0, loaded.size())
                .filter(index -> !plan.removes(index))
                .mapToObj(loaded::get);
    }

    private static void broadcast(ServerLevel level, ChunkPos pos,
                                  List<EntityLoadBatchPlanner.Removal<EntityLoadType>> removals,
                                  Map<EntityLoadType, Entity> representatives) {
        for (EntityLoadBatchPlanner.Removal<EntityLoadType> removal : removals) {
            Entity representative = representatives.get(removal.type());
            Component typeName = representative instanceof ItemEntity itemEntity
                    ? itemEntity.getItem().getItem().getDescription()
                    : representative.getType().getDescription();
            Component message = deletionMessage(
                    level.dimension().location(), pos, typeName, removal.count());
            level.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }
}
