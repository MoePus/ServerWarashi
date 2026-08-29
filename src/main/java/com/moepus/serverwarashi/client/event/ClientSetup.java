package com.moepus.serverwarashi.client.event;

import com.mojang.logging.LogUtils;
import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.client.compat.xaero.XaeroMapIntegration;
import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.client.network.ClientPerfPayloadRouter;
import com.moepus.serverwarashi.client.network.PerfMapSubscriptionClient;
import com.moepus.serverwarashi.client.screen.PerfReportScreen;
import com.moepus.serverwarashi.client.state.ClientPerfStateCoordinator;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportTextRenderer;
import com.moepus.serverwarashi.common.network.ClientPayloadHandlers;
import com.moepus.serverwarashi.common.network.LoaderControlStatePayload;
import com.moepus.serverwarashi.common.network.PerfReportPayload;
import com.moepus.serverwarashi.common.network.PerfSpatialSnapshotPayload;
import com.moepus.serverwarashi.common.network.PerfWorldHotspotPayload;
import com.moepus.serverwarashi.common.network.WeakChunkPayload;
import com.moepus.serverwarashi.common.network.WeakChunkCorrectionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.LoadingModList;
import org.slf4j.Logger;

/**
 * 客户端一次性装配（MOD 总线）：把 profiler 报告处理器接到分发器。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ServerWarashi.MODID, bus = EventBusSubscriber.Bus.MOD)

public final class ClientSetup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (LoadingModList.get().getModFileById("xaeroworldmap") != null) {
            try {
                XaeroMapIntegration.register();
            } catch (Throwable throwable) {
                LOGGER.error("Could not initialize the Xaero map integration", throwable);
            }
        }
        ClientPayloadHandlers.setPerfReportHandler(ClientSetup::handlePerfReport);
        ClientPayloadHandlers.setPerfSpatialHandler(ClientSetup::handlePerfSpatial);
        ClientPayloadHandlers.setPerfWorldHandler(ClientSetup::handlePerfWorld);
        ClientPayloadHandlers.setWeakChunkHandler(ClientSetup::handleWeakChunks);
        ClientPayloadHandlers.setWeakChunkCorrectionHandler(ClientSetup::handleWeakChunkCorrection);
        ClientPayloadHandlers.setLoaderControlHandler(ClientSetup::handleLoaderControl);
    }

    private static void handleWeakChunks(WeakChunkPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.level != null && minecraft.level.dimension().location().toString()
                    .equals(payload.snapshot().dimension())) {
                ClientWeakChunkState.update(payload);
            }
        });
    }

    private static void handleWeakChunkCorrection(WeakChunkCorrectionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.level != null && minecraft.level.dimension().location().toString()
                    .equals(payload.dimension())) {
                ClientWeakChunkState.correct(payload);
            }
        });
    }

    private static void handlePerfReport(PerfReportPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> handlePerfReportOnClient(minecraft, payload));
    }

    private static void handlePerfSpatial(PerfSpatialSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> ClientPerfPayloadRouter.applySpatialSnapshot(payload));
    }

    private static void handlePerfWorld(PerfWorldHotspotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> ClientPerfStateCoordinator.applyWorldDelta(payload));
    }

    private static void handleLoaderControl(LoaderControlStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            ClientPerfStateCoordinator.applyLoaderControl(payload);
            if (payload.status() != LoaderControlStatePayload.Status.SYNC && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.translatable(
                        loaderControlMessage(payload), payload.controllerId().toString()));
            }
        });
    }

    private static String loaderControlMessage(LoaderControlStatePayload payload) {
        return switch (payload.status()) {
            case APPLIED -> payload.manuallyFrozen()
                    ? "message.serverwarashi.loader_control.frozen"
                    : "message.serverwarashi.loader_control.unfrozen";
            case UNCHANGED -> "message.serverwarashi.loader_control.unchanged";
            case STALE -> "message.serverwarashi.loader_control.stale";
            case MISSING -> "message.serverwarashi.loader_control.missing";
            case UNSUPPORTED -> "message.serverwarashi.loader_control.unsupported";
            case DENIED -> "message.serverwarashi.loader_control.denied";
            case SYNC -> throw new IllegalArgumentException("SYNC has no feedback message");
        };
    }

    private static void handlePerfReportOnClient(Minecraft minecraft, PerfReportPayload payload) {
        ClientPerfStateCoordinator.applyFullReport(payload.report(), payload.profiling());
        if (!payload.profiling()) {
            PerfMapSubscriptionClient.setAnalysisLive(false);
        }
        switch (payload.mode()) {
            case DISPLAY -> displayPerfReport(minecraft, payload.report());
            case OPEN -> openOrUpdatePerfScreen(minecraft, payload);
            case UPDATE -> updateOpenPerfScreen(minecraft, payload);
        }
    }

    private static void openOrUpdatePerfScreen(Minecraft minecraft, PerfReportPayload payload) {
        if (minecraft.screen instanceof PerfReportScreen screen) {
            screen.updateReport(payload.report(), payload.profiling());
        } else {
            minecraft.setScreen(new PerfReportScreen(payload.report(), payload.profiling()));
        }
    }

    private static void updateOpenPerfScreen(Minecraft minecraft, PerfReportPayload payload) {
        if (minecraft.screen instanceof PerfReportScreen screen) {
            screen.updateReport(payload.report(), payload.profiling());
        }
    }

    private static void displayPerfReport(Minecraft minecraft, PerfReportData report) {
        switch (ClientConfig.perfOutputMode()) {
            case SCREEN -> minecraft.setScreen(new PerfReportScreen(report, false));
            case CHAT -> sendPerfReportToChat(minecraft, report);
            case BOTH -> {
                sendPerfReportToChat(minecraft, report);
                minecraft.setScreen(new PerfReportScreen(report, false));
            }
        }
    }

    private static void sendPerfReportToChat(Minecraft minecraft, PerfReportData report) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(PerfReportTextRenderer.render(report));
        }
    }

}
