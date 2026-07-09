package com.moepus.serverwarashi.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class TableReportPanelScreen extends Screen {
    private static final int MARGIN = 24;
    private static final int PADDING = 12;
    private static final int HEADER_H = 24;
    private static final int ROW_H = TableReportTableRenderer.ROW_H;
    private static final int SCROLLBAR_W = 4;
    private static final int MAX_PANEL_W = 560;
    private static final int SECTION_GAP = TableReportTableRenderer.SECTION_GAP;
    private static final int CLOSE_W = 44;
    private static final int CLOSE_H = 14;
    private static final int ACTION_GAP = TableReportTableRenderer.ACTION_GAP;
    private static final long COPY_NOTICE_MS = 1_200L;

    private static final int BACKDROP = 0xC0101012;
    private static final int PANEL_BG = 0xFF141416;
    static final int BORDER = 0xFF3B6EA5;
    private static final int HEADER_BG = 0xFF1E2A38;
    private static final int TITLE = 0xFF7FE0FF;
    private static final int COL_HEADER = TableReportTableRenderer.COL_HEADER;
    private static final int ROW_TEXT = TableReportTableRenderer.ROW_TEXT;
    private static final int SEP = TableReportTableRenderer.SEP;
    static final int CLOSE_BG = 0xFF24323F;
    static final int CLOSE_BG_HOVER = 0xFF3B6EA5;
    private static final int CLOSE_TEXT = 0xFFCFE3FF;
    private static final int CLOSE_TEXT_HOVER = 0xFFFFFFFF;
    static final int ACTION_TEXT = 0xFFCFE3FF;

    private static final String CLOSE_LABEL = "Close";
    private static final String COPY_LABEL = "Copy";

    private final List<TableReportActions.ActionHit> actionHits = new ArrayList<>();
    private int scroll;
    private int contentHeight;
    private TableReportActions.ActionHit hoveredAction;
    private long copiedUntilMillis;

    protected TableReportPanelScreen(Component title) {
        super(title);
    }

    protected abstract TableReport currentReport();

    /**
     * 固定报告的面板：把单个 TableReport 包成可显示面板，免去一次性子类。
     */
    public static TableReportPanelScreen of(TableReport report) {
        return new TableReportPanelScreen(Component.literal(report.title())) {
            @Override
            protected TableReport currentReport() {
                return report;
            }
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TableReport report = currentReport();
        graphics.fill(0, 0, width, height, BACKDROP);
        Geometry geometry = geometry();
        int viewHeight = viewHeight(geometry);
        contentHeight = TableReportTableRenderer.contentHeight(report);
        scroll = TableReportLayout.clampScroll(scroll, contentHeight, viewHeight);
        int closeX = geometry.panelX + geometry.panelW - PADDING - CLOSE_W;
        int closeY = geometry.panelY + 5;
        boolean closeHovered = isInsideClose(mouseX, mouseY, closeX, closeY);
        actionHits.clear();
        hoveredAction = null;

        graphics.fill(geometry.panelX, geometry.panelY, geometry.panelX + geometry.panelW, geometry.panelY + geometry.panelH, PANEL_BG);
        graphics.renderOutline(geometry.panelX, geometry.panelY, geometry.panelW, geometry.panelH, BORDER);
        graphics.fill(geometry.panelX, geometry.panelY, geometry.panelX + geometry.panelW, geometry.panelY + HEADER_H, HEADER_BG);
        graphics.drawString(font,
                fitToWidth(report.title(), Math.max(1, closeX - geometry.contentX - TableReportLayout.CELL_INSET)),
                geometry.contentX, geometry.panelY + 8, TITLE, true);

        graphics.enableScissor(geometry.panelX, geometry.contentY, geometry.panelX + geometry.panelW, geometry.contentBottom);
        renderContent(graphics, geometry, report, mouseX, mouseY);
        graphics.disableScissor();
        renderScrollbar(graphics, geometry, viewHeight);
        renderCopiedNotice(graphics, geometry);
        renderCloseButton(graphics, closeX, closeY, closeHovered);
        if (hoveredAction != null) {
            graphics.renderTooltip(font, Component.literal(hoveredAction.tooltip()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Geometry geometry = geometry();
        contentHeight = TableReportTableRenderer.contentHeight(currentReport());
        scroll = TableReportLayout.clampScroll(
                scroll - (int) Math.signum(scrollY) * ROW_H * 3,
                contentHeight,
                viewHeight(geometry)
        );
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Geometry geometry = geometry();
        int closeX = geometry.panelX + geometry.panelW - PADDING - CLOSE_W;
        int closeY = geometry.panelY + 5;
        if (button == 0 && isInsideClose(mouseX, mouseY, closeX, closeY)) {
            onClose();
            return true;
        }
        if (button == 0) {
            TableReportActions.ActionHit hit = TableReportActions.hitAction(actionHits, mouseX, mouseY);
            if (hit != null) {
                runAction(hit);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Geometry geometry = geometry();
        contentHeight = TableReportTableRenderer.contentHeight(currentReport());
        int viewHeight = viewHeight(geometry);
        switch (keyCode) {
            case InputConstants.KEY_UP -> scrollTo(scroll - ROW_H, viewHeight);
            case InputConstants.KEY_DOWN -> scrollTo(scroll + ROW_H, viewHeight);
            case InputConstants.KEY_PAGEUP -> scrollTo(scroll - viewHeight, viewHeight);
            case InputConstants.KEY_PAGEDOWN -> scrollTo(scroll + viewHeight, viewHeight);
            case InputConstants.KEY_HOME -> scrollTo(0, viewHeight);
            case InputConstants.KEY_END -> scrollTo(contentHeight, viewHeight);
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        // 打开此界面不暂停游戏（便于实时观察）
        return false;
    }

    private void renderContent(GuiGraphics graphics, Geometry geometry, TableReport report, int mouseX, int mouseY) {
        int y = geometry.contentY - scroll;
        for (String metadata : report.metadata()) {
            graphics.drawString(font, fitToWidth(metadata, geometry.contentW), geometry.contentX, y, COL_HEADER, true);
            y += ROW_H;
        }
        if (!report.metadata().isEmpty()) {
            y += SECTION_GAP;
        }

        boolean hasRows = false;
        for (TableReport.Section section : report.sections()) {
            if (section.rows().isEmpty()) {
                continue;
            }
            hasRows = true;
            y = renderSection(graphics, geometry, section, y, mouseX, mouseY);
            y += SECTION_GAP;
        }

        if (!hasRows) {
            graphics.drawString(font, "No data", geometry.contentX, y, ROW_TEXT, true);
        }
    }

    private int renderSection(GuiGraphics graphics, Geometry geometry, TableReport.Section section, int y, int mouseX, int mouseY) {
        int actionArea = TableReportActions.actionAreaWidth(font, section);
        int buttonW = TableReportActions.buttonWidth(font, COPY_LABEL);
        int buttonX = geometry.contentX + geometry.contentW - buttonW;
        renderActionButton(graphics, geometry,
                new TableReportActions.Button(COPY_LABEL, TableReportActions.ActionKind.COPY, null, section, "Copy"),
                buttonX, y + 1, mouseX, mouseY);
        return TableReportTableRenderer.renderSection(
                graphics,
                font,
                section,
                new TableReportTableRenderer.SectionRenderContext(geometry.contentX, y, geometry.contentW, actionArea),
                (rowIndex, rowY) -> renderActionButtons(graphics, geometry,
                        TableReportActions.rowButtons(section, rowIndex),
                        rowY + 1, mouseX, mouseY)
        );
    }

    private void renderActionButtons(GuiGraphics graphics, Geometry geometry, List<TableReport.Button> buttons,
                                     int y, int mouseX, int mouseY) {
        int x = geometry.contentX + geometry.contentW - TableReportActions.buttonsWidth(font, buttons);
        for (TableReport.Button button : buttons) {
            int width = TableReportActions.buttonWidth(font, button.label());
            renderActionButton(graphics, geometry,
                    new TableReportActions.Button(button.label(), TableReportActions.ActionKind.COMMAND,
                            button.command(), null, TableReportActions.tooltipFor(button.label())),
                    x, y, mouseX, mouseY);
            x += width + ACTION_GAP;
        }
    }

    private void renderActionButton(GuiGraphics graphics, Geometry geometry, TableReportActions.Button button,
                                    int x, int y, int mouseX, int mouseY) {
        TableReportActions.ActionHit hit = TableReportActions.renderButton(graphics, font, actionHits,
                button, x, y, mouseX, mouseY, geometry.contentY, geometry.contentBottom);
        if (hit != null) {
            hoveredAction = hit;
        }
    }

    private void renderScrollbar(GuiGraphics graphics, Geometry geometry, int viewHeight) {
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        if (maxScroll == 0) {
            return;
        }
        int trackX = geometry.panelX + geometry.panelW - PADDING - SCROLLBAR_W;
        int trackY = geometry.contentY;
        int thumbH = Math.max(12, viewHeight * viewHeight / Math.max(viewHeight, contentHeight));
        thumbH = Math.min(viewHeight, thumbH);
        int thumbTravel = Math.max(0, viewHeight - thumbH);
        int thumbY = trackY + Math.round((float) thumbTravel * scroll / maxScroll);
        graphics.fill(trackX, trackY, trackX + SCROLLBAR_W, trackY + viewHeight, SEP);
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, BORDER);
    }

    private void renderCopiedNotice(GuiGraphics graphics, Geometry geometry) {
        if (System.currentTimeMillis() >= copiedUntilMillis) {
            return;
        }
        String text = "Copied";
        int textX = geometry.panelX + geometry.panelW - PADDING - font.width(text);
        int textY = geometry.panelY + geometry.panelH - PADDING - ROW_H;
        graphics.drawString(font, text, textX, textY, CLOSE_TEXT_HOVER, true);
    }

    private void renderCloseButton(GuiGraphics graphics, int closeX, int closeY, boolean closeHovered) {
        graphics.fill(closeX, closeY, closeX + CLOSE_W, closeY + CLOSE_H, closeHovered ? CLOSE_BG_HOVER : CLOSE_BG);
        graphics.renderOutline(closeX, closeY, CLOSE_W, CLOSE_H, BORDER);
        int textX = closeX + (CLOSE_W - font.width(CLOSE_LABEL)) / 2;
        graphics.drawString(font, CLOSE_LABEL, textX, closeY + 3, closeHovered ? CLOSE_TEXT_HOVER : CLOSE_TEXT, true);
    }

    private Geometry geometry() {
        int availableW = Math.max(1, width - MARGIN * 2);
        int panelW = Math.min(availableW, MAX_PANEL_W);
        int panelX = Math.max(0, (width - panelW) / 2);
        int panelH = Math.max(HEADER_H + PADDING + ROW_H, height - MARGIN * 2);
        int panelY = Math.max(0, Math.min(MARGIN, height - panelH));
        int contentX = panelX + PADDING;
        int contentY = panelY + HEADER_H;
        int contentBottom = panelY + panelH - PADDING;
        int contentW = Math.max(1, panelW - PADDING * 2 - SCROLLBAR_W);
        return new Geometry(panelX, panelY, panelW, panelH, contentX, contentY, contentBottom, contentW);
    }

    private int viewHeight(Geometry geometry) {
        return Math.max(1, geometry.contentBottom - geometry.contentY);
    }

    private void scrollTo(int nextScroll, int viewHeight) {
        scroll = TableReportLayout.clampScroll(nextScroll, contentHeight, viewHeight);
    }

    private String fitToWidth(String text, int maxWidth) {
        return TableReportTableRenderer.fitToWidth(font, text, maxWidth);
    }

    private void runAction(TableReportActions.ActionHit hit) {
        if (hit.kind() == TableReportActions.ActionKind.COPY) {
            Minecraft.getInstance().keyboardHandler.setClipboard(TableReportActions.sectionClipboardText(hit.section()));
            copiedUntilMillis = System.currentTimeMillis() + COPY_NOTICE_MS;
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand(hit.command());
        }
    }

    private static boolean isInsideClose(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + CLOSE_W && mouseY >= y && mouseY < y + CLOSE_H;
    }

    private record Geometry(int panelX, int panelY, int panelW, int panelH,
                            int contentX, int contentY, int contentBottom, int contentW) {
    }
}
