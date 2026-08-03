package com.moepus.serverwarashi.api.client.map;

import com.moepus.serverwarashi.client.network.PerfControlClient;
import com.moepus.serverwarashi.client.state.ClientPerfReportState;
import com.moepus.serverwarashi.common.network.PerfControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** Shared profiling controls that map integrations can add to their screens. */
public final class MapOverlayControls {
    private static final int BUTTON_Y = 5;
    private static final int TOGGLE_X = 34;
    private static final int TOGGLE_WIDTH = 70;
    private static final int CLEAR_X = 108;
    private static final int CLEAR_WIDTH = 78;
    private static final int BUTTON_HEIGHT = 20;
    private static final int STATUS_Y = BUTTON_Y + BUTTON_HEIGHT + 5;
    private static final int STATUS_COLOR = 0xFFFFD166;
    private static final int TOOLTIP_MAX_WIDTH = 240;
    private static final float STATUS_Z = 1_000.0F;
    private static final HorizontalBounds TOGGLE_BOUNDS =
            new HorizontalBounds(TOGGLE_X, TOGGLE_WIDTH);
    private static final HorizontalBounds CLEAR_BOUNDS =
            new HorizontalBounds(CLEAR_X, CLEAR_WIDTH);
    private static Boolean requestedProfiling;

    private final Button toggle;
    private final Button clear;

    private MapOverlayControls(Button toggle, Button clear) {
        this.toggle = toggle;
        this.clear = clear;
    }

    /** Create both buttons and pass them to the map integration's widget sink. */
    static MapOverlayControls create(Consumer<Button> buttonSink) {
        return create(buttonSink, MapOverlayControls::update);
    }

    static MapOverlayControls create(Consumer<Button> buttonSink,
                                     Consumer<MapOverlayControls> initializer) {
        Objects.requireNonNull(buttonSink, "buttonSink");
        Objects.requireNonNull(initializer, "initializer");
        Button toggle = Button.builder(toggleLabel(false), ignored -> toggleProfiling())
                .bounds(TOGGLE_X, BUTTON_Y, TOGGLE_WIDTH, BUTTON_HEIGHT)
                .build();
        Button clear = Button.builder(Component.translatable("screen.serverwarashi.heatmap.clear"),
                        ignored -> PerfControlClient.send(PerfControlPayload.Action.CLEAR))
                .bounds(CLEAR_X, BUTTON_Y, CLEAR_WIDTH, BUTTON_HEIGHT)
                .build();
        MapOverlayControls controls = new MapOverlayControls(toggle, clear);
        try {
            initializer.accept(controls);
            buttonSink.accept(toggle);
            buttonSink.accept(clear);
            return controls;
        } catch (RuntimeException | Error throwable) {
            controls.hide();
            throw throwable;
        }
    }

    /** Refresh labels, visibility, and enabled state from the latest profiler snapshot. */
    public void update() {
        ClientPerfReportState.Snapshot snapshot = ClientPerfReportState.snapshot();
        boolean profiling = effectiveProfiling(snapshot);
        boolean visible = hasPerfPermission();
        toggle.visible = visible;
        clear.visible = visible;
        toggle.active = true;
        toggle.setMessage(toggleLabel(profiling));
        clear.active = !profiling && hasCompletedReport(snapshot);
    }

    /** Draw the integrated-server pause status after the map's normal foreground UI. */
    public void renderStatus(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hasPerfPermission() || !isAnalysisPaused()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        Component status = Component.translatable("screen.serverwarashi.profiler.analysis_paused");
        int width = font.width(status);
        int x = TOGGLE_X + (TOGGLE_WIDTH - width) / 2;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, STATUS_Z);
        try {
            graphics.drawString(font, status, x, STATUS_Y, STATUS_COLOR, true);
            if (mouseX >= x && mouseX < x + width
                    && mouseY >= STATUS_Y && mouseY < STATUS_Y + font.lineHeight) {
                Component tooltip = Component.translatable(
                        "screen.serverwarashi.profiler.game_paused_tooltip");
                graphics.renderTooltip(font, font.split(tooltip, TOOLTIP_MAX_WIDTH), mouseX, mouseY);
            }
        } finally {
            pose.popPose();
        }
    }

    /** Returns whether a pointer is over these controls or their pause status. */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return hasPerfPermission() && (contains(mouseX, mouseY, TOGGLE_BOUNDS)
                || contains(mouseX, mouseY, CLEAR_BOUNDS)
                || isOverPausedStatus(mouseX, mouseY));
    }

    /** Hide both buttons after their owning map integration is disabled. */
    public void hide() {
        toggle.visible = false;
        toggle.active = false;
        clear.visible = false;
        clear.active = false;
    }

    private static boolean hasPerfPermission() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getCommands().getRoot().getChild("warashi") != null;
    }

    private static boolean hasCompletedReport(ClientPerfReportState.Snapshot snapshot) {
        return snapshot.report() != null && snapshot.report().tickCount() > 0L;
    }

    private static boolean contains(double mouseX,
                                    double mouseY,
                                    HorizontalBounds bounds) {
        return mouseX >= bounds.x() && mouseX < bounds.x() + bounds.width()
                && mouseY >= BUTTON_Y && mouseY < BUTTON_Y + BUTTON_HEIGHT;
    }

    private static Component toggleLabel(boolean profiling) {
        return Component.translatable(profiling
                ? "screen.serverwarashi.profiler.stop"
                : "screen.serverwarashi.profiler.start");
    }

    private static boolean isAnalysisPaused() {
        return isIntegratedServerPaused()
                && effectiveProfiling(ClientPerfReportState.snapshot());
    }

    private static boolean isOverPausedStatus(double mouseX, double mouseY) {
        if (!isAnalysisPaused()) {
            return false;
        }
        var font = Minecraft.getInstance().font;
        int width = font.width(Component.translatable(
                "screen.serverwarashi.profiler.analysis_paused"));
        int x = TOGGLE_X + (TOGGLE_WIDTH - width) / 2;
        return mouseX >= x && mouseX < x + width
                && mouseY >= STATUS_Y && mouseY < STATUS_Y + font.lineHeight;
    }

    private static boolean effectiveProfiling(ClientPerfReportState.Snapshot snapshot) {
        if (requestedProfiling == null) {
            return snapshot.profiling();
        }
        if (snapshot.profiling() == requestedProfiling) {
            requestedProfiling = null;
            return snapshot.profiling();
        }
        return requestedProfiling;
    }

    private static boolean isIntegratedServerPaused() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && server.isPaused();
    }

    private static void toggleProfiling() {
        ClientPerfReportState.Snapshot snapshot = ClientPerfReportState.snapshot();
        boolean nextProfiling = !effectiveProfiling(snapshot);
        if (PerfControlClient.send(PerfControlPayload.Action.TOGGLE)) {
            requestedProfiling = nextProfiling;
        }
    }

    public static void clear() {
        requestedProfiling = null;
    }

    private record HorizontalBounds(int x, int width) {
    }
}
