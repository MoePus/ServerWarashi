package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.client.event.ClientHudRuntimeEvents;
import com.moepus.serverwarashi.client.state.ClientResidentCostState;
import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ResidentCostScreen extends TableReportPanelScreen {
    private static final TableReport WAITING_REPORT = new TableReport(
            "Resident Chunk Cost",
            List.of("Waiting for resident cost data"),
            List.of()
    );

    public ResidentCostScreen() {
        super(Component.literal("Resident Chunk Cost"));
    }

    @Override
    protected TableReport currentReport() {
        // 有新鲜数据则按配置行数显示，否则显示"等待数据"占位报告
        return ClientResidentCostState.freshReport(ClientConfig.residentHudMaxRows())
                .orElse(WAITING_REPORT);
    }

    @Override
    public void removed() {
        // 界面被移除时退订驻留成本推送（覆盖 ESC/toggle/断线/被替换等所有移除路径）
        ClientHudRuntimeEvents.closeResidentCostScreen();
        super.removed();
    }
}
