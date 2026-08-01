package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.client.network.WeakChunkClearClient;
import com.moepus.serverwarashi.client.screen.PerfDisplayText;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import com.moepus.serverwarashi.common.mixin.compat.xaero.XaeroGuiMapAccessor;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import com.moepus.serverwarashi.common.network.WeakChunkClearPayload;
import net.minecraft.client.gui.screens.Screen;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;
import java.util.Map;

public final class XaeroWeakChunkClearMenu {
    private static Pending pending;

    private XaeroWeakChunkClearMenu() {
    }

    public static void addOption(GuiMap screen, ArrayList<RightClickOption> options) {
        MenuTarget menuTarget = target(screen);
        if (menuTarget == null || !WeakChunkClearClient.canSend()) {
            return;
        }
        boolean confirming = isConfirming(menuTarget.pending());
        String key = confirming
                ? "gui.serverwarashi.weak_clear_confirm"
                : "gui.serverwarashi.weak_clear";
        options.add(new RightClickOption(key, options.size(), screen) {
            @Override
            public void onAction(Screen ignored) {
                select(menuTarget.pending(), confirming);
            }
        }.setNameFormatArgs(PerfDisplayText.sampledEntityName(menuTarget.type())));
    }

    public static void clear() {
        pending = null;
    }

    static void select(Pending target, boolean confirming) {
        if (!confirming) {
            pending = target;
            return;
        }
        WeakChunkClearClient.send(new WeakChunkClearPayload(
                target.dimension(), target.chunkX(), target.chunkZ(), target.type()));
        pending = null;
    }

    static boolean isConfirming(Pending target) {
        return target.equals(pending);
    }

    private static MenuTarget target(GuiMap screen) {
        XaeroGuiMapAccessor map = (XaeroGuiMapAccessor) screen;
        if (map.serverwarashi$getRightClickDimension() == null) {
            return null;
        }
        String dimension = map.serverwarashi$getRightClickDimension().location().toString();
        int chunkX = map.serverwarashi$getRightClickX() >> 4;
        int chunkZ = map.serverwarashi$getRightClickZ() >> 4;
        WeakChunkSnapshot.ChunkEntry chunk = ClientWeakChunkState.chunk(dimension, chunkX, chunkZ);
        if (chunk == null || chunk.entityCount() < WeakChunkSnapshot.ENTITY_RED_THRESHOLD) {
            return null;
        }
        Map.Entry<WeakEntityType, Integer> top = chunk.topTypes(1).getFirst();
        return new MenuTarget(new Pending(dimension, chunkX, chunkZ, top.getKey()));
    }

    record Pending(String dimension, int chunkX, int chunkZ, WeakEntityType type) {
    }

    private record MenuTarget(Pending pending) {
        WeakEntityType type() {
            return pending.type();
        }
    }
}
