package com.moepus.serverwarashi.client.screen;

record PerfReportGeometry(int panelX,
                          int panelY,
                          int panelWidth,
                          int panelHeight,
                          int contentX,
                          int contentWidth,
                          int rowsY,
                          int rowsBottom) {
    private static final int MSPT_MAX_WIDTH = 70;
    private static final int P98_MAX_WIDTH = 70;
    private static final int SAMPLES_MAX_WIDTH = 68;
    private static final int TYPE_MAX_WIDTH = 68;
    private static final int COUNT_MAX_WIDTH = 34;
    private static final int LAST_LOGIN_MAX_WIDTH = 74;
    private static final int POSITION_MAX_WIDTH = 150;

    int panelRight() {
        return panelX + panelWidth;
    }

    int panelBottom() {
        return panelY + panelHeight;
    }

    int contentRight() {
        return contentX + contentWidth;
    }

    int viewHeight() {
        return Math.max(1, rowsBottom - rowsY);
    }

    int labelWidth(int auxiliaryWidth) {
        return Math.max(1, contentWidth - positionWidth()
                - msptWidth() - p98Width() - samplesWidth() - auxiliaryWidth);
    }

    int msptWidth() {
        return numericWidth(MSPT_MAX_WIDTH);
    }

    int p98Width() {
        return numericWidth(P98_MAX_WIDTH);
    }

    int samplesWidth() {
        return numericWidth(SAMPLES_MAX_WIDTH);
    }

    int typeWidth() {
        return Math.min(TYPE_MAX_WIDTH, Math.max(1, contentWidth / 8));
    }

    int countWidth() {
        return Math.min(COUNT_MAX_WIDTH, Math.max(1, contentWidth / 12));
    }

    int lastLoginWidth() {
        return Math.min(LAST_LOGIN_MAX_WIDTH, Math.max(1, contentWidth / 7));
    }

    int positionWidth() {
        return Math.min(POSITION_MAX_WIDTH, Math.max(1, contentWidth / 5));
    }

    private int numericWidth(int maximum) {
        return Math.min(maximum, Math.max(1, contentWidth / 7));
    }
}
