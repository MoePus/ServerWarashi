package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.client.network.LoaderFreezeClient;
import com.moepus.serverwarashi.client.screen.PerfDisplayText;
import com.moepus.serverwarashi.client.state.ClientPerfReportState;
import com.moepus.serverwarashi.common.mixin.compat.xaero.XaeroGuiMapAccessor;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.LoaderFreezeActionPayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class XaeroLoaderFreezeMenu {
    private static final int MAX_OPTIONS = 12;
    private static final Comparator<MenuTarget> TARGET_ORDER = Comparator
            .comparing((MenuTarget target) -> target.controllerId().toString())
            .thenComparingInt(target -> target.ownerPos().getX())
            .thenComparingInt(target -> target.ownerPos().getY())
            .thenComparingInt(target -> target.ownerPos().getZ());

    private XaeroLoaderFreezeMenu() {
    }

    public static void addOptions(GuiMap screen, ArrayList<RightClickOption> options) {
        List<MenuTarget> targets = targets(screen);
        int visible = Math.min(targets.size(), MAX_OPTIONS);
        for (int index = 0; index < visible; index++) {
            addOption(screen, options, targets.get(index));
        }
        if (targets.size() > visible) {
            RightClickOption more = new RightClickOption(
                    "gui.serverwarashi.loader_control_more", options.size(), screen) {
                @Override
                public void onAction(Screen ignored) {
                }
            }.setNameFormatArgs(targets.size() - visible).setActive(false);
            options.add(more);
        }
    }

    static List<MenuTarget> targets(GuiMap screen) {
        if (!LoaderFreezeClient.canSend()) {
            return List.of();
        }
        XaeroGuiMapAccessor map = (XaeroGuiMapAccessor) screen;
        if (map.serverwarashi$getRightClickDimension() == null) {
            return List.of();
        }
        ResourceLocation dimension = map.serverwarashi$getRightClickDimension().location();
        int chunkX = map.serverwarashi$getRightClickX() >> 4;
        int chunkZ = map.serverwarashi$getRightClickZ() >> 4;
        PerfReportData report = ClientPerfReportState.snapshot().report();
        return targets(report, new Location(report == null ? 0L : report.reportId(),
                dimension, chunkX, chunkZ));
    }

    static List<MenuTarget> targets(PerfReportData report, Location location) {
        if (report == null || report.reportId() <= 0L || report.reportId() != location.reportId()) {
            return List.of();
        }
        Map<Identity, MenuTarget> unique = new LinkedHashMap<>();
        for (PerfReportData.LoaderSourceEntry source : report.loaderSources()) {
            addTarget(unique, source, location);
        }
        return unique.values().stream().sorted(TARGET_ORDER).toList();
    }

    private static void addTarget(Map<Identity, MenuTarget> targets,
                                  PerfReportData.LoaderSourceEntry source,
                                  Location location) {
        PerfReportData.LoaderControl control = source.control();
        if (control == null || source.omittedRegions() > 0
                || !source.dimension().equals(location.dimension().toString())
                || !isOwnerChunk(control.ownerPos(), location)) {
            return;
        }
        Identity identity = new Identity(control.controllerId(), control.ownerPos());
        targets.putIfAbsent(identity, new MenuTarget(
                location.reportId(), location.dimension(), control.controllerId(), control.ownerPos(),
                source.loaderName(), control.manuallyFrozen()));
    }

    private static boolean isOwnerChunk(BlockPos ownerPos, Location location) {
        return (ownerPos.getX() >> 4) == location.chunkX()
                && (ownerPos.getZ() >> 4) == location.chunkZ();
    }

    private static void addOption(GuiMap screen,
                                  ArrayList<RightClickOption> options,
                                  MenuTarget selection) {
        String key = selection.manuallyFrozen()
                ? "gui.serverwarashi.loader_unfreeze"
                : "gui.serverwarashi.loader_freeze";
        options.add(new RightClickOption(key, options.size(), screen) {
            @Override
            public void onAction(Screen ignored) {
                LoaderFreezeClient.send(new LoaderFreezeActionPayload(
                        selection.reportId(), selection.dimension(), selection.controllerId(),
                        selection.ownerPos(), selection.requestedFrozen()));
            }
        }.setNameFormatArgs(PerfDisplayText.resource(selection.loaderName())));
    }

    record MenuTarget(long reportId, ResourceLocation dimension, ResourceLocation controllerId,
                      BlockPos ownerPos, String loaderName, boolean manuallyFrozen) {
        boolean requestedFrozen() {
            return !manuallyFrozen;
        }
    }

    private record Identity(ResourceLocation controllerId, BlockPos ownerPos) {
    }

    record Location(long reportId, ResourceLocation dimension, int chunkX, int chunkZ) {
    }
}
