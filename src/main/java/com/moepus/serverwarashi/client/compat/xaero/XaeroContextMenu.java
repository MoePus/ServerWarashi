package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.api.client.map.MapMenuItem;
import com.moepus.serverwarashi.api.client.map.MapOverlayApi;
import com.moepus.serverwarashi.api.client.map.MapTarget;
import net.minecraft.client.gui.screens.Screen;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

public final class XaeroContextMenu {
    private XaeroContextMenu() {
    }

    public static void addOptions(GuiMap screen, ArrayList<RightClickOption> options) {
        MapTarget target = XaeroMapAccess.rightClickTarget(screen);
        if (target == null) {
            return;
        }
        for (MapMenuItem item : MapOverlayApi.contextMenu(XaeroMapIntegration.ID, target)) {
            options.add(toXaeroOption(screen, options.size(), item));
        }
    }

    private static RightClickOption toXaeroOption(GuiMap screen,
                                                   int index,
                                                   MapMenuItem item) {
        return new RightClickOption(literalName(item), index, screen) {
            @Override
            public void onAction(Screen ignored) {
                item.action().run();
            }
        }.setActive(item.active());
    }

    static String literalName(MapMenuItem item) {
        return item.label().getString().replace("%", "%%");
    }
}
