package com.moepus.serverwarashi.common.modules.weakload;

import java.util.List;

final class WeakChunkClearCursor {
    private static final int NO_SECTION = -1;
    private int sectionIndex;
    private int entityIndex;
    private int remainingInSection = NO_SECTION;
    private boolean removedInCycle;
    private Object nextAnchor;

    Window window(List<?> entities) {
        if (remainingInSection == NO_SECTION) {
            restartSection(entities.size());
        }
        relocateAnchor(entities);
        entityIndex = Math.min(entityIndex, entities.size());
        int available = Math.min(remainingInSection, entities.size() - entityIndex);
        return new Window(entityIndex, available);
    }

    void advance(int examined, Object followingEntity, List<?> entities) {
        remainingInSection -= examined;
        int expectedIndex = entityIndex + examined;
        int actualIndex = identityIndexOf(entities, followingEntity);
        if (actualIndex < 0 || actualIndex > expectedIndex) {
            restartSection(entities.size());
            return;
        }
        entityIndex = actualIndex;
        nextAnchor = followingEntity;
    }

    void finishSection() {
        sectionIndex++;
        entityIndex = 0;
        remainingInSection = NO_SECTION;
        nextAnchor = null;
    }

    void recordRemovals(int count) {
        removedInCycle |= count > 0;
    }

    boolean completeCycle() {
        boolean complete = !removedInCycle;
        sectionIndex = 0;
        entityIndex = 0;
        remainingInSection = NO_SECTION;
        removedInCycle = false;
        nextAnchor = null;
        return complete;
    }

    int sectionIndex() {
        return sectionIndex;
    }

    private void relocateAnchor(List<?> entities) {
        if (nextAnchor == null) {
            return;
        }
        int actualIndex = identityIndexOf(entities, nextAnchor);
        nextAnchor = null;
        if (actualIndex < 0 || actualIndex > entityIndex) {
            restartSection(entities.size());
            return;
        }
        entityIndex = actualIndex;
    }

    private void restartSection(int listSize) {
        entityIndex = 0;
        remainingInSection = listSize;
        nextAnchor = null;
    }

    private static int identityIndexOf(List<?> entities, Object target) {
        for (int index = 0; index < entities.size(); index++) {
            if (entities.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    record Window(int start, int length) {
    }
}
