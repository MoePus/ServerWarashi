package com.moepus.serverwarashi.client.screen;

record PerfReportBounds(int x, int y, int width, int height) {
    int right() {
        return x + width;
    }

    int bottom() {
        return y + height;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
    }
}
