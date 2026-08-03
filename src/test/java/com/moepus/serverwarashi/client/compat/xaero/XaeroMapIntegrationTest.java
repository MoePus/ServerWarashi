package com.moepus.serverwarashi.client.compat.xaero;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaeroMapIntegrationTest {
    @Test
    void followsInheritedParentFieldsThroughNestedScreens() {
        Screen map = screen();
        ParentScreen settings = new ParentScreen(map);
        ParentScreen confirmation = new ParentScreen(settings);

        assertTrue(XaeroMapIntegration.hasMapAncestor(
                confirmation, candidate -> candidate == map));
    }

    @Test
    void rejectsUnrelatedAndCyclicParentChains() {
        Screen map = screen();
        ParentScreen first = new ParentScreen(null);
        ParentScreen second = new ParentScreen(first);
        first.parent = second;

        assertFalse(XaeroMapIntegration.hasMapAncestor(screen(), candidate -> candidate == map));
        assertFalse(XaeroMapIntegration.hasMapAncestor(first, candidate -> candidate == map));
    }

    private static Screen screen() {
        return new Screen(Component.empty()) {
        };
    }

    private static final class ParentScreen extends Screen {
        public Screen parent;

        private ParentScreen(Screen parent) {
            super(Component.empty());
            this.parent = parent;
        }
    }
}
