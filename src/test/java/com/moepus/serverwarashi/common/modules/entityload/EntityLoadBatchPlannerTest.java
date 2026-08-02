package com.moepus.serverwarashi.common.modules.entityload;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityLoadBatchPlannerTest {
    @Test
    void triggersOnlyAboveProductionBatchThreshold() {
        List<String> types = repeated("entity", EntityLoadGuard.BATCH_THRESHOLD);

        var atThreshold = productionPlan(types);
        types.add("entity");
        var aboveThreshold = productionPlan(types);

        assertEquals(0, atThreshold.removalCount());
        assertEquals(4_901, aboveThreshold.removalCount());
        assertEquals(List.of(new EntityLoadBatchPlanner.Removal<>("entity", 4_901)),
                aboveThreshold.removals());
    }

    @Test
    void reducesOnlyTheOriginalTopThreeTypesToOneHundred() {
        List<String> types = new ArrayList<>();
        add(types, "alpha", 1_300);
        add(types, "beta", 1_200);
        add(types, "gamma", 1_100);
        add(types, "delta", 1_000);
        add(types, "epsilon", 401);

        var plan = productionPlan(types);

        assertEquals(3_300, plan.removalCount());
        assertEquals(List.of(
                new EntityLoadBatchPlanner.Removal<>("alpha", 1_200),
                new EntityLoadBatchPlanner.Removal<>("beta", 1_100),
                new EntityLoadBatchPlanner.Removal<>("gamma", 1_000)), plan.removals());
        assertFalse(plan.removes(3_600));
        assertFalse(plan.removes(types.size() - 1));
    }

    @Test
    void keepsTheFirstEntitiesOfEachSelectedTypeInLoadOrder() {
        List<String> types = List.of("alpha", "beta", "alpha", "alpha", "beta", "alpha");

        var plan = EntityLoadBatchPlanner.plan(types, 0, 1, 2);

        assertFalse(plan.removes(0));
        assertFalse(plan.removes(2));
        assertTrue(plan.removes(3));
        assertTrue(plan.removes(5));
        assertFalse(plan.removes(1));
        assertFalse(plan.removes(4));
    }

    @Test
    void resolvesCountTiesByTypeKey() {
        List<String> types = new ArrayList<>();
        add(types, "charlie", 4);
        add(types, "bravo", 4);
        add(types, "alpha", 4);

        var plan = EntityLoadBatchPlanner.plan(types, 0, 2, 1);

        assertEquals(List.of(
                new EntityLoadBatchPlanner.Removal<>("alpha", 3),
                new EntityLoadBatchPlanner.Removal<>("bravo", 3)), plan.removals());
    }

    @Test
    void itemRegistryIdsRemainDistinctTypeKeys() {
        EntityLoadType stone = EntityLoadType.item(ResourceLocation.parse("minecraft:stone"));
        EntityLoadType dirt = EntityLoadType.item(ResourceLocation.parse("minecraft:dirt"));
        List<EntityLoadType> types = List.of(stone, dirt, stone, dirt, stone, dirt);

        var plan = EntityLoadBatchPlanner.plan(types, 0, 3, 1);

        assertNotEquals(stone, dirt);
        assertEquals(4, plan.removalCount());
        assertEquals(2, plan.removals().size());
    }

    private static EntityLoadBatchPlanner.Plan<String> productionPlan(List<String> types) {
        return EntityLoadBatchPlanner.plan(types, EntityLoadGuard.BATCH_THRESHOLD,
                EntityLoadGuard.TOP_TYPE_LIMIT, EntityLoadGuard.RETAINED_PER_TYPE);
    }

    private static List<String> repeated(String type, int count) {
        List<String> result = new ArrayList<>(count + 1);
        add(result, type, count);
        return result;
    }

    private static void add(List<String> target, String type, int count) {
        for (int index = 0; index < count; index++) {
            target.add(type);
        }
    }
}
