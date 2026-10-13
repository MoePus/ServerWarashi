package com.moepus.serverwarashi.common.watchdog;

import com.moepus.serverwarashi.common.config.FeatureConfig;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ServerWatchdogTicketAppenderTest {
    @Test
    void skipsDiagnosticsWhenServerConfigIsUnavailable() {
        // No server config is loaded in this test, matching the unavailable state after shutdown.
        assertThrows(IllegalStateException.class, FeatureConfig::watchdogTicketReportEnabled);
        SharedConstants.tryDetectVersion();
        CrashReport report = new CrashReport("Watchdog during shutdown", new RuntimeException("Server hung"));
        Iterable<ServerLevel> levels = () -> {
            fail("Unavailable config must skip level diagnostics");
            return null;
        };

        assertDoesNotThrow(() -> ServerWatchdogTicketAppender.appendTicketInfoToReport(report, levels));
    }
}
