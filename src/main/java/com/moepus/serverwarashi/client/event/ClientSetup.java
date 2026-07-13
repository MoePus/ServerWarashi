package com.moepus.serverwarashi.client.event;

import com.mojang.blaze3d.platform.InputConstants;
import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.client.screen.TableReportPanelScreen;
import com.moepus.serverwarashi.client.state.ClientResidentCostState;
import com.moepus.serverwarashi.common.network.ClientReportDispatch;
import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.report.TableReportChatRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端一次性装配（MOD 总线）：注册按键、把两类报告的处理器接到分发器。
 * 运行期事件见 {@link ResidentCostHudEvents}（GAME 总线）。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ServerWarashi.MODID, bus = EventBusSubscriber.Bus.MOD)

public final class ClientSetup {
    private static final String KEY_CATEGORY = "key.categories.serverwarashi";
    private static final String TOGGLE_KEY = "key.serverwarashi.resident_hud";

    private static KeyMapping toggleKey;

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 性能报告：按输出模式显示界面/聊天；驻留成本报告：更新客户端状态
        ClientReportDispatch.setPerfReportHandler(ClientSetup::handlePerfReport);
        ClientReportDispatch.setResidentCostHandler(ClientResidentCostState::update);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 注册"驻留成本 HUD"切换键（默认未绑定）
        toggleKey = new KeyMapping(TOGGLE_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
        event.register(toggleKey);
    }

    static boolean consumeToggleClick() {
        // 消费一次切换键按下；按键注册事件先于任何 client tick，此处必已注册
        return toggleKey.consumeClick();
    }

    private static void handlePerfReport(TableReport report) {
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
            minecraft.player.sendSystemMessage(TableReportChatRenderer.toComponent(report));
        }
    }
}
