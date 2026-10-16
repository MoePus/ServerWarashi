package com.moepus.serverwarashi.common.config;

import com.moepus.serverwarashi.ServerWarashi;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

@EventBusSubscriber(modid = ServerWarashi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class FeatureConfig {
    // Config loading completes before the watchdog thread starts, publishing this startup value.
    private static boolean watchdogTicketReportEnabled =
            ServerConfig.WATCHDOG_TICKET_REPORT_ENABLED.getDefault();

    private FeatureConfig() {
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            // Keep the startup value after config unloading so the watchdog can still report.
            watchdogTicketReportEnabled = ServerConfig.WATCHDOG_TICKET_REPORT_ENABLED.get();
        }
    }

    public static boolean weakChunkScanEnabled() {
        return ServerConfig.WEAK_CHUNK_SCAN_ENABLED.get();
    }

    public static boolean watchdogTicketReportEnabled() {
        return watchdogTicketReportEnabled;
    }
}
