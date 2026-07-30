package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaeroWeakChunkClearMenuTest {
    private static final WeakEntityType ITEM =
            new WeakEntityType(WeakEntityType.Kind.ITEM, "minecraft:cobblestone");

    @AfterEach
    void reset() {
        XaeroWeakChunkClearMenu.clear();
    }

    @Test
    void requiresSameChunkAndTypeForConfirmation() {
        var target = pending(1, 2, ITEM);
        XaeroWeakChunkClearMenu.select(target, false);

        assertTrue(XaeroWeakChunkClearMenu.isConfirming(target));
        assertFalse(XaeroWeakChunkClearMenu.isConfirming(pending(2, 2, ITEM)));
        assertFalse(XaeroWeakChunkClearMenu.isConfirming(pending(1, 2,
                new WeakEntityType(WeakEntityType.Kind.ENTITY, "minecraft:cow"))));
    }

    private static XaeroWeakChunkClearMenu.Pending pending(int x, int z, WeakEntityType type) {
        return new XaeroWeakChunkClearMenu.Pending("minecraft:overworld", x, z, type);
    }
}
