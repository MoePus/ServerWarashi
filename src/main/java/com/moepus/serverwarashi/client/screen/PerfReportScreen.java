package com.moepus.serverwarashi.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

import static com.moepus.serverwarashi.client.screen.PerfReportStyle.*;

public final class PerfReportScreen extends Screen {
    private PerfReportData report;
    private boolean profiling;
    private final EnumMap<Tab, PerfReportTabState> states = new EnumMap<>(Tab.class);
    private final List<Hit> hits = new ArrayList<>();
    private Tab selectedTab = Tab.CHUNKS;
    private int contentHeight;

    public PerfReportScreen(PerfReportData report, boolean profiling) {
        super(Component.translatable("screen.serverwarashi.profiler.title"));
        this.report = report;
        this.profiling = profiling;
        for (Tab tab : Tab.values()) {
            states.put(tab, new PerfReportTabState());
        }
    }

    public void updateReport(PerfReportData report, boolean profiling) {
        this.report = report;
        this.profiling = profiling;
        states.values().forEach(PerfReportTabState::refresh);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);
        PerfReportGeometry geometry = geometry();
        List<PerfReportRows.Row> rows = visibleRows();
        PerfReportTabState state = state();
        contentHeight = Math.max(ROW_HEIGHT, rows.size() * ROW_HEIGHT);
        state.scroll = clampScroll(state.scroll, contentHeight, geometry.viewHeight());
        hits.clear();

        renderPanel(graphics, geometry);
        renderTitle(graphics, geometry, mouseX, mouseY);
        renderControls(graphics, geometry, mouseX, mouseY);
        renderSummary(graphics, geometry);
        renderTableHeader(graphics, geometry, mouseX, mouseY);
        String tooltip = renderRows(graphics, geometry, rows, mouseX, mouseY);
        renderScrollbar(graphics, geometry);
        renderTooltip(graphics, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                activate(hit);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PerfReportGeometry geometry = geometry();
        PerfReportTabState state = state();
        state.scroll = clampScroll(state.scroll - (int) Math.signum(scrollY) * ROW_HEIGHT * 3,
                contentHeight, geometry.viewHeight());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int step = switch (keyCode) {
            case InputConstants.KEY_UP -> -ROW_HEIGHT;
            case InputConstants.KEY_DOWN -> ROW_HEIGHT;
            case InputConstants.KEY_PAGEUP -> -geometry().viewHeight();
            case InputConstants.KEY_PAGEDOWN -> geometry().viewHeight();
            case InputConstants.KEY_HOME -> -state().scroll;
            case InputConstants.KEY_END -> contentHeight;
            default -> 0;
        };
        if (step == 0) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        state().scroll = clampScroll(state().scroll + step, contentHeight, geometry().viewHeight());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderPanel(GuiGraphics graphics, PerfReportGeometry geometry) {
        graphics.fill(geometry.panelX(), geometry.panelY(), geometry.panelRight(), geometry.panelBottom(), PANEL);
        graphics.renderOutline(geometry.panelX(), geometry.panelY(), geometry.panelWidth(), geometry.panelHeight(), BORDER);
        graphics.fill(geometry.panelX(), geometry.panelY(), geometry.panelRight(),
                geometry.panelY() + TITLE_HEIGHT, HEADER);
    }

    private void renderTitle(GuiGraphics graphics, PerfReportGeometry geometry, int mouseX, int mouseY) {
        graphics.drawString(font, title, geometry.contentX(), geometry.panelY() + 8, ACCENT, true);
        String close = "x";
        int closeX = geometry.panelRight() - PADDING - 14;
        Hit hit = new Hit(Action.CLOSE, null,
                new PerfReportBounds(closeX, geometry.panelY() + 5, 14, 14));
        renderControl(graphics, hit.bounds(), close, false, hit.contains(mouseX, mouseY), hit);
    }

    private void renderControls(GuiGraphics graphics, PerfReportGeometry geometry, int mouseX, int mouseY) {
        int y = geometry.panelY() + TITLE_HEIGHT + 4;
        int tabWidth = Math.min(TAB_WIDTH, Math.max(1, (geometry.contentWidth() - GAP * 2) / 3));
        int x = geometry.contentX();
        for (Tab tab : Tab.values()) {
            Hit hit = new Hit(Action.TAB, tab, new PerfReportBounds(x, y, tabWidth, CONTROL_HEIGHT));
            renderControl(graphics, hit.bounds(), PerfDisplayText.translated(tab.key),
                    selectedTab == tab, hit.contains(mouseX, mouseY), hit);
            x += tabWidth + GAP;
        }
        y += CONTROL_HEIGHT + GAP;
        renderSessionControl(graphics, geometry, y, mouseX, mouseY);
        renderPageControls(graphics, geometry, y + CONTROL_HEIGHT + GAP, mouseX, mouseY);
    }

    private void renderTableHeader(GuiGraphics graphics, PerfReportGeometry geometry, int mouseX, int mouseY) {
        var context = tableContext(geometry);
        PerfReportTableRenderer.renderHeader(graphics, font, context,
                new PerfReportTableRenderer.HeaderState(state().sort, state().stale, mouseX, mouseY));
        for (var header : PerfReportTableRenderer.sortHeaders(context)) {
            hits.add(new Hit(Action.SORT, header.sort(), header.bounds()));
        }
    }

    private void renderSessionControl(GuiGraphics graphics, PerfReportGeometry geometry,
                                      int y, int mouseX, int mouseY) {
        PerfReportBounds bounds = new PerfReportBounds(
                geometry.contentX(), y, SESSION_BUTTON_WIDTH, CONTROL_HEIGHT);
        Hit hit = new Hit(Action.TOGGLE_PROFILE, null, bounds);
        String key = profiling ? "screen.serverwarashi.profiler.stop" : "screen.serverwarashi.profiler.start";
        renderControl(graphics, bounds, PerfDisplayText.translated(key), profiling,
                hit.contains(mouseX, mouseY), hit);
        String status = PerfTimeText.session(report, profiling, System.currentTimeMillis()).getString();
        int statusX = bounds.right() + GAP * 2;
        graphics.drawString(font, fit(font, status, Math.max(1, geometry.contentRight() - statusX)),
                statusX, y + 4, MUTED, true);
    }

    private void renderPageControls(GuiGraphics graphics,
                                    PerfReportGeometry geometry,
                                    int y,
                                    int mouseX,
                                    int mouseY) {
        int x = geometry.contentX();
        Hit previous = new Hit(Action.PREVIOUS_PAGE, null,
                new PerfReportBounds(x, y, PAGE_BUTTON_WIDTH, CONTROL_HEIGHT));
        renderControl(graphics, previous.bounds(), "<", false, previous.contains(mouseX, mouseY), previous);
        int pages = pageCount();
        String label = PerfDisplayText.translated("screen.serverwarashi.profiler.page",
                state().page + 1, pages);
        int labelX = x + PAGE_BUTTON_WIDTH + GAP;
        graphics.drawString(font, label, labelX, y + 4, MUTED, true);
        int nextX = Math.min(labelX + font.width(label) + GAP,
                geometry.contentRight() - PAGE_BUTTON_WIDTH);
        Hit next = new Hit(Action.NEXT_PAGE, null,
                new PerfReportBounds(nextX, y, PAGE_BUTTON_WIDTH, CONTROL_HEIGHT));
        renderControl(graphics, next.bounds(), ">", false, next.contains(mouseX, mouseY), next);
    }

    private void renderControl(GuiGraphics graphics,
                               PerfReportBounds bounds,
                               String label,
                               boolean selected,
                               boolean hovered,
                               Hit hit) {
        int color = selected ? SELECTED : hovered ? HOVER : CONTROL;
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), color);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), BORDER);
        String fitted = fit(font, label, Math.max(1, bounds.width() - 4));
        int textX = bounds.x() + (bounds.width() - font.width(fitted)) / 2;
        graphics.drawString(font, fitted, textX, bounds.y() + 4, selected ? 0xFFFFFFFF : TEXT, true);
        hits.add(hit);
    }

    private void renderSummary(GuiGraphics graphics, PerfReportGeometry geometry) {
        String key = selectedTab == Tab.TICKABLES
                ? "screen.serverwarashi.profiler.summary.tickables"
                : "screen.serverwarashi.profiler.summary";
        String text = PerfDisplayText.translated(key,
                String.format(Locale.ROOT, "%.2f", report.serverMspt()),
                String.format(Locale.ROOT, "%.1f", report.elapsedMillis() / 1000.0),
                String.format(Locale.ROOT, "%,d", report.tickCount()), report.intervalMicros(),
                String.format(Locale.ROOT, "%,d", omittedForTab()),
                String.format(Locale.ROOT, "%.2f", report.recentGcMspt()), report.recentGcMaxMillis());
        int y = geometry.panelY() + TITLE_HEIGHT + CONTROLS_HEIGHT + 3;
        graphics.drawString(font, fit(font, text, geometry.contentWidth()), geometry.contentX(), y, MUTED, true);
    }

    private String renderRows(GuiGraphics graphics,
                              PerfReportGeometry geometry,
                              List<PerfReportRows.Row> rows,
                              int mouseX,
                              int mouseY) {
        String tooltip = null;
        graphics.enableScissor(geometry.contentX(), geometry.rowsY(), geometry.contentRight(), geometry.rowsBottom());
        int y = geometry.rowsY() - state().scroll;
        if (rows.isEmpty()) {
            graphics.drawString(font, PerfDisplayText.translated("screen.serverwarashi.profiler.no_data"),
                    geometry.contentX() + 2, y + 3, MUTED, true);
        }
        for (int index = 0; index < rows.size(); index++) {
            String rowTooltip = renderRow(graphics, geometry, rows.get(index), index, y, mouseX, mouseY);
            if (rowTooltip != null) {
                tooltip = rowTooltip;
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        return tooltip;
    }

    private String renderRow(GuiGraphics graphics,
                             PerfReportGeometry geometry,
                             PerfReportRows.Row row,
                             int index,
                             int y,
                             int mouseX,
                             int mouseY) {
        if (y + ROW_HEIGHT <= geometry.rowsY() || y >= geometry.rowsBottom()) {
            return null;
        }
        var context = new PerfReportTableRenderer.RowContext(tableContext(geometry), row, y);
        List<Hit> rowHits = rowHits(row, geometry, y);
        boolean tooltipHovered = row.tooltip() != null
                && PerfReportTableRenderer.labelBounds(font, context).contains(mouseX, mouseY);
        boolean hovered = tooltipHovered || rowHits.stream().anyMatch(hit -> hit.contains(mouseX, mouseY));
        if (hovered || index % 2 == 0) {
            graphics.fill(geometry.contentX(), y, geometry.contentRight(), y + ROW_HEIGHT,
                    hovered ? HOVER : ROW_ALT);
        }
        PerfReportTableRenderer.renderRow(graphics, font, context);
        hits.addAll(rowHits);
        return tooltipHovered ? row.tooltip() : null;
    }

    private void renderTooltip(GuiGraphics graphics, String tooltip, int mouseX, int mouseY) {
        if (tooltip == null) {
            return;
        }
        int tooltipWidth = Math.max(1, Math.min(TOOLTIP_MAX_WIDTH, width - TOOLTIP_MARGIN));
        graphics.renderTooltip(font, font.split(Component.literal(tooltip), tooltipWidth), mouseX, mouseY);
    }

    private List<Hit> rowHits(PerfReportRows.Row row, PerfReportGeometry geometry, int y) {
        List<Hit> result = new ArrayList<>(2);
        if (row.teleportCommand() != null) {
            var context = new PerfReportTableRenderer.RowContext(tableContext(geometry), row, y);
            PerfReportBounds bounds = PerfReportTableRenderer.coordinateBounds(font, context);
            if (bounds != null) {
                result.add(new Hit(Action.TELEPORT, row.teleportCommand(), bounds));
            }
        }
        if (row.expandable()) {
            int clickWidth = PerfReportTableRenderer.labelWidth(tableContext(geometry));
            result.add(new Hit(Action.EXPAND, row.expansionKey(),
                    new PerfReportBounds(geometry.contentX(), y, clickWidth, ROW_HEIGHT)));
        }
        return result;
    }

    private void renderScrollbar(GuiGraphics graphics, PerfReportGeometry geometry) {
        int maxScroll = Math.max(0, contentHeight - geometry.viewHeight());
        if (maxScroll == 0) {
            return;
        }
        int thumbHeight = Math.max(12, geometry.viewHeight() * geometry.viewHeight() / contentHeight);
        int travel = geometry.viewHeight() - thumbHeight;
        int thumbY = geometry.rowsY() + Math.round((float) travel * state().scroll / maxScroll);
        int x = geometry.contentRight() + 3;
        graphics.fill(x, geometry.rowsY(), x + SCROLLBAR_WIDTH, geometry.rowsBottom(), SEPARATOR);
        graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, BORDER);
    }

    private void activate(Hit hit) {
        switch (hit.action()) {
            case CLOSE -> onClose();
            case TAB -> selectedTab = (Tab) hit.target();
            case SORT -> selectSort((PerfReportRows.SortKey) hit.target());
            case EXPAND -> toggleExpanded((String) hit.target());
            case TELEPORT -> sendCommand((String) hit.target());
            case TOGGLE_PROFILE -> toggleProfiling();
            case PREVIOUS_PAGE -> changePage(-1);
            case NEXT_PAGE -> changePage(1);
        }
    }

    private void selectSort(PerfReportRows.SortKey sort) {
        PerfReportTabState state = state();
        state.resort(sort);
        state.rows = buildRows();
        state.rememberOrder();
    }

    private void toggleExpanded(String key) {
        if (!state().expanded.remove(key)) {
            state().expanded.add(key);
        }
        state().rows = null;
    }

    private List<PerfReportRows.Row> visibleRows() {
        List<PerfReportRows.Row> rows = allRows();
        state().page = PerfReportPager.clampPage(state().page, rows.size());
        return PerfReportPager.page(rows, state().page);
    }

    private List<PerfReportRows.Row> allRows() {
        if (state().rows == null) {
            state().rows = buildRows();
            state().rememberOrder();
        }
        return state().rows;
    }

    private int pageCount() {
        return PerfReportPager.pageCount(allRows().size());
    }

    private void changePage(int delta) {
        state().page = PerfReportPager.clampPage(state().page + delta, allRows().size());
        state().scroll = 0;
    }

    private List<PerfReportRows.Row> buildRows() {
        return switch (selectedTab) {
            case CHUNKS -> PerfReportRows.chunks(report, state().sort, state().expanded, state().stableOrder);
            case TICKABLES -> PerfReportRows.tickables(report, state().sort, state().stableOrder);
            case PLAYERS -> PerfReportRows.players(report, state().sort, state().expanded, state().stableOrder);
        };
    }

    private void sendCommand(String command) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand(command);
        }
    }

    private void toggleProfiling() {
        sendCommand(profiling ? "warashi perf analyze stop" : "warashi perf analyze start");
    }

    private PerfReportTableRenderer.TableContext tableContext(PerfReportGeometry geometry) {
        return new PerfReportTableRenderer.TableContext(geometry, selectedTab.layout);
    }

    private int omittedForTab() {
        return switch (selectedTab) {
            case CHUNKS -> report.omittedChunks();
            case TICKABLES -> report.omittedTickables();
            case PLAYERS -> report.omittedOwnerSources();
        };
    }

    private PerfReportGeometry geometry() {
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(1, width - MARGIN * 2));
        int panelHeight = Math.max(1, height - MARGIN * 2);
        int panelX = Math.max(0, (width - panelWidth) / 2);
        int panelY = Math.max(0, (height - panelHeight) / 2);
        int contentX = panelX + PADDING;
        int contentWidth = Math.max(1, panelWidth - PADDING * 2 - SCROLLBAR_WIDTH - 3);
        int rowsY = panelY + TITLE_HEIGHT + CONTROLS_HEIGHT + SUMMARY_HEIGHT + HEADER_HEIGHT;
        int rowsBottom = Math.max(rowsY + 1, panelY + panelHeight - PADDING);
        return new PerfReportGeometry(panelX, panelY, panelWidth, panelHeight,
                contentX, contentWidth, rowsY, rowsBottom);
    }

    private PerfReportTabState state() {
        return states.get(selectedTab);
    }

    private enum Tab {
        CHUNKS("screen.serverwarashi.profiler.tab.chunks", PerfReportTableRenderer.Layout.CHUNKS),
        TICKABLES("screen.serverwarashi.profiler.tab.tickables", PerfReportTableRenderer.Layout.TICKABLES),
        PLAYERS("screen.serverwarashi.profiler.tab.players", PerfReportTableRenderer.Layout.PLAYERS);

        private final String key;
        private final PerfReportTableRenderer.Layout layout;

        Tab(String key, PerfReportTableRenderer.Layout layout) {
            this.key = key;
            this.layout = layout;
        }
    }

    private enum Action {
        CLOSE,
        TAB,
        SORT,
        EXPAND,
        TELEPORT,
        TOGGLE_PROFILE,
        PREVIOUS_PAGE,
        NEXT_PAGE
    }

    private record Hit(Action action, Object target, PerfReportBounds bounds) {
        boolean contains(double mouseX, double mouseY) {
            return bounds.contains(mouseX, mouseY);
        }
    }

}
