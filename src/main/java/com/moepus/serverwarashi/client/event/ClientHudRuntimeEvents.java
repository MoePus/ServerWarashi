package com.moepus.serverwarashi.client.event;

import com.moepus.serverwarashi.Serverwarashi;
import com.moepus.serverwarashi.client.screen.ResidentCostScreen;
import com.moepus.serverwarashi.client.state.ClientResidentCostState;
import com.moepus.serverwarashi.common.network.ResidentHudSubscribePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(value = Dist.CLIENT, modid = Serverwarashi.MODID)

public final class ClientHudRuntimeEvents {
    private static ClientPacketListener activeConnection;
    private static boolean subscribed;

    private ClientHudRuntimeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) {
            clearConnection();
            return;
        }
        if (connection != activeConnection) {
            activeConnection = connection;
            subscribed = false;
            ClientResidentCostState.clear();
        }
        while (ClientHudHandler.consumeToggleClick()) {
            toggleResidentCostScreen(minecraft);
        }
    }

    static void toggleResidentCostScreen(Minecraft minecraft) {
        if (minecraft.screen instanceof ResidentCostScreen) {
            minecraft.screen.onClose();
            return;
        }
        ClientResidentCostState.clear();
        if (syncSubscription(true)) {
            minecraft.setScreen(new ResidentCostScreen());
            sendStatus("Resident cost screen opened");
        }
    }

    public static void closeResidentCostScreen() {
        boolean wasEnabled = subscribed;
        ClientResidentCostState.clear();
        if (syncSubscription(false) && wasEnabled) {
            sendStatus("Resident cost screen closed");
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    static boolean syncSubscription(boolean enabled) {
        if (activeConnection == null || subscribed == enabled) {
            return true;
        }
        // 两个 payload 由同一 registrar 同版本一并注册，协商结果必然同有同无，查订阅包一个即可
        if (!NetworkRegistry.hasChannel(activeConnection, ResidentHudSubscribePayload.TYPE.id())) {
            subscribed = false;
            ClientResidentCostState.clear();
            sendStatus("Resident cost HUD is not supported by this server");
            return false;
        }
        PacketDistributor.sendToServer(new ResidentHudSubscribePayload(enabled));
        subscribed = enabled;
        return true;
    }

    private static void clearConnection() {
        activeConnection = null;
        subscribed = false;
        ClientResidentCostState.clear();
    }

    private static void sendStatus(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(text));
        }
    }
}
