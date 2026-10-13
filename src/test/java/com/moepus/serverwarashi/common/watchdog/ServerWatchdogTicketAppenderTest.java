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
        // 测试中不加载服务端配置，模拟关服卸载后配置不可读取的状态。
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
