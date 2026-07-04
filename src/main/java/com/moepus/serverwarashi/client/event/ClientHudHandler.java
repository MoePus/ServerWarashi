package com.moepus.serverwarashi.client.event;

import com.mojang.blaze3d.platform.InputConstants;
import com.moepus.serverwarashi.Serverwarashi;
import com.moepus.serverwarashi.client.state.ClientResidentCostState;
import com.moepus.serverwarashi.common.network.ReportDispatch;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(value = Dist.CLIENT, modid = Serverwarashi.MODID, bus = EventBusSubscriber.Bus.MOD)

public final class ClientHudHandler {
    private static final String KEY_CATEGORY = "key.categories.serverwarashi";
    private static final String TOGGLE_KEY = "key.serverwarashi.resident_hud";

    private static KeyMapping toggleKey;

    private ClientHudHandler() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 客户端初始化：把驻留成本报告的处理器接到客户端状态的 update
        ReportDispatch.setResidentCostHandler(ClientResidentCostState::update);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 注册"驻留成本 HUD"切换键（默认未绑定）
        toggleKey = new KeyMapping(TOGGLE_KEY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
        event.register(toggleKey);
    }

    static boolean consumeToggleClick() {
        // 消费一次切换键按下（含未注册时的空安全）
        return toggleKey != null && toggleKey.consumeClick();
    }
}
