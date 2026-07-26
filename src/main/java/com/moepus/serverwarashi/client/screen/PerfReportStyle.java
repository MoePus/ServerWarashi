package com.moepus.serverwarashi.client.screen;

import net.minecraft.client.gui.Font;

final class PerfReportStyle {
    static final int MARGIN = 16;
    static final int PADDING = 12;
    static final int MAX_PANEL_WIDTH = 760;
    static final int TITLE_HEIGHT = 25;
    static final int CONTROLS_HEIGHT = 61;
    static final int SUMMARY_HEIGHT = 16;
    static final int HEADER_HEIGHT = 15;
    static final int ROW_HEIGHT = 14;
    static final int SCROLLBAR_WIDTH = 4;
    static final int TAB_WIDTH = 72;
    static final int PAGE_BUTTON_WIDTH = 20;
    static final int SESSION_BUTTON_WIDTH = 48;
    static final int CONTROL_HEIGHT = 16;
    static final int GAP = 3;
    static final int INDENT = 12;

    static final int BACKDROP = 0xC0101012;
    static final int PANEL = 0xFF161719;
    static final int BORDER = 0xFF4D769F;
    static final int HEADER = 0xFF202B35;
    static final int SELECTED = 0xFF355B7A;
    static final int CONTROL = 0xFF252A2E;
    static final int HOVER = 0xFF31404B;
    static final int TEXT = 0xFFE6E9EC;
    static final int MUTED = 0xFFAEB7BF;
    static final int ACCENT = 0xFF85D6F4;
    static final int LINK = 0xFF55DDE0;
    static final int ROW_ALT = 0x16FFFFFF;
    static final int SEPARATOR = 0xFF34383C;

    private PerfReportStyle() {
    }

    static int clampScroll(int value, int contentHeight, int viewHeight) {
        return Math.max(0, Math.min(value, Math.max(0, contentHeight - viewHeight)));
    }

    static String fit(Font font, String value, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(value) <= width) {
            return value;
        }
        String suffix = "...";
        if (font.width(suffix) > width) {
            return font.plainSubstrByWidth(value, width);
        }
        int available = Math.max(0, width - font.width(suffix));
        return font.plainSubstrByWidth(value, available) + suffix;
    }
}
