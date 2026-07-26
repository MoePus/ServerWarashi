package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.client.network.PerfControlClient;
import com.moepus.serverwarashi.client.state.ClientPerfReportState;
import com.moepus.serverwarashi.common.network.PerfControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import xaero.map.gui.GuiMap;

public final class XaeroPerfControls {
    private static final int BUTTON_Y = 5;
    private static final int TOGGLE_X = 34;
    private static final int TOGGLE_WIDTH = 70;
    private static final int CLEAR_X = 108;
    private static final int CLEAR_WIDTH = 78;
    private static final int BUTTON_HEIGHT = 20;

    private XaeroPerfControls() {
    }

    public static Controls install(GuiMap map) {
        Button toggle = Button.builder(toggleLabel(false), ignored -> send(PerfControlPayload.Action.TOGGLE))
                .bounds(TOGGLE_X, BUTTON_Y, TOGGLE_WIDTH, BUTTON_HEIGHT)
                .build();
        Button clear = Button.builder(Component.translatable("screen.serverwarashi.heatmap.clear"),
                        ignored -> send(PerfControlPayload.Action.CLEAR))
                .bounds(CLEAR_X, BUTTON_Y, CLEAR_WIDTH, BUTTON_HEIGHT)
                .build();
        map.addButton(toggle);
        map.addButton(clear);
        Controls controls = new Controls(toggle, clear);
        update(controls);
        return controls;
    }

    public static void update(Controls controls) {
        if (controls == null) {
            return;
        }
        ClientPerfReportState.Snapshot snapshot = ClientPerfReportState.snapshot();
        boolean visible = hasPerfPermission();
        controls.toggle().visible = visible;
        controls.clear().visible = visible;
        controls.toggle().setMessage(toggleLabel(snapshot.profiling()));
        controls.clear().active = !snapshot.profiling() && hasCompletedReport(snapshot);
    }

    public static boolean isMouseOver(double mouseX, double mouseY) {
        return hasPerfPermission() && (contains(mouseX, mouseY, TOGGLE_X, TOGGLE_WIDTH)
                || contains(mouseX, mouseY, CLEAR_X, CLEAR_WIDTH));
    }

    private static boolean hasPerfPermission() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getCommands().getRoot().getChild("warashi") != null;
    }

    private static boolean hasCompletedReport(ClientPerfReportState.Snapshot snapshot) {
        return snapshot.report() != null && snapshot.report().tickCount() > 0L;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int width) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= BUTTON_Y && mouseY < BUTTON_Y + BUTTON_HEIGHT;
    }

    private static Component toggleLabel(boolean profiling) {
        return Component.translatable(profiling
                ? "screen.serverwarashi.profiler.stop"
                : "screen.serverwarashi.profiler.start");
    }

    private static void send(PerfControlPayload.Action action) {
        PerfControlClient.send(action);
    }

    public record Controls(Button toggle, Button clear) {
    }
}
