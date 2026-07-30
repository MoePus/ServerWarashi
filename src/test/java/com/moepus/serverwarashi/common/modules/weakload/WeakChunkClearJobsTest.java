package com.moepus.serverwarashi.common.modules.weakload;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeakChunkClearJobsTest {
    private static final WeakEntityType FIRST = entity("test:first");
    private static final WeakEntityType SECOND = entity("test:second");
    private static final WeakEntityType THIRD = entity("test:third");
    private static final WeakEntityType FOURTH = entity("test:fourth");

    @Test
    void acceptsCurrentTopThreeButNotFourth() {
        var warning = new WeakChunkSnapshot.ChunkEntry(0, 0, 1_000,
                Map.of(FIRST, 400, SECOND, 300, THIRD, 200, FOURTH, 100));

        assertTrue(WeakChunkClearJobs.isEstimatedTarget(warning, FIRST));
        assertTrue(WeakChunkClearJobs.isEstimatedTarget(warning, THIRD));
        assertFalse(WeakChunkClearJobs.isEstimatedTarget(warning, FOURTH));
    }

    @Test
    void rejectsWarningBelowDangerThreshold() {
        var warning = new WeakChunkSnapshot.ChunkEntry(0, 0, 499, Map.of(FIRST, 499));

        assertFalse(WeakChunkClearJobs.isEstimatedTarget(warning, FIRST));
    }

    @Test
    void attemptBudgetAllowsADeletePassAndARefusalPass() {
        assertEquals(201_000, WeakChunkClearJobs.attemptBudget(100_000));
    }

    @Test
    void attemptBudgetUsesLongArithmetic() {
        assertEquals(4_294_968_294L, WeakChunkClearJobs.attemptBudget(Integer.MAX_VALUE));
    }

    @Test
    void restoresOnlyWhenManagerDidNotEnterAndEntityHasNoManagedLocation() {
        assertEquals(WeakChunkBatchRemoval.RestoreAction.RESTORE,
                WeakChunkBatchRemoval.restoreAction(false, false));
        assertEquals(WeakChunkBatchRemoval.RestoreAction.MANAGED,
                WeakChunkBatchRemoval.restoreAction(false, true));
        assertEquals(WeakChunkBatchRemoval.RestoreAction.CALLBACK_ENTERED,
                WeakChunkBatchRemoval.restoreAction(true, false));
        assertEquals(WeakChunkBatchRemoval.RestoreAction.CALLBACK_ENTERED,
                WeakChunkBatchRemoval.restoreAction(true, true));
    }

    @Test
    void scanCursorDoesNotRevisitDetachedSurvivorsInSameCycle() {
        var cursor = new WeakChunkClearCursor();
        Object first = new Object();
        Object second = new Object();
        Object third = new Object();
        Object fourth = new Object();
        Object fifth = new Object();
        List<Object> entities = new ArrayList<>(List.of(first, second, third, fourth, fifth));
        assertEquals(new WeakChunkClearCursor.Window(0, 5), cursor.window(entities));

        entities.clear();
        entities.addAll(List.of(second, fourth, fifth, first, third));
        cursor.advance(3, fourth, entities);

        assertEquals(new WeakChunkClearCursor.Window(1, 2), cursor.window(entities));
    }

    @Test
    void scanCursorRelocatesAfterPrefixDeletion() {
        var cursor = new WeakChunkClearCursor();
        Object n0 = new Object();
        Object n1 = new Object();
        Object t0 = new Object();
        Object n2 = new Object();
        Object n3 = new Object();
        Object t1 = new Object();
        Object n4 = new Object();
        Object t2 = new Object();
        List<Object> entities = new ArrayList<>(List.of(n0, n1, t0, n2, n3, t1, n4, t2));
        cursor.window(entities);

        entities.clear();
        entities.addAll(List.of(n0, n1, n2, n3, n4, t2, t0, t1));
        cursor.advance(6, n4, entities);
        entities.remove(n0);
        entities.add(new Object());

        assertEquals(new WeakChunkClearCursor.Window(3, 2), cursor.window(entities));
    }

    @Test
    void scanCursorRestartsSectionWhenAnchorDisappears() {
        var cursor = new WeakChunkClearCursor();
        Object first = new Object();
        Object anchor = new Object();
        Object last = new Object();
        List<Object> entities = new ArrayList<>(List.of(first, anchor, last));
        cursor.window(entities);
        cursor.advance(1, anchor, entities);

        entities.remove(anchor);

        assertEquals(new WeakChunkClearCursor.Window(0, 2), cursor.window(entities));
    }

    @Test
    void scanCursorRestartsSectionWhenAnchorMovesRight() {
        var cursor = new WeakChunkClearCursor();
        Object first = new Object();
        Object anchor = new Object();
        Object last = new Object();
        List<Object> entities = new ArrayList<>(List.of(first, anchor, last));
        cursor.window(entities);
        cursor.advance(1, anchor, entities);

        entities.remove(anchor);
        entities.add(anchor);

        assertEquals(new WeakChunkClearCursor.Window(0, 3), cursor.window(entities));
    }

    @Test
    void scanCursorExcludesEntitiesAppendedBeyondInitialSlots() {
        var cursor = new WeakChunkClearCursor();
        Object first = new Object();
        Object anchor = new Object();
        Object appended = new Object();
        List<Object> entities = new ArrayList<>(List.of(first, anchor));
        cursor.window(entities);

        entities.add(appended);
        cursor.advance(1, anchor, entities);

        assertEquals(new WeakChunkClearCursor.Window(1, 1), cursor.window(entities));
    }

    @Test
    void scanCursorOnlyRestartsCycleAfterProgress() {
        var cursor = new WeakChunkClearCursor();

        cursor.finishSection();
        cursor.recordRemovals(1);
        assertFalse(cursor.completeCycle());
        assertEquals(0, cursor.sectionIndex());

        cursor.finishSection();
        assertTrue(cursor.completeCycle());
    }

    private static WeakEntityType entity(String id) {
        return new WeakEntityType(WeakEntityType.Kind.ENTITY, id);
    }
}
