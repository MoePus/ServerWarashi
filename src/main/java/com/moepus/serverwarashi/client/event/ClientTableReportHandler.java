package com.moepus.serverwarashi.client.event;

import com.moepus.serverwarashi.Serverwarashi;
import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.client.screen.TableReportPanelScreen;
import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.report.TableReportRenderer;
import com.moepus.serverwarashi.common.network.ReportDispatch;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = Serverwarashi.MODID, bus = EventBusSubscriber.Bus.MOD)

public final class ClientTableReportHandler {
    private ClientTableReportHandler() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 客户端初始化：把性能报告的处理器接上
        ReportDispatch.setPerfReportHandler(ClientTableReportHandler::handleReport);
    }

    private static void handleReport(TableReport report) {
        // 按客户端配置的输出模式：界面 / 聊天 / 两者
        Minecraft minecraft = Minecraft.getInstance();
        switch (ClientConfig.perfOutputMode()) {
            case SCREEN -> minecraft.setScreen(TableReportPanelScreen.of(report));
            case CHAT -> sendToChat(minecraft, report);
            case BOTH -> {
                sendToChat(minecraft, report);
                minecraft.setScreen(TableReportPanelScreen.of(report));
            }
        }
    }

    private static void sendToChat(Minecraft minecraft, TableReport report) {
        // 把报告渲染成可点击聊天文本发给本地玩家
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(TableReportRenderer.toComponent(report));
        }
    }
}
